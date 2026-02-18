const fs = require('fs');
const path = require('path');
const readline = require('readline');
const vm = require('vm');

const ERROR_CODES = {
  REPO_NOT_FOUND: 2001,
  FILE_NOT_FOUND: 2002,
  PARSE_FAILED: 2003,
  FILE_EXCLUDED: 2005,
  DAEMON_ERROR: 2006
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
  program: null,
  fileUriMap: new Map(),
  trackedFiles: []
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

function shouldSkipPath(filePath) {
  if (!filePath) {
    return false;
  }
  const parts = filePath.replace(/\\/g, '/').split('/');
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

function buildModuleIndex(repoRoot, extraRoots) {
  const index = new Map();
  const extraList = Array.isArray(extraRoots) ? extraRoots : [];
  for (const filePath of scanRepo(repoRoot)) {
    const repoModule = moduleNameForPath(filePath, repoRoot);
    if (repoModule) {
      index.set(repoModule, filePath);
    }
    for (const root of extraList) {
      const extraModule = moduleNameForPath(filePath, root);
      if (extraModule) {
        if (!index.has(extraModule)) {
          index.set(extraModule, filePath);
        }
      }
    }
  }
  return index;
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

function collectImports(fileText, ctx) {
  const importedModules = new Map();
  const importedSymbols = new Map();
  const statements = collectImportStatements(fileText);
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
  return { importedModules, importedSymbols };
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
  return candidates.filter(Boolean);
}

function resolveModuleSymbol(moduleName, symbolName, ctx) {
  if (!moduleName || !symbolName) {
    return null;
  }
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
  if (!filePath) {
    return null;
  }
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

function resolveTypeFromRaw(raw, ctx) {
  if (!raw) {
    return null;
  }
  const candidates = extractCandidateNames(raw);
  for (const cand of candidates) {
    if (!cand) {
      continue;
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

function textForNode(node, fileText) {
  if (!node || typeof node.start !== 'number' || typeof node.length !== 'number') {
    return '';
  }
  return fileText.slice(node.start, node.start + node.length).trim();
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
  const statements = getStatementsFromSuite(parseTree);
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
  if (!filePath) {
    return null;
  }
  const pkgName = packageNameForPath(filePath, ctx.repoRoot);
  const modName = moduleBaseNameForPath(filePath);
  if (!modName) {
    return null;
  }
  if (pkgName) {
    return pkgName + '.' + modName + '.' + className;
  }
  return modName + '.' + className;
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

function resolveTypeRef(annotationNode, rawText, ctx) {
  const raw = rawText ? rawText.trim() : '';
  const evaluated = resolveTypeWithEvaluator(annotationNode, raw, ctx);
  if (evaluated && evaluated.targetUniqueName) {
    return evaluated;
  }
  const resolved = resolveTypeFromRaw(raw, ctx);
  if (resolved) {
    return { raw, targetUniqueName: resolved, externalLabel: null };
  }
  const externalLabel = evaluated && evaluated.externalLabel ? evaluated.externalLabel : externalLabelFor(raw, ctx);
  return { raw, targetUniqueName: null, externalLabel };
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
  return name + '(' + args + ') -> ' + (returnType || 'Any');
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
    const rawType = annotationNode ? textForNode(annotationNode, ctx.fileText) : 'Any';
    const ref = resolveTypeRef(annotationNode, rawType, ctx);
    params.push({
      name: paramName,
      rawType: ref ? ref.raw : rawType,
      targetUniqueName: ref ? ref.targetUniqueName : null,
      externalLabel: ref ? ref.externalLabel : (rawType || 'Any')
    });
  }
  return params;
}

function extractMethod(methodNode, ctx, parseNodeType) {
  const name = nameFromNode(methodNode.d && methodNode.d.name ? methodNode.d.name : null);
  if (!name) {
    return null;
  }
  const params = extractParams(methodNode, ctx, parseNodeType);
  const returnNode = getNodeProp(methodNode, ['returnTypeAnnotation', 'returnType', 'returnAnnotation']);
  const returnRaw = returnNode ? textForNode(returnNode, ctx.fileText) : 'Any';
  const signature = buildSignature(name, params, returnRaw || 'Any');
  const uniqueName = ctx.classUniqueName + '.' + signature;
  const returnRef = resolveTypeRef(returnNode, returnRaw || 'Any', ctx);
  return {
    name,
    signature,
    uniqueName,
    params,
    return: returnRef
  };
}

function extractFunction(functionNode, ctx, parseNodeType) {
  const name = nameFromNode(functionNode.d && functionNode.d.name ? functionNode.d.name : null);
  if (!name) {
    return null;
  }
  const params = extractParams(functionNode, ctx, parseNodeType);
  const returnNode = getNodeProp(functionNode, ['returnTypeAnnotation', 'returnType', 'returnAnnotation']);
  const returnRaw = returnNode ? textForNode(returnNode, ctx.fileText) : 'Any';
  const signature = buildSignature(name, params, returnRaw || 'Any');
  const moduleUniqueName = ctx.packageName
    ? ctx.packageName + '.' + ctx.moduleName
    : ctx.moduleName;
  const uniqueName = moduleUniqueName + '.' + signature;
  const returnRef = resolveTypeRef(returnNode, returnRaw || 'Any', ctx);
  return {
    name,
    signature,
    uniqueName,
    params,
    return: returnRef
  };
}

function extractFieldFromStatement(statement, ctx, parseNodeType, options) {
  if (!statement) {
    return null;
  }
  const requireAnnotation = !options || options.requireAnnotation !== false;
  const annotationNode = getNodeProp(statement, ['typeAnnotation', 'annotation', 'annotationExpression', 'typeExpression']);
  const targetNode = getNodeProp(statement, ['valueExpression', 'expression', 'target', 'name', 'leftExpr', 'valueExpr']);
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
  const rawType = annotationNode ? textForNode(annotationNode, ctx.fileText) : 'Any';
  const ref = annotationNode ? resolveTypeRef(annotationNode, rawType, ctx) : null;
  return {
    name: fieldName,
    rawType: ref ? ref.raw : rawType,
    targetUniqueName: ref ? ref.targetUniqueName : null,
    externalLabel: ref ? ref.externalLabel : (rawType || 'Any')
  };
}

function extractClasses(parseTree, ctx, parseNodeType) {
  const classes = [];
  const statements = getStatementsFromSuite(parseTree);
  for (const statement of statements) {
    if (!statement || !isClassNode(statement, parseNodeType)) {
      continue;
    }
    const className = nameFromNode(statement.d && statement.d.name ? statement.d.name : null);
    if (!className) {
      continue;
    }
    const classUniqueName = ctx.packageName
      ? ctx.packageName + '.' + ctx.moduleName + '.' + className
      : ctx.moduleName + '.' + className;
    let baseExprs = [];
    if (statement.d && Array.isArray(statement.d.baseClassExpressions)) {
      baseExprs = statement.d.baseClassExpressions;
    } else if (statement.d && Array.isArray(statement.d.arguments)) {
      baseExprs = statement.d.arguments
        .map(arg => (arg && arg.d && arg.d.valueExpr ? arg.d.valueExpr : arg))
        .filter(Boolean);
    }
    const bases = baseExprs.map(expr => resolveTypeRef(expr, textForNode(expr, ctx.fileText), ctx)).filter(Boolean);
    const classCtx = Object.assign({}, ctx, { className, classUniqueName });
    const suiteStatements = getStatementsFromSuite(statement.d ? statement.d.suite : null);
    const methods = [];
    const fields = [];
    for (const node of suiteStatements) {
      if (!node) {
        continue;
      }
      if (isFunctionNode(node, parseNodeType)) {
        const method = extractMethod(node, classCtx, parseNodeType);
        if (method) {
          methods.push(method);
        }
        continue;
      }
      const field = extractFieldFromStatement(node, classCtx, parseNodeType, { requireAnnotation: true });
      if (field) {
        fields.push(field);
      }
    }
    classes.push({
      className,
      uniqueName: classUniqueName,
      bases,
      methods,
      fields
    });
  }
  return classes;
}

function extractFunctions(parseTree, ctx, parseNodeType) {
  const functions = [];
  const statements = getStatementsFromSuite(parseTree);
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
  const statements = getStatementsFromSuite(parseTree);
  for (const statement of statements) {
    if (!statement || isClassNode(statement, parseNodeType) || isFunctionNode(statement, parseNodeType)) {
      continue;
    }
    const field = extractFieldFromStatement(statement, ctx, parseNodeType, { requireAnnotation: false });
    if (field) {
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
  state.extraModuleRoots = extraPaths;
  state.trackedFiles = scanRepo(normalized);
  state.moduleIndex = buildModuleIndex(normalized, extraPaths);
  state.fileUriMap = new Map();
  state.program = null;
  state.importResolver = null;
  state.serviceProvider = null;
  state.fileSystem = null;

  try {
    ensureProgram(state.pythonVersion, extraPaths);
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
  const imported = collectImports(fileText, {
    moduleIndex: state.moduleIndex,
    fullModuleName,
    packageName,
    repoRoot: state.repoRoot
  });
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
    fileText,
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
  return {
    result: {
      filePath: normalized,
      packageName,
      moduleName,
      classes,
      functions,
      moduleFields,
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
