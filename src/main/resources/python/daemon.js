const fs = require('fs');
const path = require('path');
const readline = require('readline');
const vm = require('vm');

const ERROR_CODES = {
  REPO_NOT_FOUND: 1004,
  FILE_NOT_FOUND: 2002,
  PARSE_FAILED: 2003,
  FILE_EXCLUDED: 2005,
  DAEMON_ERROR: 2004
};

const EXCLUDED_DIRS = new Set([
  '.venv',
  'venv',
  '__pycache__',
  '.tox',
  'build',
  'dist',
  'node_modules',
  '.mypy_cache',
  '.pytest_cache'
]);

const DEFAULT_PYTHON_VERSION = '3.10';
const FALLBACK_PYTHON_VERSIONS = ['3.12', '3.11', '3.10', '3.9', '3.8'];
const SINGLE_QUOTE = '\u0027';
const DOUBLE_QUOTE = String.fromCharCode(34);
const BACKTICK = String.fromCharCode(96);

let state = {
  repoRoot: null,
  pythonVersion: DEFAULT_PYTHON_VERSION,
  configSource: 'default',
  moduleIndex: new Map(),
  extraModuleRoots: [],
  pyright: null,
  pyrightApi: null,
  fileSystem: null,
  serviceProvider: null,
  importResolver: null,
  evaluation: null,
  program: null,
  fileUriMap: new Map(),
  moduleDeclarations: new Map(),
  moduleImports: new Map(),
  aliasMembers: new Map(),
  aliasStack: new Set()
};

function writeResponse(id, result) {
  process.stdout.write(JSON.stringify({ id, ok: true, result }) + '\n');
}

function writeError(id, code, message) {
  process.stdout.write(JSON.stringify({ id, ok: false, error: { code, message } }) + '\n');
}

function normalizePath(p) {
  if (!p) {
    return p;
  }
  return path.resolve(p);
}

/**
 * Whether a path lies under an excluded directory of the repository. Only the part of the path
 * within the repository root is examined, so a repository that itself sits under a directory named
 * like an excluded one is still read.
 */
function shouldSkipPath(filePath) {
  if (!filePath) {
    return false;
  }
  let withinRepo = filePath;
  if (state.repoRoot) {
    const rel = path.relative(state.repoRoot, path.resolve(filePath));
    const outside = rel === '..' || rel.startsWith('..' + path.sep) || path.isAbsolute(rel);
    if (!outside) {
      withinRepo = rel;
    }
  }
  const parts = withinRepo.replace(/\\/g, '/').split('/');
  for (const part of parts) {
    if (EXCLUDED_DIRS.has(part)) {
      return true;
    }
  }
  return false;
}

function scanRepo(root) {
  const results = [];
  const stack = [root];
  while (stack.length > 0) {
    const current = stack.pop();
    if (!current || shouldSkipPath(current)) {
      continue;
    }
    let entries;
    try {
      entries = fs.readdirSync(current, { withFileTypes: true });
    } catch (err) {
      continue;
    }
    for (const entry of entries) {
      const full = path.join(current, entry.name);
      if (entry.isDirectory()) {
        if (!shouldSkipPath(full)) {
          stack.push(full);
        }
      } else if (entry.isFile() && entry.name.toLowerCase().endsWith('.py')) {
        if (!shouldSkipPath(full)) {
          results.push(full);
        }
      }
    }
  }
  return results;
}

/**
 * The directories imports are written from, read off the packages themselves.
 *
 * A package is imported by its name from the directory that holds it. A repository that keeps
 * its code under a directory of its own, `backend/` or `services/api/`, runs with that directory
 * on the import path, and writes `from app.models import User` for `backend/app/models.py`. The
 * directory is the parent of the outermost directory that has an `__init__.py`, which is what
 * makes it the top of a package and not a part of one.
 *
 * Shallowest first, so that where two directories hold a package of one name the one nearer the
 * root supplies it, as it would were both on the import path in the order a project lists them.
 */
function inferImportRoots(repoRoot, files, known) {
  const packageDirs = new Set();
  for (const filePath of files) {
    if (path.basename(filePath).toLowerCase() === '__init__.py') {
      packageDirs.add(path.dirname(filePath));
    }
  }
  const taken = new Set((known || []).map((root) => path.resolve(root)));
  const roots = new Set();
  for (const dir of packageDirs) {
    if (packageDirs.has(path.dirname(dir))) {
      continue;
    }
    const root = path.dirname(dir);
    if (root !== repoRoot && root.startsWith(repoRoot + path.sep) && !taken.has(root)) {
      roots.add(root);
    }
  }
  const depth = (dir) => dir.split(path.sep).length;
  return Array.from(roots).sort((a, b) => depth(a) - depth(b) || a.localeCompare(b));
}

function moduleNameForPath(filePath, root) {
  let rel;
  try {
    rel = path.relative(root, filePath);
  } catch (err) {
    return '';
  }
  if (rel.startsWith('..')) {
    return '';
  }
  const withoutExt = rel.replace(/\\/g, '/').replace(/\.py$/i, '');
  return withoutExt.split('/').filter(Boolean).join('.');
}

function moduleBaseNameForPath(filePath) {
  const base = path.basename(filePath);
  return base.replace(/\.py$/i, '');
}

function packageNameForPath(filePath, root) {
  let rel;
  try {
    rel = path.relative(root, filePath);
  } catch (err) {
    return '';
  }
  if (rel.startsWith('..')) {
    return '';
  }
  const dir = path.dirname(rel.replace(/\\/g, '/'));
  if (!dir || dir === '.') {
    return '';
  }
  return dir.split('/').filter(Boolean).join('.');
}

const INIT_SUFFIX = '.__init__';

function buildModuleIndex(repoRoot, extraRoots, filesToIndex) {
  const index = new Map();
  const extraList = Array.isArray(extraRoots) ? extraRoots : [];
  const files = Array.isArray(filesToIndex) ? filesToIndex : scanRepo(repoRoot);
  const packageAliases = [];
  for (const filePath of files) {
    const repoModule = moduleNameForPath(filePath, repoRoot);
    if (repoModule) {
      index.set(repoModule, filePath);
      collectPackageAlias(packageAliases, repoModule, filePath);
    }
    for (const root of extraList) {
      const extraModule = moduleNameForPath(filePath, root);
      if (extraModule) {
        if (!index.has(extraModule)) {
          index.set(extraModule, filePath);
        }
        collectPackageAlias(packageAliases, extraModule, filePath);
      }
    }
  }
  // The directories the packages themselves show imports to be written from, after the ones the
  // project names, and each only for a name nothing before it supplies.
  for (const root of inferImportRoots(repoRoot, files, [repoRoot].concat(extraList))) {
    for (const filePath of files) {
      const inferredModule = moduleNameForPath(filePath, root);
      if (inferredModule) {
        if (!index.has(inferredModule)) {
          index.set(inferredModule, filePath);
        }
        collectPackageAlias(packageAliases, inferredModule, filePath);
      }
    }
  }
  // Second pass so a real `pkg.py` always beats the `pkg/__init__.py` alias regardless of the
  // order files were scanned in. Registering as we went made the winner depend on directory order.
  for (const [packageName, filePath] of packageAliases) {
    if (!index.has(packageName)) {
      index.set(packageName, filePath);
    }
  }
  return index;
}

/**
 * `pkg/__init__.py` IS the module `pkg` in Python, so `from pkg import Thing` must resolve to it.
 *
 * The index recorded it only under `pkg.__init__`, which no import ever writes, so every such
 * import missed and the reference was dropped -- silently, since an unresolved name is simply not
 * an internal dependency. Measured on scrapy: all 8 edges reaching a class declared in an
 * `__init__.py` were same-file, and not one crossed a module. `TextResponse` lost its `Response`
 * base class and `JsonRequest` lost `Request`, which is most of a package's public surface, since
 * `__init__.py` is exactly where a package puts the names it wants imported.
 *
 * Only the lookup key is aliased. Component unique names still carry the `__init__` segment, and
 * must: `resolveModuleSymbol` rebuilds them from the resolved file's own path, so the two agree by
 * construction, and renaming components would change every identifier this library emits.
 */
function collectPackageAlias(into, moduleName, filePath) {
  if (!moduleName.endsWith(INIT_SUFFIX)) {
    return;
  }
  const packageName = moduleName.slice(0, -INIT_SUFFIX.length);
  if (packageName) {
    into.push([packageName, filePath]);
  }
}

function parsePyrightConfig(repoRoot) {
  const configPath = path.join(repoRoot, 'pyrightconfig.json');
  if (!fs.existsSync(configPath)) {
    return null;
  }
  try {
    const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    const version = config && config.pythonVersion ? String(config.pythonVersion) : null;
    const extraPaths = Array.isArray(config && config.extraPaths)
      ? config.extraPaths.map(entry => String(entry))
      : [];
    return { version, extraPaths, source: 'pyrightconfig' };
  } catch (err) {
    return { version: null, extraPaths: [], source: 'pyrightconfig' };
  }
}

function parseArrayValue(text) {
  if (!text) {
    return [];
  }
  const start = text.indexOf('[');
  const end = text.lastIndexOf(']');
  if (start < 0 || end < 0 || end <= start) {
    return [];
  }
  const body = text.slice(start + 1, end);
  const parts = body.split(',');
  const values = [];
  for (let part of parts) {
    part = part.trim();
    if (!part) {
      continue;
    }
    if ((part.startsWith(DOUBLE_QUOTE) && part.endsWith(DOUBLE_QUOTE))
      || (part.startsWith(SINGLE_QUOTE) && part.endsWith(SINGLE_QUOTE))) {
      part = part.slice(1, -1);
    }
    if (part) {
      values.push(part);
    }
  }
  return values;
}

function parsePyproject(repoRoot) {
  const configPath = path.join(repoRoot, 'pyproject.toml');
  if (!fs.existsSync(configPath)) {
    return null;
  }
  try {
    const content = fs.readFileSync(configPath, 'utf8');
    const lines = content.split(/\r?\n/);
    let version = null;
    let extraPaths = [];
    let inPyrightSection = false;
    for (let i = 0; i < lines.length; i += 1) {
      const line = stripComment(lines[i]).trim();
      if (!line) {
        continue;
      }
      if (line.startsWith('[') && line.endsWith(']')) {
        inPyrightSection = line === '[tool.pyright]';
        continue;
      }
      if (!inPyrightSection) {
        continue;
      }
      if (line.startsWith('pythonVersion')) {
        const equalsIndex = line.indexOf('=');
        if (equalsIndex >= 0) {
          let value = line.slice(equalsIndex + 1).trim();
          if ((value.startsWith(DOUBLE_QUOTE) && value.endsWith(DOUBLE_QUOTE))
            || (value.startsWith(SINGLE_QUOTE) && value.endsWith(SINGLE_QUOTE))) {
            value = value.slice(1, -1);
          }
          version = value;
        }
      }
      if (line.startsWith('extraPaths')) {
        const equalsIndex = line.indexOf('=');
        if (equalsIndex < 0) {
          continue;
        }
        let value = line.slice(equalsIndex + 1).trim();
        while (value && !value.includes(']') && i + 1 < lines.length) {
          i += 1;
          const nextPart = stripComment(lines[i]).trim();
          if (!nextPart) {
            continue;
          }
          value += ' ' + nextPart;
        }
        extraPaths = parseArrayValue(value);
      }
    }
    return { version, extraPaths, source: 'pyproject' };
  } catch (err) {
    return { version: null, extraPaths: [], source: 'pyproject' };
  }
}

function resolvePythonVersion(repoRoot, options) {
  if (options && options.pythonVersion) {
    return { version: String(options.pythonVersion), source: 'override', extraPaths: [] };
  }
  const pyright = parsePyrightConfig(repoRoot);
  if (pyright) {
    return { version: pyright.version || DEFAULT_PYTHON_VERSION, source: pyright.source, extraPaths: pyright.extraPaths };
  }
  const pyproject = parsePyproject(repoRoot);
  if (pyproject) {
    return { version: pyproject.version || DEFAULT_PYTHON_VERSION, source: pyproject.source, extraPaths: pyproject.extraPaths };
  }
  return { version: DEFAULT_PYTHON_VERSION, source: 'default', extraPaths: [] };
}

function resolveExtraPaths(repoRoot, configExtraPaths) {
  const result = new Set();
  result.add(repoRoot);
  const srcPath = path.join(repoRoot, 'src');
  if (fs.existsSync(srcPath) && fs.statSync(srcPath).isDirectory()) {
    result.add(srcPath);
  }
  if (Array.isArray(configExtraPaths)) {
    for (const extra of configExtraPaths) {
      if (!extra) {
        continue;
      }
      const resolved = path.isAbsolute(extra) ? extra : path.resolve(repoRoot, extra);
      result.add(resolved);
    }
  }
  return Array.from(result);
}

function splitArgs(text) {
  const result = [];
  let current = '';
  let depth = 0;
  let inString = false;
  let stringChar = '';
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (inString) {
      current += ch;
      if (ch === stringChar && text[i - 1] !== '\\') {
        inString = false;
        stringChar = '';
      }
      continue;
    }
    if (ch === DOUBLE_QUOTE || ch === SINGLE_QUOTE) {
      inString = true;
      stringChar = ch;
      current += ch;
      continue;
    }
    if (ch === '(' || ch === '[' || ch === '{') {
      depth += 1;
      current += ch;
      continue;
    }
    if (ch === ')' || ch === ']' || ch === '}') {
      depth = Math.max(0, depth - 1);
      current += ch;
      continue;
    }
    if (ch === ',' && depth === 0) {
      const trimmed = current.trim();
      if (trimmed) {
        result.push(trimmed);
      }
      current = '';
      continue;
    }
    current += ch;
  }
  const trimmed = current.trim();
  if (trimmed) {
    result.push(trimmed);
  }
  return result;
}

function stripComment(line) {
  const idx = line.indexOf('#');
  if (idx >= 0) {
    return line.slice(0, idx);
  }
  return line;
}

function isTypeCheckingLine(line) {
  if (!line) {
    return false;
  }
  return /^if\s+(typing\.)?TYPE_CHECKING\s*:/.test(line);
}

function importLineStarts(line) {
  if (!line) {
    return false;
  }
  return line.startsWith('import ') || line.startsWith('from ');
}

function stripLineContinuation(line) {
  if (!line) {
    return { text: '', hasContinuation: false };
  }
  const hasContinuation = /\\\s*$/.test(line);
  if (hasContinuation) {
    return {
      text: line.replace(/\\\s*$/, '').trim(),
      hasContinuation: true
    };
  }
  return { text: line.trim(), hasContinuation: false };
}

function countGroupingDepth(line) {
  if (!line) {
    return 0;
  }
  let depth = 0;
  let inString = false;
  let stringChar = '';
  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i];
    if (inString) {
      if (ch === stringChar && line[i - 1] !== '\\') {
        inString = false;
        stringChar = '';
      }
      continue;
    }
    if (ch === DOUBLE_QUOTE || ch === SINGLE_QUOTE) {
      inString = true;
      stringChar = ch;
      continue;
    }
    if (ch === '(' || ch === '[' || ch === '{') {
      depth += 1;
      continue;
    }
    if (ch === ')' || ch === ']' || ch === '}') {
      depth -= 1;
    }
  }
  return depth;
}

function collectImportStatements(fileText) {
  const lines = fileText.split(/\r?\n/);
  const statements = [];
  let typeCheckingIndent = null;
  let collecting = false;
  let buffer = '';
  let depth = 0;

  for (const rawLine of lines) {
    const indent = rawLine.match(/^\s*/)[0].length;
    const trimmed = stripComment(rawLine).trim();

    if (!collecting && typeCheckingIndent !== null && trimmed && indent <= typeCheckingIndent) {
      typeCheckingIndent = null;
    }
    if (!collecting && trimmed && isTypeCheckingLine(trimmed)) {
      typeCheckingIndent = indent;
      continue;
    }
    if (!collecting && !trimmed) {
      continue;
    }

    if (!collecting) {
      const inTypeCheckingBlock = typeCheckingIndent !== null && indent > typeCheckingIndent;
      const allowedContext = indent === 0 || inTypeCheckingBlock;
      if (!allowedContext || !importLineStarts(trimmed)) {
        continue;
      }
      const firstPart = stripLineContinuation(trimmed);
      buffer = firstPart.text;
      depth = countGroupingDepth(buffer);
      collecting = firstPart.hasContinuation || depth > 0;
      if (!collecting && buffer) {
        statements.push(buffer);
        buffer = '';
        depth = 0;
      }
      continue;
    }

    const nextPart = stripLineContinuation(trimmed);
    if (nextPart.text) {
      buffer = buffer ? buffer + ' ' + nextPart.text : nextPart.text;
      depth += countGroupingDepth(nextPart.text);
    }
    if (!nextPart.hasContinuation && depth <= 0) {
      if (buffer) {
        statements.push(buffer);
      }
      collecting = false;
      buffer = '';
      depth = 0;
    }
  }

  if (collecting && buffer) {
    statements.push(buffer);
  }
  return statements;
}

function resolveRelativeModule(nodeModule, level, currentModule) {
  let parts = [];
  if (level && level > 0) {
    parts = currentModule ? currentModule.split('.') : [];
    if (level <= parts.length) {
      parts = parts.slice(0, parts.length - level);
    } else {
      parts = [];
    }
  }
  if (nodeModule) {
    parts = parts.concat(nodeModule.split('.'));
  }
  parts = parts.filter(Boolean);
  return parts.join('.');
}

/**
 * Every import statement in a parsed file, wherever it is written. `moduleScope` holds those that
 * bind a module-level name: the top level and any depth of `if`, `elif`, `else`, `try`, `except`,
 * `finally`, `with`, `for`, `while` or `match` that is not inside a function or class. `nested`
 * holds those written inside a function or class body at any depth.
 */
function collectParsedImportStatements(parseTree, fileText, parseNodeType) {
  const types = parseNodeType || {};
  const moduleScope = [];
  const nested = [];

  function visit(node, inScope) {
    if (!node || typeof node !== 'object' || !node.d) {
      return;
    }
    if (node.nodeType === types.Import || node.nodeType === types.ImportFrom) {
      (inScope ? nested : moduleScope).push(...collectImportStatements(textForNode(node, fileText)));
      return;
    }
    const enters = isFunctionNode(node, parseNodeType) || isClassNode(node, parseNodeType);
    for (const key of Object.keys(node.d)) {
      const child = node.d[key];
      if (Array.isArray(child)) {
        child.forEach(item => visit(item, inScope || enters));
      } else if (child && typeof child === 'object' && child.d) {
        visit(child, inScope || enters);
      }
    }
  }

  visit(parseTree, false);
  return { moduleScope, nested };
}

const DYNAMIC_MODULE_NAME = /^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*$/;

/**
 * The modules a file loads by calling `importlib.import_module("pkg.mod")`, `import_module("pkg.mod")`
 * after `from importlib import import_module`, or `__import__("pkg.mod")`.
 *
 * Only a string literal names a module: an f-string, a variable or a concatenation is not read. The
 * call has to reach `importlib` through an import the file makes. A relative name -- `".x"` -- is
 * resolved only when the package it is relative to is written plainly beside it, as `package=__package__`,
 * `package=__name__` or a string literal; otherwise it is left out.
 *
 * Each is returned as `{ moduleName, start }`, `start` being where the call begins in the file.
 */
function findDynamicImports(parseTree, parseNodeType, importCtx, scopes) {
  const types = parseNodeType || {};
  const found = [];
  const bindsImportlib = name => {
    if (!name) return false;
    return scopes.some(scope => {
      const bound = scope.importedModules.get(name);
      if (bound === 'importlib') return true;
      // `import importlib.util` binds `importlib`, and is recorded under its full dotted name.
      return name === 'importlib' && Array.from(scope.importedModules.keys())
        .some(key => key === 'importlib' || key.startsWith('importlib.'));
    });
  };
  const bindsImportModule = name => name && scopes.some(scope => {
    const symbol = scope.importedSymbols.get(name);
    return !!symbol && symbol.moduleName === 'importlib' && symbol.symbolName === 'import_module';
  });
  const positional = (call, index) => {
    const args = (call.d.args || []).filter(arg => arg && arg.d && !arg.d.name && arg.d.argCategory === 0);
    return args[index] ? args[index].d.valueExpr : null;
  };
  const keyword = (call, name) => {
    const arg = (call.d.args || []).find(each => each && each.d && each.d.name && nameFromNode(each.d.name) === name);
    return arg ? arg.d.valueExpr : null;
  };
  const packageOf = call => {
    const expr = positional(call, 1) || keyword(call, 'package');
    if (!expr) return null;
    const written = nameFromNode(expr);
    if (written === '__package__') return importCtx.packageName || null;
    if (written === '__name__') {
      return importCtx.moduleName === '__init__' ? importCtx.packageName || null : importCtx.fullModuleName;
    }
    return stringLiteralValue(expr, types);
  };
  const resolveName = (call, written) => {
    if (!written.startsWith('.')) {
      return DYNAMIC_MODULE_NAME.test(written) ? written : null;
    }
    const dots = written.length - written.replace(/^\.+/, '').length;
    const rest = written.slice(dots);
    const base = packageOf(call);
    if (!base || (rest && !DYNAMIC_MODULE_NAME.test(rest))) return null;
    const parts = base.split('.');
    if (dots - 1 >= parts.length) return null;
    const resolved = parts.slice(0, parts.length - (dots - 1)).concat(rest ? [rest] : []).join('.');
    // A relative name is only as certain as the package it is relative to, so it counts when the
    // module it works out to is one the repo holds.
    return importCtx.moduleIndex.has(resolved) ? resolved : null;
  };

  function visit(node) {
    if (!node || typeof node !== 'object' || !node.d) return;
    if (node.nodeType === types.Call) {
      const callee = node.d.leftExpr;
      let loads = false;
      if (callee && callee.nodeType === types.Name) {
        const calleeName = nameFromNode(callee);
        loads = calleeName === '__import__' || bindsImportModule(calleeName);
      } else if (callee && callee.nodeType === types.MemberAccess) {
        loads = nameFromNode(callee.d.member) === 'import_module' && bindsImportlib(dottedNameOf(callee.d.leftExpr, types));
      }
      const written = loads ? stringLiteralValue(positional(node, 0), types) : null;
      const moduleName = written ? resolveName(node, written) : null;
      if (moduleName) found.push({ moduleName, start: node.start });
    }
    for (const key of Object.keys(node.d)) {
      const child = node.d[key];
      if (Array.isArray(child)) {
        child.forEach(visit);
      } else if (child && typeof child === 'object' && child.d) {
        visit(child);
      }
    }
  }
  visit(parseTree);
  return found;
}

/**
 * Pyright's import resolver over the repository, built on first use after `initRepo`: the repository
 * root and the import roots the daemon already works from are the places an absolute import is looked
 * for, exactly as they are for the module index. Null when the resolver cannot be built, in which case
 * no file is reported for any import.
 */
function repoImportResolution() {
  if (state.importResolver !== null) {
    return state.importResolver || null;
  }
  try {
    const api = getPyrightApi();
    const nullConsole = new api.console.NullConsole();
    const tempFiles = new api.fileSystem.RealTempFile();
    const fileSystem = api.fileSystem.createFromRealFileSystem(tempFiles, nullConsole);
    const serviceProvider = api.serviceProvider.createServiceProvider(fileSystem, nullConsole, tempFiles);
    const toUri = filePath => toFileUri(api, filePath, serviceProvider);
    const configOptions = new api.configOptions.ConfigOptions(toUri(state.repoRoot));
    // Set outright: the option is not a member of a fresh `ConfigOptions` until it is assigned.
    configOptions.defaultExtraPaths = (state.extraModuleRoots || []).map(toUri);
    const resolver = new api.importResolver.ImportResolver(serviceProvider, configOptions,
      createPythonHost(api, serviceProvider));
    state.importResolver = { resolver, toUri, execEnv: configOptions.getDefaultExecEnvironment() };
  } catch (err) {
    state.importResolver = false;
  }
  return state.importResolver || null;
}

/**
 * The on-disk path of a resolved module file when it is a file of the repository that is read, else
 * null: an empty entry (a namespace package directory has no file), a file outside the repository
 * (the standard library and typeshed stubs live there), a file under an excluded directory (an
 * installed package), and anything that is not a `.py` or `.pyi` file.
 *
 * Pyright prefers a stub to the module it describes, but the repository's files are its `.py` files:
 * when resolution lands on `mod.pyi` (or a package's `__init__.pyi`), the file is the `.py` beside it,
 * and a stub with no `.py` beside it names no file.
 */
function repoFileOf(uri, selfPath) {
  if (!uri || (typeof uri.isEmpty === 'function' && uri.isEmpty())) return null;
  const resolved = typeof uri.getFilePath === 'function' ? uri.getFilePath() : null;
  if (!resolved || !/\.pyi?$/i.test(resolved)) return null;
  let filePath = normalizePath(resolved);
  if (/\.pyi$/i.test(filePath)) {
    filePath = filePath.slice(0, -1);
    try {
      if (!fs.statSync(filePath).isFile()) return null;
    } catch (err) {
      return null;
    }
  }
  if (!filePath.startsWith(state.repoRoot + path.sep) || shouldSkipPath(filePath) || filePath === selfPath) {
    return null;
  }
  return filePath;
}

/**
 * The repository files the imports of one file resolve to, in the order the imports appear and
 * without repeats, by Pyright's own resolution of each import from the file's position.
 *
 * `import a.b.c` names the file of `a.b.c` alone: the `__init__.py` of the parent packages run too,
 * but are not what the import names. `from a.b import c` names the file of the submodule `c` when
 * there is one, and otherwise the file of `a.b` -- its `__init__.py` for a package -- which is where a
 * name `c` it defines is found. A star import names the file of its module. A relative import is
 * resolved from the file's own package. The literal-argument `importlib.import_module`,
 * `import_module` and `__import__` loads found by `findDynamicImports` count as `import a.b.c`.
 *
 * Imports are read wherever they are written, function and class bodies and `if TYPE_CHECKING:`
 * included. Unresolved imports, the standard library, stubs bundled with the checker and installed
 * packages name no repository file and add nothing, and neither does an import of the file itself.
 */
function collectImportedFiles(parseTree, parseNodeType, filePath, dynamicImports) {
  const types = parseNodeType || {};
  const resolution = repoImportResolution();
  if (!resolution) return [];
  const found = [];
  const add = (start, fileOf) => found.push({ start, fileOf });
  const resolve = (leadingDots, nameParts, importedSymbols) => {
    try {
      const result = resolution.resolver.resolveImport(resolution.toUri(filePath), resolution.execEnv,
        { leadingDots, nameParts, importedSymbols });
      return result && result.isImportFound ? result : null;
    } catch (err) {
      return null;
    }
  };
  const moduleFile = result => {
    const uris = result.resolvedUris || [];
    return repoFileOf(uris[uris.length - 1], filePath);
  };
  const modulePartsOf = moduleNode => ({
    leadingDots: moduleNode.d.leadingDots || 0,
    nameParts: (moduleNode.d.nameParts || []).map(part => nameFromNode(part))
  });

  function visit(node) {
    if (!node || typeof node !== 'object' || !node.d) return;
    if (node.nodeType === types.ImportAs && node.d.module) {
      const { leadingDots, nameParts } = modulePartsOf(node.d.module);
      add(node.start, () => {
        const result = resolve(leadingDots, nameParts, undefined);
        return result ? [moduleFile(result)] : [];
      });
    } else if (node.nodeType === types.ImportFrom && node.d.module) {
      const { leadingDots, nameParts } = modulePartsOf(node.d.module);
      const names = (node.d.imports || []).map(imported => nameFromNode(imported.d.name));
      add(node.start, () => {
        const result = resolve(leadingDots, nameParts, node.d.isWildcardImport ? undefined : new Set(names));
        if (!result) return [];
        if (node.d.isWildcardImport) return [moduleFile(result)];
        const submodules = result.filteredImplicitImports || new Map();
        return names.map(name => {
          const submodule = submodules.get(name);
          return submodule ? repoFileOf(submodule.uri, filePath) : moduleFile(result);
        });
      });
    }
    for (const key of Object.keys(node.d)) {
      const child = node.d[key];
      if (Array.isArray(child)) {
        child.forEach(visit);
      } else if (child && typeof child === 'object' && child.d) {
        visit(child);
      }
    }
  }
  visit(parseTree);
  for (const dynamic of dynamicImports || []) {
    const nameParts = dynamic.moduleName.split('.');
    add(dynamic.start, () => {
      const result = resolve(0, nameParts, undefined);
      return result ? [moduleFile(result)] : [];
    });
  }
  // Source order: statements and calls are collected in separate passes.
  found.sort((a, b) => a.start - b.start);
  const files = [];
  const seen = new Set();
  for (const entry of found) {
    for (const file of entry.fileOf()) {
      if (file && !seen.has(file)) {
        seen.add(file);
        files.push(file);
      }
    }
  }
  return files;
}

function collectImports(fileText, ctx) {
  return resolveImportStatements(collectImportStatements(fileText), ctx);
}

/**
 * The names a list of import statements binds: `importedModules` maps a local name to the module it
 * binds, `importedSymbols` a local name to the module member it binds, and `starModules` lists the
 * modules star-imported from, in order.
 */
function resolveImportStatements(statements, ctx) {
  const importedModules = new Map();
  const importedSymbols = new Map();
  const starModules = [];
  for (const statement of statements) {
    const trimmed = statement.trim();
    if (!trimmed) {
      continue;
    }
    if (trimmed.startsWith('import ')) {
      const rest = trimmed.slice(7).trim();
      const parts = splitArgs(rest);
      for (const part of parts) {
        const seg = part.split(/\s+as\s+/);
        const moduleName = seg[0].trim();
        if (!moduleName) {
          continue;
        }
        const alias = seg[1] ? seg[1].trim() : moduleName;
        importedModules.set(alias, moduleName);
      }
      continue;
    }
    if (trimmed.startsWith('from ')) {
      const match = trimmed.match(/^from\s+([^\s]+)\s+import\s+(.+)$/);
      if (!match) {
        continue;
      }
      const rawModule = match[1];
      let level = 0;
      let modulePart = rawModule;
      while (modulePart.startsWith('.')) {
        level += 1;
        modulePart = modulePart.slice(1);
      }
      const fullModule = resolveRelativeModule(modulePart, level, ctx.fullModuleName);
      let namesPart = match[2].trim();
      if (namesPart.startsWith('(') && namesPart.endsWith(')')) {
        namesPart = namesPart.slice(1, -1);
      }
      const names = splitArgs(namesPart);
      for (const name of names) {
        if (name === '*') {
          if (fullModule) {
            starModules.push(fullModule);
          }
          continue;
        }
        const seg = name.split(/\s+as\s+/);
        const symbolName = seg[0].trim();
        if (!symbolName) {
          continue;
        }
        const localName = seg[1] ? seg[1].trim() : symbolName;
        const candidateModule = fullModule ? fullModule + '.' + symbolName : symbolName;
        if (ctx.moduleIndex.has(candidateModule)) {
          importedModules.set(localName, candidateModule);
        } else {
          importedSymbols.set(localName, { moduleName: fullModule, symbolName });
        }
      }
    }
  }
  return { importedModules, importedSymbols, starModules };
}

function extractCandidateNames(raw) {
  if (!raw) {
    return [];
  }
  let text = raw.trim();
  if ((text.startsWith(DOUBLE_QUOTE) && text.endsWith(DOUBLE_QUOTE))
    || (text.startsWith(SINGLE_QUOTE) && text.endsWith(SINGLE_QUOTE))) {
    text = text.slice(1, -1);
  }
  const candidates = [];
  let current = '';
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    const isIdent = /[A-Za-z0-9_\.]/.test(ch);
    if (isIdent) {
      current += ch;
      continue;
    }
    if (current) {
      candidates.push(current);
      current = '';
    }
  }
  if (current) {
    candidates.push(current);
  }
  // A type name starts with a letter or an underscore. `.` has to be part of the candidate alphabet
  // for `pkg.models.User` to survive, and that alone made `...` a candidate -- so `tuple[str, ...]`
  // and `Callable[..., Any]` produced a reference to `...`, which is punctuation and names no type
  // under any reading.
  return candidates.filter((candidate) => /^[A-Za-z_]/.test(candidate));
}

/**
 * The repo file a module name refers to from the current file: as written, then relative to the
 * current package, then relative to the current module's parent. Null when the module is not in the repo.
 */
function moduleFileFor(moduleName, ctx) {
  let filePath = ctx.moduleIndex.get(moduleName);
  if (!filePath && ctx.packageName) {
    filePath = ctx.moduleIndex.get(ctx.packageName + '.' + moduleName);
  }
  if (!filePath && ctx.fullModuleName && ctx.fullModuleName.includes('.')) {
    const parts = ctx.fullModuleName.split('.');
    parts.pop();
    if (parts.length) {
      filePath = ctx.moduleIndex.get(parts.join('.') + '.' + moduleName) || filePath;
    }
  }
  return filePath || null;
}

/**
 * The names a repo module declares at its top level -- classes, functions and assigned names --
 * read from its own source and cached per repo. `classes` is the evidence that a name imported from
 * the module is a class, and not a function, a constant or a re-export, before a use of that name is
 * recorded as a type reference. A module that cannot be read or parsed declares nothing and is
 * marked unparsed, so no name is ever attributed to it on the strength of a parse that did not happen.
 */
/**
 * The Pyright program that infers the types of unannotated locals, created on first use: the
 * repository's import roots, the configured Python version and the bundled typeshed, so that the
 * builtins and the standard library resolve and a class's own members are known. Null when the
 * program cannot be built, in which case nothing is inferred.
 */
function evaluationProgram() {
  if (state.evaluation !== null) {
    return state.evaluation || null;
  }
  try {
    const api = getPyrightApi();
    const nullConsole = new api.console.NullConsole();
    const tempFiles = new api.fileSystem.RealTempFile();
    const fileSystem = api.fileSystem.createFromRealFileSystem(tempFiles, nullConsole);
    const serviceProvider = api.serviceProvider.createServiceProvider(fileSystem, nullConsole, tempFiles);
    const toUri = filePath => toFileUri(api, filePath, serviceProvider);
    const configOptions = new api.configOptions.ConfigOptions(toUri(state.repoRoot));
    configOptions.defaultExtraPaths = (state.extraModuleRoots || []).map(toUri);
    applyPythonVersion(configOptions, resolvePythonVersionEnum(state.pythonVersion, api));
    const typeshed = path.join(__dirname, 'node_modules', 'pyright', 'dist', 'typeshed-fallback');
    if (!fs.existsSync(typeshed)) {
      throw new Error('typeshed missing');
    }
    configOptions.typeshedPath = toUri(typeshed);
    const resolver = new api.importResolver.ImportResolver(serviceProvider, configOptions,
      createPythonHost(api, serviceProvider));
    state.evaluation = { program: new api.program.Program(resolver, configOptions, serviceProvider), toUri, files: 0 };
  } catch (err) {
    state.evaluation = false;
  }
  return state.evaluation || null;
}

/** How many files the program holds before its cache is dropped, so that a large repository stays bounded. */
const EVALUATION_FILE_BUDGET = 200;

/** The kinds of type Pyright's evaluator reports, and the flag that marks a type as an instance. */
const PYRIGHT_CATEGORY_CLASS = 6;
const PYRIGHT_CATEGORY_UNION = 8;
const PYRIGHT_FLAG_INSTANCE = 2;

/** Modules whose classes are the language's own vocabulary, not a dependency worth recording for a local. */
const UNATTRIBUTED_MODULES = new Set(['builtins', 'types', 'typing', 'typing_extensions', '_typeshed']);

/** The node of `root` that spans exactly what `target` spans in the file, or null. */
function sameNodeIn(root, target) {
  let node = root;
  while (node && node.d) {
    if (node.start === target.start && node.length === target.length && node.nodeType === target.nodeType) {
      return node;
    }
    let next = null;
    for (const key of Object.keys(node.d)) {
      const child = node.d[key];
      for (const item of Array.isArray(child) ? child : [child]) {
        if (item && typeof item === 'object' && item.d && key !== 'parent' && typeof item.start === 'number'
          && item.start <= target.start && item.start + item.length >= target.start + target.length) {
          next = item;
          break;
        }
      }
      if (next) {
        break;
      }
    }
    node = next;
  }
  return null;
}

/**
 * The one class an evaluated type is an instance of, or null when it is not exactly one: an unknown
 * or `Any` type, a type variable, a module or a class object, a union of several classes, and the
 * classes of the language's own vocabulary (`int`, `list`, `None`) all name nothing worth recording.
 * `Optional[X]` is an `X`.
 */
function instanceClassOf(type) {
  let candidate = type;
  if (candidate && candidate.category === PYRIGHT_CATEGORY_UNION) {
    const arms = ((candidate.priv && candidate.priv.subtypes) || []).filter(arm => !(arm && arm.shared
      && arm.shared.name === 'NoneType'));
    candidate = arms.length === 1 ? arms[0] : null;
  }
  if (!candidate || candidate.category !== PYRIGHT_CATEGORY_CLASS || !(candidate.flags & PYRIGHT_FLAG_INSTANCE)) {
    return null;
  }
  const shared = candidate.shared;
  if (!shared || !shared.name || !shared.moduleName || !shared.fullName || UNATTRIBUTED_MODULES.has(shared.moduleName)) {
    return null;
  }
  const own = shared.fullName.startsWith(shared.moduleName + '.')
    ? shared.fullName.slice(shared.moduleName.length + 1) : null;
  return own ? { moduleName: shared.moduleName, classPath: own } : null;
}

/**
 * The reference a class found by evaluation stands for: the repository class when the module that
 * defines it is a repository module that declares it, else the library class `module.Class`. A class
 * the module index cannot account for is not guessed at.
 */
function inferredClassRef(info) {
  const file = state.moduleIndex ? state.moduleIndex.get(info.moduleName) : null;
  if (file) {
    if (!moduleDeclarations(file).classPaths.has(info.classPath)) {
      return null;
    }
    return { targetUniqueName: uniqueNameInFile(file, info.classPath, { repoRoot: state.repoRoot }), externalLabel: null };
  }
  return { targetUniqueName: null, externalLabel: info.moduleName + '.' + info.classPath };
}

/**
 * A function that gives the class Pyright infers for a node of the file, or null for it. The program
 * is only built and the file only read when the first node is asked about, and any failure of the
 * evaluator is "no answer".
 */
function typeInferrer(filePath) {
  let session;
  const open = () => {
    const evaluation = evaluationProgram();
    if (!evaluation) {
      return null;
    }
    evaluation.files += 1;
    if (evaluation.files % EVALUATION_FILE_BUDGET === 0) {
      evaluation.program.setTrackedFiles([]);
      evaluation.program.emptyCache();
    }
    const uri = evaluation.toUri(filePath);
    evaluation.program.addTrackedFile(uri);
    const parsed = evaluation.program.getParseResults(uri);
    const tree = parsed && parsed.parserOutput ? parsed.parserOutput.parseTree : null;
    return tree ? { tree, evaluator: evaluation.program.evaluator } : null;
  };
  return (node) => {
    try {
      if (session === undefined) {
        session = open();
      }
      const found = session ? sameNodeIn(session.tree, node) : null;
      const info = found ? instanceClassOf(session.evaluator.getTypeOfExpression(found).type) : null;
      return info ? inferredClassRef(info) : null;
    } catch (err) {
      return null;
    }
  };
}

function moduleDeclarations(filePath) {
  if (state.moduleDeclarations.has(filePath)) {
    return state.moduleDeclarations.get(filePath);
  }
  let declarations = { parsed: false, classes: new Set(), classPaths: new Set(), names: new Set(),
    functions: new Set(), aliases: new Map() };
  try {
    const api = getPyrightApi();
    const parser = new api.parser.Parser();
    const parseOptions = new api.parser.ParseOptions();
    const versionEnum = resolvePythonVersionEnum(state.pythonVersion, api);
    if (versionEnum && 'pythonVersion' in parseOptions) {
      parseOptions.pythonVersion = versionEnum;
    }
    const declaredText = fs.readFileSync(filePath, 'utf8');
    const parseResult = parser.parseSourceFile(declaredText, parseOptions, undefined);
    let parseTree = null;
    if (parseResult && parseResult.parserOutput && parseResult.parserOutput.parseTree) {
      parseTree = parseResult.parserOutput.parseTree;
    } else if (parseResult && parseResult.parseTree) {
      parseTree = parseResult.parseTree;
    }
    if (parseTree) {
      const parseNodeType = getParseNodeTypeEnum(api);
      declarations = {
        parsed: true,
        classes: collectLocalClasses(parseTree, parseNodeType),
        classPaths: collectClassNames(getModuleStatements(parseTree, parseNodeType), '', parseNodeType, new Set()),
        names: collectTopLevelNames(parseTree, parseNodeType),
        functions: collectLocalFunctions(parseTree, parseNodeType),
        aliases: collectAliasCandidates(parseTree, parseNodeType, declaredText)
      };
    }
  } catch (err) {
    declarations = { parsed: false, classes: new Set(), classPaths: new Set(), names: new Set(),
      functions: new Set(), aliases: new Map() };
  }
  state.moduleDeclarations.set(filePath, declarations);
  return declarations;
}

function classesDefinedIn(filePath) {
  return moduleDeclarations(filePath).classes;
}

/** The names a module binds at its top level by assignment: its constants and other module fields. */
function moduleLevelValueNames(parseTree, parseNodeType) {
  const values = new Set();
  for (const name of collectTopLevelNames(parseTree, parseNodeType)) {
    values.add(name);
  }
  for (const statement of getModuleStatements(parseTree, parseNodeType)) {
    if (statement && (isClassNode(statement, parseNodeType) || isFunctionNode(statement, parseNodeType))) {
      values.delete(nameFromNode(statement.d && statement.d.name ? statement.d.name : null));
    }
  }
  return values;
}

/** The names bound at the top of a module by a function definition. */
function collectLocalFunctions(parseTree, parseNodeType) {
  const functions = new Set();
  for (const statement of getModuleStatements(parseTree, parseNodeType)) {
    if (statement && isFunctionNode(statement, parseNodeType)) {
      const name = nameFromNode(statement.d && statement.d.name ? statement.d.name : null);
      if (name) {
        functions.add(name);
      }
    }
  }
  return functions;
}

/** The name a top-level assignment binds, or an empty string when its target is not a plain name. */
function assignedNameOf(statement) {
  let target = getNodeProp(statement, ['leftExpr', 'leftExpression', 'valueExpr', 'valueExpression']);
  // `x: T = v` assigns to an annotation node, whose own value expression is the name.
  if (target && !nameFromNode(target)) {
    target = getNodeProp(target, ['valueExpr', 'valueExpression']);
  }
  return nameFromNode(target);
}

/**
 * The top-level assignments whose value could name a type -- a name, a dotted name, a subscript or a
 * `|` union -- with the text of that value. Whether each is an alias depends on what the names in it
 * resolve to, which is settled when one is looked up.
 */
function collectAliasCandidates(parseTree, parseNodeType, fileText) {
  const aliases = new Map();
  for (const statement of getModuleStatements(parseTree, parseNodeType)) {
    if (!statement || !isAssignmentStatement(statement, parseNodeType)) {
      continue;
    }
    const value = statement.d ? statement.d.rightExpr : null;
    const name = assignedNameOf(statement);
    if (name && value && value.d) {
      aliases.set(name, textForNode(value, fileText));
    }
  }
  return aliases;
}

/** The names bound at the top of a module by a class, a function or an assignment. */
function collectTopLevelNames(parseTree, parseNodeType) {
  const names = new Set();
  for (const statement of getModuleStatements(parseTree, parseNodeType)) {
    if (!statement) {
      continue;
    }
    let name = '';
    if (isClassNode(statement, parseNodeType) || isFunctionNode(statement, parseNodeType)) {
      name = nameFromNode(statement.d && statement.d.name ? statement.d.name : null);
    } else if (isAssignmentStatement(statement, parseNodeType)) {
      name = assignedNameOf(statement);
    }
    if (name) {
      names.add(name);
    }
  }
  return names;
}

/**
 * A repo module's own file-level imports, resolved from that module's position in the repo and
 * cached per repo, together with the context `moduleFileFor` needs to find what they name.
 */
function moduleImportsOf(filePath) {
  if (state.moduleImports.has(filePath)) {
    return state.moduleImports.get(filePath);
  }
  const moduleName = moduleBaseNameForPath(filePath);
  const packageName = packageNameForPath(filePath, state.repoRoot);
  const ctx = {
    repoRoot: state.repoRoot,
    moduleIndex: state.moduleIndex,
    moduleName,
    packageName,
    fullModuleName: packageName ? packageName + '.' + moduleName : moduleName
  };
  let imports = { importedModules: new Map(), importedSymbols: new Map(), starModules: [] };
  try {
    imports = collectImports(fs.readFileSync(filePath, 'utf8'), ctx);
  } catch (err) {
    // An unreadable module re-exports nothing that can be followed.
  }
  const result = { ctx, imports };
  state.moduleImports.set(filePath, result);
  return result;
}

/** How many re-exports deep a name is followed before resolution gives up on finding its declaration. */
const REEXPORT_MAX_DEPTH = 8;

/**
 * The repo module that declares `symbolName` as seen from the module in `filePath`, and the name it
 * declares it under, or null when that declaration cannot be found.
 *
 * A package's `__init__.py` usually declares none of the names it exposes: it re-exports them with
 * `from .responses import Redirect`, `from .bases import Base as PublicBase` or `from .extras import *`.
 * A name the module declares itself is its own, whatever it also imports. Otherwise an explicit import
 * of the name is followed, then each star import in order. The walk is bounded in depth and never
 * revisits a module for the same name, so an import cycle ends it. A name bound to a module, a name
 * imported from outside the repo, and a module that could not be parsed all end it with null: none of
 * those is evidence of where the name is declared.
 */
function findDeclaration(filePath, symbolName, depth, visited) {
  const key = filePath + '\u0000' + symbolName;
  if (depth > REEXPORT_MAX_DEPTH || visited.has(key)) {
    return null;
  }
  visited.add(key);
  const declarations = moduleDeclarations(filePath);
  if (!declarations.parsed) {
    return null;
  }
  if (declarations.names.has(symbolName)) {
    return { filePath, symbolName };
  }
  const { ctx, imports } = moduleImportsOf(filePath);
  const explicit = imports.importedSymbols.get(symbolName);
  if (explicit) {
    const nextFile = explicit.moduleName ? moduleFileFor(explicit.moduleName, ctx) : null;
    return nextFile ? findDeclaration(nextFile, explicit.symbolName, depth + 1, visited) : null;
  }
  if (imports.importedModules.has(symbolName)) {
    return null;
  }
  for (const starModule of imports.starModules) {
    const nextFile = moduleFileFor(starModule, ctx);
    const found = nextFile ? findDeclaration(nextFile, symbolName, depth + 1, visited) : null;
    if (found) {
      return found;
    }
  }
  return null;
}

/**
 * The unique name a symbol of the module in `filePath` carries. It is rebuilt from the file's own
 * path, which is what component unique names are built from too.
 */
function uniqueNameInFile(filePath, symbolName, ctx) {
  const pkgName = packageNameForPath(filePath, ctx.repoRoot);
  const modName = moduleBaseNameForPath(filePath);
  if (!modName) {
    return null;
  }
  if (pkgName) {
    return pkgName + '.' + modName + '.' + symbolName;
  }
  return modName + '.' + symbolName;
}

/**
 * The unique name of the repo class a plain name refers to in the current file -- a class defined at
 * the top of this file, or one imported from a repo module that declares it or re-exports it -- or
 * null for anything else: a function, a constant, or a name from outside the repo.
 */
function resolveRepoClass(name, ctx) {
  if (!name) {
    return null;
  }
  if (ctx.localClasses && ctx.localClasses.has(name)) {
    return resolveCandidate(name, ctx);
  }
  const imported = ctx.importedSymbols ? ctx.importedSymbols.get(name) : null;
  if (!imported || !imported.moduleName || !imported.symbolName) {
    return null;
  }
  const filePath = moduleFileFor(imported.moduleName, ctx);
  if (!filePath) {
    return null;
  }
  const declared = findDeclaration(filePath, imported.symbolName, 0, new Set());
  if (!declared || !classesDefinedIn(declared.filePath).has(declared.symbolName)) {
    return null;
  }
  return uniqueNameInFile(declared.filePath, declared.symbolName, ctx);
}

/**
 * The unique name of `symbolName` imported from `moduleName`: in the module that declares it when the
 * named module only re-exports it, and otherwise in the named module itself.
 */
function resolveModuleSymbol(moduleName, symbolName, ctx) {
  if (!moduleName || !symbolName) {
    return null;
  }
  const filePath = moduleFileFor(moduleName, ctx);
  if (!filePath) {
    return null;
  }
  const declared = findDeclaration(filePath, symbolName, 0, new Set());
  if (declared) {
    return uniqueNameInFile(declared.filePath, declared.symbolName, ctx);
  }
  return uniqueNameInFile(filePath, symbolName, ctx);
}

function resolveDottedCandidate(candidate, ctx) {
  const parts = candidate.split('.').filter(Boolean);
  if (parts.length < 2) {
    return null;
  }
  const aliasModule = ctx.importedModules.get(parts[0]);
  if (aliasModule) {
    const combined = aliasModule.split('.').concat(parts.slice(1));
    for (let i = combined.length - 1; i >= 1; i -= 1) {
      const prefix = combined.slice(0, i).join('.');
      if (ctx.moduleIndex.has(prefix)) {
        const symbol = combined[i];
        const resolved = resolveModuleSymbol(prefix, symbol, ctx);
        if (resolved) {
          return resolved;
        }
      }
    }
  }
  for (let i = parts.length - 1; i >= 1; i -= 1) {
    const prefix = parts.slice(0, i).join('.');
    if (ctx.moduleIndex.has(prefix)) {
      const symbol = parts[i];
      const resolved = resolveModuleSymbol(prefix, symbol, ctx);
      if (resolved) {
        return resolved;
      }
    }
  }
  return null;
}

function resolveCandidate(candidate, ctx) {
  if (!candidate) {
    return null;
  }
  if (candidate.includes('.')) {
    return resolveDottedCandidate(candidate, ctx);
  }
  if (ctx.localClasses.has(candidate)) {
    if (ctx.packageName) {
      return ctx.packageName + '.' + ctx.moduleName + '.' + candidate;
    }
    return ctx.moduleName + '.' + candidate;
  }
  const importedSymbol = ctx.importedSymbols.get(candidate);
  if (importedSymbol) {
    return resolveModuleSymbol(importedSymbol.moduleName, importedSymbol.symbolName, ctx);
  }
  return null;
}

/**
 * The unique name of a class nested in a class of this file that an annotation names: `Late` written
 * inside `Holder` is `Holder.Late` when `Holder` declares it, and `Holder.Deep.Leaf` is the class of
 * that path whether or not `Holder` is imported. Classes are looked for from the innermost
 * enclosing class outwards; a bare name is never looked for at module level here, where an ordinary
 * lookup already finds it. Null when this file declares no such class.
 */
function resolveNestedClass(candidate, ctx) {
  if (!candidate || !ctx.classNames) {
    return null;
  }
  const moduleUnique = localUniqueName('', ctx).slice(0, -1);
  let scope = ctx.classUniqueName || null;
  while (scope && scope.length > moduleUnique.length) {
    const nested = scope + '.' + candidate;
    if (ctx.classNames.has(nested)) {
      return nested;
    }
    scope = scope.slice(0, scope.lastIndexOf('.'));
  }
  if (candidate.includes('.') && ctx.classNames.has(moduleUnique + '.' + candidate)) {
    return moduleUnique + '.' + candidate;
  }
  return null;
}

/** The unique names of every class a module declares, nested ones included. */
function collectClassNames(statements, prefix, parseNodeType, into) {
  for (const statement of statements) {
    if (!statement || !isClassNode(statement, parseNodeType)) {
      continue;
    }
    const name = nameFromNode(statement.d && statement.d.name ? statement.d.name : null);
    if (!name) {
      continue;
    }
    const unique = prefix ? prefix + '.' + name : name;
    into.add(unique);
    collectClassNames(getStatementsFromSuite(statement.d ? statement.d.suite : null), unique, parseNodeType, into);
  }
  return into;
}

function resolveTypeFromRaw(raw, ctx, inAnnotation) {
  if (!raw) {
    return null;
  }
  const candidates = extractCandidateNames(raw);
  for (const cand of candidates) {
    if (!cand) {
      continue;
    }
    const nested = inAnnotation ? resolveNestedClass(cand, ctx) : null;
    if (nested) {
      return nested;
    }
    const resolved = resolveCandidate(cand, ctx);
    if (resolved) {
      return resolved;
    }
  }
  return null;
}

function externalLabelFor(raw, ctx) {
  if (!raw) {
    return raw;
  }
  const candidates = extractCandidateNames(raw);
  for (const cand of candidates) {
    if (cand.includes('.')) {
      return cand;
    }
    const imported = ctx.importedSymbols.get(cand);
    if (imported && imported.moduleName) {
      return imported.moduleName + '.' + imported.symbolName;
    }
  }
  return raw.trim();
}

function splitTopLevelCsv(text) {
  const values = [];
  if (!text) {
    return values;
  }
  let current = '';
  let depth = 0;
  let quote = '';
  let escape = false;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (quote) {
      current += ch;
      if (escape) {
        escape = false;
        continue;
      }
      if (ch === '\\') {
        escape = true;
        continue;
      }
      if (ch === quote) {
        quote = '';
      }
      continue;
    }
    if (ch === '"' || ch === "'") {
      quote = ch;
      current += ch;
      continue;
    }
    if (ch === '[' || ch === '(' || ch === '{') {
      depth += 1;
      current += ch;
      continue;
    }
    if (ch === ']' || ch === ')' || ch === '}') {
      depth = Math.max(0, depth - 1);
      current += ch;
      continue;
    }
    if (ch === ',' && depth === 0) {
      if (current.trim()) {
        values.push(current.trim());
      }
      current = '';
      continue;
    }
    current += ch;
  }
  if (current.trim()) {
    values.push(current.trim());
  }
  return values;
}

/**
 * The arms of a top-level union annotation. `Foo | Bar` is two names; `dict[str, int | None]` is
 * one, because that `|` is nested inside a type argument. Brackets and quotes are respected so only
 * a top-level separator splits.
 */
function splitTopLevelUnion(text) {
  const parts = [];
  if (!text) {
    return parts;
  }
  let current = '';
  let depth = 0;
  let quote = '';
  let escape = false;
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i];
    if (quote) {
      current += ch;
      if (escape) {
        escape = false;
      } else if (ch === '\\') {
        escape = true;
      } else if (ch === quote) {
        quote = '';
      }
      continue;
    }
    if (ch === DOUBLE_QUOTE || ch === SINGLE_QUOTE) {
      quote = ch;
      current += ch;
      continue;
    }
    if (ch === '[' || ch === '(' || ch === '{') {
      depth += 1;
      current += ch;
      continue;
    }
    if (ch === ']' || ch === ')' || ch === '}') {
      depth = Math.max(0, depth - 1);
      current += ch;
      continue;
    }
    if (ch === '|' && depth === 0) {
      if (current.trim()) {
        parts.push(current.trim());
      }
      current = '';
      continue;
    }
    current += ch;
  }
  if (current.trim()) {
    parts.push(current.trim());
  }
  return parts;
}

// `None` in a union spells the absence of a value, not a type the declaration depends on.
const UNION_NON_TYPES = new Set(['none', 'nonetype']);

function isUnionNonType(text) {
  return UNION_NON_TYPES.has(String(text || '').trim().toLowerCase());
}

function literalTokenToPythonType(token) {
  if (!token) {
    return '';
  }
  const text = token.trim();
  if (!text) {
    return '';
  }
  if (/^(True|False)$/i.test(text)) {
    return 'bool';
  }
  if (/^(None)$/i.test(text)) {
    return 'None';
  }
  if (/^[+-]?\d+$/.test(text)) {
    return 'int';
  }
  if (/^[+-]?(?:\d+\.\d*|\d*\.\d+)$/.test(text)) {
    return 'float';
  }
  if (/^[bB]?(\"([^\"\\]|\\.)*\"|'([^'\\]|\\.)*')$/.test(text)) {
    return text[0].toLowerCase() === 'b' ? 'bytes' : 'str';
  }
  return '';
}

function normalizePythonTypeDisplay(raw) {
  if (!raw) {
    return raw;
  }
  const text = raw.trim();
  if (!text) {
    return text;
  }
  const literalMatch = text.match(/^(?:typing\.)?Literal\s*\[(.*)\]$/);
  if (literalMatch) {
    const tokens = splitTopLevelCsv(literalMatch[1]);
    const mapped = [];
    for (const token of tokens) {
      const normalized = literalTokenToPythonType(token);
      if (normalized) {
        mapped.push(normalized);
      }
    }
    if (!mapped.length) {
      return text;
    }
    const deduped = Array.from(new Set(mapped));
    return deduped.length === 1 ? deduped[0] : deduped.join(' | ');
  }
  const literalType = literalTokenToPythonType(text);
  if (literalType) {
    return literalType;
  }
  return text;
}

function loadBuiltins(pyrightJsPath) {
  if (!fs.existsSync(pyrightJsPath)) {
    return {};
  }
  const text = fs.readFileSync(pyrightJsPath, 'utf8');
  let marker = text.indexOf('s={');
  if (marker < 0) {
    marker = text.indexOf('s = {');
  }
  if (marker < 0) {
    return {};
  }
  const start = text.indexOf('{', marker);
  if (start < 0) {
    return {};
  }
  let depth = 0;
  let inString = false;
  let strChar = '';
  let escape = false;
  let inLineComment = false;
  let inBlockComment = false;
  let inRegex = false;
  let prevNonSpace = '';
  let end = -1;
  for (let i = start; i < text.length; i += 1) {
    const ch = text[i];
    const next = text[i + 1];
    if (inLineComment) {
      if (ch === '\n') {
        inLineComment = false;
      }
      continue;
    }
    if (inBlockComment) {
      if (ch === '*' && next === '/') {
        inBlockComment = false;
        i += 1;
      }
      continue;
    }
    if (inString) {
      if (escape) {
        escape = false;
        continue;
      }
      if (ch === '\\') {
        escape = true;
        continue;
      }
      if (ch === strChar) {
        inString = false;
        strChar = '';
      }
      continue;
    }
    if (inRegex) {
      if (ch === '\\') {
        i += 1;
        continue;
      }
      if (ch === '/') {
        inRegex = false;
      }
      continue;
    }
    if (ch === '/' && next === '/') {
      inLineComment = true;
      i += 1;
      continue;
    }
    if (ch === '/' && next === '*') {
      inBlockComment = true;
      i += 1;
      continue;
    }
    if (ch === DOUBLE_QUOTE || ch === SINGLE_QUOTE || ch === BACKTICK) {
      inString = true;
      strChar = ch;
      continue;
    }
    if (ch === '/') {
      if (!prevNonSpace || '([{:;,=!?&|+-*%~^<>'.indexOf(prevNonSpace) >= 0) {
        inRegex = true;
        continue;
      }
    }
    if (ch === '{') {
      depth += 1;
    } else if (ch === '}') {
      depth -= 1;
      if (depth === 0) {
        end = i + 1;
        break;
      }
    }
    if (ch && ch.trim()) {
      prevNonSpace = ch;
    }
  }
  if (end < 0) {
    return {};
  }
  const literal = text.slice(start, end);
  const code = 'module.exports=' + literal;
  const mod = { exports: {} };
  vm.runInNewContext(code, { module: mod, require });
  return mod.exports;
}

function createWebpackRequire(modules) {
  const cache = {};
  function __webpack_require__(id) {
    if (cache[id]) {
      return cache[id].exports;
    }
    const moduleFn = modules[id];
    if (!moduleFn) {
      throw new Error('Pyright module ' + id + ' not found');
    }
    const module = { exports: {} };
    cache[id] = module;
    moduleFn(module, module.exports, __webpack_require__);
    return module.exports;
  }
  __webpack_require__.d = (exports, definition) => {
    for (const key in definition) {
      if (Object.prototype.hasOwnProperty.call(definition, key)
        && !Object.prototype.hasOwnProperty.call(exports, key)) {
        Object.defineProperty(exports, key, { enumerable: true, get: definition[key] });
      }
    }
  };
  __webpack_require__.r = (exports) => {
    if (typeof Symbol !== 'undefined' && Symbol.toStringTag) {
      Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
    }
    Object.defineProperty(exports, '__esModule', { value: true });
  };
  __webpack_require__.o = (obj, prop) => Object.prototype.hasOwnProperty.call(obj, prop);
  __webpack_require__.n = (module) => {
    const getter = module && module.__esModule ? () => module.default : () => module;
    __webpack_require__.d(getter, { a: getter });
    return getter;
  };
  __webpack_require__.nmd = (module) => {
    module.paths = [];
    return module;
  };
  __webpack_require__.g = (function () {
    if (typeof globalThis !== 'undefined') {
      return globalThis;
    }
    if (typeof self !== 'undefined') {
      return self;
    }
    if (typeof window !== 'undefined') {
      return window;
    }
    if (typeof global !== 'undefined') {
      return global;
    }
    return Function('return this')();
  }());
  __webpack_require__.m = modules;
  __webpack_require__.c = cache;
  return __webpack_require__;
}

function loadPyright() {
  const pyrightRoot = path.join(__dirname, 'node_modules', 'pyright');
  const internalPath = path.join(pyrightRoot, 'dist', 'pyright-internal.js');
  if (!fs.existsSync(internalPath)) {
    throw new Error('Pyright bundle missing at ' + internalPath);
  }
  const internal = require(internalPath);
  const vendor = require(path.join(pyrightRoot, 'dist', 'vendor.js'));
  const builtins = loadBuiltins(path.join(pyrightRoot, 'dist', 'pyright.js'));
  const modules = Object.assign({}, builtins, vendor.modules || {}, internal.modules || {});
  if (modules[5100]) {
    modules[5100] = (module) => {
      module.exports = {};
    };
  }
  if (modules[8240]) {
    modules[8240] = (module) => {
      module.exports = {};
    };
  }
  return { req: createWebpackRequire(modules) };
}

function getPyrightApi() {
  if (state.pyrightApi) {
    return state.pyrightApi;
  }
  if (!state.pyright) {
    state.pyright = loadPyright();
  }
  const req = state.pyright.req;
  const safeReq = (id) => {
    try {
      return req(id);
    } catch (err) {
      return null;
    }
  };
  const api = {
    configOptions: req(2076),
    pythonVersion: req(8818),
    uri: safeReq(8643),
    uriEx: safeReq(6734),
    serviceProvider: req(2816),
    fileSystem: req(9029),
    console: req(8909),
    partialStub: req(1510),
    docString: req(6068),
    sourceFile: req(6028),
    program: req(6767),
    importResolver: req(9180),
    parseNodes: safeReq(5952),
    parser: safeReq(703),
    hostAccess: safeReq(3343),
    noAccessHost: safeReq(712),
    typeUtils: safeReq(6195)
  };
  state.pyrightApi = api;
  return api;
}

function toFileUri(api, filePath, serviceProvider) {
  const uriMod = api.uri;
  if (uriMod && typeof uriMod.file === 'function') {
    return uriMod.file(filePath, serviceProvider);
  }
  if (uriMod && uriMod.Uri && typeof uriMod.Uri.file === 'function') {
    return uriMod.Uri.file(filePath, serviceProvider);
  }
  const uriEx = api.uriEx;
  if (uriEx && uriEx.UriEx && typeof uriEx.UriEx.file === 'function') {
    return uriEx.UriEx.file(filePath);
  }
  return filePath;
}

function resolvePythonVersionEnum(versionText, api) {
  const pyVersionMod = api.pythonVersion || {};
  if (typeof pyVersionMod.pythonVersionFromString === 'function') {
    return pyVersionMod.pythonVersionFromString(versionText);
  }
  const versionEnum = pyVersionMod.PythonVersion || pyVersionMod;
  if (!versionEnum || typeof versionEnum !== 'object') {
    return null;
  }
  const cleaned = String(versionText || '').trim();
  const candidates = [];
  if (cleaned) {
    const digits = cleaned.replace(/\./g, '');
    const underscore = cleaned.replace(/\./g, '_');
    candidates.push('Python' + digits);
    candidates.push('Python' + underscore);
    candidates.push('Python' + cleaned);
  }
  candidates.push('Latest');
  candidates.push('LatestStable');
  candidates.push('PythonLatest');
  for (const key of candidates) {
    if (Object.prototype.hasOwnProperty.call(versionEnum, key)) {
      return versionEnum[key];
    }
  }
  const keys = Object.keys(versionEnum).filter(k => typeof versionEnum[k] === 'number');
  for (const key of keys) {
    if (cleaned && key.includes(cleaned.replace(/\./g, ''))) {
      return versionEnum[key];
    }
  }
  if (typeof pyVersionMod.latestStablePythonVersion === 'function') {
    return pyVersionMod.latestStablePythonVersion();
  }
  return null;
}

function applyPythonVersion(configOptions, versionEnum) {
  if (!configOptions || versionEnum === null || versionEnum === undefined) {
    return;
  }
  if ('pythonVersion' in configOptions) {
    configOptions.pythonVersion = versionEnum;
  }
  if ('defaultPythonVersion' in configOptions) {
    configOptions.defaultPythonVersion = versionEnum;
  }
}

function applyExtraPaths(configOptions, extraPaths, api, serviceProvider) {
  if (!configOptions || !Array.isArray(extraPaths)) {
    return;
  }
  const uris = extraPaths.map(p => toFileUri(api, p, serviceProvider));
  if ('extraPaths' in configOptions) {
    configOptions.extraPaths = uris;
  }
  if ('defaultExtraPaths' in configOptions) {
    configOptions.defaultExtraPaths = uris;
  }
  if (typeof configOptions.setExecutionEnvironments === 'function') {
    const envs = configOptions.getExecutionEnvironments ? configOptions.getExecutionEnvironments() : [];
    if (!envs || envs.length === 0) {
      return;
    }
    for (const env of envs) {
      if ('extraPaths' in env) {
        env.extraPaths = uris;
      }
    }
  }
}

function createPythonHost(api, serviceProvider) {
  if (api.noAccessHost && api.noAccessHost.NoAccessHost) {
    return new api.noAccessHost.NoAccessHost();
  }
  if (api.hostAccess && api.hostAccess.NoAccessHost) {
    return new api.hostAccess.NoAccessHost();
  }
  if (api.hostAccess && api.hostAccess.FullAccessHost) {
    return new api.hostAccess.FullAccessHost(serviceProvider);
  }
  return {
    getPythonSearchPaths: () => ({ paths: [], prefix: undefined }),
    getPythonVersion: () => undefined,
    getPythonPlatform: () => undefined,
    runScript: async () => ({ stdout: '', stderr: '' }),
    runSnippet: async () => ({ stdout: '', stderr: '' })
  };
}

function initPyright(versionText, extraPaths) {
  const api = getPyrightApi();
  if (!api.parser || !api.parser.Parser || !api.parser.ParseOptions) {
    throw new Error('Pyright parser unavailable');
  }
}

function ensureProgram(versionText, extraPaths) {
  const attempts = [];
  if (versionText) {
    attempts.push(versionText);
  }
  for (const candidate of FALLBACK_PYTHON_VERSIONS) {
    if (!attempts.includes(candidate)) {
      attempts.push(candidate);
    }
  }
  let lastError = null;
  for (const version of attempts) {
    try {
      initPyright(version, extraPaths);
      state.pythonVersion = version;
      return;
    } catch (err) {
      lastError = err;
    }
  }
  if (lastError) {
    throw lastError;
  }
}

async function analyzeFile(program, fileUri) {
  if (!program || !fileUri) {
    return;
  }
  if (typeof program.analyzeFile === 'function') {
    const result = program.analyzeFile(fileUri, undefined);
    if (result && typeof result.then === 'function') {
      await result;
    }
  }
}

function ensureFileUri(filePath) {
  if (!filePath) {
    return null;
  }
  let fileUri = state.fileUriMap.get(filePath);
  if (fileUri) {
    return fileUri;
  }
  const api = getPyrightApi();
  if (!api || !state.serviceProvider) {
    return null;
  }
  fileUri = toFileUri(api, filePath, state.serviceProvider);
  if (!fileUri) {
    return null;
  }
  state.fileUriMap.set(filePath, fileUri);
  if (state.program) {
    if (typeof state.program.addTrackedFile === 'function') {
      state.program.addTrackedFile(fileUri);
    } else if (typeof state.program.setTrackedFiles === 'function') {
      const uris = Array.from(state.fileUriMap.values());
      state.program.setTrackedFiles(uris);
    }
  }
  return fileUri;
}

function getEvaluator(program, fileUri) {
  if (!program) {
    return null;
  }
  if (program.evaluator) {
    return program.evaluator;
  }
  if (typeof program.getTypeEvaluator === 'function') {
    return program.getTypeEvaluator();
  }
  if (typeof program.getTypeEvaluatorForFile === 'function') {
    return program.getTypeEvaluatorForFile(fileUri);
  }
  return null;
}

function getParseTree(sourceFile) {
  if (!sourceFile) {
    return null;
  }
  if (typeof sourceFile.getParseResults === 'function') {
    const results = sourceFile.getParseResults();
    if (results && results.parseTree) {
      return results.parseTree;
    }
  }
  if (typeof sourceFile.getParserOutput === 'function') {
    const output = sourceFile.getParserOutput();
    if (output && output.parseTree) {
      return output.parseTree;
    }
  }
  if (sourceFile.parseTree) {
    return sourceFile.parseTree;
  }
  return null;
}

function getParseNodeTypeEnum(api) {
  const parseMod = api.parseNodes;
  if (!parseMod) {
    return null;
  }
  if (parseMod.ParseNodeType) {
    return parseMod.ParseNodeType;
  }
  if (parseMod.ParseNodeTypeMap) {
    return parseMod.ParseNodeTypeMap;
  }
  return parseMod;
}

/**
 * The statements of a module that declare something at its top level: its own statements, and the
 * classes and functions that `if`, `elif`, `else`, `try`, `except` and `finally` blocks at module
 * level declare. `if sys.version_info >= (3, 8): class Compat` and `try: ... except ImportError:
 * class Speedy` declare `Compat` and `Speedy` of the module as surely as a bare `class` does.
 *
 * A name is declared once. When the branches of a conditional declare the same name, the first
 * declaration in source order is the one kept and the later ones are not read, so the choice does not
 * depend on which branch a run would take. Only classes and functions are drawn out of the blocks:
 * what else they assign or import is not a declaration of the module.
 */
function getModuleStatements(parseTree, parseNodeType) {
  const types = parseNodeType || {};
  const declared = new Set();
  const result = [];
  const declare = (statement) => {
    const name = nameFromNode(statement.d && statement.d.name ? statement.d.name : null);
    if (name) {
      declared.add(name);
    }
  };
  const wrapped = (suite) => {
    const nested = [];
    for (const statement of getStatementsFromSuite(suite)) {
      if (!statement) {
        continue;
      }
      if (isClassNode(statement, parseNodeType) || isFunctionNode(statement, parseNodeType)) {
        const name = nameFromNode(statement.d && statement.d.name ? statement.d.name : null);
        if (name && !declared.has(name)) {
          declared.add(name);
          nested.push(statement);
        }
      } else {
        nested.push(...compound(statement));
      }
    }
    return nested;
  };
  const compound = (statement) => {
    if (!statement || !statement.d) {
      return [];
    }
    if (statement.nodeType === types.If) {
      const elseSuite = statement.d.elseSuite;
      return wrapped(statement.d.ifSuite).concat(
        elseSuite && elseSuite.nodeType === types.If ? compound(elseSuite) : wrapped(elseSuite));
    }
    if (statement.nodeType === types.Try) {
      const clauses = (statement.d.exceptClauses || []).map(clause => clause.d.exceptSuite);
      return [statement.d.trySuite].concat(clauses, [statement.d.elseSuite, statement.d.finallySuite])
        .reduce((all, suite) => all.concat(wrapped(suite)), []);
    }
    return [];
  };
  for (const statement of getStatementsFromSuite(parseTree)) {
    if (!statement) {
      continue;
    }
    if (isClassNode(statement, parseNodeType) || isFunctionNode(statement, parseNodeType)) {
      declare(statement);
      result.push(statement);
    } else {
      result.push(statement, ...compound(statement));
    }
  }
  return result;
}

function getStatementsFromSuite(node) {
  if (!node || !node.d) {
    return [];
  }
  const statements = Array.isArray(node.d.statements) ? node.d.statements : [];
  const flattened = [];
  for (const statement of statements) {
    if (statement && statement.d && Array.isArray(statement.d.statements)
      && !statement.d.name && !statement.d.params && !statement.d.parameters
      && !statement.d.arguments && !statement.d.baseClassExpressions
      && !statement.d.leftExpr && !statement.d.valueExpr
      && !statement.d.annotation && !statement.d.typeAnnotation
      && !statement.d.suite) {
      flattened.push(...statement.d.statements);
    } else {
      flattened.push(statement);
    }
  }
  return flattened;
}

function nameFromNode(node) {
  if (!node) {
    return '';
  }
  if (typeof node === 'string') {
    return node;
  }
  if (node.value) {
    return node.value;
  }
  if (node.d && typeof node.d.value === 'string') {
    return node.d.value;
  }
  if (node.d && typeof node.d.name === 'string') {
    return node.d.name;
  }
  if (node.d && node.d.name && typeof node.d.name.value === 'string') {
    return node.d.name.value;
  }
  return '';
}

function instanceFieldNameFromExpression(text) {
  if (!text) {
    return '';
  }
  const match = text.trim().match(/^(?:self|cls)\.([A-Za-z_][A-Za-z0-9_]*)$/);
  if (!match) {
    return '';
  }
  return match[1] || '';
}

function instanceFieldNameFromStatement(text) {
  if (!text) {
    return '';
  }
  const match = text.trim().match(/^(?:self|cls)\.([A-Za-z_][A-Za-z0-9_]*)(?:\s*:[^=]+)?\s*=/);
  if (!match) {
    return '';
  }
  return match[1] || '';
}

function textForNode(node, fileText) {
  if (!node || typeof node.start !== 'number' || typeof node.length !== 'number') {
    return '';
  }
  return fileText.slice(node.start, node.start + node.length).trim();
}

function stableImplementationHash(text) {
  const normalized = String(text || '').replace(/\r\n/g, '\n').trim();
  if (!normalized.length) {
    return 0;
  }
  let hash = 0;
  for (let i = 0; i < normalized.length; i += 1) {
    hash = ((hash * 31) + normalized.charCodeAt(i)) | 0;
  }
  // Zero is reserved for "nothing to hash", so that the Java side can tell a real hash from a missing one.
  return hash === 0 ? 1 : hash;
}

function decodeDocStringLiteral(statementText) {
  if (!statementText) {
    return '';
  }
  let text = statementText.trim();
  if (!text) {
    return '';
  }
  text = text.replace(/^[rRuUbBfF]+/, '');
  if (!text) {
    return '';
  }
  if ((text.startsWith('"""') && text.endsWith('"""'))
    || (text.startsWith("'''") && text.endsWith("'''"))) {
    return text.slice(3, -3).replace(/\r\n/g, '\n').trim();
  }
  if ((text.startsWith('"') && text.endsWith('"'))
    || (text.startsWith("'") && text.endsWith("'"))) {
    return text.slice(1, -1).replace(/\r\n/g, '\n').trim();
  }
  return '';
}

function extractSuiteDocString(suiteNode, fileText) {
  const statements = getStatementsFromSuite(suiteNode);
  if (!statements.length) {
    return '';
  }
  const firstStatement = statements[0];
  const statementText = textForNode(firstStatement, fileText);
  return decodeDocStringLiteral(statementText);
}

function normalizeDecoratorName(rawDecoratorText) {
  if (!rawDecoratorText) {
    return '';
  }
  let name = rawDecoratorText.trim();
  if (!name) {
    return '';
  }
  if (name.startsWith('@')) {
    name = name.slice(1);
  }
  const callIdx = name.indexOf('(');
  if (callIdx >= 0) {
    name = name.slice(0, callIdx);
  }
  name = name.trim();
  if (!name) {
    return '';
  }
  const parts = name.split('.');
  return parts[parts.length - 1] || '';
}

function extractDecoratorNames(functionNode, fileText) {
  const decorators = getNodeProp(functionNode, ['decorators', 'decoratorExpressions', 'decoratorNodes']);
  if (!Array.isArray(decorators) || !decorators.length) {
    return [];
  }
  const names = [];
  for (const decorator of decorators) {
    const raw = textForNode(decorator, fileText) || nameFromNode(decorator);
    const normalized = normalizeDecoratorName(raw);
    if (normalized) {
      names.push(normalized);
    }
  }
  return names;
}

/**
 * The arguments written on each decorator extractDecoratorNames reports, in the same order: for
 * each, null when the decorator is not a plain (dotted) name optionally called once, so its
 * arguments cannot be read, and otherwise the list of its arguments, empty when it has none.
 */
function extractDecoratorArguments(functionNode, ctx) {
  const decorators = getNodeProp(functionNode, ['decorators', 'decoratorExpressions', 'decoratorNodes']);
  if (!Array.isArray(decorators) || !decorators.length) {
    return [];
  }
  const uses = [];
  for (const decorator of decorators) {
    const raw = textForNode(decorator, ctx.fileText) || nameFromNode(decorator);
    if (!normalizeDecoratorName(raw)) {
      continue;
    }
    uses.push(readDecoratorArguments(raw, ctx));
  }
  return uses;
}

const PYTHON_DOTTED_PATH = /^[A-Za-z_][A-Za-z0-9_]*(\s*\.\s*[A-Za-z_][A-Za-z0-9_]*)*$/;

function readDecoratorArguments(rawDecoratorText, ctx) {
  let text = rawDecoratorText.trim();
  if (text.startsWith('@')) {
    text = text.slice(1).trim();
  }
  const open = text.indexOf('(');
  if (open < 0) {
    return PYTHON_DOTTED_PATH.test(text) ? [] : null;
  }
  if (!PYTHON_DOTTED_PATH.test(text.slice(0, open).trim())) {
    return null;
  }
  const close = matchingBracket(text, open);
  if (close < 0 || text.slice(close + 1).trim()) {
    return null;
  }
  const args = [];
  for (const part of splitExpressionList(text.slice(open + 1, close), ',')) {
    const argument = part.trim();
    if (!argument) {
      continue;
    }
    const keyword = /^([A-Za-z_][A-Za-z0-9_]*)\s*=(?!=)\s*([\s\S]*)$/.exec(argument);
    if (keyword) {
      args.push({ name: keyword[1], value: readLiteralValue(keyword[2].trim(), ctx) });
    } else if (argument.startsWith('*')) {
      args.push({ name: null, value: { kind: 'other' } });
    } else {
      args.push({ name: null, value: readLiteralValue(argument, ctx) });
    }
  }
  return args;
}

/** The index of the bracket closing the one at `open`, ignoring strings, or -1. */
function matchingBracket(text, open) {
  let depth = 0;
  for (let i = open; i < text.length; i += 1) {
    const skipped = skipStringLiteral(text, i);
    if (skipped < 0) {
      return -1;
    }
    i = skipped;
    const ch = text[i];
    if (ch === '(' || ch === '[' || ch === '{') {
      depth += 1;
    } else if (ch === ')' || ch === ']' || ch === '}') {
      depth -= 1;
      if (depth === 0) {
        return i;
      }
    }
  }
  return -1;
}

/**
 * If a string literal starts at `at`, the index of its last character, -1 for one that is never
 * closed; any other position is returned unchanged.
 */
function skipStringLiteral(text, at) {
  let i = at;
  if (i > 0 && /[A-Za-z0-9_]/.test(text[i - 1])) {
    return at;
  }
  while (i < text.length && i - at < 2 && /[rRbBuUfF]/.test(text[i])) {
    i += 1;
  }
  const quote = text[i];
  if (quote !== '"' && quote !== "'") {
    return at;
  }
  const triple = text.startsWith(quote.repeat(3), i);
  const delimiter = triple ? quote.repeat(3) : quote;
  let j = i + delimiter.length;
  while (j < text.length) {
    if (text[j] === '\\') {
      j += 2;
      continue;
    }
    if (text.startsWith(delimiter, j)) {
      return j + delimiter.length - 1;
    }
    j += 1;
  }
  return -1;
}

/** Splits at a separator outside every string and bracket. */
function splitExpressionList(text, separator) {
  const parts = [];
  let depth = 0;
  let start = 0;
  for (let i = 0; i < text.length; i += 1) {
    const skipped = skipStringLiteral(text, i);
    if (skipped < 0) {
      break;
    }
    i = skipped;
    const ch = text[i];
    if (ch === '(' || ch === '[' || ch === '{') {
      depth += 1;
    } else if (ch === ')' || ch === ']' || ch === '}') {
      depth -= 1;
    } else if (ch === separator && depth === 0) {
      parts.push(text.slice(start, i));
      start = i + 1;
    }
  }
  parts.push(text.slice(start));
  return parts;
}

const PYTHON_NUMBER = /^[+-]?(0[xX][0-9a-fA-F_]+|0[oO][0-7_]+|0[bB][01_]+|(\d[\d_]*\.?[\d_]*|\.\d[\d_]*)([eE][+-]?\d+)?)$/;
const PYTHON_DOTTED_NAME = /^[A-Za-z_][A-Za-z0-9_]*(\s*\.\s*[A-Za-z_][A-Za-z0-9_]*)+$/;
const PYTHON_NAME = /^[A-Za-z_][A-Za-z0-9_]*$/;

/**
 * An argument's value as written, when it is a literal: a string (implicit or `+` concatenation
 * included), a number, a boolean, a list or tuple of these, a class the repository declares, or a
 * dotted name, whose qualifier is resolved so that the Java side can tell an enum member. Any other
 * expression is `other`: present, and not read.
 */
function readLiteralValue(text, ctx) {
  const value = text.trim();
  if (!value) {
    return { kind: 'other' };
  }
  if (value.startsWith('(') && matchingBracket(value, 0) === value.length - 1) {
    const inner = value.slice(1, -1).trim();
    const items = splitExpressionList(inner, ',');
    if (!inner || items.length > 1) {
      return arrayValue(items, ctx);
    }
    return readLiteralValue(inner, ctx);
  }
  if (value.startsWith('[') && matchingBracket(value, 0) === value.length - 1) {
    return arrayValue(splitExpressionList(value.slice(1, -1), ','), ctx);
  }
  const string = readStringExpression(value);
  if (string !== null) {
    return { kind: 'string', text: string };
  }
  if (value === 'True' || value === 'False') {
    return { kind: 'boolean', text: value.toLowerCase() };
  }
  if (PYTHON_NUMBER.test(value)) {
    return { kind: 'number', text: value.replace(/_/g, '').replace(/^\+/, '') };
  }
  const signed = /^([+-])\s*(\S[\s\S]*)$/.exec(value);
  if (signed && PYTHON_NUMBER.test(signed[2].replace(/\s+/g, ''))) {
    return { kind: 'number', text: (signed[1] === '-' ? '-' : '') + signed[2].replace(/_/g, '').replace(/\s+/g, '') };
  }
  if (PYTHON_NAME.test(value)) {
    const target = resolveCandidate(value, ctx);
    return target ? { kind: 'class', text: value, target } : { kind: 'other' };
  }
  if (PYTHON_DOTTED_NAME.test(value)) {
    const compact = value.replace(/\s+/g, '');
    const qualifier = compact.slice(0, compact.lastIndexOf('.'));
    return { kind: 'qualified', text: compact, target: resolveCandidate(qualifier, ctx) || null };
  }
  return { kind: 'other' };
}

function arrayValue(items, ctx) {
  const elements = [];
  for (const item of items) {
    if (item.trim()) {
      elements.push(readLiteralValue(item, ctx));
    }
  }
  return { kind: 'array', elements };
}

/**
 * The value of one or more adjacent or `+`-joined plain string literals that make up the whole
 * text, or null. A byte string, an f-string, or anything else in the sequence makes it null.
 */
function readStringExpression(text) {
  let i = 0;
  let result = '';
  let count = 0;
  while (i < text.length) {
    while (i < text.length && /\s/.test(text[i])) {
      i += 1;
    }
    if (i >= text.length) {
      break;
    }
    if (text[i] === '+' && count > 0) {
      i += 1;
      continue;
    }
    const end = skipStringLiteral(text, i);
    if (end < 0 || end === i) {
      return null;
    }
    const decoded = decodePythonString(text.slice(i, end + 1));
    if (decoded === null) {
      return null;
    }
    result += decoded;
    count += 1;
    i = end + 1;
  }
  return count > 0 ? result : null;
}

function decodePythonString(literal) {
  const match = /^([A-Za-z]{0,2})('''|"""|'|")([\s\S]*)$/.exec(literal);
  if (!match) {
    return null;
  }
  const prefix = match[1].toLowerCase();
  if (prefix !== '' && prefix !== 'r' && prefix !== 'u') {
    return null;
  }
  const delimiter = match[2];
  const rest = match[3];
  if (rest.length < delimiter.length || !rest.endsWith(delimiter)) {
    return null;
  }
  const body = rest.slice(0, rest.length - delimiter.length);
  if (prefix === 'r') {
    return body;
  }
  let out = '';
  for (let i = 0; i < body.length; i += 1) {
    const ch = body[i];
    if (ch !== '\\') {
      out += ch;
      continue;
    }
    const next = body[i + 1];
    i += 1;
    switch (next) {
      case 'n': out += '\n'; break;
      case 't': out += '\t'; break;
      case 'r': out += '\r'; break;
      case '0': out += '\0'; break;
      case '\\': out += '\\'; break;
      case "'": out += "'"; break;
      case '"': out += '"'; break;
      case '\n': break;
      case 'x': {
        const hex = body.slice(i + 1, i + 3);
        if (!/^[0-9a-fA-F]{2}$/.test(hex)) {
          return null;
        }
        out += String.fromCharCode(parseInt(hex, 16));
        i += 2;
        break;
      }
      case 'u': {
        const hex = body.slice(i + 1, i + 5);
        if (!/^[0-9a-fA-F]{4}$/.test(hex)) {
          return null;
        }
        out += String.fromCharCode(parseInt(hex, 16));
        i += 4;
        break;
      }
      default:
        out += '\\' + (next === undefined ? '' : next);
    }
  }
  return out;
}

function getNodeProp(node, keys) {
  if (!node || !node.d) {
    return null;
  }
  for (const key of keys) {
    if (node.d[key]) {
      return node.d[key];
    }
  }
  return null;
}

function isClassNode(node, parseNodeType) {
  if (!node) {
    return false;
  }
  if (parseNodeType && parseNodeType.Class !== undefined) {
    return node.nodeType === parseNodeType.Class;
  }
  return !!(node.d && node.d.name && node.d.suite && (Array.isArray(node.d.baseClassExpressions) || Array.isArray(node.d.arguments)));
}

function isFunctionNode(node, parseNodeType) {
  if (!node) {
    return false;
  }
  if (parseNodeType && parseNodeType.Function !== undefined) {
    return node.nodeType === parseNodeType.Function;
  }
  return !!(node.d && node.d.name && node.d.suite && (Array.isArray(node.d.parameters) || Array.isArray(node.d.params)));
}

function collectLocalClasses(parseTree, parseNodeType) {
  const classes = new Set();
  const statements = getModuleStatements(parseTree, parseNodeType);
  for (const statement of statements) {
    if (!statement) {
      continue;
    }
    if (isClassNode(statement, parseNodeType)) {
      const name = nameFromNode(statement.d && statement.d.name ? statement.d.name : null);
      if (name) {
        classes.add(name);
      }
    }
  }
  return classes;
}

function resolveModuleClass(moduleName, className, ctx) {
  if (!moduleName || !className) {
    return null;
  }
  return resolveModuleSymbol(moduleName, className, ctx);
}

function extractClassInfo(type) {
  if (!type || typeof type !== 'object') {
    return null;
  }
  if (Array.isArray(type.subtypes)) {
    for (const sub of type.subtypes) {
      const info = extractClassInfo(sub);
      if (info) {
        return info;
      }
    }
  }
  const candidates = [];
  if (type.details) {
    candidates.push(type.details);
  }
  if (type.shared) {
    candidates.push(type.shared);
  }
  if (type.priv && type.priv.details) {
    candidates.push(type.priv.details);
  }
  for (const details of candidates) {
    if (!details) {
      continue;
    }
    const className = details.name || details.className || details.typeName;
    if (className) {
      return {
        className,
        moduleName: details.moduleName || details.module || null,
        fullName: details.fullName || details.qualifiedName || null,
        fileUri: details.fileUri || details.uri || null
      };
    }
  }
  if (type.classType) {
    return extractClassInfo(type.classType);
  }
  return null;
}

function resolveTypeWithEvaluator(node, raw, ctx) {
  if (!node || !ctx.evaluator) {
    return null;
  }
  let typeResult;
  try {
    typeResult = ctx.evaluator.getTypeOfExpression(node);
  } catch (err) {
    return null;
  }
  const type = typeResult ? typeResult.type : null;
  if (!type) {
    return null;
  }
  const info = extractClassInfo(type);
  if (!info) {
    return null;
  }
  const resolved = info.moduleName ? resolveModuleClass(info.moduleName, info.className, ctx) : null;
  if (resolved) {
    return { raw, targetUniqueName: resolved, externalLabel: null };
  }
  let label = null;
  if (info.moduleName && info.className) {
    label = info.moduleName + '.' + info.className;
  } else if (info.fullName) {
    label = info.fullName;
  }
  if (label) {
    return { raw, targetUniqueName: null, externalLabel: label };
  }
  return null;
}

/** How many aliases deep an alias of aliases is followed. */
const ALIAS_MAX_DEPTH = 6;

/** The arms of what an alias is assigned: `A | B`, `Union[A, B]` or `Optional[A]`. */
function aliasArms(rhs) {
  const text = String(rhs || '').trim();
  const wrapped = text.match(/^(?:typing\.)?(Union|Optional)\[([\s\S]*)\]$/);
  if (wrapped) {
    return wrapped[1] === 'Optional' ? [wrapped[2]] : splitTopLevelCsv(wrapped[2]);
  }
  return splitTopLevelUnion(text);
}

/** The context a repo module's own names resolve in. */
function aliasContextFor(filePath) {
  const { ctx, imports } = moduleImportsOf(filePath);
  return Object.assign({}, ctx, imports, { localClasses: classesDefinedIn(filePath) });
}

/** Whether a resolved unique name is a class declared in a repo module. */
function isRepoClassUnique(unique, ctx) {
  const parts = String(unique).split('.');
  for (let i = parts.length - 1; i >= 1; i -= 1) {
    const file = ctx.moduleIndex.get(parts.slice(0, i).join('.'));
    if (file) {
      return i === parts.length - 1 && classesDefinedIn(file).has(parts[i]);
    }
  }
  return false;
}

/**
 * The types a module-level assignment names when it is a type alias, or null when it is not.
 *
 * An alias is a value written entirely as type names -- `Shape = Circle | Square`,
 * `Either = Union[A, B]`, `Manager = LocalManager` -- at least one of which is a class declared in the
 * repo, or another such alias. Anything else assigned at module level is an ordinary value. The members
 * are returned flattened: an alias of an alias lists the inner alias and what it names.
 */
function aliasMembersOf(filePath, symbol) {
  const key = filePath + '\u0000' + symbol;
  if (state.aliasMembers.has(key)) {
    return state.aliasMembers.get(key);
  }
  if (state.aliasStack.has(key) || state.aliasStack.size > ALIAS_MAX_DEPTH) {
    return null;
  }
  const rhs = moduleDeclarations(filePath).aliases.get(symbol);
  let members = null;
  if (rhs) {
    state.aliasStack.add(key);
    try {
      members = aliasMembersOfValue(rhs, aliasContextFor(filePath));
    } finally {
      state.aliasStack.delete(key);
    }
  }
  if (state.aliasStack.size === 0) {
    state.aliasMembers.set(key, members);
  }
  return members;
}

function aliasMembersOfValue(rhs, ctx) {
  const arms = aliasArms(rhs).map(arm => arm.trim()).filter(arm => arm && !isUnionNonType(arm));
  if (!arms.length) {
    return null;
  }
  const members = [];
  let namesAType = false;
  for (const arm of arms) {
    if (!/^[A-Za-z_][A-Za-z0-9_.]*(\[[\s\S]*\])?$/.test(arm)) {
      return null;
    }
    const ref = resolveSingleTypeRef(null, arm, ctx);
    members.push(ref, ...ref.alternates);
    const bare = arm.replace(/\[[\s\S]*$/, '');
    if (ref.targetUniqueName && arm === bare
      && (isRepoClassUnique(ref.targetUniqueName, ctx) || ref.alternates.length > 0)) {
      namesAType = true;
    }
  }
  return namesAType ? members.map(member => Object.assign({}, member, { alternates: [] })) : null;
}

/** The repo declaration a name written in the current file refers to: its file and the name there. */
function declarationOfName(name, ctx) {
  const thisFile = ctx.moduleIndex ? ctx.moduleIndex.get(ctx.fullModuleName) : null;
  if (!name.includes('.')) {
    return thisFile ? findDeclaration(thisFile, name, 0, new Set()) : null;
  }
  const unique = resolveTypeFromRaw(name, ctx);
  const parts = unique ? unique.split('.') : [];
  for (let i = parts.length - 1; i >= 1; i -= 1) {
    const file = ctx.moduleIndex.get(parts.slice(0, i).join('.'));
    if (file) {
      return i === parts.length - 1 ? findDeclaration(file, parts[i], 0, new Set()) : null;
    }
  }
  return null;
}

/**
 * Looks through the type aliases an annotation names. The alias is a dependency in its own right and
 * so is each type it stands for, recorded as alternates of the annotation's reference.
 */
function expandTypeAliases(result, raw, ctx) {
  const extras = [];
  for (const candidate of extractCandidateNames(raw)) {
    const declared = declarationOfName(candidate, ctx);
    const members = declared ? aliasMembersOf(declared.filePath, declared.symbolName) : null;
    if (members) {
      extras.push({
        raw: candidate,
        targetUniqueName: uniqueNameInFile(declared.filePath, declared.symbolName, ctx),
        externalLabel: null,
        alternates: []
      }, ...members);
    }
  }
  if (!extras.length) {
    return result;
  }
  if (!result.targetUniqueName && extras[0].raw === raw) {
    const first = extras.shift();
    result.targetUniqueName = first.targetUniqueName;
    result.externalLabel = null;
  }
  const keyOf = ref => ref.targetUniqueName || ref.externalLabel || ref.raw;
  const present = new Set([result].concat(result.alternates).map(keyOf));
  for (const extra of extras) {
    if (!present.has(keyOf(extra))) {
      present.add(keyOf(extra));
      result.alternates.push(extra);
    }
  }
  return result;
}

function resolveSingleTypeRef(annotationNode, rawText, ctx) {
  return expandTypeAliases(resolveSingleTypeRefPlain(annotationNode, rawText, ctx), rawText ? rawText.trim() : '', ctx);
}

function resolveSingleTypeRefPlain(annotationNode, rawText, ctx) {
  const raw = rawText ? rawText.trim() : '';
  // A type variable is not a type: `x: T` depends on nothing named `T`. Its bound is recorded where
  // the variable is declared.
  if (ctx.typeVariables && ctx.typeVariables.has(raw)) {
    return { raw, targetUniqueName: null, externalLabel: null, typeVariable: true, alternates: [] };
  }
  const evaluated = resolveTypeWithEvaluator(annotationNode, raw, ctx);
  if (evaluated && evaluated.targetUniqueName) {
    return { raw, targetUniqueName: evaluated.targetUniqueName, externalLabel: null, alternates: [] };
  }
  const resolved = resolveTypeFromRaw(raw, ctx, true);
  if (resolved) {
    return { raw, targetUniqueName: resolved, externalLabel: null, alternates: [] };
  }
  const externalLabel = evaluated && evaluated.externalLabel ? evaluated.externalLabel : externalLabelFor(raw, ctx);
  return { raw, targetUniqueName: null, externalLabel, alternates: [] };
}

/**
 * The type or types an annotation names.
 *
 * A union names one type per arm, so it becomes one reference per arm: the first is the reference
 * itself and the rest are its alternates. `None` is dropped, being the absence of a value rather
 * than a dependency. Recording the union expression instead -- `str | None` -- matched no component
 * and never could, and got it wrong in both directions at once: the edge into `str` was missing,
 * and the declaration carried an edge out to a type that does not exist.
 *
 * The annotation text itself is preserved as `raw`, so a field still displays the type the author
 * wrote while depending on the types that type names.
 */
function resolveTypeRef(annotationNode, rawText, ctx) {
  const raw = rawText ? rawText.trim() : '';
  const arms = splitTopLevelUnion(raw);
  if (arms.length < 2) {
    return resolveSingleTypeRef(annotationNode, raw, ctx);
  }
  const resolvedArms = [];
  for (const arm of arms) {
    if (isUnionNonType(arm)) {
      continue;
    }
    // Resolved without the annotation node: the evaluator's type covers the whole union, not an arm.
    resolvedArms.push(resolveSingleTypeRef(null, arm, ctx));
  }
  if (!resolvedArms.length) {
    return { raw, targetUniqueName: null, externalLabel: null, alternates: [] };
  }
  const primary = resolvedArms[0];
  // Each further arm is an alternate, followed by the types its own alias stands for.
  const further = [];
  for (const arm of resolvedArms.slice(1)) {
    further.push(Object.assign({}, arm, { alternates: [] }), ...arm.alternates);
  }
  primary.alternates = primary.alternates.concat(further);
  primary.raw = raw;
  return primary;
}

/**
 * An annotation as written, with each string forward reference replaced by the type expression it
 * holds, so `Optional["Late"]` reads `Optional[Late]` and `"Holder.Deep.Leaf"` reads
 * `Holder.Deep.Leaf`. Strings that are not types -- the arguments of `Literal[...]` and the metadata of
 * `Annotated[...]` -- stay as written. `quoted` says whether any string was replaced.
 */
function annotationText(node, ctx, parseNodeType) {
  if (!node || typeof node.start !== 'number' || typeof node.length !== 'number') {
    return { text: '', quoted: false };
  }
  const types = parseNodeType || ctx.parseNodeType || {};
  const replacements = [];
  const visit = (n) => {
    if (!n || typeof n !== 'object' || n.nodeType === undefined) {
      return;
    }
    if (n.nodeType === types.StringList) {
      const inner = n.d && n.d.annotation;
      if (inner && Array.isArray(n.d.strings) && n.d.strings.length === 1) {
        replacements.push({ start: n.start, end: n.start + n.length, text: annotationText(inner, ctx, types).text });
      }
      return;
    }
    if (n.nodeType === types.Index) {
      const form = textForNode(n.d && n.d.leftExpr, ctx.fileText).split('.').pop();
      if (form === 'Literal') {
        return;
      }
      visit(n.d && n.d.leftExpr);
      const args = (n.d && n.d.items) || [];
      (form === 'Annotated' ? args.slice(0, 1) : args).forEach(visit);
      return;
    }
    for (const key of Object.keys(n.d || {})) {
      const child = n.d[key];
      if (Array.isArray(child)) {
        child.forEach(visit);
      } else if (child && typeof child === 'object' && key !== 'parent') {
        visit(child);
      }
    }
  };
  visit(node);
  if (!replacements.length) {
    return { text: textForNode(node, ctx.fileText), quoted: false };
  }
  replacements.sort((a, b) => b.start - a.start);
  let text = ctx.fileText.slice(node.start, node.start + node.length);
  for (const r of replacements) {
    text = text.slice(0, r.start - node.start) + r.text + text.slice(r.end - node.start);
  }
  return { text: text.trim(), quoted: true };
}

/**
 * What an annotation that held a string forward reference depends on: what it resolves to as an
 * unquoted one would, less any name that resolves to nothing. An unqualified name no declaration or
 * import supplies adds nothing, and is never recorded as the quoted literal.
 */
function withoutUnresolvedNames(ref) {
  if (!ref) {
    return ref;
  }
  const kept = [ref].concat(ref.alternates || []).filter(arm => arm.typeVariable
    || arm.targetUniqueName || String(arm.externalLabel || '').includes('.'));
  if (!kept.length) {
    // Flagged the way a type variable is: the annotation is displayed, and names nothing.
    return { raw: ref.raw, targetUniqueName: null, externalLabel: null, typeVariable: true, alternates: [] };
  }
  return Object.assign({}, kept[0], { raw: ref.raw, alternates: kept.slice(1) });
}

/** The display text of an annotation and what it depends on. */
function readAnnotation(node, ctx, parseNodeType, display) {
  const written = annotationText(node, ctx, parseNodeType);
  const text = display ? display(written.text) : written.text;
  const ref = resolveTypeRef(node, text, ctx);
  return { text, ref: written.quoted ? withoutUnresolvedNames(ref) : ref };
}

function buildSignature(name, params, returnType) {
  const parts = [];
  for (const param of params) {
    if (!param || !param.name) {
      continue;
    }
    parts.push(param.name + ': ' + (param.rawType || 'Any'));
  }
  const args = parts.join(', ');
  return name + '(' + args + ') : ' + (returnType || 'Any');
}

function stripStringsAndComments(text) {
  if (!text) {
    return '';
  }
  let sanitized = text;
  sanitized = sanitized.replace(/'''[\s\S]*?'''/g, ' ');
  sanitized = sanitized.replace(/"""[\s\S]*?"""/g, ' ');
  sanitized = sanitized.replace(/'(?:\\.|[^'\\])*'/g, ' ');
  sanitized = sanitized.replace(/"(?:\\.|[^"\\])*"/g, ' ');
  sanitized = sanitized.replace(/#.*$/gm, ' ');
  return sanitized;
}

function countMatches(text, pattern) {
  if (!text || !pattern) {
    return 0;
  }
  const matches = text.match(pattern);
  return matches ? matches.length : 0;
}

function computeCyclo(functionNode, fileText) {
  if (!functionNode || !functionNode.d || !functionNode.d.suite) {
    return 0;
  }
  const suiteText = textForNode(functionNode.d.suite, fileText);
  if (!suiteText) {
    return 1;
  }
  const body = stripStringsAndComments(suiteText);
  let complexity = 1;
  complexity += countMatches(body, /\bif\b/g);
  complexity += countMatches(body, /\belif\b/g);
  complexity += countMatches(body, /\bfor\b/g);
  complexity += countMatches(body, /\bwhile\b/g);
  complexity += countMatches(body, /\bexcept\b/g);
  complexity += countMatches(body, /\bcase\b/g);
  complexity += countMatches(body, /\band\b/g);
  complexity += countMatches(body, /\bor\b/g);
  return complexity;
}

function extractParams(funcNode, ctx, parseNodeType) {
  const params = [];
  let rawParams = [];
  if (funcNode && funcNode.d) {
    if (Array.isArray(funcNode.d.parameters)) {
      rawParams = funcNode.d.parameters;
    } else if (Array.isArray(funcNode.d.params)) {
      rawParams = funcNode.d.params;
    }
  }
  for (const param of rawParams) {
    if (!param) {
      continue;
    }
    const nameNode = getNodeProp(param, ['name', 'paramName', 'target']);
    const paramName = nameFromNode(nameNode);
    if (!paramName) {
      continue;
    }
    if (paramName === 'self' || paramName === 'cls') {
      continue;
    }
    const annotationNode = getNodeProp(param, ['typeAnnotation', 'annotation', 'typeExpression']);
    // `Any` is what an unannotated parameter *displays* as, not something the author depended on, so it
    // stays in the signature but yields no reference.
    const read = annotationNode ? readAnnotation(annotationNode, ctx, parseNodeType) : null;
    const rawType = read ? read.text : 'Any';
    const ref = read ? read.ref : null;
    params.push({
      name: paramName,
      rawType: ref ? ref.raw : rawType,
      implementationHash: stableImplementationHash(textForNode(param, ctx.fileText)),
      targetUniqueName: ref ? ref.targetUniqueName : null,
      externalLabel: ref ? ref.externalLabel : null,
      alternates: ref ? ref.alternates : [],
    typeVariable: ref ? !!ref.typeVariable : false
    });
  }
  return params;
}

/**
 * The name node an import clause binds: the alias of `import a.b as m` and `from a import b as m`,
 * the first segment of `import a.b`, and the name of `from a import b`.
 */
function importBindingNode(node, types) {
  if (node.nodeType === types.ImportFromAs) {
    return node.d.alias || node.d.name;
  }
  const nameParts = node.d.module && node.d.module.d ? node.d.module.d.nameParts : null;
  if (node.d.alias) {
    return node.d.alias;
  }
  return Array.isArray(nameParts) && nameParts.length ? nameParts[0] : null;
}

/**
 * The scope a function body resolves names in. Python binds an import written in a function -- at
 * any depth of `if`, `try`, `with` or loop -- in that function's scope alone, so its names resolve
 * the references in that function and in no other, and hide any module-level binding of the same
 * name there. A name the function also binds some other way -- `Order = fallback` -- may hold
 * anything wherever it is used, so it resolves through neither the import nor the module. A nested
 * function's or class's imports are its own. `functionImportNames` holds the names the function's
 * own imports resolve.
 */
function functionScope(functionNode, ctx, parseNodeType) {
  const types = parseNodeType || {};
  const statements = [];
  const importBound = new Set();

  function visit(node, isRoot) {
    if (!node || typeof node !== 'object' || !node.d) {
      return;
    }
    if (!isRoot && (isFunctionNode(node, parseNodeType) || isClassNode(node, parseNodeType))) {
      return;
    }
    if (node.nodeType === types.Import || node.nodeType === types.ImportFrom) {
      statements.push(...collectImportStatements(textForNode(node, ctx.fileText)));
    }
    if (node.nodeType === types.ImportAs || node.nodeType === types.ImportFromAs) {
      const bound = nameFromNode(importBindingNode(node, types));
      if (bound) {
        importBound.add(bound);
      }
    }
    for (const key of Object.keys(node.d)) {
      const child = node.d[key];
      if (Array.isArray(child)) {
        child.forEach(item => visit(item, false));
      } else if (child && typeof child === 'object' && child.d) {
        visit(child, false);
      }
    }
  }

  visit(functionNode, true);
  if (!importBound.size) {
    return ctx;
  }
  const rebound = collectLocalNames(functionNode, parseNodeType, false);
  const importedModules = new Map(ctx.importedModules);
  const importedSymbols = new Map(ctx.importedSymbols);
  const localClasses = new Set(ctx.localClasses);
  for (const name of importBound) {
    importedModules.delete(name);
    importedSymbols.delete(name);
    localClasses.delete(name);
  }
  const functionImportNames = new Set();
  const local = resolveImportStatements(statements, ctx);
  // `import a.b` is keyed `a.b` and binds `a`.
  const boundBy = key => key.split('.')[0];
  for (const [key, moduleName] of local.importedModules) {
    if (!rebound.has(boundBy(key))) {
      importedModules.set(key, moduleName);
      functionImportNames.add(boundBy(key));
    }
  }
  for (const [key, symbol] of local.importedSymbols) {
    if (!rebound.has(key)) {
      importedSymbols.set(key, symbol);
      functionImportNames.add(key);
    }
  }
  return Object.assign({}, ctx, { importedModules, importedSymbols, localClasses, functionImportNames });
}

/**
 * The names a function binds for itself: its parameters, and every name it assigns, loops over,
 * catches, imports, deletes or otherwise declares anywhere in its body. Python decides scope per
 * function, not per statement -- a name bound anywhere in a function is local throughout it -- so in
 * `models = pick(); models.User()` the `models` is never the module imported under that name, on
 * whichever line the assignment sits. Nested functions and classes contribute only their own name.
 * Lambdas and comprehensions have scopes of their own but are counted in, which can only withhold a
 * reference, never invent one. Without `includeImports`, a name bound only by an import is left out.
 */
function collectLocalNames(functionNode, parseNodeType, includeImports = true) {
  const names = new Set();
  const globals = new Set();
  const types = parseNodeType || {};

  function addTarget(expr) {
    if (!expr || !expr.d) {
      return;
    }
    if (expr.nodeType === types.Name) {
      names.add(nameFromNode(expr));
    } else if (expr.nodeType === types.Tuple || expr.nodeType === types.List) {
      (expr.d.items || []).forEach(addTarget);
    } else if (expr.nodeType === types.TypeAnnotation) {
      addTarget(expr.d.valueExpr);
    } else if (expr.nodeType === types.Unpack) {
      addTarget(expr.d.expr);
    }
    // Assigning to an attribute or an item -- `a.b = x`, `a[0] = x` -- binds no name.
  }

  function visit(node, isRoot) {
    if (!node || typeof node !== 'object' || !node.d) {
      return;
    }
    if (!isRoot && (isFunctionNode(node, parseNodeType) || isClassNode(node, parseNodeType))) {
      names.add(nameFromNode(node.d.name));
      return;
    }
    const type = node.nodeType;
    if (type === types.Parameter || type === types.Except || type === types.AssignmentExpression) {
      addTarget(node.d.name);
    } else if (type === types.Assignment || type === types.AugmentedAssignment) {
      addTarget(node.d.leftExpr);
    } else if (type === types.TypeAnnotation) {
      addTarget(node.d.valueExpr);
    } else if (type === types.For || type === types.ComprehensionFor) {
      addTarget(node.d.targetExpr);
    } else if (type === types.WithItem || type === types.PatternCapture || type === types.PatternAs) {
      addTarget(node.d.target);
    } else if (type === types.Del || type === types.Nonlocal) {
      (node.d.targets || []).forEach(addTarget);
    } else if (type === types.Global) {
      (node.d.targets || []).forEach(target => globals.add(nameFromNode(target)));
    } else if (includeImports && (type === types.ImportAs || type === types.ImportFromAs)) {
      addTarget(importBindingNode(node, types));
    }
    for (const key of Object.keys(node.d)) {
      const child = node.d[key];
      if (Array.isArray(child)) {
        child.forEach(item => visit(item, false));
      } else if (child && typeof child === 'object' && child.d) {
        visit(child, false);
      }
    }
  }

  visit(functionNode, true);
  // `global models` makes `models` the module-level name, which is the import.
  globals.forEach(name => names.delete(name));
  return names;
}

/**
 * The name an expression spells when it is nothing but identifiers joined by dots -- `pkg.models.User`
 * -- or null for anything else, `self.x`, `f().x` and `a[0].x` included where they are not plain names.
 */
function dottedNameOf(node, parseNodeType) {
  if (!node || !node.d || !parseNodeType) {
    return null;
  }
  if (node.nodeType === parseNodeType.Name) {
    return nameFromNode(node) || null;
  }
  if (parseNodeType.MemberAccess !== undefined && node.nodeType === parseNodeType.MemberAccess) {
    const left = dottedNameOf(node.d.leftExpr, parseNodeType);
    const member = nameFromNode(node.d.member);
    return left && member ? left + '.' + member : null;
  }
  return null;
}

/**
 * Whether a file-level import binds the name to a module: `import a.b` binds `a`, `import a.b as m`
 * binds `m`, and `from a import b` binds `b` when `a.b` is a module.
 */
function bindsModule(name, ctx) {
  if (!name || !ctx.importedModules) {
    return false;
  }
  if (ctx.importedModules.has(name)) {
    return true;
  }
  for (const [localName, moduleName] of ctx.importedModules) {
    if (localName === moduleName && localName.startsWith(name + '.')) {
      return true;
    }
  }
  return false;
}

/**
 * The member a dotted name reaches through a module binding: the name immediately after the longest
 * prefix that is a module in the repo. `m.User.LIMIT` with `m` bound to `pkg.models` is
 * `pkg.models.User`. A name that is itself a module names no member, and a name under no repo module
 * resolves to nothing.
 */
function resolveModuleMember(dotted, ctx) {
  const parts = dotted.split('.');
  const boundModule = ctx.importedModules.get(parts[0]);
  const qualified = boundModule ? boundModule.split('.').concat(parts.slice(1)) : parts;
  if (ctx.moduleIndex.has(qualified.join('.'))) {
    return null;
  }
  for (let i = qualified.length - 1; i >= 1; i -= 1) {
    const moduleName = qualified.slice(0, i).join('.');
    if (ctx.moduleIndex.has(moduleName)) {
      return resolveModuleSymbol(moduleName, qualified[i], ctx);
    }
  }
  return null;
}

function collectBodyReferences(functionNode, ctx, parseNodeType) {
  const suite = functionNode && functionNode.d ? functionNode.d.suite : null;
  if (!suite || !suite.d) {
    return [];
  }
  return collectReferences(functionNode, null, null, ctx, parseNodeType);
}

/**
 * What the decorators of a definition refer to, as the arguments of `@register(Baz)` do: a class's
 * decorators are evaluated in the scope around it, so they are read the way a function's are.
 */
function collectDecoratorReferences(definitionNode, ctx, parseNodeType) {
  return collectReferences(definitionNode, null, null, ctx, parseNodeType, true);
}

/**
 * The names a class body binds directly: its nested classes and methods, its class attributes and
 * whatever else its statements assign or import. A name bound in a method's body is not among them.
 */
function classBodyBoundNames(suiteStatements, parseNodeType) {
  const names = new Set();
  for (const node of suiteStatements) {
    if (!node || !node.d) {
      continue;
    }
    if (isClassNode(node, parseNodeType) || isFunctionNode(node, parseNodeType)) {
      const name = nameFromNode(node.d.name);
      if (name) {
        names.add(name);
      }
      continue;
    }
    collectLocalNames(node, parseNodeType).forEach(name => names.add(name));
  }
  return names;
}

/**
 * What the value assigned to a class attribute or a module-level name refers to -- `objects = Manager()`,
 * `handlers = [Foo]`, `c = lambda self: Cfg()`, `x = Bar.LIMIT` -- found the way the same expression is
 * found in a function body. `boundNames` are the names the surrounding class body has already bound,
 * which hide the module's own for what follows.
 */
function collectValueExpressionReferences(valueExpression, boundNames, ctx, parseNodeType) {
  if (!valueExpression || !valueExpression.d) {
    return [];
  }
  return collectReferences(null, valueExpression, boundNames, ctx, parseNodeType);
}

/**
 * The references made by a function's body, or by a single value expression (exactly one of
 * `functionNode` and `valueExpression` is given).
 */
function collectReferences(functionNode, valueExpression, boundNames, ctx, parseNodeType, decoratorsOnly) {
  const suite = functionNode ? functionNode.d.suite : null;
  const refs = [];
  const seen = new Set();
  const localNames = functionNode ? collectLocalNames(functionNode, parseNodeType)
    : collectLocalNames(valueExpression, parseNodeType);
  (boundNames || []).forEach(name => localNames.add(name));
  // A name the function's own imports bind resolves through them.
  (ctx.functionImportNames || []).forEach(name => localNames.delete(name));
  // The names bound by the scopes being walked, innermost last. A nested function or class binds
  // names of its own, which hide the module's for everything written inside it.
  let scopes = [localNames];
  // Whether the code being walked is a default value or a decorator, where a bare function name
  // handed on as a value (`Depends(get_db)`) is a reference as well.
  let inHeader = false;
  const isLocal = name => scopes.some(scope => scope.has(name));
  const moduleMemberTargets = new Set();
  const calleeNames = new Set();

  /**
   * A dotted name whose first segment is a module binding the function does not shadow resolves the
   * way the same name written as an annotation does, to the member of the repo module it names. That
   * is the same reference `User()` gets after `from pkg.models import User`: a class in the repo is an
   * internal dependency, and anything else -- a function, a constant, a name the module does not
   * define -- is classified external by the model, as the imported form's is.
   */
  function addModuleMemberRef(dotted) {
    if (!dotted || dotted.indexOf('.') < 0) {
      return false;
    }
    const base = dotted.substring(0, dotted.indexOf('.'));
    if (isLocal(base) || !bindsModule(base, ctx)) {
      return false;
    }
    const resolved = resolveModuleMember(dotted, ctx);
    if (!resolved) {
      return false;
    }
    pushTarget(dotted, resolved);
    return true;
  }

  /**
   * A repo class used by name in the body is a dependency however it is used -- `User.create()`,
   * `User.LIMIT`, `isinstance(x, User)`, `register(User)`, `handler = User`, `(User, Admin)` -- as
   * it already was when called, `User()`. Only a name that is a repo class counts, and only where the
   * function does not bind that name itself.
   */
  function addClassRef(name, raw) {
    if (!name || isLocal(name)) {
      return false;
    }
    const resolved = resolveRepoClass(name, ctx);
    if (!resolved) {
      return false;
    }
    pushTarget(raw, resolved);
    return true;
  }

  /**
   * A function or value declared at the top of this module, used by name where the function does not
   * bind that name itself, is a dependency on that declaration: `helper()`, `register(helper)`,
   * `for p in _PARSERS`.
   */
  function addModuleLevelRef(name) {
    if (!name || isLocal(name) || (ctx.functionImportNames && ctx.functionImportNames.has(name))) {
      return false;
    }
    if (!(ctx.localFunctions && ctx.localFunctions.has(name))
      && !(ctx.localValues && ctx.localValues.has(name))) {
      return false;
    }
    pushTarget(name, localUniqueName(name, ctx));
    return true;
  }

  /**
   * A constant imported from a repo module that declares it is a dependency on that declaration
   * wherever it is used and the function does not bind the name itself: `code in CODES` after `from pkg.errors import CODES`.
   */
  function addImportedValueRef(name) {
    if (!name || isLocal(name) || !ctx.importedSymbols) {
      return false;
    }
    const imported = ctx.importedSymbols.get(name);
    if (!imported || !imported.moduleName || !imported.symbolName) {
      return false;
    }
    const filePath = moduleFileFor(imported.moduleName, ctx);
    const declared = filePath ? findDeclaration(filePath, imported.symbolName, 0, new Set()) : null;
    // A function passed on as a value is not a type or a constant, and is left out.
    if (!declared || moduleDeclarations(declared.filePath).functions.has(declared.symbolName)) {
      return false;
    }
    pushTarget(name, uniqueNameInFile(declared.filePath, declared.symbolName, ctx));
    return true;
  }

  function pushTarget(raw, target) {
    if (!moduleMemberTargets.has(target)) {
      moduleMemberTargets.add(target);
      refs.push({ raw, targetUniqueName: target, externalLabel: target });
    }
  }

  function isKnownImport(baseName) {
    if (!baseName) return false;
    if (ctx.importedModules && ctx.importedModules.has(baseName)) return true;
    if (ctx.importedSymbols && ctx.importedSymbols.has(baseName)) return true;
    if (ctx.localClasses && ctx.localClasses.has(baseName)) return true;
    return false;
  }

  function addRef(calleeText) {
    if (!calleeText || seen.has(calleeText)) return;
    seen.add(calleeText);
    const dotIdx = calleeText.indexOf('.');
    if (dotIdx > 0) {
      const base = calleeText.substring(0, dotIdx);
      if (isLocal(base)) {
        return;
      }
      if (isKnownImport(base)) {
        const resolved = resolveTypeFromRaw(base, ctx);
        if (resolved) {
          refs.push({ raw: calleeText, targetUniqueName: null, externalLabel: resolved + calleeText.substring(dotIdx) });
        }
      }
    } else if (!isLocal(calleeText)) {
      const resolved = resolveTypeFromRaw(calleeText, ctx);
      if (resolved) {
        if (!moduleMemberTargets.has(resolved)) {
          moduleMemberTargets.add(resolved);
          refs.push({ raw: calleeText, targetUniqueName: resolved, externalLabel: resolved });
        }
      } else {
        addExternalConstruction(calleeText);
      }
    }
  }

  /**
   * A capitalised name an import binds to a module outside the repo and that the code calls --
   * `Session()` after `from requests import Session` -- constructs a class of a package the repo
   * does not hold, and is the dependency the annotation `s: Session` already records, under the same
   * label. Only a name an import binds counts, and only where the function does not bind it itself;
   * a name no import binds is never guessed at.
   */
  function addExternalConstruction(name) {
    if (!/^[A-Z]/.test(name) || TYPE_VARIABLE_FACTORIES.has(name) || isLocal(name) || !ctx.importedSymbols) {
      return;
    }
    const imported = ctx.importedSymbols.get(name);
    if (!imported || !imported.moduleName || !imported.symbolName || moduleFileFor(imported.moduleName, ctx)) {
      return;
    }
    refs.push({ raw: name, targetUniqueName: null, externalLabel: imported.moduleName + '.' + imported.symbolName });
  }

  /**
   * A bound or constraint of a type variable written as a string -- `TypeVar("T", bound="Cfg")` -- is
   * the forward reference to a class it looks like.
   */
  function addTypeVariableStringBounds(call) {
    if (!isTypeVariableFactory(call.d.leftExpr, parseNodeType)) {
      return;
    }
    (call.d.args || []).forEach((arg, index) => {
      const keyword = arg && arg.d && arg.d.name ? nameFromNode(arg.d.name) : null;
      if ((index === 0 && !keyword) || (keyword && keyword !== 'bound')) {
        return;
      }
      const text = stringLiteralValue(arg.d.valueExpr, parseNodeType);
      for (const candidate of text ? extractCandidateNames(text) : []) {
        if (candidate.indexOf('.') > 0) {
          addModuleMemberRef(candidate);
        } else {
          addClassRef(candidate, candidate);
        }
      }
    });
  }

  function walk(node) {
    if (!node || typeof node !== 'object' || !node.d) return;

    if (isClassNode(node, parseNodeType) || isFunctionNode(node, parseNodeType)) {
      walkNested(node);
      return;
    }

    // A binding or a scope declaration names nothing it uses: a function-local import, and the targets of
    // `global` and `nonlocal`.
    if (parseNodeType && (node.nodeType === parseNodeType.Import || node.nodeType === parseNodeType.ImportFrom
      || node.nodeType === parseNodeType.Global || node.nodeType === parseNodeType.Nonlocal)) {
      return;
    }

    if (parseNodeType && parseNodeType.Call !== undefined && node.nodeType === parseNodeType.Call) {
      addTypeVariableStringBounds(node);
      const calleeNode = node.d.leftExpr;
      if (calleeNode && !addModuleMemberRef(dottedNameOf(calleeNode, parseNodeType))) {
        addRef(textForNode(calleeNode, ctx.fileText));
      }
      if (calleeNode && calleeNode.nodeType === parseNodeType.Name) {
        // `User()` is recorded by addRef above, as it always has been.
        calleeNames.add(calleeNode);
        addModuleLevelRef(nameFromNode(calleeNode));
      }
    }

    // A class reached through a module, or named directly, is a dependency wherever it is used, not only
    // when called: `pkg.models.User.LIMIT`, `isinstance(x, models.User)`, `User.create()`. A chain of
    // plain names holds nothing further to visit.
    if (parseNodeType && parseNodeType.MemberAccess !== undefined && node.nodeType === parseNodeType.MemberAccess) {
      const dotted = dottedNameOf(node, parseNodeType);
      if (dotted) {
        if (!addModuleMemberRef(dotted)) {
          addClassRef(dotted.substring(0, dotted.indexOf('.')), dotted);
        }
        return;
      }
    }

    if (parseNodeType && parseNodeType.Name !== undefined && node.nodeType === parseNodeType.Name) {
      if (!calleeNames.has(node)) {
        const name = nameFromNode(node);
        if (!addClassRef(name, name) && !addModuleLevelRef(name) && !addImportedValueRef(name) && inHeader && name && !isLocal(name)
          && ctx.importedSymbols && ctx.importedSymbols.has(name)) {
          addRef(name);
        }
      }
      return;
    }

    // The keyword of `f(User=1)` and `case Point(x=px)`, and the attribute of `obj().User`, are names that
    // refer to nothing in this scope.
    const nonUseKey = !parseNodeType ? null
      : (node.nodeType === parseNodeType.Argument || node.nodeType === parseNodeType.PatternClassArgument) ? 'name'
        : node.nodeType === parseNodeType.MemberAccess ? 'member' : null;
    for (const key of Object.keys(node.d)) {
      if (key === nonUseKey) {
        continue;
      }
      const child = node.d[key];
      if (Array.isArray(child)) {
        for (const item of child) {
          walk(item);
        }
      } else if (child && typeof child === 'object' && child.d) {
        walk(child);
      }
    }
  }

  /**
   * What a definition evaluates where it is written, in the scope around it: its decorators, the
   * default values of its parameters, and a class's bases and keyword arguments.
   */
  function walkHeader(node) {
    const wasInHeader = inHeader;
    inHeader = true;
    (getNodeProp(node, ['decorators']) || []).forEach(walk);
    // The bounds of a PEP 695 type parameter list -- `def f[T: Cfg]`, `class K[V: Qux]` -- are
    // evaluated where the definition is.
    for (const typeParam of typeParametersOf(node)) {
      walk(typeParam.d.boundExpr);
      walk(typeParam.d.defaultExpr);
    }
    if (isClassNode(node, parseNodeType)) {
      (getNodeProp(node, ['arguments']) || []).forEach(walk);
    } else {
      for (const param of (node.d && (node.d.params || node.d.parameters)) || []) {
        walk(getNodeProp(param, ['defaultValue', 'defaultExpr', 'defaultExpression']));
      }
    }
    inHeader = wasInHeader;
  }

  /**
   * A function or class written inside what is being walked: its header belongs to the scope around
   * it and its body to a scope of its own, so what it names counts for the enclosing function or
   * class unless the nested scope binds the name itself.
   */
  function walkNested(node) {
    walkHeader(node);
    const nestedSuite = node.d ? node.d.suite : null;
    if (!nestedSuite || !nestedSuite.d) {
      return;
    }
    const nestedLocals = collectLocalNames(node, parseNodeType);
    scopes.push(nestedLocals);
    for (const stmt of getStatementsFromSuite(nestedSuite)) {
      walk(stmt);
    }
    scopes.pop();
  }

  if (valueExpression) {
    walk(valueExpression);
    return refs;
  }
  if (decoratorsOnly) {
    scopes = [];
    inHeader = true;
    (getNodeProp(functionNode, ['decorators']) || []).forEach(walk);
    return refs;
  }
  // The definition's own decorators and defaults are evaluated outside its body.
  const bodyScopes = scopes;
  scopes = [];
  walkHeader(functionNode);
  scopes = bodyScopes;
  const statements = getStatementsFromSuite(suite);
  for (const stmt of statements) {
    walk(stmt);
  }
  return refs;
}

/** The type parameters a definition declares with PEP 695 syntax, `def f[T: Cfg]` or `class K[V]`. */
function typeParametersOf(node) {
  const list = node && node.d ? node.d.typeParams : null;
  return list && list.d && Array.isArray(list.d.params) ? list.d.params.filter(param => param && param.d) : [];
}

const TYPE_VARIABLE_FACTORIES = new Set(['TypeVar', 'ParamSpec', 'TypeVarTuple']);

/** Whether an expression names `TypeVar`, `ParamSpec` or `TypeVarTuple`, bare or through a module. */
function isTypeVariableFactory(callee, parseNodeType) {
  const dotted = dottedNameOf(callee, parseNodeType);
  return !!dotted && TYPE_VARIABLE_FACTORIES.has(dotted.substring(dotted.lastIndexOf('.') + 1));
}

/** The text of a string literal written without interpolation, or null for any other expression. */
function stringLiteralValue(node, parseNodeType) {
  if (!node || !node.d || node.nodeType !== parseNodeType.StringList || !Array.isArray(node.d.strings)
    || !node.d.strings.length) {
    return null;
  }
  let text = '';
  for (const part of node.d.strings) {
    if (!part || part.nodeType !== parseNodeType.String || typeof part.d.value !== 'string') {
      return null;
    }
    text += part.d.value;
  }
  return text;
}

/**
 * The type variables a module declares at its top level -- `T = TypeVar("T")`. A type variable names no
 * type, so an annotation naming one records no dependency.
 */
function collectTypeVariableNames(parseTree, parseNodeType) {
  const names = new Set();
  for (const statement of getStatementsFromSuite(parseTree)) {
    if (statement && statement.nodeType === parseNodeType.Assignment && statement.d.rightExpr
      && statement.d.rightExpr.nodeType === parseNodeType.Call
      && isTypeVariableFactory(statement.d.rightExpr.d.leftExpr, parseNodeType)) {
      const name = nameFromNode(statement.d.leftExpr);
      if (name) {
        names.add(name);
      }
    }
  }
  return names;
}

/** The context for a definition whose own PEP 695 type parameters are in scope inside it. */
function withTypeParameters(ctx, node) {
  const own = typeParametersOf(node).map(param => nameFromNode(param.d.name)).filter(Boolean);
  if (!own.length) {
    return ctx;
  }
  return Object.assign({}, ctx, { typeVariables: new Set([...(ctx.typeVariables || []), ...own]) });
}

/**
 * The local variables a function binds in its own body.
 *
 * Python rebinds freely -- `x = 1` then `x = "a"` is one variable, not two -- and a component is
 * identified by its name, so this yields one local per distinct name bound, taking the first
 * binding for the declared type and the hash. What counts as binding a variable: an assignment, an
 * annotated assignment, a `for` target, a `with ... as`, an `except ... as`, a walrus, a `match`
 * capture, and the elements of a tuple or list unpacking.
 *
 * Deliberately excluded. Parameters, which are components of their own. `self` and `cls`. Attribute
 * and subscript targets -- `self.x = 1` and `a[0] = 1` bind no name, and the first is already
 * modelled as a field. Anything a nested function or class binds, and the nested definition's own
 * name. Comprehension iteration variables, which have a scope of their own in Python 3. Imports,
 * which bind a module rather than a variable. And names declared `global` or `nonlocal`, which name
 * a binding in another scope entirely.
 */
function collectLocalVariables(functionNode, ctx, parseNodeType) {
  const types = parseNodeType || {};
  const found = new Map();
  const declaredElsewhere = new Set();
  const parameterNames = new Set();

  const rawParams = (functionNode && functionNode.d
    && (functionNode.d.parameters || functionNode.d.params)) || [];
  for (const param of rawParams) {
    const paramName = nameFromNode(getNodeProp(param, ['name', 'paramName', 'target']));
    if (paramName) {
      parameterNames.add(paramName);
    }
  }

  function record(expr, statement, annotationNode) {
    if (!expr || !expr.d) {
      return;
    }
    if (expr.nodeType === types.Name) {
      const localName = nameFromNode(expr);
      if (!localName || localName === 'self' || localName === 'cls') {
        return;
      }
      if (parameterNames.has(localName) || found.has(localName)) {
        return;
      }
      found.set(localName, { name: localName, statement, annotationNode, node: expr });
      return;
    }
    if (expr.nodeType === types.Tuple || expr.nodeType === types.List) {
      (expr.d.items || []).forEach(item => record(item, statement, null));
      return;
    }
    if (expr.nodeType === types.TypeAnnotation) {
      record(expr.d.valueExpr, statement, getNodeProp(expr, ANNOTATION_KEYS));
      return;
    }
    if (expr.nodeType === types.Unpack) {
      record(expr.d.expr, statement, null);
    }
    // An attribute or subscript target binds no name.
  }

  function visit(node, isRoot) {
    if (!node || typeof node !== 'object' || !node.d) {
      return;
    }
    if (!isRoot && (isFunctionNode(node, parseNodeType) || isClassNode(node, parseNodeType))) {
      return;
    }
    const type = node.nodeType;
    if (type === types.Import || type === types.ImportFrom || type === types.ComprehensionFor) {
      return;
    }
    if (type === types.Global || type === types.Nonlocal) {
      (node.d.targets || []).forEach(target => declaredElsewhere.add(nameFromNode(target)));
      return;
    }
    if (type === types.Assignment) {
      record(node.d.leftExpr, node, null);
    } else if (type === types.TypeAnnotation) {
      record(node.d.valueExpr, node, getNodeProp(node, ANNOTATION_KEYS));
    } else if (type === types.For) {
      record(node.d.targetExpr, node, null);
    } else if (type === types.WithItem) {
      record(node.d.target, node, null);
    } else if (type === types.Except || type === types.AssignmentExpression) {
      record(node.d.name, node, null);
    } else if (type === types.PatternCapture || type === types.PatternAs) {
      record(node.d.target, node, null);
    }
    for (const key of Object.keys(node.d)) {
      const child = node.d[key];
      if (Array.isArray(child)) {
        child.forEach(item => visit(item, false));
      } else if (child && typeof child === 'object' && child.d) {
        visit(child, false);
      }
    }
  }

  visit(functionNode, true);
  declaredElsewhere.forEach(name => found.delete(name));

  const locals = [];
  for (const entry of found.values()) {
    // An unannotated binding displays as `Any`, the way an unannotated parameter does.
    const read = entry.annotationNode ? readAnnotation(entry.annotationNode, ctx, parseNodeType) : null;
    const rawType = read ? read.text : 'Any';
    // An unannotated binding has the class Pyright infers for it, when it infers exactly one.
    const inferred = !read && ctx.inferType ? ctx.inferType(entry.node) : null;
    const ref = read ? read.ref : (inferred ? Object.assign({ raw: rawType, alternates: [] }, inferred) : null);
    locals.push({
      name: entry.name,
      rawType: ref ? ref.raw : rawType,
      implementationHash: stableImplementationHash(textForNode(entry.statement, ctx.fileText)),
      targetUniqueName: ref ? ref.targetUniqueName : null,
      externalLabel: ref ? ref.externalLabel : null,
      alternates: ref ? ref.alternates : [],
    typeVariable: ref ? !!ref.typeVariable : false
    });
  }
  return locals;
}

function extractMethod(methodNode, enclosingCtx, parseNodeType) {
  const ctx = withTypeParameters(enclosingCtx, methodNode);
  const name = nameFromNode(methodNode.d && methodNode.d.name ? methodNode.d.name : null);
  if (!name) {
    return null;
  }
  const decorators = extractDecoratorNames(methodNode, ctx.fileText);
  const decoratorArguments = extractDecoratorArguments(methodNode, ctx);
  const isClassMethod = decorators.includes('classmethod');
  const isStaticMethod = decorators.includes('staticmethod');
  const comment = extractSuiteDocString(methodNode.d ? methodNode.d.suite : null, ctx.fileText);
  const params = extractParams(methodNode, ctx, parseNodeType);
  const returnNode = getNodeProp(methodNode, ['returnTypeAnnotation', 'returnType', 'returnAnnotation']);
  const returnRead = returnNode ? readAnnotation(returnNode, ctx, parseNodeType, normalizePythonTypeDisplay) : null;
  const returnRaw = returnRead ? returnRead.text : 'Any';
  const signature = buildSignature(name, params, returnRaw || 'Any');
  const uniqueName = ctx.classUniqueName + '.' + signature;
  // An absent return annotation displays as `Any` but is not a dependency on anything.
  const returnRef = returnRead ? returnRead.ref : null;
  const cyclo = computeCyclo(methodNode, ctx.fileText);
  // The whole def, not just its suite: a changed signature or decorator is a change too.
  const implementationHash = stableImplementationHash(textForNode(methodNode, ctx.fileText));
  // Parameter and return annotations are evaluated where the def is; the body in its own scope.
  const bodyCtx = functionScope(methodNode, ctx, parseNodeType);
  const bodyReferences = collectBodyReferences(methodNode, bodyCtx, parseNodeType);
  return {
    name,
    signature,
    uniqueName,
    implementationHash,
    comment,
    cyclo,
    classMethod: isClassMethod,
    staticMethod: isStaticMethod,
    decorators,
    decoratorArguments,
    params,
    locals: collectLocalVariables(methodNode, bodyCtx, parseNodeType),
    return: returnRef,
    bodyReferences
  };
}

function extractFunction(functionNode, enclosingCtx, parseNodeType) {
  const ctx = withTypeParameters(enclosingCtx, functionNode);
  const name = nameFromNode(functionNode.d && functionNode.d.name ? functionNode.d.name : null);
  if (!name) {
    return null;
  }
  const comment = extractSuiteDocString(functionNode.d ? functionNode.d.suite : null, ctx.fileText);
  const decorators = extractDecoratorNames(functionNode, ctx.fileText);
  const decoratorArguments = extractDecoratorArguments(functionNode, ctx);
  const params = extractParams(functionNode, ctx, parseNodeType);
  const returnNode = getNodeProp(functionNode, ['returnTypeAnnotation', 'returnType', 'returnAnnotation']);
  const returnRead = returnNode ? readAnnotation(returnNode, ctx, parseNodeType, normalizePythonTypeDisplay) : null;
  const returnRaw = returnRead ? returnRead.text : 'Any';
  const signature = buildSignature(name, params, returnRaw || 'Any');
  const moduleUniqueName = ctx.packageName
    ? ctx.packageName + '.' + ctx.moduleName
    : ctx.moduleName;
  const uniqueName = moduleUniqueName + '.' + signature;
  // An absent return annotation displays as `Any` but is not a dependency on anything.
  const returnRef = returnRead ? returnRead.ref : null;
  const cyclo = computeCyclo(functionNode, ctx.fileText);
  // The whole def, not just its suite: a changed signature or decorator is a change too.
  const implementationHash = stableImplementationHash(textForNode(functionNode, ctx.fileText));
  // Parameter and return annotations are evaluated where the def is; the body in its own scope.
  const bodyCtx = functionScope(functionNode, ctx, parseNodeType);
  const bodyReferences = collectBodyReferences(functionNode, bodyCtx, parseNodeType);
  return {
    name,
    signature,
    uniqueName,
    implementationHash,
    comment,
    cyclo,
    classMethod: false,
    staticMethod: false,
    decorators,
    decoratorArguments,
    params,
    locals: collectLocalVariables(functionNode, bodyCtx, parseNodeType),
    return: returnRef,
    bodyReferences
  };
}

const ANNOTATION_KEYS = ['typeAnnotation', 'annotation', 'annotationExpression', 'typeExpression'];

/**
 * The declared type of a field statement, in both of the shapes Python writes one.
 *
 * A bare `x: T` parses as an annotation node carrying the type directly. Adding a value --
 * `x: T = make()`, and `self.x: T = make()` in a constructor -- parses as an ASSIGNMENT whose left
 * expression is the annotation, so the type sits one level down and the direct lookup found
 * nothing. The field was then typed `Any` and contributed no dependency: annotating a field and
 * initialising it on the same line, which is the ordinary way to write one, made its type
 * invisible to the graph.
 */
function annotationForFieldStatement(statement) {
  const direct = getNodeProp(statement, ANNOTATION_KEYS);
  if (direct) {
    return direct;
  }
  const target = getNodeProp(statement, ['leftExpr', 'leftExpression']);
  return target ? getNodeProp(target, ANNOTATION_KEYS) : null;
}

/**
 * Whether a statement is an assignment, and so declares something.
 *
 * A class body holds more than declarations -- a docstring, a call, a conditional -- and none of
 * those names a field. Gating on the statement being an assignment is what lets an unannotated
 * `kind = "standard"` be read as a field without a docstring becoming one too.
 */
function isAssignmentStatement(statement, parseNodeType) {
  if (!statement || !parseNodeType) {
    return false;
  }
  return statement.nodeType === parseNodeType.Assignment
    || statement.nodeType === parseNodeType.TypeAnnotation;
}

function extractFieldFromStatement(statement, ctx, parseNodeType, options) {
  if (!statement) {
    return null;
  }
  const requireAnnotation = !options || options.requireAnnotation !== false;
  const annotationNode = annotationForFieldStatement(statement);
  let targetNode = getNodeProp(statement, ['valueExpression', 'expression', 'target', 'name', 'leftExpr', 'valueExpr']);
  // For `x: T = v` the target found above is the annotation node, whose own value expression is
  // the name. Without this the field is dropped rather than mistyped.
  if (targetNode && !nameFromNode(targetNode)) {
    targetNode = getNodeProp(targetNode, ['valueExpr', 'valueExpression']) || targetNode;
  }
  if (!targetNode) {
    return null;
  }
  if (requireAnnotation && !annotationNode) {
    return null;
  }
  const fieldName = nameFromNode(targetNode);
  if (!fieldName) {
    return null;
  }
  const read = annotationNode ? readAnnotation(annotationNode, ctx, parseNodeType) : null;
  const rawType = read ? read.text : 'Any';
  const ref = read ? read.ref : null;
  // What the annotation already records is not recorded a second time for the value.
  const annotated = new Set();
  if (ref) {
    [ref].concat(ref.alternates || []).forEach(each => annotated.add(each.targetUniqueName || each.externalLabel));
  }
  const valueReferences = collectValueExpressionReferences(
    statement.d ? statement.d.rightExpr : null, options && options.boundNames, ctx, parseNodeType)
    .filter(each => !annotated.has(each.targetUniqueName || each.externalLabel))
    .map(each => Object.assign({ alternates: [] }, each));
  return {
    name: fieldName,
    rawType: ref ? ref.raw : rawType,
    implementationHash: stableImplementationHash(textForNode(statement, ctx.fileText)),
    targetUniqueName: ref ? ref.targetUniqueName : null,
    externalLabel: ref ? ref.externalLabel : null,
    alternates: ref ? ref.alternates : [],
    typeVariable: ref ? !!ref.typeVariable : false,
    valueReferences
  };
}

function extractInstanceFieldFromStatement(statement, ctx) {
  if (!statement) {
    return null;
  }
  const fileText = ctx && ctx.fileText ? ctx.fileText : '';
  const statementText = textForNode(statement, fileText);
  if (!statementText || statementText.indexOf('=') < 0) {
    return null;
  }
  const targetNode = getNodeProp(statement, ['valueExpression', 'expression', 'target', 'name', 'leftExpr', 'valueExpr']);
  if (!targetNode) {
    return null;
  }
  if (!statementText) {
    return null;
  }
  const statementLower = statementText.trim().toLowerCase();
  if (!statementLower.includes('self.') && !statementLower.includes('cls.')) {
    return null;
  }
  let fieldName = '';
  const targetName = nameFromNode(targetNode);
  if (targetName) {
    fieldName = instanceFieldNameFromExpression(targetName);
  }
  if (!fieldName) {
    const targetText = textForNode(targetNode, fileText);
    fieldName = instanceFieldNameFromExpression(targetText);
  }
  if (!fieldName) {
    fieldName = instanceFieldNameFromStatement(statementText);
  }
  if (!fieldName) {
    return null;
  }
  const annotationNode = annotationForFieldStatement(statement);
  const read = annotationNode ? readAnnotation(annotationNode, ctx) : null;
  const rawType = read ? read.text : 'Any';
  const ref = read ? read.ref : null;
  return {
    name: fieldName,
    rawType: ref ? ref.raw : rawType,
    implementationHash: stableImplementationHash(statementText),
    targetUniqueName: ref ? ref.targetUniqueName : null,
    externalLabel: ref ? ref.externalLabel : null,
    alternates: ref ? ref.alternates : [],
    typeVariable: ref ? !!ref.typeVariable : false
  };
}

// The properties of a compound statement that hold nested statements: the branches of an
// if/else, loop, with, try/except/finally and match.
const NESTED_SUITE_KEYS = ['suite', 'ifSuite', 'elseSuite', 'forSuite', 'whileSuite', 'trySuite',
  'finallySuite', 'exceptSuite', 'exceptClauses', 'cases'];

/**
 * Every statement of a suite, descending into the branches of compound statements but not into a
 * function or class written inside it, whose `self` is not this one's.
 */
function collectNestedStatements(suiteNode, out) {
  for (const statement of getStatementsFromSuite(suiteNode)) {
    if (!statement || !statement.d) {
      continue;
    }
    out.push(statement);
    if (statement.d.name && statement.d.suite) {
      continue;
    }
    for (const key of NESTED_SUITE_KEYS) {
      const child = statement.d[key];
      for (const node of (Array.isArray(child) ? child : [child])) {
        if (node && node.d) {
          if (Array.isArray(node.d.statements)) {
            collectNestedStatements(node, out);
          } else if (!(node.d.name && node.d.suite)) {
            // an `elif` chain is an if statement in the else slot; an except clause or a case
            // holds its own suite
            collectNestedStatements({ d: { statements: [node] } }, out);
          }
        }
      }
    }
  }
}

/** The leaf targets of a tuple or list unpacking target, flattening nested and starred ones. */
function unpackedTargets(node, out) {
  if (!node || !node.d) {
    return out;
  }
  const items = Array.isArray(node.d.items) ? node.d.items
    : (Array.isArray(node.d.expressions) ? node.d.expressions : null);
  if (items) {
    items.forEach(item => unpackedTargets(item, out));
  } else if (node.d.expr && !node.d.leftExpr && !node.d.member) {
    unpackedTargets(node.d.expr, out);
  } else {
    out.push(node);
  }
  return out;
}

/** The fields one assignment creates: one for `self.a = v`, one per target for `self.a, self.b = v`. */
function extractInstanceFieldsFromStatement(statement, ctx) {
  const target = statement && statement.d ? getNodeProp(statement, ['leftExpr']) : null;
  const leaves = target && target.d && (Array.isArray(target.d.items) || Array.isArray(target.d.expressions))
    ? unpackedTargets(target, []) : null;
  if (!leaves) {
    const field = extractInstanceFieldFromStatement(statement, ctx);
    return field ? [field] : [];
  }
  const fileText = ctx && ctx.fileText ? ctx.fileText : '';
  const statementText = textForNode(statement, fileText);
  const fields = [];
  for (const leaf of leaves) {
    const name = instanceFieldNameFromExpression(textForNode(leaf, fileText));
    if (name) {
      fields.push({
        name,
        rawType: 'Any',
        implementationHash: stableImplementationHash(statementText),
        targetUniqueName: null,
        externalLabel: null,
        alternates: []
      });
    }
  }
  return fields;
}

function extractInstanceFieldsFromMethod(methodNode, ctx) {
  if (!methodNode) {
    return [];
  }
  const statements = [];
  collectNestedStatements(methodNode.d ? methodNode.d.suite : null, statements);
  const seen = new Set();
  const fields = [];
  for (const statement of statements) {
    for (const field of extractInstanceFieldsFromStatement(statement, ctx)) {
      if (!field || !field.name || seen.has(field.name)) {
        continue;
      }
      seen.add(field.name);
      fields.push(field);
    }
  }
  return fields;
}

// The standard library's enum bases. A class deriving from one of these is an enumeration, and its
// class-level assignments are its members.
const ENUM_BASE_NAMES = new Set(['Enum', 'IntEnum', 'StrEnum', 'Flag', 'IntFlag', 'ReprEnum']);

/**
 * Whether a base class expression names one of the enum bases. Matched on the last dotted segment,
 * so `enum.Enum` and an `Enum` imported from it are both recognised.
 */
function isEnumBase(rawBase) {
  if (!rawBase) {
    return false;
  }
  const segments = String(rawBase).trim().split('.');
  return ENUM_BASE_NAMES.has(segments[segments.length - 1]);
}

/** The unique name a top-level declaration of the module being read carries. */
function localUniqueName(name, ctx) {
  return (ctx.packageName ? ctx.packageName + '.' : '') + ctx.moduleName + '.' + name;
}

function resolveClassUniqueName(className, ctx, parentUniqueName) {
  if (!className) {
    return '';
  }
  if (parentUniqueName) {
    return parentUniqueName + '.' + className;
  }
  if (ctx.packageName) {
    return ctx.packageName + '.' + ctx.moduleName + '.' + className;
  }
  return ctx.moduleName + '.' + className;
}

function extractClassesFromStatements(statements, ctx, parseNodeType, parentUniqueName) {
  const classes = [];
  for (const statement of statements) {
    if (!statement || !isClassNode(statement, parseNodeType)) {
      continue;
    }
    const className = nameFromNode(statement.d && statement.d.name ? statement.d.name : null);
    if (!className) {
      continue;
    }
    const classUniqueName = resolveClassUniqueName(className, ctx, parentUniqueName);
    // The class's own PEP 695 type parameters are in scope in its bases, its bounds and its body.
    const scopedCtx = withTypeParameters(ctx, statement);
    const classComment = extractSuiteDocString(statement.d ? statement.d.suite : null, ctx.fileText);
    const classDecorators = extractDecoratorNames(statement, ctx.fileText);
    const classDecoratorArguments = extractDecoratorArguments(statement, ctx);
    let baseExprs = [];
    if (statement.d && Array.isArray(statement.d.baseClassExpressions)) {
      baseExprs = statement.d.baseClassExpressions;
    } else if (statement.d && Array.isArray(statement.d.arguments)) {
      // `metaclass=ABCMeta` and other keyword arguments configure the class; only positional
      // arguments (and `*bases`) are bases.
      baseExprs = statement.d.arguments
        .filter(arg => !(arg && arg.d && arg.d.name))
        .map(arg => (arg && arg.d && arg.d.valueExpr ? arg.d.valueExpr : arg))
        .filter(Boolean);
    }
    const bases = baseExprs.map(expr => resolveTypeRef(expr, textForNode(expr, ctx.fileText), scopedCtx)).filter(Boolean);
    // The type arguments of a generic base -- `Base[arch.Item]` -- are types the class depends on; only
    // the generic itself is extended.
    const baseArguments = [];
    const baseArgumentTargets = new Set(bases.map(base => base.targetUniqueName));
    for (const expr of baseExprs) {
      const written = textForNode(expr, ctx.fileText);
      const open = written.indexOf('[');
      if (open <= 0 || !written.endsWith(']')) {
        continue;
      }
      for (const candidate of extractCandidateNames(written.slice(open + 1, -1))) {
        const target = resolveTypeFromRaw(candidate, ctx);
        if (target && !baseArgumentTargets.has(target)) {
          baseArgumentTargets.add(target);
          baseArguments.push({ raw: candidate, targetUniqueName: target, externalLabel: null, alternates: [] });
        }
      }
    }
    // A `metaclass=` keyword and the bounds of the class's own type parameters are types the class
    // depends on; neither is a base.
    const dependencyExprs = [];
    if (statement.d && Array.isArray(statement.d.arguments)) {
      for (const arg of statement.d.arguments) {
        if (arg && arg.d && arg.d.name && nameFromNode(arg.d.name) === 'metaclass' && arg.d.valueExpr) {
          dependencyExprs.push(arg.d.valueExpr);
        }
      }
    }
    for (const typeParam of typeParametersOf(statement)) {
      const bound = typeParam.d.boundExpr;
      if (bound) {
        dependencyExprs.push(...(bound.nodeType === parseNodeType.Tuple ? bound.d.items || [] : [bound]));
      }
    }
    for (const expr of dependencyExprs) {
      const dependency = resolveTypeRef(expr, textForNode(expr, ctx.fileText), scopedCtx);
      const key = dependency ? dependency.targetUniqueName || dependency.externalLabel : null;
      if (key && !baseArgumentTargets.has(key)) {
        baseArgumentTargets.add(key);
        baseArguments.push({ raw: dependency.raw, targetUniqueName: dependency.targetUniqueName,
          externalLabel: dependency.externalLabel, alternates: dependency.alternates });
      }
    }
    const isEnum = baseExprs.some(expr => isEnumBase(textForNode(expr, ctx.fileText)));
    const classCtx = Object.assign({}, scopedCtx, { className, classUniqueName });
    const suiteStatements = getStatementsFromSuite(statement.d ? statement.d.suite : null);
    const methods = [];
    const fields = [];
    const fieldNames = new Set();
    const nestedClasses = [];
    const otherMethodFields = [];
    // Every name the class body itself binds -- nested classes, methods, other class attributes --
    // hides the module's name of the same spelling in the value of an attribute of the class.
    const classBodyNames = classBodyBoundNames(suiteStatements, parseNodeType);
    for (const node of suiteStatements) {
      if (!node) {
        continue;
      }
      if (isClassNode(node, parseNodeType)) {
        nestedClasses.push(...extractClassesFromStatements([node], classCtx, parseNodeType, classUniqueName));
        continue;
      }
      if (isFunctionNode(node, parseNodeType)) {
        const method = extractMethod(node, classCtx, parseNodeType);
        if (method) {
          methods.push(method);
          // `__init__` is read first so its declarations win over a later method's.
          const instanceFields = extractInstanceFieldsFromMethod(node, functionScope(node, classCtx, parseNodeType));
          if (method.name === '__init__') {
            for (const instanceField of instanceFields) {
              if (!instanceField || !instanceField.name || fieldNames.has(instanceField.name)) {
                continue;
              }
              fieldNames.add(instanceField.name);
              fields.push(instanceField);
            }
          } else {
            otherMethodFields.push(...instanceFields);
          }
        }
        continue;
      }
      // A class-level assignment declares a field whether or not it carries an annotation:
      // `kind = "standard"` is as much a member of the class as `limit: int = 10`, and requiring an
      // annotation left every unannotated one out of the model.
      const field = extractFieldFromStatement(node, classCtx, parseNodeType, {
        requireAnnotation: !isAssignmentStatement(node, parseNodeType),
        boundNames: new Set([...fieldNames, ...classBodyNames])
      });
      if (field && field.name && !fieldNames.has(field.name)) {
        // In an enumeration an unannotated class-level assignment names a member of it rather than
        // an ordinary field: `FAST = 1` is what `Mode.FAST` is.
        field.enumConstant = isEnum && !annotationForFieldStatement(node);
        fieldNames.add(field.name);
        fields.push(field);
      }
    }
    // An attribute first assigned in an ordinary method belongs to the class unless the class body
    // or `__init__` already declared it.
    for (const instanceField of otherMethodFields) {
      if (instanceField.name && !fieldNames.has(instanceField.name)) {
        fieldNames.add(instanceField.name);
        fields.push(instanceField);
      }
    }
    classes.push({
      className,
      kind: isEnum ? 'enum' : 'class',
      uniqueName: classUniqueName,
      comment: classComment,
      implementationHash: stableImplementationHash(textForNode(statement, ctx.fileText)),
      decorators: classDecorators,
      decoratorArguments: classDecoratorArguments,
      decoratorReferences: collectDecoratorReferences(statement, ctx, parseNodeType),
      bases,
      baseArguments,
      methods,
      fields,
      classes: nestedClasses
    });
  }
  return classes;
}

function extractClasses(parseTree, ctx, parseNodeType) {
  const statements = getModuleStatements(parseTree, parseNodeType);
  return extractClassesFromStatements(statements, ctx, parseNodeType, null);
}

function extractFunctions(parseTree, ctx, parseNodeType) {
  const functions = [];
  const statements = getModuleStatements(parseTree, parseNodeType);
  for (const statement of statements) {
    if (!statement || !isFunctionNode(statement, parseNodeType)) {
      continue;
    }
    const fn = extractFunction(statement, ctx, parseNodeType);
    if (fn) {
      functions.push(fn);
    }
  }
  return functions;
}

function extractModuleFields(parseTree, ctx, parseNodeType) {
  const fields = [];
  const statements = getModuleStatements(parseTree, parseNodeType);
  for (const statement of statements) {
    if (!statement || isClassNode(statement, parseNodeType) || isFunctionNode(statement, parseNodeType)) {
      continue;
    }
    const field = extractFieldFromStatement(statement, ctx, parseNodeType, { requireAnnotation: false });
    if (field) {
      const thisFile = ctx.moduleIndex ? ctx.moduleIndex.get(ctx.fullModuleName) : null;
      const members = thisFile && isAssignmentStatement(statement, parseNodeType)
        ? aliasMembersOf(thisFile, field.name) : null;
      if (members && members.length) {
        field.typeAlias = true;
        field.rawType = moduleDeclarations(thisFile).aliases.get(field.name);
        field.targetUniqueName = members[0].targetUniqueName;
        field.externalLabel = members[0].externalLabel;
        field.alternates = members.slice(1);
        field.valueReferences = [];
      }
      fields.push(field);
    }
  }
  return fields;
}

async function handleInitRepo(params) {
  const repoRoot = params && params.repoRoot ? params.repoRoot : null;
  if (!repoRoot || !fs.existsSync(repoRoot) || !fs.statSync(repoRoot).isDirectory()) {
    return { error: { code: ERROR_CODES.REPO_NOT_FOUND, message: 'Repo not found' } };
  }
  const normalized = normalizePath(repoRoot);
  state.repoRoot = normalized;
  const versionInfo = resolvePythonVersion(normalized, params ? params.options : null);
  state.pythonVersion = versionInfo.version || DEFAULT_PYTHON_VERSION;
  state.configSource = versionInfo.source || 'default';
  const extraPaths = resolveExtraPaths(normalized, versionInfo.extraPaths);
  const scannedFiles = scanRepo(normalized);
  const importRoots = extraPaths.concat(inferImportRoots(normalized, scannedFiles, extraPaths));
  state.extraModuleRoots = importRoots;
  state.moduleIndex = buildModuleIndex(normalized, extraPaths, scannedFiles);
  state.fileUriMap = new Map();
  state.moduleDeclarations = new Map();
  state.moduleImports = new Map();
  state.aliasMembers = new Map();
  state.aliasStack = new Set();
  state.program = null;
  state.importResolver = null;
  state.evaluation = null;
  state.serviceProvider = null;
  state.fileSystem = null;

  try {
    ensureProgram(state.pythonVersion, importRoots);
  } catch (err) {
    return { error: { code: ERROR_CODES.DAEMON_ERROR, message: err && err.message ? err.message : 'Python daemon error' } };
  }

  return {
    result: {
      effectivePythonVersion: state.pythonVersion,
      configSource: state.configSource,
      warnings: []
    }
  };
}

/** The repository files the file's imports resolve to; none when resolution fails, never an error. */
function importedFilesOf(parseTree, parseNodeType, filePath, dynamicImports) {
  try {
    return collectImportedFiles(parseTree, parseNodeType, filePath, dynamicImports);
  } catch (err) {
    return [];
  }
}

async function handleGetFileModel(params) {
  const filePath = params && params.filePath ? params.filePath : null;
  if (!filePath) {
    return { error: { code: ERROR_CODES.FILE_NOT_FOUND, message: 'File not provided' } };
  }
  const normalized = normalizePath(filePath);
  if (!normalized.startsWith(state.repoRoot)) {
    return { error: { code: ERROR_CODES.FILE_NOT_FOUND, message: 'File not in repo' } };
  }
  if (shouldSkipPath(normalized)) {
    return { error: { code: ERROR_CODES.FILE_EXCLUDED, message: 'File excluded' } };
  }
  if (!fs.existsSync(normalized) || !fs.statSync(normalized).isFile()) {
    return { error: { code: ERROR_CODES.FILE_NOT_FOUND, message: 'File not found' } };
  }
  let fileText = '';
  try {
    fileText = fs.readFileSync(normalized, 'utf8');
  } catch (err) {
    return { error: { code: ERROR_CODES.PARSE_FAILED, message: 'Failed to read file' } };
  }
  const api = getPyrightApi();
  if (!api.parser || !api.parser.Parser || !api.parser.ParseOptions) {
    return { error: { code: ERROR_CODES.DAEMON_ERROR, message: 'Pyright parser unavailable' } };
  }
  let parseTree = null;
  try {
    const parser = new api.parser.Parser();
    const parseOptions = new api.parser.ParseOptions();
    const versionEnum = resolvePythonVersionEnum(state.pythonVersion, api);
    if (versionEnum && 'pythonVersion' in parseOptions) {
      parseOptions.pythonVersion = versionEnum;
    }
    if ('isStubFile' in parseOptions) {
      parseOptions.isStubFile = normalized.toLowerCase().endsWith('.pyi');
    }
    const parseResult = parser.parseSourceFile(fileText, parseOptions, undefined);
    if (parseResult && parseResult.parserOutput && parseResult.parserOutput.parseTree) {
      parseTree = parseResult.parserOutput.parseTree;
    } else if (parseResult && parseResult.parseTree) {
      parseTree = parseResult.parseTree;
    }
  } catch (err) {
    return { error: { code: ERROR_CODES.PARSE_FAILED, message: 'Parse failed' } };
  }
  if (!parseTree) {
    return { error: { code: ERROR_CODES.PARSE_FAILED, message: 'Parse failed' } };
  }
  const parseNodeType = getParseNodeTypeEnum(api);
  const moduleName = moduleBaseNameForPath(normalized);
  const packageName = packageNameForPath(normalized, state.repoRoot);
  const fullModuleName = packageName ? packageName + '.' + moduleName : moduleName;
  const importCtx = {
    moduleIndex: state.moduleIndex,
    fullModuleName,
    moduleName,
    packageName,
    repoRoot: state.repoRoot
  };
  const topLevelStatements = collectImportStatements(fileText);
  const parsedImports = collectParsedImportStatements(parseTree, fileText, parseNodeType);
  const seenStatements = new Set(topLevelStatements);
  const moduleStatements = topLevelStatements.concat(
    parsedImports.moduleScope.filter(statement => !seenStatements.has(statement)));
  const imported = resolveImportStatements(moduleStatements, importCtx);
  const nestedImported = resolveImportStatements(parsedImports.nested, importCtx);
  const localClasses = collectLocalClasses(parseTree, parseNodeType);
  const ctx = {
    repoRoot: state.repoRoot,
    moduleIndex: state.moduleIndex,
    moduleName,
    packageName,
    fullModuleName,
    importedModules: imported.importedModules,
    importedSymbols: imported.importedSymbols,
    localClasses,
    localFunctions: collectLocalFunctions(parseTree, parseNodeType),
    localValues: moduleLevelValueNames(parseTree, parseNodeType),
    typeVariables: collectTypeVariableNames(parseTree, parseNodeType),
    classNames: collectClassNames(getModuleStatements(parseTree, parseNodeType),
      (packageName ? packageName + '.' : '') + moduleName, parseNodeType, new Set()),
    fileText,
    parseNodeType,
    inferType: typeInferrer(normalized),
    evaluator: null
  };
  let classes = [];
  let functions = [];
  let moduleFields = [];
  try {
    classes = extractClasses(parseTree, ctx, parseNodeType);
    functions = extractFunctions(parseTree, ctx, parseNodeType);
    moduleFields = extractModuleFields(parseTree, ctx, parseNodeType);
  } catch (err) {
    return { error: { code: ERROR_CODES.PARSE_FAILED, message: 'Parse failed' } };
  }
  // The imports are already resolved above -- relative forms expanded, internal-ness checked
  // against the module index -- and were then dropped from the payload, so `imports()` was empty
  // for every Python component while the same data drove type resolution. See issue #156.
  const importList = [];
  for (const moduleName of imported.importedModules.values()) {
    if (moduleName) importList.push(moduleName);
  }
  for (const symbol of imported.importedSymbols.values()) {
    if (symbol && symbol.moduleName && symbol.symbolName) {
      importList.push(symbol.moduleName + '.' + symbol.symbolName);
    }
  }
  for (const moduleName of nestedImported.importedModules.values()) {
    if (moduleName) importList.push(moduleName);
  }
  for (const symbol of nestedImported.importedSymbols.values()) {
    if (symbol && symbol.moduleName && symbol.symbolName) {
      importList.push(symbol.moduleName + '.' + symbol.symbolName);
    }
  }
  // A module loaded by name from a string literal is imported as much as one a statement names.
  const dynamicImports = findDynamicImports(parseTree, parseNodeType, importCtx,
    [imported, nestedImported]);
  importList.push(...dynamicImports.map(dynamic => dynamic.moduleName));
  return {
    result: {
      filePath: normalized,
      packageName,
      moduleName,
      classes,
      functions,
      moduleFields,
      imports: Array.from(new Set(importList)),
      importedFiles: importedFilesOf(parseTree, parseNodeType, normalized, dynamicImports),
      warnings: []
    }
  };
}

async function handleRequest(request) {
  if (!request || typeof request !== 'object') {
    return { error: { code: ERROR_CODES.PARSE_FAILED, message: 'Invalid request' } };
  }
  const op = request.op;
  if (op === 'initRepo') {
    return handleInitRepo(request.params || {});
  }
  if (op === 'getFileModel') {
    return handleGetFileModel(request.params || {});
  }
  if (op === 'shutdown') {
    process.exit(0);
  }
  return { error: { code: ERROR_CODES.PARSE_FAILED, message: 'Unknown op' } };
}

const rl = readline.createInterface({
  input: process.stdin,
  crlfDelay: Infinity
});

rl.on('line', line => {
  if (!line || !line.trim()) {
    return;
  }
  let request;
  try {
    request = JSON.parse(line);
  } catch (err) {
    return;
  }
  Promise.resolve(handleRequest(request)).then(response => {
    if (response.error) {
      writeError(request.id, response.error.code, response.error.message);
    } else {
      writeResponse(request.id, response.result);
    }
  }).catch(err => {
    const message = err && err.message ? err.message : 'Python daemon error';
    writeError(request.id, ERROR_CODES.DAEMON_ERROR, message);
  });
});
