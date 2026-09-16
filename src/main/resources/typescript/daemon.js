const fs = require("fs");
const path = require("path");
const readline = require("readline");

const ERROR_CODES = {
  TYPESCRIPT_NOT_FOUND: 1001,
  NO_TSCONFIG: 1002,
  CONFIG_PARSE_FAILED: 1003,
  PROGRAM_CREATE_FAILED: 1004,
  FILE_NOT_IN_PROGRAM: 2001,
  FILE_NOT_FOUND: 2002,
  RESOLUTION_FAILED: 2006
};

const MAX_TYPE_DEPTH = 10;

// How many TypeScript programs may be resident at once. A program is the expensive object in this
// daemon: it holds every source file it reaches plus a type checker over them. Two is enough for
// files to be answered from a warm program while walking a tree in directory order, and small
// enough that a repository with dozens of configs cannot exhaust the heap.
const DEFAULT_MAX_PROGRAMS = 2;

let state = {
  repoRoot: null,
  ts: null,
  configs: [],
  fileMap: new Map(),
  programCache: new Map(),
  programOrder: [],
  maxPrograms: DEFAULT_MAX_PROGRAMS
};

function writeResponse(id, result) {
  process.stdout.write(JSON.stringify({ jsonrpc: "2.0", id, result }) + "\n");
}

function writeError(id, code, message, data) {
  const error = { code, message };
  if (data) {
    error.data = data;
  }
  process.stdout.write(JSON.stringify({ jsonrpc: "2.0", id, error }) + "\n");
}

function stableImplementationHash(text) {
  const normalized = String(text || "").replace(/\r\n/g, "\n").trim();
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

function loadTypeScript(repoRoot) {
  function tryResolve(resolver) {
    try {
      const resolved = resolver();
      if (resolved) {
        return require(resolved);
      }
    } catch (err) {
      return null;
    }
    return null;
  }

  // Use only the bundled TypeScript runtime that ships with Clarpse.
  const bundledResolved = tryResolve(() => require.resolve("typescript", { paths: [__dirname] }));
  if (bundledResolved) {
    return bundledResolved;
  }
  return null;
}

function findTsconfigs(root) {
  const results = [];
  const stack = [root];
  while (stack.length > 0) {
    const current = stack.pop();
    let entries;
    try {
      entries = fs.readdirSync(current, { withFileTypes: true });
    } catch (err) {
      continue;
    }
    for (const entry of entries) {
      if (entry.name === "node_modules" || entry.name === ".git") {
        continue;
      }
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
      } else if (entry.isFile() && entry.name === "tsconfig.json") {
        results.push(fullPath);
      }
    }
  }
  return results;
}

function findTypeScriptFiles(configDir) {
  const results = [];
  const stack = [configDir];
  while (stack.length > 0) {
    const current = stack.pop();
    let entries;
    try {
      entries = fs.readdirSync(current, { withFileTypes: true });
    } catch (err) {
      continue;
    }
    for (const entry of entries) {
      if (entry.name === "node_modules" || entry.name === ".git") {
        continue;
      }
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
      } else if (entry.isFile() && isTypeScriptFile(entry.name)) {
        results.push(fullPath);
      }
    }
  }
  return results;
}

function isTypeScriptFile(filePath) {
  if (!filePath) {
    return false;
  }
  const lower = filePath.toLowerCase();
  if (lower.endsWith(".d.ts")) {
    return true;
  }
  return lower.endsWith(".ts") || lower.endsWith(".tsx");
}

function filterTypeScriptRoots(fileNames) {
  if (!Array.isArray(fileNames)) {
    return [];
  }
  return fileNames.filter(isTypeScriptFile);
}

// Diagnostics that mean "this config's `extends` chain could not be resolved", rather than "this
// config is broken". TS5083 -- "Cannot read file '...'" -- is what a config gets when the base it
// extends is absent, and it was not among them, so such a config was discarded along with every
// source file its own `include` claimed. The others are 6053 (file not found), 6075 (base config
// resolution) and 18003 (no inputs found).
const EXTENDS_ERROR_CODES = new Set([5083, 6053, 6075, 18003]);

function isExtendsError(diagnostic) {
  if (!diagnostic) {
    return false;
  }
  if (diagnostic.code && EXTENDS_ERROR_CODES.has(diagnostic.code)) {
    return true;
  }
  const text = typeof diagnostic.messageText === "string"
    ? diagnostic.messageText
    : String(diagnostic.messageText || diagnostic.message || diagnostic);
  return text.includes("extends");
}

// Reads a tsconfig, tolerating the trailing commas that are legal in a tsconfig but not in JSON.
function readNormalizedConfigFile(ts, configPath) {
  return ts.readConfigFile(configPath, (filePath) => {
    const content = ts.sys.readFile(filePath);
    if (content === undefined) {
      return undefined;
    }
    return content.replace(/,(\s*[}\]])/g, "$1");
  });
}

/**
 * The config files a config's `references` entries point at. A reference names either a config file
 * or a directory holding a `tsconfig.json`, and is resolved relative to the referring config.
 */
function referencedConfigPaths(ts, configPath) {
  let raw;
  try {
    raw = readNormalizedConfigFile(ts, configPath);
  } catch (err) {
    return [];
  }
  const references = raw && raw.config && Array.isArray(raw.config.references)
    ? raw.config.references
    : [];
  const configDir = path.dirname(configPath);
  const results = [];
  for (const reference of references) {
    const referencePath = typeof reference === "string" ? reference : (reference && reference.path);
    if (!referencePath) {
      continue;
    }
    const resolved = path.resolve(configDir, referencePath);
    try {
      if (fs.existsSync(resolved) && fs.statSync(resolved).isDirectory()) {
        results.push(path.join(resolved, "tsconfig.json"));
      } else {
        results.push(resolved);
      }
    } catch (err) {
      continue;
    }
  }
  return results;
}

/**
 * Every config reachable from the given ones, the projects they reference included.
 *
 * A solution-style `tsconfig.json` -- the shape Nx and similar tools generate -- carries
 * `"files": []`, `"include": []` and a `references` array, so it owns no source of its own and the
 * projects it points at hold the code. Those projects are routinely named `tsconfig.lib.json`
 * rather than `tsconfig.json`, so walking the tree for that exact name never reaches them: their
 * sources land in no program, yield no components, and look exactly like a project that declares
 * nothing.
 */
function expandProjectReferences(ts, configPaths) {
  const ordered = [];
  const seen = new Set();
  const queue = configPaths.slice();
  while (queue.length > 0) {
    const configPath = queue.shift();
    const key = normalizePath(ts, configPath);
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    try {
      if (!fs.existsSync(configPath) || !fs.statSync(configPath).isFile()) {
        continue;
      }
    } catch (err) {
      continue;
    }
    ordered.push(configPath);
    for (const referenced of referencedConfigPaths(ts, configPath)) {
      queue.push(referenced);
    }
  }
  return ordered;
}

/**
 * Reads every tsconfig into the compiler options and root file names it implies, and builds no
 * programs. `parseJsonConfigFileContent` expands `include`/`exclude` globs on its own, so which
 * files a config owns is known without constructing a program for it.
 */
function parseConfigs(ts, repoRoot, configPaths) {
  const configs = [];
  const invalidConfigs = [];

  for (const configPath of configPaths) {
    let configFile;
    try {
      configFile = readNormalizedConfigFile(ts, configPath);
    } catch (err) {
      if (isExtendsError(err)) {
        // The extends chain could not be read; fall through to minimal compiler options.
        configFile = null;
      } else {
        invalidConfigs.push({ configPath, error: "CONFIG_READ_FAILED" });
        continue;
      }
    }

    if (configFile && configFile.error && !isExtendsError(configFile.error)) {
      invalidConfigs.push({ configPath, error: "CONFIG_PARSE_FAILED" });
      continue;
    }

    let config;
    let hasExtendErrors = false;
    if (configFile) {
      try {
        config = ts.parseJsonConfigFileContent(
          configFile.config,
          ts.sys,
          path.dirname(configPath)
        );
        hasExtendErrors = config && config.errors && config.errors.some(isExtendsError);
      } catch (err) {
        // parseJsonConfigFileContent threw an exception - check if it's extends-related
        if (isExtendsError(err)) {
          hasExtendErrors = true;
          config = null;
        } else {
          // Failed for reasons other than extends - mark as invalid
          invalidConfigs.push({ configPath, error: "CONFIG_PARSE_FAILED" });
          continue;
        }
      }
    }
    if (!config && !hasExtendErrors) {
      // Completely failed to parse, not due to extends
      invalidConfigs.push({ configPath, error: "CONFIG_PARSE_FAILED" });
      continue;
    }
    if (config && config.errors && config.errors.length > 0 && !hasExtendErrors) {
      // Has non-extends errors
      invalidConfigs.push({ configPath, error: "CONFIG_PARSE_FAILED" });
      continue;
    }
    let options, rootNames, projectReferences;
    if (hasExtendErrors) {
      // Extends couldn't be resolved (e.g., missing base config in node_modules)
      // Use minimal compiler options that will work
      // configFile might be null if readConfigFile threw an extends-related exception
      const rawConfig = configFile ? configFile.config : {};
      const configDir = path.dirname(configPath);

      const baseOptions = {
        allowJs: false,
        checkJs: false,
        strict: false,
        esModuleInterop: true,
        skipLibCheck: true,
        jsx: ts.JsxEmit.React,
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2015,
        moduleResolution: ts.ModuleResolutionKind.Node10,
        lib: []
      };
      options = baseOptions;

      console.warn(`[clarpse] Config at ${configPath} has 'extends' that couldn't be resolved. Using minimal compiler options.`);

      // Resolve project references relative to config directory
      const rawRefs = rawConfig.references || [];
      projectReferences = rawRefs.map(ref => {
        if (typeof ref === 'string') {
          const refPath = path.resolve(configDir, ref);
          return { path: refPath };
        }
        if (ref && ref.path) {
          return { path: path.resolve(configDir, ref.path) };
        }
        return ref;
      });

      rootNames = findTypeScriptFiles(configDir);
    } else {
      options = Object.assign({}, config.options, { allowJs: false, checkJs: false });
      rootNames = filterTypeScriptRoots(config.fileNames);
      projectReferences = config.projectReferences || [];
    }
    configs.push({
      configPath,
      dir: normalizePath(ts, path.dirname(configPath)),
      options,
      rootNames,
      projectReferences,
      programFailed: false
    });
  }
  return { configs, invalidConfigs };
}

function normalizePath(ts, filePath) {
  let normalized = path.resolve(filePath);
  if (ts && ts.sys && ts.sys.realpath) {
    try {
      normalized = ts.sys.realpath(normalized);
    } catch (err) {
      // ignore resolution failures
    }
  }
  if (ts && ts.sys && !ts.sys.useCaseSensitiveFileNames) {
    return normalized.toLowerCase();
  }
  return normalized;
}

/**
 * The program for a config, built on first use and evicted once `maxPrograms` newer ones are in
 * front of it. A config whose program cannot be built is remembered as failed and never retried, so
 * one broken config costs one attempt rather than one per file.
 */
function programFor(index) {
  const cached = state.programCache.get(index);
  if (cached) {
    touchProgram(index);
    return cached;
  }
  const config = state.configs[index];
  if (!config || config.programFailed) {
    return null;
  }
  let program;
  try {
    program = state.ts.createProgram({
      rootNames: config.rootNames,
      options: config.options,
      projectReferences: config.projectReferences
    });
  } catch (err) {
    console.error("[clarpse] PROGRAM_CREATE_FAILED for", config.configPath, err.message);
    config.programFailed = true;
    return null;
  }
  const entry = {
    configPath: config.configPath,
    program,
    options: config.options,
    checker: program.getTypeChecker()
  };
  state.programCache.set(index, entry);
  state.programOrder.push(index);
  while (state.programOrder.length > state.maxPrograms) {
    state.programCache.delete(state.programOrder.shift());
  }
  return entry;
}

function touchProgram(index) {
  const at = state.programOrder.indexOf(index);
  if (at >= 0) {
    state.programOrder.splice(at, 1);
  }
  state.programOrder.push(index);
}

/**
 * The configs that may own a file: the one listing it as a root file, and otherwise the configs
 * whose directory encloses it, nearest first. A file can belong to a program through an import
 * rather than an `include` glob, and trying enclosing configs finds it without building every
 * program in the repository to answer one question.
 */
function configIndicesForFile(filePath) {
  if (state.fileMap.has(filePath)) {
    return [state.fileMap.get(filePath)];
  }
  const candidates = [];
  for (let i = 0; i < state.configs.length; i += 1) {
    const config = state.configs[i];
    if (!config.rootNames.length || config.programFailed) {
      continue;
    }
    if (filePath === config.dir || filePath.startsWith(config.dir + path.sep)) {
      candidates.push(i);
    }
  }
  candidates.sort((a, b) => state.configs[b].dir.length - state.configs[a].dir.length);
  return candidates;
}

function findProgramEntryForFile(filePath) {
  for (const index of configIndicesForFile(filePath)) {
    const entry = programFor(index);
    if (!entry) {
      continue;
    }
    const source = entry.program.getSourceFile(filePath);
    if (source) {
      state.fileMap.set(filePath, index);
      return { entry, source };
    }
  }
  return null;
}

function resolveMaxPrograms(params) {
  const requested = Number.parseInt(params && params.maxPrograms, 10);
  if (Number.isFinite(requested) && requested > 0) {
    return requested;
  }
  return DEFAULT_MAX_PROGRAMS;
}

function collectModifiers(ts, node) {
  const modifiers = [];
  if (!node.modifiers) {
    return modifiers;
  }
  for (const mod of node.modifiers) {
    switch (mod.kind) {
      case ts.SyntaxKind.PublicKeyword:
        modifiers.push("public");
        break;
      case ts.SyntaxKind.PrivateKeyword:
        modifiers.push("private");
        break;
      case ts.SyntaxKind.ProtectedKeyword:
        modifiers.push("protected");
        break;
      case ts.SyntaxKind.StaticKeyword:
        modifiers.push("static");
        break;
      case ts.SyntaxKind.AbstractKeyword:
        modifiers.push("abstract");
        break;
      case ts.SyntaxKind.ReadonlyKeyword:
        modifiers.push("readonly");
        break;
      case ts.SyntaxKind.AsyncKeyword:
        modifiers.push("async");
        break;
      case ts.SyntaxKind.ExportKeyword:
        modifiers.push("export");
        break;
      case ts.SyntaxKind.DefaultKeyword:
        modifiers.push("default");
        break;
      case ts.SyntaxKind.DeclareKeyword:
        modifiers.push("declare");
        break;
      case ts.SyntaxKind.OverrideKeyword:
        modifiers.push("override");
        break;
      default:
        break;
    }
  }
  // `export` puts a declaration on the module's public surface. Spell that out as `public` too, so that
  // asking "is this public?" means the same thing here as it does for Java, C# or Python.
  if (modifiers.indexOf("export") >= 0 && modifiers.indexOf("public") < 0) {
    modifiers.push("public");
  }
  return modifiers;
}

function getJsDoc(node) {
  if (!node.jsDoc || !node.jsDoc.length) {
    return "";
  }
  return node.jsDoc.map((doc) => doc.getText()).join("\n");
}

function typeToString(checker, node) {
  if (!checker) {
    return "";
  }
  const type = checker.getTypeAtLocation(node);
  return checker.typeToString(type);
}

function getReturnType(checker, node) {
  if (!checker) {
    return "";
  }
  const signature = checker.getSignatureFromDeclaration(node);
  if (!signature) {
    return "";
  }
  const returnType = checker.getReturnTypeOfSignature(signature);
  return normalizeReturnType(returnType, checker);
}

function getReturnTypeObject(checker, node) {
  if (!checker) {
    return null;
  }
  const signature = checker.getSignatureFromDeclaration(node);
  if (!signature) {
    return null;
  }
  return checker.getReturnTypeOfSignature(signature);
}

function normalizeReturnType(type, checker, depth) {
  if (!type || !checker || !state.ts) {
    return "";
  }
  depth = depth || 0;
  if (depth >= MAX_TYPE_DEPTH) {
    return checker.typeToString(type);
  }
  const ts = state.ts;
  const flags = type.flags || 0;

  if (flags & ts.TypeFlags.StringLiteral) {
    return "string";
  }
  if (flags & ts.TypeFlags.NumberLiteral) {
    return "number";
  }
  if (flags & ts.TypeFlags.BooleanLiteral) {
    return "boolean";
  }
  if (flags & ts.TypeFlags.BigIntLiteral) {
    return "bigint";
  }

  if (type.isUnion && type.isUnion() && Array.isArray(type.types)) {
    const parts = [];
    for (const subType of type.types) {
      const normalized = normalizeReturnType(subType, checker, depth + 1);
      if (!normalized) {
        continue;
      }
      parts.push(normalized);
    }
    const unique = Array.from(new Set(parts));
    return unique.join(" | ");
  }

  if (type.isIntersection && type.isIntersection() && Array.isArray(type.types)) {
    const parts = [];
    for (const subType of type.types) {
      const normalized = normalizeReturnType(subType, checker, depth + 1);
      if (!normalized) {
        continue;
      }
      parts.push(normalized);
    }
    const unique = Array.from(new Set(parts));
    return unique.join(" & ");
  }

  return checker.typeToString(type);
}

function buildSignature(name, parameters, checker) {
  const paramTypes = parameters.map((param) => typeToString(checker, param));
  return `${name}(${paramTypes.join(", ")})`;
}

function isInternalFile(filePath, repoRootOverride) {
  const rootPath = repoRootOverride || state.repoRoot;
  if (!rootPath) {
    return false;
  }
  const normalized = path.resolve(filePath);
  const root = path.resolve(rootPath);
  if (!normalized.startsWith(root)) {
    return false;
  }
  if (normalized.includes(`${path.sep}node_modules${path.sep}`)) {
    return false;
  }
  return true;
}

function resolveSymbolName(symbol, checker) {
  let actual = symbol;
  if (symbol.flags & state.ts.SymbolFlags.Alias) {
    actual = checker.getAliasedSymbol(symbol);
  }
  const declarations = actual.declarations || [];
  for (const decl of declarations) {
    if (decl.name && decl.name.getText) {
      return decl.name.getText();
    }
  }
  return actual.getName();
}

function collectSymbolEntries(type, checker, entries, depth) {
  if (!type) {
    return;
  }
  depth = depth || 0;
  if (depth >= MAX_TYPE_DEPTH) {
    const symbol = type.aliasSymbol || type.symbol;
    if (symbol) {
      entries.push({ symbol, type });
    }
    return;
  }
  if (type.isUnionOrIntersection && type.isUnionOrIntersection()) {
    for (const sub of type.types) {
      collectSymbolEntries(sub, checker, entries, depth + 1);
    }
    return;
  }
  if (type.aliasTypeArguments) {
    for (const arg of type.aliasTypeArguments) {
      if (arg && arg.isThisType) {
        continue;
      }
      collectSymbolEntries(arg, checker, entries, depth + 1);
    }
  }
  if (type.typeArguments) {
    for (const arg of type.typeArguments) {
      if (arg && arg.isThisType) {
        continue;
      }
      collectSymbolEntries(arg, checker, entries, depth + 1);
    }
  }
  const symbol = type.aliasSymbol || type.symbol;
  if (symbol) {
    entries.push({ symbol, type });
  }
}

function collectDisplayNames(type, checker, names, depth) {
  if (!type) {
    return;
  }
  depth = depth || 0;
  if (depth >= MAX_TYPE_DEPTH) {
    return;
  }
  if (type.isUnionOrIntersection && type.isUnionOrIntersection()) {
    for (const sub of type.types) {
      collectDisplayNames(sub, checker, names, depth + 1);
    }
    return;
  }
  if (type.aliasTypeArguments) {
    for (const arg of type.aliasTypeArguments) {
      if (arg && arg.isThisType) {
        continue;
      }
      collectDisplayNames(arg, checker, names, depth + 1);
    }
  }
  if (type.typeArguments) {
    for (const arg of type.typeArguments) {
      if (arg && arg.isThisType) {
        continue;
      }
      collectDisplayNames(arg, checker, names, depth + 1);
    }
  }
  const symbol = type.aliasSymbol || type.symbol;
  if (symbol) {
    return;
  }
  const displayName = checker.typeToString(type);
  if (displayName && displayName !== "void") {
    names.add(displayName);
  }
}

function referenceKey(reference) {
  if (!reference) {
    return "";
  }
  if (reference.external) {
    return `${reference.kind}|external|${reference.displayName}`;
  }
  if (reference.target) {
    return `${reference.kind}|internal|${reference.target.filePath}|${reference.target.symbolName}`;
  }
  return `${reference.kind}|external|${reference.displayName || ""}`;
}

function mergeReferences(...lists) {
  const merged = [];
  const seen = new Set();
  for (const list of lists) {
    if (!list) {
      continue;
    }
    for (const ref of list) {
      const key = referenceKey(ref);
      if (!key || seen.has(key)) {
        continue;
      }
      seen.add(key);
      merged.push(ref);
    }
  }
  return merged;
}

// TypeScript's own names for symbols it synthesises: the anonymous object type a mixin factory
// returns, a call or index signature, an anonymous class. None of them names a type a consumer can
// look up, so recording one as a dependency invents an edge to something that does not exist.
const TS_INTERNAL_SYMBOL_NAMES = new Set([
  "__object", "__type", "__function", "__class", "__call", "__new", "__index", "__constructor", "__global"
]);

function isInternalSymbolName(name) {
  return !!name && TS_INTERNAL_SYMBOL_NAMES.has(name);
}

function buildReferenceModelsFromType(type, checker, kind) {
  const references = [];
  const seen = new Set();
  const entries = [];
  collectSymbolEntries(type, checker, entries);
  const displayNames = new Set();
  collectDisplayNames(type, checker, displayNames);
  if (!entries.length) {
    const displayName = checker.typeToString(type);
    if (displayName && displayName !== "void") {
      references.push({ kind, external: true, displayName });
    }
    return references;
  }
  for (const entry of entries) {
    const name = resolveSymbolName(entry.symbol, checker);
    if (isInternalSymbolName(name)) {
      continue;
    }
    const decls = (entry.symbol.flags & state.ts.SymbolFlags.Alias)
      ? checker.getAliasedSymbol(entry.symbol).declarations
      : entry.symbol.declarations;
    const decl = decls && decls.length ? decls[0] : null;
    const fileName = decl && decl.getSourceFile ? decl.getSourceFile().fileName : null;
    if (fileName && name && isInternalFile(fileName)) {
      const normalized = path.resolve(fileName);
      const key = `${kind}|internal|${normalized}|${name}`;
      if (!seen.has(key)) {
        references.push({
          kind,
          external: false,
          target: { filePath: normalized, symbolName: name }
        });
        seen.add(key);
      }
    } else {
      const displayName = checker.typeToString(entry.type || type);
      if (!displayName || displayName === "void") {
        continue;
      }
      const key = `${kind}|external|${displayName}`;
      if (!seen.has(key)) {
        references.push({ kind, external: true, displayName });
        seen.add(key);
      }
    }
  }
  for (const displayName of displayNames) {
    if (!displayName || displayName === "void") {
      continue;
    }
    const key = `${kind}|external|${displayName}`;
    if (!seen.has(key)) {
      references.push({ kind, external: true, displayName });
      seen.add(key);
    }
  }
  return references;
}

function buildHeritageReferences(node, checker) {
  const references = [];
  if (!node.heritageClauses) {
    return references;
  }
  for (const clause of node.heritageClauses) {
    const kind = clause.token === state.ts.SyntaxKind.ExtendsKeyword ? "extends" : "implements";
    for (const typeNode of clause.types) {
      const type = checker.getTypeAtLocation(typeNode);
      references.push(...buildReferenceModelsFromType(type, checker, kind));
    }
  }
  return references;
}

function decoratorTypeName(expression) {
  if (!expression) {
    return null;
  }
  if (state.ts.isCallExpression(expression)) {
    return decoratorTypeName(expression.expression);
  }
  if (state.ts.isPropertyAccessExpression(expression)) {
    return expression.name.text;
  }
  if (state.ts.isIdentifier(expression)) {
    return expression.text;
  }
  return expression.getText ? expression.getText() : null;
}

// An applied decorator (`@Component`, `@Injectable`) names a type the way `extends`/`implements` do,
// so it is emitted as an "annotation"-kind reference rather than a separate field. The decorator's
// call arguments are not retained -- only the decorator type's name. Decorators live outside
// `node.modifiers` in TypeScript 5, so they are read through `getDecorators`.
function buildDecoratorReferences(node) {
  const references = [];
  if (!node || typeof state.ts.canHaveDecorators !== "function" || !state.ts.canHaveDecorators(node)) {
    return references;
  }
  const decorators = state.ts.getDecorators(node) || [];
  for (const decorator of decorators) {
    const name = decoratorTypeName(decorator.expression);
    if (name) {
      references.push({ kind: "annotation", external: true, displayName: name });
    }
  }
  return references;
}

function buildCallReferences(node, checker) {
  const references = [];
  if (!node) {
    return references;
  }
  const callType = checker.getTypeAtLocation(node);
  references.push(...buildReferenceModelsFromType(callType, checker, "type"));
  const expr = node.expression;
  if (state.ts.isPropertyAccessExpression(expr) || state.ts.isElementAccessExpression(expr)) {
    const receiverType = checker.getTypeAtLocation(expr.expression);
    references.push(...buildReferenceModelsFromType(receiverType, checker, "type"));
  }
  return references;
}

function collectVariableModifiers(ts, declaration) {
  const modifiers = [];
  if (!declaration || !declaration.parent) {
    return modifiers;
  }
  const list = declaration.parent;
  if (list.flags & ts.NodeFlags.Const) {
    modifiers.push("const");
  } else if (list.flags & ts.NodeFlags.Let) {
    modifiers.push("let");
  }
  return modifiers;
}

function buildLocalVariableModel(declaration, checker) {
  if (!state.ts.isIdentifier(declaration.name)) {
    return null;
  }
  const variableType = checker.getTypeAtLocation(declaration);
  let references = buildReferenceModelsFromType(variableType, checker, "type");
  if (!declaration.type && declaration.initializer) {
    references = mergeReferences(
      references,
      buildReferenceModelsFromType(checker.getTypeAtLocation(declaration.initializer), checker, "type")
    );
  }
  return {
    kind: "local",
    name: declaration.name.text,
    type: checker.typeToString(variableType),
    implementationHash: stableImplementationHash(declaration.getText()),
    modifiers: collectVariableModifiers(state.ts, declaration),
    jsDoc: getJsDoc(declaration),
    references
  };
}

function buildTopLevelVariableModel(declaration, statement, checker) {
  const model = buildLocalVariableModel(declaration, checker);
  if (!model) {
    return null;
  }
  model.kind = "moduleField";
  if (statement) {
    model.modifiers = collectModifiers(state.ts, statement).concat(model.modifiers || []);
    if (statement.declarationList) {
      const flags = statement.declarationList.flags;
      if (!(flags & state.ts.NodeFlags.Const) && !(flags & state.ts.NodeFlags.Let)) {
        model.modifiers.push("var");
      }
    }
    if (!model.jsDoc || !model.jsDoc.length) {
      model.jsDoc = getJsDoc(statement);
    }
  }
  return model;
}

function collectBodyDetails(body, checker) {
  const references = [];
  const locals = [];
  function visit(node) {
    if (state.ts.isFunctionLike(node) && node !== body) {
      return;
    }
    if (state.ts.isVariableDeclaration(node)) {
      const localModel = buildLocalVariableModel(node, checker);
      if (localModel) {
        locals.push(localModel);
      }
    }
    if (state.ts.isCallExpression(node)) {
      references.push(...buildCallReferences(node, checker));
    }
    if (state.ts.isNewExpression(node)) {
      const newType = checker.getTypeAtLocation(node);
      references.push(...buildReferenceModelsFromType(newType, checker, "type"));
    }
    state.ts.forEachChild(node, visit);
  }
  if (body) {
    visit(body);
  }
  return { references, locals };
}

function computeCyclo(ts, node) {
  if (!node || !node.body) {
    return 0;
  }
  let complexity = 1;
  function visit(n) {
    if (
      ts.isIfStatement(n) ||
      ts.isForStatement(n) ||
      ts.isForInStatement(n) ||
      ts.isForOfStatement(n) ||
      ts.isWhileStatement(n) ||
      ts.isDoStatement(n) ||
      ts.isConditionalExpression(n) ||
      ts.isCatchClause(n)
    ) {
      complexity += 1;
    }
    if (ts.isCaseClause(n) || ts.isDefaultClause(n)) {
      complexity += 1;
    }
    if (
      ts.isBinaryExpression(n) &&
      (n.operatorToken.kind === ts.SyntaxKind.AmpersandAmpersandToken ||
        n.operatorToken.kind === ts.SyntaxKind.BarBarToken)
    ) {
      complexity += 1;
    }
    ts.forEachChild(n, visit);
  }
  visit(node.body);
  return complexity;
}

function buildParameterModels(parameters, checker) {
  const models = [];
  for (const param of parameters) {
    const paramType = checker.getTypeAtLocation(param);
    models.push({
      kind: "parameter",
      name: param.name.getText(),
      type: typeToString(checker, param),
      implementationHash: stableImplementationHash(param.getText()),
      modifiers: collectModifiers(state.ts, param),
      jsDoc: getJsDoc(param),
      references: buildReferenceModelsFromType(paramType, checker, "type")
    });
  }
  return models;
}

function buildFieldModel(node, checker) {
  if (!node.name) {
    return null;
  }
  const fieldType = checker.getTypeAtLocation(node);
  return {
    kind: "field",
    name: node.name.getText(),
    type: typeToString(checker, node),
    implementationHash: stableImplementationHash(node.getText()),
    modifiers: collectModifiers(state.ts, node),
    jsDoc: getJsDoc(node),
    references: mergeReferences(
      buildReferenceModelsFromType(fieldType, checker, "type"),
      buildDecoratorReferences(node)
    )
  };
}

function buildMethodModel(node, checker, kindLabel) {
  const name = kindLabel === "constructor" ? "constructor" : node.name.getText();
  const signature = buildSignature(name, node.parameters || [], checker);
  const returnTypeObject = kindLabel === "constructor" ? null : getReturnTypeObject(checker, node);
  const bodyDetails = collectBodyDetails(node.body, checker);
  const paramModels = buildParameterModels(node.parameters || [], checker);
  return {
    kind: kindLabel,
    name,
    signature,
    // The whole declaration, not just the body: a changed return type or modifier is a change too, and a
    // body-less declaration (an interface method, an abstract method) still gets a real hash this way.
    implementationHash: stableImplementationHash(node.getText()),
    returnType: kindLabel === "constructor" ? "" : getReturnType(checker, node),
    modifiers: collectModifiers(state.ts, node),
    jsDoc: getJsDoc(node),
    cyclo: computeCyclo(state.ts, node),
    members: paramModels.concat(bodyDetails.locals),
    references: mergeReferences(
      returnTypeObject ? buildReferenceModelsFromType(returnTypeObject, checker, "type") : [],
      bodyDetails.references,
      buildDecoratorReferences(node)
    )
  };
}

function buildAccessorModel(node, checker, accessorKind) {
  const name = node.name.getText();
  const signature = buildSignature(name, node.parameters || [], checker);
  const modifiers = collectModifiers(state.ts, node);
  modifiers.push(accessorKind);
  const bodyDetails = collectBodyDetails(node.body, checker);
  return {
    kind: "method",
    name,
    signature,
    implementationHash: stableImplementationHash(node.getText()),
    returnType: accessorKind === "set" ? "" : getReturnType(checker, node),
    modifiers,
    jsDoc: getJsDoc(node),
    cyclo: computeCyclo(state.ts, node),
    members: buildParameterModels(node.parameters || [], checker).concat(bodyDetails.locals),
    references: mergeReferences(bodyDetails.references, buildDecoratorReferences(node))
  };
}

function buildEnumModel(node) {
  const members = node.members.map((member) => ({
    kind: "enumMember",
    name: member.name.getText(),
    implementationHash: stableImplementationHash(member.getText())
  }));
  return {
    kind: "enum",
    name: node.name.text,
    implementationHash: stableImplementationHash(node.getText()),
    modifiers: collectModifiers(state.ts, node),
    jsDoc: getJsDoc(node),
    members,
    references: []
  };
}

function buildInterfaceModel(node, checker) {
  const members = [];
  for (const member of node.members) {
    if (state.ts.isMethodSignature(member)) {
      members.push(buildMethodModel(member, checker, "method"));
    } else if (state.ts.isPropertySignature(member)) {
      const fieldModel = buildFieldModel(member, checker);
      if (fieldModel) {
        members.push(fieldModel);
      }
    }
  }
  return {
    kind: "interface",
    name: node.name.text,
    implementationHash: stableImplementationHash(node.getText()),
    modifiers: collectModifiers(state.ts, node),
    jsDoc: getJsDoc(node),
    members,
    references: buildHeritageReferences(node, checker)
  };
}

/**
 * The `extends` clause whose expression is a call, or null.
 *
 * `class User extends Schema.Class<User>("User")({...})` declares its members in the argument to a
 * call rather than in its own body, which is the shape the Effect `Schema.Class` and `Context.Tag`
 * patterns take. The class body is then empty and the members look absent.
 */
function classProducingBaseExpression(node) {
  if (!node.heritageClauses) {
    return null;
  }
  for (const clause of node.heritageClauses) {
    if (clause.token !== state.ts.SyntaxKind.ExtendsKeyword) {
      continue;
    }
    for (const typeNode of clause.types) {
      if (typeNode.expression && state.ts.isCallExpression(typeNode.expression)) {
        return typeNode;
      }
    }
  }
  return null;
}

function signatureParameterTypes(signature, checker, location) {
  const parameters = signature.getParameters ? signature.getParameters() : [];
  return parameters.map((parameter) => {
    try {
      return checker.typeToString(checker.getTypeOfSymbolAtLocation(parameter, location));
    } catch (err) {
      return "any";
    }
  });
}

/**
 * The members a class gets from a class-producing base expression, as the type checker sees them.
 *
 * Only the members the class does not declare itself are built, and only for a class whose base is
 * a call expression -- an ordinary `extends Base` is left alone, so a subclass does not absorb a
 * copy of everything its superclass declares.
 */
function buildBaseExpressionMembers(node, checker, declaredNames) {
  const models = [];
  const symbol = node.name ? checker.getSymbolAtLocation(node.name) : null;
  if (!symbol) {
    return models;
  }
  let instanceType;
  try {
    instanceType = checker.getDeclaredTypeOfSymbol(symbol);
  } catch (err) {
    return models;
  }
  if (!instanceType) {
    return models;
  }
  let properties;
  try {
    properties = checker.getPropertiesOfType(instanceType) || [];
  } catch (err) {
    return models;
  }
  for (const property of properties) {
    const name = property.getName ? property.getName() : null;
    if (!name || declaredNames.has(name) || isInternalSymbolName(name)) {
      continue;
    }
    let propertyType;
    try {
      propertyType = checker.getTypeOfSymbolAtLocation(property, node);
    } catch (err) {
      continue;
    }
    if (!propertyType) {
      continue;
    }
    let callSignatures = [];
    try {
      callSignatures = checker.getSignaturesOfType(propertyType, state.ts.SignatureKind.Call) || [];
    } catch (err) {
      callSignatures = [];
    }
    const references = buildReferenceModelsFromType(propertyType, checker, "type");
    if (callSignatures.length > 0) {
      const signature = callSignatures[0];
      const paramTypes = signatureParameterTypes(signature, checker, node);
      let returnType = "";
      try {
        returnType = normalizeReturnType(checker.getReturnTypeOfSignature(signature), checker);
      } catch (err) {
        returnType = "";
      }
      models.push({
        kind: "method",
        name,
        signature: `${name}(${paramTypes.join(", ")})`,
        implementationHash: stableImplementationHash(`${name}(${paramTypes.join(", ")}):${returnType}`),
        returnType,
        modifiers: [],
        jsDoc: "",
        cyclo: 0,
        members: [],
        references
      });
      continue;
    }
    const typeText = checker.typeToString(propertyType);
    models.push({
      kind: "field",
      name,
      type: typeText,
      implementationHash: stableImplementationHash(`${name}:${typeText}`),
      modifiers: [],
      jsDoc: "",
      references
    });
  }
  return models;
}

function buildClassModel(node, checker, unresolvedBases) {
  const members = [];
  for (const member of node.members) {
    if (state.ts.isConstructorDeclaration(member)) {
      if (member.body) {
        members.push(buildMethodModel(member, checker, "constructor"));
      }
      continue;
    }
    if (state.ts.isMethodDeclaration(member)) {
      if (member.body || collectModifiers(state.ts, member).includes("abstract")) {
        members.push(buildMethodModel(member, checker, "method"));
      }
      continue;
    }
    if (state.ts.isPropertyDeclaration(member)) {
      const fieldModel = buildFieldModel(member, checker);
      if (fieldModel) {
        members.push(fieldModel);
      }
      continue;
    }
    if (state.ts.isGetAccessorDeclaration(member)) {
      members.push(buildAccessorModel(member, checker, "get"));
      continue;
    }
    if (state.ts.isSetAccessorDeclaration(member)) {
      members.push(buildAccessorModel(member, checker, "set"));
    }
  }
  const baseExpression = classProducingBaseExpression(node);
  if (baseExpression) {
    const declaredNames = new Set();
    for (const member of members) {
      if (member && member.name) {
        declaredNames.add(member.name);
      }
    }
    const inherited = buildBaseExpressionMembers(node, checker, declaredNames);
    if (inherited.length > 0) {
      members.push(...inherited);
    } else if (Array.isArray(unresolvedBases)) {
      // The base is a call and the checker could see no members through it. Saying nothing here
      // would leave the class looking like one that genuinely declares none.
      unresolvedBases.push(`${node.name.text} extends ${baseExpression.getText()}`);
    }
  }
  const typeParams = node.typeParameters && node.typeParameters.length
    ? `<${node.typeParameters.map((param) => param.getText()).join(", ")}>`
    : "";
  return {
    kind: "class",
    name: node.name.text,
    signature: typeParams,
    implementationHash: stableImplementationHash(node.getText()),
    modifiers: collectModifiers(state.ts, node),
    jsDoc: getJsDoc(node),
    members,
    references: mergeReferences(
      buildHeritageReferences(node, checker),
      buildDecoratorReferences(node)
    )
  };
}

function buildFunctionModel(node, checker) {
  if (!node.name) {
    return null;
  }
  if (!node.body) {
    return null;
  }
  return buildMethodModel(node, checker, "function");
}

function collectTopLevelDeclarations(ts, sourceFile, checker, unresolvedBases) {
  const declarations = [];
  sourceFile.forEachChild((node) => {
    if (ts.isClassDeclaration(node) && node.name) {
      declarations.push(buildClassModel(node, checker, unresolvedBases));
      return;
    }
    if (ts.isInterfaceDeclaration(node)) {
      declarations.push(buildInterfaceModel(node, checker));
      return;
    }
    if (ts.isEnumDeclaration(node)) {
      declarations.push(buildEnumModel(node));
      return;
    }
    if (ts.isFunctionDeclaration(node)) {
      const model = buildFunctionModel(node, checker);
      if (model) {
        declarations.push(model);
      }
      return;
    }
    if (ts.isVariableStatement(node)) {
      const list = node.declarationList;
      for (const declaration of list.declarations) {
        const model = buildTopLevelVariableModel(declaration, node, checker);
        if (model) {
          declarations.push(model);
        }
      }
    }
  });
  return declarations.filter(Boolean);
}

async function handleInitRepo(params) {
  const repoRoot = params && params.repoRoot ? params.repoRoot : null;
  if (!repoRoot) {
    throw new Error("NO_REPO_ROOT");
  }
  const ts = loadTypeScript(repoRoot);
  if (!ts) {
    const err = new Error("TYPESCRIPT_NOT_FOUND");
    err.code = ERROR_CODES.TYPESCRIPT_NOT_FOUND;
    throw err;
  }
  const configs = expandProjectReferences(ts, findTsconfigs(repoRoot).sort());
  if (!configs.length) {
    const err = new Error("NO_TSCONFIG");
    err.code = ERROR_CODES.NO_TSCONFIG;
    throw err;
  }
  let parsedConfigs = [];
  let invalidConfigs = [];
  try {
    const result = parseConfigs(ts, repoRoot, configs);
    parsedConfigs = result.configs || [];
    invalidConfigs = result.invalidConfigs || [];
  } catch (err) {
    const errObj = new Error("CONFIG_PARSE_FAILED");
    errObj.code = ERROR_CODES.CONFIG_PARSE_FAILED;
    errObj.data = err.message;
    throw errObj;
  }
  if (!parsedConfigs.length) {
    const errObj = new Error("CONFIG_PARSE_FAILED");
    errObj.code = ERROR_CODES.CONFIG_PARSE_FAILED;
    errObj.data = invalidConfigs.map((entry) => entry.configPath);
    throw errObj;
  }
  const fileMap = new Map();
  parsedConfigs.forEach((config, index) => {
    for (const rootName of config.rootNames) {
      const normalized = normalizePath(ts, rootName);
      if (!isInternalFile(normalized, repoRoot)) {
        continue;
      }
      if (!fileMap.has(normalized)) {
        fileMap.set(normalized, index);
      }
    }
  });
  state = {
    repoRoot,
    ts,
    configs: parsedConfigs,
    fileMap,
    programCache: new Map(),
    programOrder: [],
    maxPrograms: resolveMaxPrograms(params)
  };
  const fileCount = parsedConfigs.reduce((sum, config) => sum + config.rootNames.length, 0);
  return {
    tsVersion: ts.version || "",
    configCount: parsedConfigs.length,
    fileCount,
    invalidConfigCount: invalidConfigs.length,
    invalidConfigs,
    // Zero by construction: initialization reads configs and builds no programs. Reported so a
    // caller can tell that the daemon is not holding a program per config before any file is asked
    // for, which is what exhausted the heap on a large monorepo.
    residentProgramCount: state.programCache.size,
    maxPrograms: state.maxPrograms
  };
}

async function handleGetFileModel(params) {
  if (!state.ts || !state.configs || !state.configs.length) {
    const err = new Error("PROGRAM_NOT_READY");
    err.code = ERROR_CODES.PROGRAM_CREATE_FAILED;
    throw err;
  }
  const filePath = params && params.filePath ? params.filePath : null;
  if (!filePath) {
    const err = new Error("FILE_NOT_FOUND");
    err.code = ERROR_CODES.FILE_NOT_FOUND;
    throw err;
  }
  const ts = state.ts;
  const normalized = normalizePath(ts, filePath);
  const entryInfo = findProgramEntryForFile(normalized);
  if (!entryInfo) {
    const err = new Error("FILE_NOT_IN_PROGRAM");
    err.code = ERROR_CODES.FILE_NOT_IN_PROGRAM;
    throw err;
  }
  const checker = entryInfo.entry.checker || entryInfo.entry.program.getTypeChecker();
  const unresolvedBases = [];
  let declarations;
  try {
    declarations = collectTopLevelDeclarations(ts, entryInfo.source, checker, unresolvedBases);
  } catch (err) {
    if (err instanceof RangeError || (err.message && err.message.includes("Maximum call stack size exceeded"))) {
      console.warn(`[clarpse] Stack overflow during type resolution for ${filePath}.`);
      err.code = ERROR_CODES.RESOLUTION_FAILED;
    } else {
      throw err;
    }
    throw err;
  }
  let imports = [];
  try {
    imports = collectImports(ts, entryInfo.source, checker);
  } catch (err) {
    imports = [];
  }
  return {
    filePath: normalized,
    declarations,
    imports,
    unresolvedBases
  };
}


// Imports were never collected: module resolution is delegated to the TypeScript compiler's symbol
// table, so nothing needed them and `imports()` came back empty for every TypeScript component
// while Java and C# populated it. Issue #156.
//
// Resolved through the checker rather than by string manipulation on the specifier, so an import
// that TypeScript itself cannot resolve is reported as external rather than guessed into a path.
function collectImports(ts, source, checker) {
  const results = [];
  for (const statement of source.statements) {
    if (!ts.isImportDeclaration(statement) || !statement.moduleSpecifier) continue;
    const specifier = statement.moduleSpecifier.text;
    if (!specifier) continue;

    let resolvedFile = null;
    try {
      const moduleSymbol = checker.getSymbolAtLocation(statement.moduleSpecifier);
      const declaration = moduleSymbol && moduleSymbol.declarations && moduleSymbol.declarations[0];
      if (declaration && declaration.getSourceFile) {
        const fileName = declaration.getSourceFile().fileName;
        if (fileName && isInternalFile(fileName)) resolvedFile = fileName;
      }
    } catch (err) {
      resolvedFile = null;
    }

    const names = [];
    const clause = statement.importClause;
    if (clause) {
      if (clause.name) names.push(clause.name.text);
      const bindings = clause.namedBindings;
      if (bindings) {
        if (ts.isNamedImports(bindings)) {
          for (const element of bindings.elements) names.push(element.name.text);
        } else if (bindings.name) {
          names.push(bindings.name.text);
        }
      }
    }
    if (names.length === 0) {
      results.push({ module: specifier, filePath: resolvedFile, symbolName: null });
      continue;
    }
    for (const name of names) {
      results.push({ module: specifier, filePath: resolvedFile, symbolName: name });
    }
  }
  return results;
}

async function handleRequest(request) {
  const { id, method, params } = request;
  if (method === "initRepo") {
    try {
      const result = await handleInitRepo(params);
      writeResponse(id, result);
    } catch (err) {
      if (err.code) {
        writeError(id, err.code, err.message, err.data);
      } else {
        writeError(id, ERROR_CODES.PROGRAM_CREATE_FAILED, err.message);
      }
    }
    return;
  }
  if (method === "getFileModel") {
    try {
      const result = await handleGetFileModel(params);
      writeResponse(id, result);
    } catch (err) {
      if (err instanceof RangeError || (err.message && err.message.includes("Maximum call stack size exceeded"))) {
        writeError(id, ERROR_CODES.RESOLUTION_FAILED, "Maximum call stack size exceeded");
      } else if (err.code) {
        writeError(id, err.code, err.message, err.data);
      } else {
        writeError(id, ERROR_CODES.PROGRAM_CREATE_FAILED, err.message);
      }
    }
    return;
  }
  if (method === "shutdown") {
    writeResponse(id, { ok: true });
    process.exit(0);
    return;
  }
  writeError(id, -32601, "Method not found");
}

const rl = readline.createInterface({ input: process.stdin, crlfDelay: Infinity });
rl.on("line", (line) => {
  const trimmed = line.trim();
  if (!trimmed) {
    return;
  }
  let payload;
  try {
    payload = JSON.parse(trimmed);
  } catch (err) {
    writeError(null, -32700, "Parse error");
    return;
  }
  handleRequest(payload);
});
