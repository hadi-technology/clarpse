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

// In a one-level compile each program holds only planned files, which programs share, so up to this
// many stay resident whatever `maxPrograms` is: files are modelled analysed first and level one
// second, and a program evicted between the two would be built again.
const MAX_ONE_LEVEL_RESIDENT_PROGRAMS = 16;

let state = {
  repoRoot: null,
  ts: null,
  configs: [],
  fileMap: new Map(),
  programCache: new Map(),
  programOrder: [],
  maxPrograms: DEFAULT_MAX_PROGRAMS,
  oneLevel: null,
  shallowBody: false,
  sourceFiles: new Map(),
  settingsKeys: new WeakMap(),
  documentRegistry: null,
  programBuilds: 0
};

// Standard output carries replies and nothing else: the reader on the other end takes each line
// for one. The compiler, and anything it loads, writes there too when a project's own options ask
// it to report what it is doing, so every other write is sent to standard error and only the two
// functions below hold the channel.
const writeReply = process.stdout.write.bind(process.stdout);
process.stdout.write = (chunk, encoding, callback) => process.stderr.write(chunk, encoding, callback);

function writeResponse(id, result) {
  writeReply(JSON.stringify({ jsonrpc: "2.0", id, result }) + "\n");
}

function writeError(id, code, message, data) {
  const error = { code, message };
  if (data) {
    error.data = data;
  }
  writeReply(JSON.stringify({ jsonrpc: "2.0", id, error }) + "\n");
}

// Compiler options that make the compiler report on its own work. A project sets them to debug
// its build; none changes what a program holds, and each costs output nobody here reads.
const REPORTING_OPTIONS = [
  "traceResolution", "listFiles", "listFilesOnly", "listEmittedFiles", "explainFiles",
  "extendedDiagnostics", "diagnostics", "generateTrace", "generateCpuProfile"
];

function withoutReporting(options) {
  const quiet = Object.assign({}, options);
  for (const name of REPORTING_OPTIONS) {
    delete quiet[name];
  }
  return quiet;
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
    if (bundledResolved.sys) {
      bundledResolved.sys.write = (text) => process.stderr.write(text);
    }
    return bundledResolved;
  }
  return null;
}

/**
 * The configs a repository is read from: every `tsconfig.json`, and, in a directory that has
 * none, the `tsconfig.<name>.json` files that stand in for one. A repository that builds the same
 * tree several ways keeps one such file per way and no `tsconfig.json`. A variant another config
 * extends is a base for that config and owns nothing itself, and one another config references is
 * reached through the reference.
 */
function findProjectConfigs(ts, root) {
  const exact = findTsconfigs(root).sort();
  const withExact = new Set(exact.map((configPath) => path.dirname(configPath)));
  const variants = findTsconfigs(root, /^tsconfig\..+\.json$/)
    .filter((configPath) => !withExact.has(path.dirname(configPath)))
    .sort();
  if (!variants.length) {
    return exact;
  }
  const named = new Set();
  for (const configPath of exact.concat(variants)) {
    for (const other of referencedConfigPaths(ts, configPath).concat(extendedConfigPaths(ts, configPath))) {
      named.add(normalizePath(ts, other));
    }
  }
  return exact.concat(variants.filter((configPath) => !named.has(normalizePath(ts, configPath))));
}

/** The config files a config's `extends` names by path, resolved relative to the config. */
function extendedConfigPaths(ts, configPath) {
  let raw;
  try {
    raw = readNormalizedConfigFile(ts, configPath);
  } catch (err) {
    return [];
  }
  const named = raw && raw.config ? raw.config.extends : null;
  const results = [];
  for (const base of Array.isArray(named) ? named : [named]) {
    if (typeof base !== "string" || !(base.startsWith(".") || path.isAbsolute(base))) {
      continue;
    }
    const resolved = path.resolve(path.dirname(configPath), base);
    results.push(resolved);
    if (!resolved.toLowerCase().endsWith(".json")) {
      results.push(resolved + ".json");
    }
  }
  return results;
}

function findTsconfigs(root, named) {
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
      } else if (entry.isFile() && (named ? named.test(entry.name) : entry.name === "tsconfig.json")) {
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
/**
 * The packages of a repository that is a workspace, as `paths` entries that send an import of the
 * package to its source.
 *
 * A workspace package is imported by its name, and the name resolves through `node_modules` to
 * the package's built output (`exports` naming `dist/index.d.ts`), which a checkout does not have
 * until the repository is built and installed. The import then resolves to nothing, and every
 * edge into the package is lost. Mapping the name to the package's source folder is what a build
 * would have produced the output from.
 *
 * Workspaces are those the root `package.json` (`workspaces`) or `pnpm-workspace.yaml` list. A
 * dependency declared as an alias of one (`"alias": "npm:@scope/pkg@1"`, or
 * `"alias": "workspace:@scope/pkg@*"`) maps the alias too.
 */
function workspacePaths(repoRoot) {
  const patterns = workspacePatterns(repoRoot);
  if (!patterns.include.length) {
    return {};
  }
  const packages = new Map();
  for (const file of findPackageJsonFiles(repoRoot)) {
    const dir = path.dirname(file);
    const relative = path.relative(repoRoot, dir).split(path.sep).join("/");
    if (!relative || !matchesWorkspace(relative, patterns)) {
      continue;
    }
    const manifest = readJson(file);
    if (manifest && typeof manifest.name === "string" && manifest.name) {
      packages.set(manifest.name, { dir, manifest });
    }
  }
  const mapping = {};
  for (const [name, entry] of packages) {
    addPackagePaths(mapping, name, entry);
  }
  for (const entry of packages.values()) {
    for (const field of ["dependencies", "devDependencies", "peerDependencies", "optionalDependencies"]) {
      const dependencies = entry.manifest[field];
      for (const [alias, spec] of Object.entries(dependencies && typeof dependencies === "object" ? dependencies : {})) {
        const target = typeof spec === "string" ? aliasTarget(spec) : null;
        if (target && packages.has(target) && !mapping[alias]) {
          addPackagePaths(mapping, alias, packages.get(target));
        }
      }
    }
  }
  return mapping;
}

/** The package an `npm:` or `workspace:` dependency spec names, or null where it names none. */
function aliasTarget(spec) {
  const match = /^(?:npm|workspace):(@[^/@]+\/[^@/]+|[^@/]+)(?:@.*)?$/.exec(spec);
  return match ? match[1] : null;
}

function readJson(file) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch (err) {
    return null;
  }
}

function findPackageJsonFiles(root) {
  const found = [];
  const pending = [root];
  while (pending.length && found.length < 5000) {
    const dir = pending.pop();
    let entries;
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch (err) {
      continue;
    }
    for (const entry of entries) {
      if (entry.isDirectory()) {
        if (entry.name !== "node_modules" && entry.name !== ".git") {
          pending.push(path.join(dir, entry.name));
        }
      } else if (entry.name === "package.json") {
        found.push(path.join(dir, entry.name));
      }
    }
  }
  return found.sort();
}

function workspacePatterns(repoRoot) {
  const include = [];
  const exclude = [];
  const add = (pattern) => {
    if (typeof pattern !== "string") {
      return;
    }
    const cleaned = pattern.trim().replace(/^\.\//, "").replace(/\/+$/, "");
    if (cleaned.startsWith("!")) {
      exclude.push(globToRegExp(cleaned.slice(1)));
    } else if (cleaned) {
      include.push(globToRegExp(cleaned));
    }
  };
  const root = readJson(path.join(repoRoot, "package.json"));
  if (root && root.workspaces) {
    const listed = Array.isArray(root.workspaces) ? root.workspaces : root.workspaces.packages;
    (Array.isArray(listed) ? listed : []).forEach(add);
  }
  try {
    const yaml = fs.readFileSync(path.join(repoRoot, "pnpm-workspace.yaml"), "utf8");
    let inPackages = false;
    for (const line of yaml.split(/\r?\n/)) {
      if (/^packages:\s*$/.test(line)) {
        inPackages = true;
      } else if (inPackages) {
        const item = /^\s*-\s*['"]?([^'"#]+?)['"]?\s*(?:#.*)?$/.exec(line);
        if (item) {
          add(item[1]);
        } else if (/^\S/.test(line)) {
          inPackages = false;
        }
      }
    }
  } catch (err) {
    // No pnpm workspace file.
  }
  return { include, exclude };
}

function globToRegExp(glob) {
  let source = "";
  for (let i = 0; i < glob.length; i += 1) {
    const char = glob[i];
    if (char === "*" && glob[i + 1] === "*") {
      source += ".*";
      i += 1;
      if (glob[i + 1] === "/") {
        i += 1;
        source += "(?:/)?";
      }
    } else if (char === "*") {
      source += "[^/]*";
    } else {
      source += char.replace(/[.+?^${}()|[\]\\]/g, "\\$&");
    }
  }
  return new RegExp(`^${source}$`);
}

function matchesWorkspace(relative, patterns) {
  return patterns.include.some((pattern) => pattern.test(relative))
    && !patterns.exclude.some((pattern) => pattern.test(relative));
}

const BUILD_OUTPUT_DIRECTORIES = /^(?:dist|build|out|lib|esm|cjs|es|types)\//;

/** The source file a built file of a package was made from, or null where it cannot be found. */
function sourceOfBuiltFile(packageDir, built, sourceRoots) {
  if (typeof built !== "string") {
    return null;
  }
  const relative = built.replace(/^\.\//, "");
  const stems = new Set();
  const stem = relative.replace(/\.d\.[cm]?ts$|\.[cm]?[jt]sx?$/, "");
  stems.add(stem);
  stems.add(stem.replace(BUILD_OUTPUT_DIRECTORIES, ""));
  for (const candidate of stems) {
    for (const root of sourceRoots) {
      for (const extension of [".ts", ".tsx", ".mts", ".cts"]) {
        const file = path.join(root, candidate + extension);
        if (fs.existsSync(file)) {
          return file;
        }
      }
    }
  }
  return null;
}

function addPackagePaths(mapping, name, entry) {
  const { dir, manifest } = entry;
  const sourceRoots = ["src", "sources", "lib", ""].map((folder) => path.join(dir, folder))
    .filter((folder) => fs.existsSync(folder));
  const builtOf = (value) => {
    if (typeof value === "string") {
      return [value];
    }
    if (value && typeof value === "object") {
      return Object.values(value).flatMap(builtOf);
    }
    return [];
  };
  const exportsField = manifest.exports;
  const subpaths = exportsField && typeof exportsField === "object" && !Array.isArray(exportsField)
    && Object.keys(exportsField).some((key) => key.startsWith("."))
    ? exportsField
    : { ".": exportsField };
  let main = null;
  for (const [key, value] of Object.entries(subpaths)) {
    if (key.includes("*")) {
      continue;
    }
    const source = builtOf(value).map((built) => sourceOfBuiltFile(dir, built, sourceRoots)).find(Boolean);
    if (!source) {
      continue;
    }
    const specifier = key === "." ? name : `${name}/${key.replace(/^\.\//, "")}`;
    if (!mapping[specifier]) {
      mapping[specifier] = [source];
    }
    if (key === ".") {
      main = source;
    }
  }
  if (!main) {
    const legacy = [manifest.types, manifest.typings, manifest.module, manifest.main]
      .map((built) => sourceOfBuiltFile(dir, built, sourceRoots)).find(Boolean);
    const index = sourceRoots.flatMap((root) => ["index.ts", "index.tsx"].map((file) => path.join(root, file)))
      .find((file) => fs.existsSync(file));
    main = legacy || index || null;
    if (main && !mapping[name]) {
      mapping[name] = [main];
    }
  }
  if (!mapping[`${name}/*`]) {
    mapping[`${name}/*`] = sourceRoots.map((root) => path.join(root, "*"));
  }
}

/** The options with the workspace packages' sources named, without overriding a path already written. */
function withWorkspacePaths(options) {
  const mapped = state.workspacePaths;
  if (!mapped || !Object.keys(mapped).length) {
    return options;
  }
  return Object.assign({}, options, { paths: Object.assign({}, mapped, options.paths || {}) });
}

function parseConfigs(ts, repoRoot, configPaths) {
  const configs = [];
  const invalidConfigs = [];
  const configDiagnostics = [];

  for (const configPath of configPaths) {
    let configFile;
    try {
      configFile = readNormalizedConfigFile(ts, configPath);
    } catch (err) {
      if (isExtendsError(err)) {
        // The extends chain could not be read; fall through to minimal compiler options.
        configFile = null;
      } else {
        invalidConfigs.push(invalidConfig(ts, configPath, "CONFIG_READ_FAILED", err));
        continue;
      }
    }

    if (configFile && configFile.error && !isExtendsError(configFile.error)) {
      invalidConfigs.push(invalidConfig(ts, configPath, "CONFIG_PARSE_FAILED", configFile.error));
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
          invalidConfigs.push(invalidConfig(ts, configPath, "CONFIG_PARSE_FAILED", err));
          continue;
        }
      }
    }
    if (!config && !hasExtendErrors) {
      // Completely failed to parse, not due to extends
      invalidConfigs.push(invalidConfig(ts, configPath, "CONFIG_PARSE_FAILED", null));
      continue;
    }
    if (config && config.errors && config.errors.length > 0 && !hasExtendErrors) {
      // The config was read, and the compiler has said which files it names and which options
      // it could make sense of. What it could not is reported and the rest is used: an option
      // newer than this compiler, or a value it does not know, is a statement about one option
      // and not about the project's files.
      for (const diagnostic of config.errors) {
        configDiagnostics.push(describeDiagnostic(ts, configPath, diagnostic));
      }
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
      options = withoutReporting(Object.assign({}, config.options, { allowJs: false, checkJs: false }));
      rootNames = filterTypeScriptRoots(config.fileNames);
      projectReferences = config.projectReferences || [];
    }
    configs.push({
      configPath,
      dir: normalizePath(ts, path.dirname(configPath)),
      options: withWorkspacePaths(options),
      rootNames,
      projectReferences,
      programFailed: false
    });
  }
  return { configs, invalidConfigs, configDiagnostics };
}

/** A diagnostic as a caller can log it: the config, the compiler's code for it, and its text. */
function describeDiagnostic(ts, configPath, diagnostic) {
  let message = "";
  try {
    message = diagnostic && diagnostic.messageText !== undefined
      ? ts.flattenDiagnosticMessageText(diagnostic.messageText, " ")
      : String((diagnostic && diagnostic.message) || diagnostic || "");
  } catch (err) {
    message = String(diagnostic);
  }
  return {
    configPath,
    code: diagnostic && Number.isInteger(diagnostic.code) ? diagnostic.code : 0,
    message: message.slice(0, 500)
  };
}

function invalidConfig(ts, configPath, error, cause) {
  const described = cause ? describeDiagnostic(ts, configPath, cause) : { code: 0, message: "" };
  return { configPath, error, code: described.code, message: described.message };
}

/**
 * The options a program is given where no config supplies any. They are the ones that read the
 * most source without a project to say otherwise: the newest language level, imports resolved
 * the way a bundler or a runtime that runs TypeScript directly resolves them, and an import
 * allowed to name a file by its TypeScript extension.
 */
function optionsWithoutAConfig(ts) {
  return {
    allowJs: false,
    checkJs: false,
    strict: false,
    noEmit: true,
    esModuleInterop: true,
    skipLibCheck: true,
    resolveJsonModule: true,
    allowImportingTsExtensions: true,
    jsx: ts.JsxEmit.Preserve,
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ESNext,
    moduleResolution: ts.ModuleResolutionKind.Bundler,
    lib: []
  };
}

/**
 * A program for the files no config names, one for each top-level directory that holds any.
 *
 * A file no config names is not a file with nothing in it. A repository run by a runtime that
 * needs no config has none at all, and one whose configs cover its packages leaves its scripts
 * and tools outside them. Such a file is read with the options above. The top-level directory is
 * the bound on a program's size: what a repository keeps apart at its root is as near to a
 * project as a tree with no config states.
 *
 * Each is a config like any other, placed after every config the repository wrote, and tried
 * only where none of those holds the file.
 */
function programsForUnownedFiles(ts, repoRoot, configs) {
  const owned = new Set();
  for (const config of configs) {
    for (const rootName of config.rootNames) {
      owned.add(normalizePath(ts, rootName));
    }
  }
  const root = normalizePath(ts, repoRoot);
  const groups = new Map();
  for (const file of findTypeScriptFiles(repoRoot)) {
    const normalized = normalizePath(ts, file);
    if (owned.has(normalized)) {
      continue;
    }
    const relative = path.relative(root, normalized).split(path.sep);
    const dir = relative.length > 1 ? path.join(root, relative[0]) : root;
    if (!groups.has(dir)) {
      groups.set(dir, []);
    }
    groups.get(dir).push(normalized);
  }
  const options = withWorkspacePaths(optionsWithoutAConfig(ts));
  return Array.from(groups.keys()).sort().map((dir) => ({
    configPath: null,
    dir,
    options,
    rootNames: groups.get(dir).sort(),
    projectReferences: [],
    programFailed: false,
    unowned: true
  }));
}

/** Nearest enclosing directory first, and a config the repository wrote before one it did not. */
function nearestWrittenFirst(a, b) {
  const left = state.configs[a];
  const right = state.configs[b];
  if (!!left.unowned !== !!right.unowned) {
    return left.unowned ? 1 : -1;
  }
  return right.dir.length - left.dir.length;
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
    state.programBuilds += 1;
    program = state.ts.createProgram(programSpec(config));
  } catch (err) {
    console.error("[clarpse] PROGRAM_CREATE_FAILED for", config.configPath || config.dir, err.message);
    config.programFailed = true;
    return null;
  }
  // Which repository files the program holds, kept after the program itself is evicted, so a file
  // it does not hold is later answered without building the program again. Kept only for a config
  // with no project references: with them, `getSourceFile` can answer for a source path through a
  // redirect to its output declarations, which the program's file list does not name, and such a
  // config is asked about each file instead (see `rememberAbsent`).
  if (!state.oneLevel && !(config.projectReferences && config.projectReferences.length)) {
    config.memberFiles = new Set(
      program
        .getSourceFiles()
        .map((source) => normalizePath(state.ts, source.fileName))
        .filter((fileName) => isInternalFile(fileName))
    );
  }
  const entry = {
    configPath: config.configPath || config.dir,
    program,
    options: config.options,
    checker: withPortableTypeText(program.getTypeChecker())
  };
  state.programCache.set(index, entry);
  state.programOrder.push(index);
  const resident = state.oneLevel
    ? Math.max(state.maxPrograms, state.oneLevel.residentPrograms)
    : state.maxPrograms;
  while (state.programOrder.length > resident) {
    state.programCache.delete(state.programOrder.shift());
  }
  return entry;
}

/**
 * What a config's program is built from. Ordinarily that is every root file the config claims. In a
 * one-level analysis it is only the files planned for that config (see `handlePlanOneLevel`), with
 * `noResolve` so the compiler does not follow imports past them, and without project references,
 * which would redirect a referenced project's sources to build output a checkout does not have.
 */
function programSpec(config) {
  if (state.oneLevel) {
    const options = Object.assign({}, config.options, { noResolve: true });
    return {
      rootNames: config.oneLevelRoots || [],
      options,
      host: sharedSourceFileHost(options)
    };
  }
  return {
    rootNames: config.rootNames,
    options: config.options,
    projectReferences: config.projectReferences,
    host: sharedSourceFileHost(config.options)
  };
}

/**
 * A compiler host whose parsed source files are shared by every program built in this session, so a
 * file held by several programs is read and parsed once. A file is shared only between programs
 * whose options parse and bind it identically: the key is the file, the language version, module
 * format and JSDoc mode it was created with, and the compiler's own key for the options that
 * affect a source file (the one a language service's document registry uses).
 *
 * In a one-level compile every file is shared, since the planned files bound what any program can
 * hold. Otherwise only the default library declarations are, because a program may reach a large
 * part of a repository and holding every parsed file would defeat the bound on resident programs.
 */
function sharedSourceFileHost(options) {
  const ts = state.ts;
  const host = ts.createCompilerHost(options);
  const getSourceFile = host.getSourceFile.bind(host);
  const libDirectory = path.dirname(ts.getDefaultLibFilePath({}));
  host.getSourceFile = (fileName, languageVersionOrOptions, onError, shouldCreateNewSourceFile) => {
    const shared = state.oneLevel || isUnder(fileName, libDirectory);
    if (!shared || shouldCreateNewSourceFile) {
      return getSourceFile(fileName, languageVersionOrOptions, onError, shouldCreateNewSourceFile);
    }
    const key = sourceFileKey(options, fileName, languageVersionOrOptions);
    if (state.sourceFiles.has(key)) {
      return state.sourceFiles.get(key);
    }
    const source = getSourceFile(fileName, languageVersionOrOptions, onError, shouldCreateNewSourceFile);
    if (source) {
      state.sourceFiles.set(key, source);
    }
    return source;
  };
  return host;
}

function isUnder(fileName, directory) {
  const relative = path.relative(directory, fileName);
  return relative !== "" && !relative.startsWith("..") && !path.isAbsolute(relative);
}

function sourceFileKey(options, fileName, languageVersionOrOptions) {
  if (!state.documentRegistry) {
    state.documentRegistry = state.ts.createDocumentRegistry();
  }
  let settings = state.settingsKeys.get(options);
  if (settings === undefined) {
    settings = state.documentRegistry.getKeyForCompilationSettings(options);
    state.settingsKeys.set(options, settings);
  }
  let creation = String(languageVersionOrOptions);
  if (languageVersionOrOptions && typeof languageVersionOrOptions === "object") {
    creation = [
      languageVersionOrOptions.languageVersion,
      languageVersionOrOptions.impliedNodeFormat,
      languageVersionOrOptions.jsDocParsingMode
    ].join(",");
  }
  return [fileName, creation, settings].join("|");
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
  if (state.oneLevel) {
    // A one-level analysis models exactly the files it planned, each in the program of the config
    // that owns it, and never builds a program it did not plan.
    if (state.oneLevel.fileConfig.has(filePath)) {
      return [state.oneLevel.fileConfig.get(filePath)];
    }
    return [];
  }
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
  candidates.sort(nearestWrittenFirst);
  return candidates;
}

function findProgramEntryForFile(filePath) {
  for (const index of configIndicesForFile(filePath)) {
    const config = state.configs[index];
    if (knownAbsent(config, filePath)) {
      continue;
    }
    const entry = programFor(index);
    if (!entry) {
      continue;
    }
    const source = entry.program.getSourceFile(filePath);
    if (source) {
      state.fileMap.set(filePath, index);
      return { entry, source };
    }
    rememberAbsent(config, filePath);
  }
  return null;
}

/**
 * Records that a config's program was asked for the file and does not hold it. The answer is the
 * program's own (`getSourceFile`), so a later request for the file skips the program instead of
 * building it again, without a second opinion about what the program holds.
 */
function knownAbsent(config, filePath) {
  return !!config && ((config.memberFiles && !config.memberFiles.has(filePath))
    || (config.absentFiles && config.absentFiles.has(filePath)));
}

function rememberAbsent(config, filePath) {
  if (!config) {
    return;
  }
  if (!config.absentFiles) {
    config.absentFiles = new Set();
  }
  config.absentFiles.add(filePath);
}

/**
 * For each file, the index of the config whose program holds it, or -1 where none does, found the
 * way a request for the file's model finds it. Files are asked of one config's program together, so
 * a program is built once for all the files it is asked about, instead of once per file.
 */
function handleOwnersOf(params) {
  requireReady();
  const owners = {};
  let pending = new Map();
  for (const file of (params.files || [])) {
    const normalized = normalizePath(state.ts, file);
    const candidates = configIndicesForFile(normalized);
    if (state.oneLevel) {
      owners[file] = candidates.length ? candidates[0] : -1;
    } else if (state.fileMap.has(normalized)) {
      owners[file] = state.fileMap.get(normalized);
    } else {
      pending.set(file, { normalized, candidates });
    }
  }
  // Each file waits at the first of its candidate configs not yet known to lack it. A pass visits
  // the configs in order and builds the program of each that has files waiting at it, once, for all
  // of them; a file the program lacks moves on to its next candidate.
  const waitingAt = (entry, file) => {
    const config = () => state.configs[entry.candidates[entry.next]];
    while (entry.next < entry.candidates.length && knownAbsent(config(), entry.normalized)) {
      entry.next += 1;
    }
    return entry.next < entry.candidates.length ? entry.candidates[entry.next] : -1;
  };
  for (const entry of pending.values()) {
    entry.next = 0;
  }
  let moved = true;
  while (pending.size > 0 && moved) {
    moved = false;
    const configsWaited = new Set();
    for (const [file, entry] of pending) {
      const index = waitingAt(entry, file);
      if (index < 0) {
        owners[file] = -1;
        pending.delete(file);
      } else {
        configsWaited.add(index);
      }
    }
    for (const index of Array.from(configsWaited).sort((a, b) => a - b)) {
      const waiting = [];
      for (const [file, entry] of pending) {
        if (waitingAt(entry, file) === index) {
          waiting.push(file);
        }
      }
      if (!waiting.length) {
        continue;
      }
      const config = state.configs[index];
      const known = config.memberFiles;
      const built = known ? null : programFor(index);
      for (const file of waiting) {
        const entry = pending.get(file);
        if (known ? known.has(entry.normalized)
            : (built && built.program.getSourceFile(entry.normalized))) {
          state.fileMap.set(entry.normalized, index);
          owners[file] = index;
          pending.delete(file);
        } else if (known || built) {
          rememberAbsent(config, entry.normalized);
        } else {
          entry.next += 1;
        }
        moved = true;
      }
    }
  }
  for (const file of pending.keys()) {
    owners[file] = -1;
  }
  return { owners };
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

/**
 * The repository root as the checker writes paths: resolved, with its real path too when it is
 * reached through a symbolic link, each with a trailing separator and forward slashes.
 */
function repositoryRootPrefixes() {
  if (!state.repoRoot) {
    return [];
  }
  const roots = new Set([path.resolve(state.repoRoot)]);
  try {
    roots.add(fs.realpathSync(state.repoRoot));
  } catch (err) {
    // the resolved root alone
  }
  return Array.from(roots)
    .map((root) => root.replace(/\\/g, "/").replace(/\/+$/, "") + "/")
    .sort((left, right) => right.length - left.length);
}

/**
 * Type text with every path inside the repository written relative to its root. The checker writes
 * a type it cannot name from where it is printed as `import("<absolute path>")`, and the absolute
 * path is wherever the sources were put on disk for this run; relative to the root, the text is the
 * same however and wherever the repository is read.
 */
function portableTypeText(text) {
  if (!text || text.indexOf("/") < 0) {
    return text;
  }
  let portable = text;
  for (const prefix of repositoryRootPrefixes()) {
    portable = portable.split(prefix).join("");
  }
  return portable;
}

/**
 * The text of a type with the members of every union in it in an order of their own. The checker
 * orders a union's members by the order it created their types in, so the same union reads
 * `"verbose" | "debug"` in one program and `"debug" | "verbose"` in another that met `"debug"`
 * first, and the text then names the same declaration two ways. The members are ordered by their
 * own text, with `null` and `undefined` left last as the checker leaves them.
 *
 * Only text that holds a union is touched, and it is rebuilt from the checker's own type node. The
 * rebuilt text is used only when printing the node unchanged gives exactly the checker's text and
 * the reordered text is the same characters in another order; otherwise the checker's text stands.
 */
function stableUnionTypeText(ts, checker, typeToStringAt, args, text) {
  const type = args[0];
  if (!text || text.indexOf(" | ") < 0 || text.endsWith("...") || args.length > 3
    || !type || typeof type.flags !== "number") {
    return text;
  }
  try {
    const flags = args[2] === undefined
      ? ts.TypeFormatFlags.AllowUniqueESSymbolType | ts.TypeFormatFlags.UseAliasDefinedOutsideCurrentScope
      : args[2];
    const node = checker.typeToTypeNode(type, args[1], flags | ts.NodeBuilderFlags.IgnoreErrors);
    if (!node) {
      return text;
    }
    const printer = ts.createPrinter({ removeComments: true });
    const host = ts.createSourceFile("type.ts", "", ts.ScriptTarget.Latest);
    const print = (typeNode) => printer.printNode(ts.EmitHint.Unspecified, typeNode, host);
    if (print(node) !== text) {
      return text;
    }
    const ordered = ts.transform(node, [(context) => (root) => {
      const visit = (child) => {
        const visited = ts.visitEachChild(child, visit, context);
        return ts.isUnionTypeNode(visited) ? withOrderedMembers(ts, visited, print) : visited;
      };
      return ts.visitNode(root, visit);
    }]);
    const rewritten = print(ordered.transformed[0]);
    ordered.dispose();
    if (rewritten.indexOf("\n") >= 0 || rewritten.length !== text.length
      || rewritten.split("").sort().join("") !== text.split("").sort().join("")) {
      return text;
    }
    return rewritten;
  } catch (err) {
    return text;
  }
}

function withOrderedMembers(ts, union, print) {
  const isLast = (member) => member.kind === ts.SyntaxKind.UndefinedKeyword
    || member.kind === ts.SyntaxKind.NullKeyword
    || (ts.isLiteralTypeNode(member) && member.literal.kind === ts.SyntaxKind.NullKeyword);
  const leading = union.types.filter((member) => !isLast(member))
    .map((member) => ({ member, text: print(member) }))
    .sort((left, right) => (left.text < right.text ? -1 : left.text > right.text ? 1 : 0))
    .map((entry) => entry.member);
  const trailing = union.types.filter(isLast);
  return ts.factory.updateUnionTypeNode(union, ts.factory.createNodeArray([...leading, ...trailing]));
}

/** The checker, with `typeToString` answering portable type text. */
function withPortableTypeText(checker) {
  if (!checker || checker.portableTypeText) {
    return checker;
  }
  const typeToStringAt = checker.typeToString.bind(checker);
  checker.typeToString = (...args) => portableTypeText(
    stableUnionTypeText(state.ts, checker, typeToStringAt, args, typeToStringAt(...args)));
  checker.portableTypeText = true;
  return checker;
}

/**
 * Whether a symbol is a module that is a whole source file, as a namespace import (`import * as
 * dom from "./dom"`) or `typeof import("./dom")` names it. Its name is the file's absolute path,
 * never a declared name.
 */
function isSourceFileModule(symbol) {
  const declarations = symbol && symbol.declarations;
  return !!(declarations && declarations.length && state.ts.isSourceFile(declarations[0]));
}

function typeToString(checker, node) {
  if (!checker) {
    return "";
  }
  const type = checker.getTypeAtLocation(node);
  return checker.typeToString(type);
}

/**
 * The type annotation a declaration is written with, on one line, or null when it has none.
 *
 * The checker prints a resolved type, and for an alias such as `(typeof TOKENS)[number]` it drops
 * the alias name and writes out every literal. The annotation is what the author wrote.
 */
function writtenTypeText(node) {
  if (!node || !node.type) {
    return null;
  }
  const text = node.type.getText().replace(/\s+/g, " ").trim();
  return text || null;
}

/**
 * Whether a type is a literal, or a union of literals (optionally with `null` / `undefined`).
 * `normalizeReturnType` widens such a type, so a return type of this shape keeps that text.
 */
function isLiteralOrLiteralUnion(type) {
  const ts = state.ts;
  const literal = ts.TypeFlags.StringLiteral | ts.TypeFlags.NumberLiteral
    | ts.TypeFlags.BooleanLiteral | ts.TypeFlags.BigIntLiteral;
  const absent = ts.TypeFlags.Null | ts.TypeFlags.Undefined | ts.TypeFlags.Void;
  const members = type.isUnion && type.isUnion() && Array.isArray(type.types) ? type.types : [type];
  let sawLiteral = false;
  for (const member of members) {
    const flags = member.flags || 0;
    if (flags & literal) {
      sawLiteral = true;
    } else if (!(flags & absent)) {
      return false;
    }
  }
  return sawLiteral;
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
  const written = writtenTypeText(node);
  // A type predicate (`x is Foo`) is printed by the checker as `boolean`, and a literal return type
  // is widened by `normalizeReturnType`; both keep the text they have always had.
  if (written && !state.ts.isTypePredicateNode(node.type) && !isLiteralOrLiteralUnion(returnType)) {
    return written;
  }
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

function collectSymbolEntries(type, checker, entries, depth, withoutTypeArguments) {
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
      collectSymbolEntries(sub, checker, entries, depth + 1, withoutTypeArguments);
    }
    return;
  }
  if (!withoutTypeArguments && type.aliasTypeArguments) {
    for (const arg of type.aliasTypeArguments) {
      if (arg && arg.isThisType) {
        continue;
      }
      collectSymbolEntries(arg, checker, entries, depth + 1);
    }
  }
  if (!withoutTypeArguments && type.typeArguments) {
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

function collectDisplayNames(type, checker, names, depth, withoutTypeArguments) {
  if (!type) {
    return;
  }
  depth = depth || 0;
  if (depth >= MAX_TYPE_DEPTH) {
    return;
  }
  if (type.isUnionOrIntersection && type.isUnionOrIntersection()) {
    for (const sub of type.types) {
      collectDisplayNames(sub, checker, names, depth + 1, withoutTypeArguments);
    }
    return;
  }
  if (!withoutTypeArguments && type.aliasTypeArguments) {
    for (const arg of type.aliasTypeArguments) {
      if (arg && arg.isThisType) {
        continue;
      }
      collectDisplayNames(arg, checker, names, depth + 1);
    }
  }
  if (!withoutTypeArguments && type.typeArguments) {
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

function buildReferenceModelsFromType(type, checker, kind, withoutTypeArguments) {
  const references = [];
  const seen = new Set();
  const entries = [];
  collectSymbolEntries(type, checker, entries, 0, withoutTypeArguments);
  const displayNames = new Set();
  collectDisplayNames(type, checker, displayNames, 0, withoutTypeArguments);
  if (!entries.length) {
    const displayName = checker.typeToString(type);
    if (displayName && displayName !== "void") {
      references.push(externalReference(kind, displayName, isLostType(type, displayName)));
    }
    return references;
  }
  for (const entry of entries) {
    const actual = (entry.symbol.flags & state.ts.SymbolFlags.Alias)
      ? checker.getAliasedSymbol(entry.symbol)
      : entry.symbol;
    const decls = actual.declarations;
    const decl = decls && decls.length ? decls[0] : null;
    const fileName = decl && decl.getSourceFile ? decl.getSourceFile().fileName : null;
    if (isSourceFileModule(actual)) {
      // A whole module is referenced by its file: the target has no symbol name, and the module's
      // own name is derived from the file's place in the repository.
      if (fileName && isInternalFile(fileName)) {
        const normalized = path.resolve(fileName);
        const key = `${kind}|module|${normalized}`;
        if (!seen.has(key)) {
          references.push({ kind, external: false, target: { filePath: normalized, symbolName: null } });
          seen.add(key);
        }
        continue;
      }
    }
    const name = resolveSymbolName(entry.symbol, checker);
    if (isInternalSymbolName(name)) {
      continue;
    }
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
        references.push(externalReference(kind, displayName, !decl || isLostType(entry.type || type, displayName)));
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
      references.push(externalReference(kind, displayName, isLostType(null, displayName)));
      seen.add(key);
    }
  }
  return references;
}

/**
 * An external reference, flagged `unresolved` in a one-level analysis when the type behind it was
 * lost rather than found outside the repository. Outside a one-level analysis nothing is flagged,
 * so ordinary output is unchanged.
 */
function externalReference(kind, displayName, lost) {
  if (state.oneLevel && lost) {
    return { kind, external: true, unresolved: true, displayName };
  }
  return { kind, external: true, displayName };
}

const ANY_WORD = /(^|[^A-Za-z0-9_$])any([^A-Za-z0-9_$]|$)/;

/**
 * Whether a type is one a one-level program cannot tell apart from a lost one: `any`, alone or
 * nested (`any[]`, `Promise<any>`). With `noResolve`, a type declared past level one has no
 * declaration and collapses to `any` once combined with another type, so an explicitly written
 * `any` is flagged as well; the flag errs toward "not known".
 */
function isLostType(type, displayName) {
  if (type && type.flags !== undefined && (type.flags & state.ts.TypeFlags.Any)) {
    return true;
  }
  return !!displayName && ANY_WORD.test(displayName);
}

/** The type arguments a type is written with, through unions and intersections, without `this`. */
function writtenTypeArguments(type, depth) {
  const found = [];
  if (!type || (depth || 0) >= MAX_TYPE_DEPTH) {
    return found;
  }
  if (type.isUnionOrIntersection && type.isUnionOrIntersection()) {
    for (const sub of type.types) {
      found.push(...writtenTypeArguments(sub, (depth || 0) + 1));
    }
    return found;
  }
  for (const list of [type.aliasTypeArguments, type.typeArguments]) {
    for (const arg of list || []) {
      if (arg && !arg.isThisType) {
        found.push(arg);
      }
    }
  }
  return found;
}

/**
 * The references a heritage clause makes. Only the type named in the clause is extended or
 * implemented; the types written as its type arguments (`B<C>`) are ordinary references.
 */
function buildHeritageReferences(node, checker) {
  const references = [];
  if (!node.heritageClauses) {
    return references;
  }
  for (const clause of node.heritageClauses) {
    const kind = clause.token === state.ts.SyntaxKind.ExtendsKeyword ? "extends" : "implements";
    for (const typeNode of clause.types) {
      const type = checker.getTypeAtLocation(typeNode);
      references.push(...buildReferenceModelsFromType(type, checker, kind, true));
      for (const argument of writtenTypeArguments(type)) {
        references.push(...buildReferenceModelsFromType(argument, checker, "type"));
      }
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
// so it is emitted as an "annotation"-kind reference rather than a separate field. Its call
// arguments are read as `arguments`, see readDecoratorArguments. Decorators live outside
// `node.modifiers` in TypeScript 5, so they are read through `getDecorators`.
function buildDecoratorReferences(node, checker) {
  const references = [];
  if (!node || typeof state.ts.canHaveDecorators !== "function" || !state.ts.canHaveDecorators(node)) {
    return references;
  }
  const decorators = state.ts.getDecorators(node) || [];
  for (const decorator of decorators) {
    const name = decoratorTypeName(decorator.expression);
    if (name) {
      references.push({
        kind: "annotation",
        external: true,
        displayName: name,
        arguments: readDecoratorArguments(decorator.expression, checker)
      });
    }
  }
  return references;
}

// The arguments written on a decorator, in written order: an empty list for a decorator written
// without a call or with an empty one, null when its arguments cannot be read (a call whose callee
// is itself a call). A call whose only argument is an object literal of plain `name: literal`
// properties contributes those properties as its arguments, which is how option objects are
// written; any other object literal is an argument that is present and not read.
function readDecoratorArguments(expression, checker) {
  const ts = state.ts;
  if (!ts.isCallExpression(expression)) {
    return ts.isIdentifier(expression) || ts.isPropertyAccessExpression(expression) ? [] : null;
  }
  if (ts.isCallExpression(expression.expression)) {
    return null;
  }
  const args = expression.arguments || [];
  if (args.length === 1 && ts.isObjectLiteralExpression(args[0])) {
    const properties = readOptionProperties(args[0], checker);
    if (properties) {
      return properties;
    }
  }
  return args.map(argument => ({ name: null, value: readLiteralArgument(argument, checker) }));
}

function readOptionProperties(objectLiteral, checker) {
  const ts = state.ts;
  const properties = [];
  for (const property of objectLiteral.properties) {
    if (!ts.isPropertyAssignment(property)) {
      return null;
    }
    const key = property.name;
    let name = null;
    if (ts.isIdentifier(key) || ts.isStringLiteral(key) || ts.isNumericLiteral(key)) {
      name = key.text;
    }
    if (name === null) {
      return null;
    }
    properties.push({ name, value: readLiteralArgument(property.initializer, checker) });
  }
  return properties;
}

function readLiteralArgument(node, checker) {
  const ts = state.ts;
  while (ts.isParenthesizedExpression(node) || ts.isAsExpression(node) || ts.isSatisfiesExpression(node)
    || ts.isNonNullExpression(node)) {
    node = node.expression;
  }
  if (ts.isStringLiteral(node) || ts.isNoSubstitutionTemplateLiteral(node)) {
    return { kind: "string", text: node.text };
  }
  if (ts.isNumericLiteral(node)) {
    return { kind: "number", text: node.text };
  }
  if (node.kind === ts.SyntaxKind.TrueKeyword || node.kind === ts.SyntaxKind.FalseKeyword) {
    return { kind: "boolean", text: node.kind === ts.SyntaxKind.TrueKeyword ? "true" : "false" };
  }
  if (ts.isPrefixUnaryExpression(node) && ts.isNumericLiteral(node.operand)) {
    if (node.operator === ts.SyntaxKind.MinusToken) {
      return { kind: "number", text: "-" + node.operand.text };
    }
    if (node.operator === ts.SyntaxKind.PlusToken) {
      return { kind: "number", text: node.operand.text };
    }
  }
  if (ts.isBinaryExpression(node) && node.operatorToken.kind === ts.SyntaxKind.PlusToken) {
    const left = readLiteralArgument(node.left, checker);
    const right = readLiteralArgument(node.right, checker);
    if (left.kind === "string" && right.kind === "string") {
      return { kind: "string", text: left.text + right.text };
    }
    return { kind: "other" };
  }
  if (ts.isArrayLiteralExpression(node)) {
    return { kind: "array", elements: node.elements.map(element => readLiteralArgument(element, checker)) };
  }
  if (ts.isIdentifier(node) || ts.isPropertyAccessExpression(node)) {
    return readNamedArgument(node, checker);
  }
  return { kind: "other" };
}

// A name used as an argument is an enum member or a class when the type checker says so, and is not
// read otherwise: a constant, a variable and a function say nothing about their value here.
function readNamedArgument(node, checker) {
  const ts = state.ts;
  let symbol;
  try {
    symbol = checker.getSymbolAtLocation(ts.isPropertyAccessExpression(node) ? node.name : node);
    if (symbol && (symbol.flags & ts.SymbolFlags.Alias)) {
      symbol = checker.getAliasedSymbol(symbol);
    }
  } catch (err) {
    return { kind: "other" };
  }
  const declaration = symbol && symbol.declarations && symbol.declarations.length ? symbol.declarations[0] : null;
  if (!declaration) {
    return { kind: "other" };
  }
  const text = node.getText().replace(/\s+/g, "");
  if (ts.isEnumMember(declaration)) {
    return { kind: "enum", text };
  }
  if (ts.isClassDeclaration(declaration) && declaration.name) {
    const fileName = declaration.getSourceFile().fileName;
    const target = fileName && isInternalFile(fileName)
      ? { filePath: path.resolve(fileName), symbolName: declaration.name.text }
      : null;
    return { kind: "class", text: declaration.name.text, target };
  }
  return { kind: "other" };
}

/**
 * The module-level function a call names, when it names one in this repository.
 *
 * A call's own type is the type it evaluates to -- `number`, for a function returning one -- so
 * building references from that alone recorded what a call produced and never what it called. One
 * module-level function calling another therefore yielded no edge at all, which is the ordinary
 * shape of TypeScript written as functions rather than classes: such a codebase came back with no
 * relations between its files.
 *
 * Deliberately limited to a function declared at the top level of its file. That is the one callee
 * whose component name is derivable from its declaration -- `<package>.<module>.<name>`, which is
 * how a top-level function is named here. A method's name would have to carry its signature and its
 * declaring type, and guessing at it would point the edge at a component that does not exist.
 */
function buildCalleeReference(node, checker) {
  const expr = node.expression;
  if (!expr) {
    return null;
  }
  let symbol;
  try {
    symbol = checker.getSymbolAtLocation(expr);
  } catch (err) {
    return null;
  }
  if (!symbol) {
    return null;
  }
  let actual = symbol;
  if (symbol.flags & state.ts.SymbolFlags.Alias) {
    try {
      actual = checker.getAliasedSymbol(symbol);
    } catch (err) {
      actual = symbol;
    }
  }
  const name = actual.getName ? actual.getName() : null;
  if (!name || isInternalSymbolName(name)) {
    return null;
  }
  const declarations = actual.declarations || [];
  const declaration = declarations.length ? declarations[0] : null;
  if (!declaration || !declaration.getSourceFile) {
    return null;
  }
  if (!state.ts.isFunctionDeclaration(declaration)
    || !declaration.parent
    || !state.ts.isSourceFile(declaration.parent)) {
    return null;
  }
  const fileName = declaration.getSourceFile().fileName;
  if (!fileName || !isInternalFile(fileName)) {
    return null;
  }
  return {
    kind: "type",
    external: false,
    target: { filePath: path.resolve(fileName), symbolName: name }
  };
}

function buildCallReferences(node, checker) {
  const references = [];
  if (!node) {
    return references;
  }
  const callType = checker.getTypeAtLocation(node);
  references.push(...buildReferenceModelsFromType(callType, checker, "type"));
  const callee = buildCalleeReference(node, checker);
  if (callee) {
    references.push(callee);
  }
  const expr = node.expression;
  if (state.ts.isPropertyAccessExpression(expr) || state.ts.isElementAccessExpression(expr)) {
    const receiverType = checker.getTypeAtLocation(expr.expression);
    references.push(...buildReferenceModelsFromType(receiverType, checker, "type"));
    const libraryMethod = buildLibraryMethodReference(node, checker);
    if (libraryMethod) {
      references.push(libraryMethod);
    }
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
  let references = mergeReferences(
    buildReferenceModelsFromType(variableType, checker, "type"),
    writtenAliasReferences(declaration.type, checker)
  );
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

/**
 * The types a function value is declared with: its parameters' and its return type, and the type
 * aliases they are written as. A `const` holding an arrow function, or cast to a function type, is
 * a function as much as a declaration is, and its signature is what it depends on.
 */
function functionValueSignatureReferences(declaration, checker) {
  const references = [];
  if (state.shallowBody) {
    return references;
  }
  const ts = state.ts;
  let type;
  try {
    type = checker.getTypeAtLocation(declaration);
  } catch (err) {
    return references;
  }
  const signatures = type ? checker.getSignaturesOfType(type, ts.SignatureKind.Call) : [];
  for (const signature of signatures) {
    for (const parameter of signature.getParameters()) {
      const parameterType = checker.getTypeOfSymbolAtLocation(parameter, declaration);
      references.push(...buildReferenceModelsFromType(parameterType, checker, "type"));
    }
    references.push(...buildReferenceModelsFromType(checker.getReturnTypeOfSignature(signature), checker, "type"));
    const predicate = checker.getTypePredicateOfSignature(signature);
    if (predicate && predicate.type) {
      references.push(...buildReferenceModelsFromType(predicate.type, checker, "type"));
    }
  }
  // The signature as written: on the function itself, or on the function type it is cast to.
  let written = declaration.initializer;
  while (written && (ts.isParenthesizedExpression(written) || ts.isAsExpression(written)
      || ts.isSatisfiesExpression(written) || ts.isTypeAssertionExpression(written))) {
    if (ts.isAsExpression(written) || ts.isSatisfiesExpression(written) || ts.isTypeAssertionExpression(written)) {
      references.push(...writtenAliasReferences(written.type, checker));
    }
    written = written.expression;
  }
  if (written && (ts.isArrowFunction(written) || ts.isFunctionExpression(written))) {
    for (const parameter of written.parameters) {
      references.push(...writtenAliasReferences(parameter.type, checker));
    }
    references.push(...writtenAliasReferences(written.type, checker));
  }
  return references;
}

function buildTopLevelVariableModel(declaration, statement, checker) {
  const model = buildLocalVariableModel(declaration, checker);
  if (!model) {
    return null;
  }
  model.kind = "moduleField";
  model.references = mergeReferences(model.references, functionValueSignatureReferences(declaration, checker));
  model.type = writtenTypeText(declaration) || model.type;
  if (declaration.initializer) {
    // What the initializer calls or constructs, as for a function body, whether the binding holds
    // a function or any other value.
    model.references = mergeReferences(
      model.references,
      collectBodyDetails(declaration.initializer, checker, declaration).references,
      collectValueReferences(declaration.initializer, checker, declaration)
    );
  }
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

// Whether a declaration is one this program models as a component of its own: a class, an
// enum, a function or a binding declared at the top level of a module.
function isModelledValueDeclaration(declaration) {
  const ts = state.ts;
  if (!declaration || !declaration.parent) {
    return false;
  }
  if (ts.isClassDeclaration(declaration) || ts.isEnumDeclaration(declaration)) {
    return !!declaration.name && ts.isSourceFile(declaration.parent);
  }
  if (ts.isFunctionDeclaration(declaration)) {
    return !!declaration.name && ts.isSourceFile(declaration.parent);
  }
  if (ts.isVariableDeclaration(declaration)) {
    const list = declaration.parent;
    const statement = list && list.parent;
    return ts.isIdentifier(declaration.name)
      && !!statement
      && ts.isVariableStatement(statement)
      && !!statement.parent
      && ts.isSourceFile(statement.parent);
  }
  return false;
}

// The references a piece of code makes by naming a declaration as a value: a class or function
// placed in an array or object literal, a constant used in an expression, a function passed as an
// argument, a component named as a JSX tag, a function called. A declaration of the repository is
// recorded as the component it is. A symbol of a package the code imports is recorded by name, as
// `package:Symbol`. A name that resolves to a local, a parameter, a member, or the language's own
// library is not recorded.
function collectValueReferences(initializer, checker, owner) {
  const references = [];
  if (!initializer || state.shallowBody) {
    return references;
  }
  const ts = state.ts;
  function visit(node) {
    if (ts.isTypeNode(node)) {
      return;
    }
    if (ts.isIdentifier(node)) {
      const reference = buildValueReference(node, checker, owner);
      if (reference) {
        references.push(reference);
      }
    }
    if (ts.isPropertyAccessExpression(node)) {
      const member = buildNamespaceMemberReference(node, checker);
      if (member) {
        references.push(member);
      }
    }
    ts.forEachChild(node, visit);
  }
  visit(initializer);
  return references;
}

function externalNamedReference(packageName, symbolName) {
  return { kind: "type", external: true, displayName: `${packageName}:${symbolName}` };
}

/** The module specifier a symbol was imported with, or null where it was not imported. */
function importedFrom(symbol) {
  const ts = state.ts;
  const declaration = symbol && symbol.declarations && symbol.declarations[0];
  let node = declaration;
  while (node && !ts.isImportDeclaration(node) && !ts.isImportEqualsDeclaration(node)
      && !ts.isVariableDeclaration(node)) {
    node = node.parent;
  }
  if (!node) {
    return null;
  }
  if (ts.isImportDeclaration(node)) {
    return ts.isStringLiteralLike(node.moduleSpecifier) ? node.moduleSpecifier.text : null;
  }
  if (ts.isImportEqualsDeclaration(node)) {
    const reference = node.moduleReference;
    return ts.isExternalModuleReference(reference) && ts.isStringLiteralLike(reference.expression)
      ? reference.expression.text
      : null;
  }
  return requiredModule(node.initializer);
}

/** The module `require("x")` or `(await) import("x")` loads, whatever wraps it, or null. */
function requiredModule(expression) {
  const ts = state.ts;
  let node = expression;
  while (node && (ts.isAwaitExpression(node) || ts.isParenthesizedExpression(node)
      || ts.isAsExpression(node) || ts.isNonNullExpression(node))) {
    node = node.expression;
  }
  if (node && ts.isCallExpression(node) && node.arguments.length === 1
      && ts.isStringLiteralLike(node.arguments[0])) {
    const callee = node.expression;
    if (callee.kind === ts.SyntaxKind.ImportKeyword
        || (ts.isIdentifier(callee) && callee.text === "require")) {
      return node.arguments[0].text;
    }
  }
  return null;
}

/** The package a specifier written in an import names: `@scope/pkg/sub` is `@scope/pkg`. */
function packageOfSpecifier(specifier) {
  if (!specifier || specifier.startsWith(".") || specifier.startsWith("/") || specifier.startsWith("node:")) {
    return specifier && specifier.startsWith("node:") ? specifier : null;
  }
  const parts = specifier.split("/");
  return specifier.startsWith("@") ? parts.slice(0, 2).join("/") : parts[0];
}

/** The package a file under `node_modules` belongs to, or null where it belongs to none. */
function packageOfFile(fileName) {
  const marker = `${path.sep}node_modules${path.sep}`;
  const at = fileName.lastIndexOf(marker);
  if (at < 0) {
    return null;
  }
  const parts = fileName.slice(at + marker.length).split(path.sep);
  if (parts[0] === "@types" && parts.length > 1) {
    // `@types/scope__name` types `@scope/name`.
    return parts[1].includes("__") ? `@${parts[1].replace("__", "/")}` : parts[1];
  }
  return parts[0].startsWith("@") ? parts.slice(0, 2).join("/") : parts[0];
}

/** Whether a file is one of the compiler's own library declarations. */
function isCompilerLibraryFile(fileName) {
  return /[\\/]typescript[\\/]lib[\\/]lib\.[^\\/]*\.d\.ts$/.test(fileName)
    || /^lib\.[^\\/]*\.d\.ts$/.test(path.basename(fileName));
}

/**
 * A reference to a symbol of a package: the identifier names something the file imported from a
 * package the repository does not hold, whether or not the package has declarations.
 */
function buildExternalValueReference(identifier, imported, actual) {
  const packageName = packageOfSpecifier(imported);
  if (!packageName) {
    return null;
  }
  if (actual && actual.declarations && actual.declarations.length) {
    const fileName = actual.declarations[0].getSourceFile().fileName;
    if (isInternalFile(fileName) || isCompilerLibraryFile(fileName)) {
      return null;
    }
  }
  let name = actual && actual.getName ? actual.getName() : identifier.text;
  if (!name || name === "default" || name === "export=" || name === "unknown" || isInternalSymbolName(name)) {
    name = identifier.text;
  }
  return externalNamedReference(packageName, name);
}

/** `ns.member` where `ns` is a namespace import of a package: a reference to `package:member`. */
function buildNamespaceMemberReference(node, checker) {
  const ts = state.ts;
  if (!ts.isIdentifier(node.expression)) {
    return null;
  }
  let symbol;
  try {
    symbol = checker.getSymbolAtLocation(node.expression);
  } catch (err) {
    return null;
  }
  const declaration = symbol && symbol.declarations && symbol.declarations[0];
  if (!declaration || !(ts.isNamespaceImport(declaration) || ts.isImportEqualsDeclaration(declaration))) {
    return null;
  }
  const packageName = packageOfSpecifier(importedFrom(symbol));
  if (!packageName) {
    return null;
  }
  let target = null;
  try {
    target = checker.getAliasedSymbol(symbol);
  } catch (err) {
    target = null;
  }
  const targetFile = target && target.declarations && target.declarations.length
    ? target.declarations[0].getSourceFile().fileName
    : null;
  if (targetFile && (isInternalFile(targetFile) || isCompilerLibraryFile(targetFile))) {
    return null;
  }
  return externalNamedReference(packageName, node.name.text);
}

/**
 * A call to a method of a library object: the object's type, in its package, and the method. A
 * method of the object's own class would be a member of a declaration this program models; one of a
 * class a package declares is not, and was otherwise reduced to the type the call returns.
 */
function buildLibraryMethodReference(callNode, checker) {
  const ts = state.ts;
  const callee = callNode.expression;
  if (!ts.isPropertyAccessExpression(callee)) {
    return null;
  }
  let type;
  try {
    type = checker.getTypeAtLocation(callee.expression);
  } catch (err) {
    return null;
  }
  const symbol = type && (type.getSymbol ? type.getSymbol() : null);
  const declaration = symbol && symbol.declarations && symbol.declarations[0];
  if (!declaration || !declaration.getSourceFile) {
    return null;
  }
  const fileName = declaration.getSourceFile().fileName;
  const packageName = packageOfFile(fileName);
  const typeName = symbol.getName();
  if (!packageName || isCompilerLibraryFile(fileName) || !typeName || isInternalSymbolName(typeName)
      || typeName === "__type") {
    return null;
  }
  return externalNamedReference(packageName, `${typeName}.${callee.name.text}`);
}

function buildValueReference(identifier, checker, owner) {
  const ts = state.ts;
  const parent = identifier.parent;
  if (parent && ts.isPropertyAccessExpression(parent) && parent.name === identifier) {
    return null;
  }
  let symbol;
  let imported = null;
  let boundByRequire = false;
  try {
    symbol = parent && ts.isShorthandPropertyAssignment(parent) && parent.name === identifier
      ? checker.getShorthandAssignmentValueSymbol(parent)
      : checker.getSymbolAtLocation(identifier);
    if (symbol && (symbol.flags & ts.SymbolFlags.Alias)) {
      imported = importedFrom(symbol);
      const named = symbol.declarations && symbol.declarations[0];
      const wholeModule = named && (ts.isNamespaceImport(named) || ts.isImportEqualsDeclaration(named));
      symbol = checker.getAliasedSymbol(symbol);
      if (wholeModule) {
        // `ns` alone binds a module, which is named by what is read from it (see
        // `buildNamespaceMemberReference`).
        return null;
      }
    } else if (symbol && symbol.declarations && symbol.declarations.length && !imported) {
      // A binding made by `const { x } = require("pkg")`.
      const declaration = symbol.declarations[0];
      if (ts.isBindingElement(declaration) || ts.isVariableDeclaration(declaration)) {
        let variable = declaration;
        while (variable && !ts.isVariableDeclaration(variable)) {
          variable = variable.parent;
        }
        const loaded = variable && variable.initializer ? requiredModule(variable.initializer) : null;
        if (loaded && ts.isBindingElement(declaration)) {
          imported = loaded;
          boundByRequire = true;
        }
      }
    }
  } catch (err) {
    return null;
  }
  const declarations = symbol && symbol.declarations ? symbol.declarations : [];
  if (imported && packageOfSpecifier(imported) && (boundByRequire || !declarations.length
      || !declarations.some((d) => isInternalFile(d.getSourceFile().fileName)))) {
    return buildExternalValueReference(identifier, imported, boundByRequire ? null : symbol);
  }
  if (!declarations.length) {
    return null;
  }
  const declaration = declarations.find(isModelledValueDeclaration);
  if (!declaration || declaration === owner) {
    return null;
  }
  const fileName = declaration.getSourceFile().fileName;
  const name = declaration.name.getText();
  if (!fileName || !name || isInternalSymbolName(name) || !isInternalFile(fileName)) {
    return null;
  }
  return {
    kind: "type",
    external: false,
    target: { filePath: path.resolve(fileName), symbolName: name }
  };
}

function collectBodyDetails(body, checker, owner) {
  const references = [];
  const locals = [];
  if (state.shallowBody) {
    // A boundary file of a one-level analysis: its bodies reach into types past level one, which
    // its program does not hold, so they are not read at all.
    return { references, locals };
  }
  // A function expression or arrow function written inside the body is part of it: what it calls
  // or constructs is a reference of the declaration that holds it. The variables it declares are
  // its own, not members of that declaration.
  let nestedDepth = 0;
  function visit(node) {
    const nested = state.ts.isFunctionLike(node) && node !== body;
    if (!nested && nestedDepth === 0 && state.ts.isVariableDeclaration(node)) {
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
    if (nested) {
      nestedDepth += 1;
    }
    state.ts.forEachChild(node, visit);
    if (nested) {
      nestedDepth -= 1;
    }
  }
  if (body) {
    visit(body);
    references.push(...collectValueReferences(body, checker, owner || null));
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
      references: mergeReferences(
        buildReferenceModelsFromType(paramType, checker, "type"),
        writtenAliasReferences(param.type, checker)
      )
    });
  }
  return models;
}

/**
 * A member's name as a reader says it: the text of a quoted or computed name, without the brackets
 * and quotes it is written with. `["~standard"]` is `~standard` and `[Symbol.iterator]` is
 * `Symbol.iterator`.
 */
function memberName(nameNode) {
  const ts = state.ts;
  if (ts.isComputedPropertyName(nameNode)) {
    const expression = nameNode.expression;
    if (ts.isStringLiteralLike(expression) || ts.isNumericLiteral(expression)) {
      return expression.text;
    }
    return expression.getText();
  }
  if (ts.isStringLiteralLike(nameNode) || ts.isNumericLiteral(nameNode)) {
    return nameNode.text;
  }
  return nameNode.getText();
}

function buildFieldModel(node, checker) {
  if (!node.name) {
    return null;
  }
  const fieldType = checker.getTypeAtLocation(node);
  return {
    kind: "field",
    name: memberName(node.name),
    type: writtenTypeText(node) || typeToString(checker, node),
    implementationHash: stableImplementationHash(node.getText()),
    modifiers: collectModifiers(state.ts, node),
    jsDoc: getJsDoc(node),
    references: mergeReferences(
      buildReferenceModelsFromType(fieldType, checker, "type"),
      writtenAliasReferences(node.type, checker),
      node.initializer ? collectBodyDetails(node.initializer, checker).references : [],
      collectValueReferences(node.initializer, checker, null),
      buildDecoratorReferences(node, checker)
    )
  };
}

function buildMethodModel(node, checker, kindLabel) {
  const name = kindLabel === "constructor" ? "constructor" : memberName(node.name);
  const signature = buildSignature(name, node.parameters || [], checker);
  const returnTypeObject = kindLabel === "constructor" ? null : getReturnTypeObject(checker, node);
  const bodyDetails = collectBodyDetails(node.body, checker, kindLabel === "function" ? node : null);
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
      writtenAliasReferences(node.type, checker),
      typePredicateReferences(node, checker),
      typeParameterReferences(node, checker),
      bodyDetails.references,
      buildDecoratorReferences(node, checker)
    )
  };
}

function buildAccessorModel(node, checker, accessorKind) {
  const name = memberName(node.name);
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
    references: mergeReferences(bodyDetails.references, buildDecoratorReferences(node, checker))
  };
}

function buildEnumModel(node) {
  const members = node.members.map((member) => ({
    kind: "enumMember",
    name: memberName(member.name),
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
    references: mergeReferences(buildHeritageReferences(node, checker), typeParameterReferences(node, checker))
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

/** Whether a constructor parameter also declares a field: it carries an accessibility or `readonly` modifier. */
function isParameterProperty(parameter) {
  const ts = state.ts;
  return ts.isIdentifier(parameter.name) && (parameter.modifiers || []).some((modifier) =>
    modifier.kind === ts.SyntaxKind.PublicKeyword
    || modifier.kind === ts.SyntaxKind.PrivateKeyword
    || modifier.kind === ts.SyntaxKind.ProtectedKeyword
    || modifier.kind === ts.SyntaxKind.ReadonlyKeyword
    || modifier.kind === ts.SyntaxKind.OverrideKeyword);
}

function buildClassModel(node, checker, unresolvedBases) {
  const members = [];
  for (const member of node.members) {
    if (state.ts.isConstructorDeclaration(member)) {
      if (member.body) {
        members.push(buildMethodModel(member, checker, "constructor"));
      }
      // `constructor(public readonly timeout: number)` declares a field as well as a parameter.
      for (const parameter of member.parameters) {
        if (isParameterProperty(parameter)) {
          const fieldModel = buildFieldModel(parameter, checker);
          if (fieldModel) {
            members.push(fieldModel);
          }
        }
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
      typeParameterReferences(node, checker),
      buildDecoratorReferences(node, checker)
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

/** A reference to the repository type alias a type reference names, or null where it names none. */
function typeAliasReference(node, checker) {
  const nameNode = state.ts.isTypeReferenceNode(node) ? node.typeName : node.expression;
  let symbol;
  try {
    symbol = nameNode ? checker.getSymbolAtLocation(nameNode) : null;
    if (symbol && (symbol.flags & state.ts.SymbolFlags.Alias)) {
      symbol = checker.getAliasedSymbol(symbol);
    }
  } catch (err) {
    return null;
  }
  if (!symbol || !(symbol.flags & state.ts.SymbolFlags.TypeAlias)) {
    return null;
  }
  const declaration = symbol.declarations && symbol.declarations.length ? symbol.declarations[0] : null;
  const fileName = declaration && declaration.getSourceFile ? declaration.getSourceFile().fileName : null;
  if (!fileName || !isInternalFile(fileName) || !declaration.parent
    || !state.ts.isSourceFile(declaration.parent)) {
    return null;
  }
  return { kind: "type", external: false, target: { filePath: path.resolve(fileName), symbolName: symbol.getName() } };
}

/**
 * A reference to the repository class, interface, enum or type alias a type reference names, as
 * written, or null where it names none. Unlike the type the name resolves to, the written name is
 * not lost when the type is an instantiation of a generic or has been given a name of its own.
 */
function declaredTypeReference(node, checker) {
  const nameNode = state.ts.isTypeReferenceNode(node) ? node.typeName : node.expression;
  let symbol;
  try {
    symbol = nameNode ? checker.getSymbolAtLocation(nameNode) : null;
    if (symbol && (symbol.flags & state.ts.SymbolFlags.Alias)) {
      symbol = checker.getAliasedSymbol(symbol);
    }
  } catch (err) {
    return null;
  }
  const kinds = state.ts.SymbolFlags.Class | state.ts.SymbolFlags.Interface
    | state.ts.SymbolFlags.Enum | state.ts.SymbolFlags.TypeAlias;
  if (!symbol || !(symbol.flags & kinds)) {
    return null;
  }
  const declaration = symbol.declarations && symbol.declarations.length ? symbol.declarations[0] : null;
  const fileName = declaration && declaration.getSourceFile ? declaration.getSourceFile().fileName : null;
  if (!fileName || !isInternalFile(fileName) || !declaration.parent
    || !state.ts.isSourceFile(declaration.parent)) {
    return null;
  }
  return { kind: "type", external: false, target: { filePath: path.resolve(fileName), symbolName: symbol.getName() } };
}

/**
 * The type aliases a type is written with: `Tracked` in `f(): Tracked`, however deep in a union,
 * an array or a type argument. The type an alias stands for is what the checker reports, and for a
 * union or an intersection that is its members, so the name the code was written against would
 * otherwise be lost.
 */
function writtenAliasReferences(typeNode, checker) {
  const references = [];
  if (!typeNode || state.shallowBody) {
    return references;
  }
  function visit(child) {
    if (state.ts.isTypeReferenceNode(child) || state.ts.isExpressionWithTypeArguments(child)) {
      const named = typeAliasReference(child, checker);
      if (named) {
        references.push(named);
      }
    }
    state.ts.forEachChild(child, visit);
  }
  visit(typeNode);
  return references;
}

/**
 * The type a type predicate narrows to: `ApiError` in `isApiError(e): e is ApiError`. The function
 * returns a boolean, and the type it tests for is what it is about.
 */
function typePredicateReferences(node, checker) {
  const returned = node && node.type;
  if (!returned || !state.ts.isTypePredicateNode(returned) || !returned.type || state.shallowBody) {
    return [];
  }
  return mergeReferences(
    buildReferenceModelsFromType(checker.getTypeAtLocation(returned.type), checker, "type"),
    writtenAliasReferences(returned.type, checker)
  );
}

/**
 * What the constraints of a declaration's type parameters name: `Ctx` in `f<T extends Ctx>`. A
 * constraint bounds what a caller may pass, so the declaration depends on the type it names.
 */
function typeParameterReferences(node, checker) {
  const references = [];
  if (!node || !node.typeParameters || state.shallowBody) {
    return references;
  }
  function visit(child) {
    if (state.ts.isTypeReferenceNode(child) || state.ts.isExpressionWithTypeArguments(child)) {
      const declared = typeAliasReference(child, checker) || declaredTypeReference(child, checker);
      if (declared) {
        references.push(declared);
      }
    }
    state.ts.forEachChild(child, visit);
  }
  for (const parameter of node.typeParameters) {
    if (parameter.constraint) {
      visit(parameter.constraint);
      const constraint = checker.getTypeAtLocation(parameter.constraint);
      if (!(constraint.flags & state.ts.TypeFlags.TypeParameter)) {
        references.push(...buildReferenceModelsFromType(constraint, checker, "type"));
      }
    }
  }
  return references;
}

/**
 * A type alias: the name a module gives a type expression. Its references are the named types the
 * expression is written with, read from the expression itself so that a type nested inside an
 * object literal, a mapped type or a function type is found as well.
 */
function buildTypeAliasModel(node, checker) {
  const references = [];
  function visit(child) {
    if (state.ts.isTypeReferenceNode(child) || state.ts.isExpressionWithTypeArguments(child)) {
      const named = typeAliasReference(child, checker);
      const referenced = checker.getTypeAtLocation(child);
      if (named) {
        // Another alias, named as written: resolving its type would expand a union or literal
        // alias into its members and lose the name the code was written against.
        references.push(named);
      } else if (declaredTypeReference(child, checker)) {
        // A generic instantiation such as `Shape<"card">` resolves to a type the alias itself
        // names, so the declaration it instantiates is taken from the written name.
        references.push(declaredTypeReference(child, checker));
        const referenced = checker.getTypeAtLocation(child);
        if (!(referenced.flags & state.ts.TypeFlags.TypeParameter)) {
          references.push(...buildReferenceModelsFromType(referenced, checker, "type"));
        }
      } else if (!(referenced.flags & state.ts.TypeFlags.TypeParameter)) {
        // The alias's own type parameters name no declaration a consumer could look up.
        references.push(...buildReferenceModelsFromType(referenced, checker, "type"));
      }
    }
    state.ts.forEachChild(child, visit);
  }
  if (node.type) {
    visit(node.type);
  }
  const typeParameters = node.typeParameters && node.typeParameters.length
    ? "<" + node.typeParameters.map((parameter) => parameter.name.text).join(", ") + ">"
    : "";
  return {
    kind: "typeAlias",
    name: node.name.text,
    signature: node.name.text + typeParameters,
    implementationHash: stableImplementationHash(node.getText()),
    modifiers: collectModifiers(state.ts, node),
    jsDoc: getJsDoc(node),
    members: [],
    references: mergeReferences(references, typeParameterReferences(node, checker))
  };
}

/**
 * Marks the declarations an export list names as exported: `export { a, b as c }` at the end of a
 * file, and `export default a`. A declaration is on the module's public surface however the export
 * is written, so it carries the modifiers an inline `export` gives it.
 */
function withListedExports(ts, sourceFile, declarations) {
  const exported = new Set();
  let defaultName = null;
  for (const statement of sourceFile.statements) {
    if (ts.isExportDeclaration(statement) && !statement.moduleSpecifier && !statement.isTypeOnly
        && statement.exportClause && ts.isNamedExports(statement.exportClause)) {
      for (const element of statement.exportClause.elements) {
        exported.add((element.propertyName || element.name).text);
      }
    } else if (ts.isExportAssignment(statement) && !statement.isExportEquals
        && ts.isIdentifier(statement.expression)) {
      exported.add(statement.expression.text);
      defaultName = statement.expression.text;
    }
  }
  if (!exported.size) {
    return declarations;
  }
  for (const declaration of declarations) {
    if (!declaration || !exported.has(declaration.name)) {
      continue;
    }
    const modifiers = declaration.modifiers || (declaration.modifiers = []);
    if (!modifiers.includes("export")) {
      modifiers.push("export");
    }
    if (declaration.name === defaultName && !modifiers.includes("default")) {
      modifiers.push("default");
    }
    if (!modifiers.includes("public")) {
      modifiers.push("public");
    }
  }
  return declarations;
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
    if (ts.isTypeAliasDeclaration(node)) {
      declarations.push(buildTypeAliasModel(node, checker));
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
  return withoutShadowedAliases(withListedExports(ts, sourceFile, declarations.filter(Boolean)));
}

/**
 * A module may declare a value and a type alias of one name (`const Kind = 0; type Kind = typeof
 * Kind;`), which TypeScript keeps apart and a component model cannot: both would take the module's
 * one name for it. The value stands for the name, and the alias is left out.
 */
function withoutShadowedAliases(declarations) {
  const values = new Set(declarations.filter((d) => d.kind !== "typeAlias").map((d) => d.name));
  return declarations.filter((d) => d.kind !== "typeAlias" || !values.has(d.name));
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
  const configs = expandProjectReferences(ts, findProjectConfigs(ts, repoRoot));
  const mappedWorkspaces = workspacePaths(repoRoot);
  state.workspacePaths = mappedWorkspaces;
  let parsedConfigs = [];
  let invalidConfigs = [];
  let configDiagnostics = [];
  try {
    const result = parseConfigs(ts, repoRoot, configs);
    parsedConfigs = result.configs || [];
    invalidConfigs = result.invalidConfigs || [];
    configDiagnostics = result.configDiagnostics || [];
  } catch (err) {
    const errObj = new Error("CONFIG_PARSE_FAILED");
    errObj.code = ERROR_CODES.CONFIG_PARSE_FAILED;
    errObj.data = err.message;
    throw errObj;
  }
  const writtenConfigCount = parsedConfigs.length;
  const unowned = params.modelUnownedFiles === false
    ? []
    : programsForUnownedFiles(ts, repoRoot, parsedConfigs);
  parsedConfigs = parsedConfigs.concat(unowned);
  if (!parsedConfigs.length) {
    const errObj = new Error(configs.length ? "CONFIG_PARSE_FAILED" : "NO_TSCONFIG");
    errObj.code = configs.length ? ERROR_CODES.CONFIG_PARSE_FAILED : ERROR_CODES.NO_TSCONFIG;
    if (configs.length) {
      errObj.data = invalidConfigs.map((entry) => entry.configPath);
    }
    throw errObj;
  }
  const fileMap = new Map();
  parsedConfigs.forEach((config, index) => {
    if (config.unowned) {
      // Found by the directory it sits under, after every config the repository wrote has been
      // asked: a file none of them names may still be in one of their programs by an import.
      return;
    }
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
    workspacePaths: mappedWorkspaces,
    ts,
    configs: parsedConfigs,
    fileMap,
    programCache: new Map(),
    programOrder: [],
    maxPrograms: resolveMaxPrograms(params),
    oneLevel: null,
    shallowBody: false,
    sourceFiles: new Map(),
    settingsKeys: new WeakMap(),
    documentRegistry: null,
    programBuilds: 0
  };
  const fileCount = parsedConfigs.reduce((sum, config) => sum + config.rootNames.length, 0);
  return {
    tsVersion: ts.version || "",
    configCount: writtenConfigCount,
    fileCount,
    invalidConfigCount: invalidConfigs.length,
    invalidConfigs,
    configDiagnostics,
    unownedProgramCount: unowned.length,
    unownedFileCount: unowned.reduce((sum, config) => sum + config.rootNames.length, 0),
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
  const checker = entryInfo.entry.checker || withPortableTypeText(entryInfo.entry.program.getTypeChecker());
  const unresolvedBases = [];
  let declarations;
  state.shallowBody = !!(state.oneLevel && params.boundary);
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
  state.shallowBody = false;
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
/**
 * The repository file a module specifier written in the file resolves to, or null where it does
 * not resolve to one.
 */
function resolveImportedFile(specifierNode, specifier, source, checker) {
  let resolvedFile = null;
  try {
    const moduleSymbol = checker.getSymbolAtLocation(specifierNode);
    const declaration = moduleSymbol && moduleSymbol.declarations && moduleSymbol.declarations[0];
    if (declaration && declaration.getSourceFile) {
      const fileName = declaration.getSourceFile().fileName;
      if (fileName && isInternalFile(fileName)) resolvedFile = fileName;
    }
  } catch (err) {
    resolvedFile = null;
  }
  if (!resolvedFile) {
    // A one-level program does not load what a boundary file imports, and the checker finds no
    // module behind the argument of a require() call, so it cannot see the module; its path is
    // still known without loading it.
    resolvedFile = resolveInternalModule(specifier, source.fileName);
  }
  return resolvedFile;
}

function collectImports(ts, source, checker) {
  const results = [];
  for (const statement of source.statements) {
    if (ts.isImportEqualsDeclaration(statement)
        && ts.isExternalModuleReference(statement.moduleReference)
        && ts.isStringLiteralLike(statement.moduleReference.expression)) {
      const specifierNode = statement.moduleReference.expression;
      results.push({
        module: specifierNode.text,
        filePath: resolveImportedFile(specifierNode, specifierNode.text, source, checker),
        symbolName: null,
        namespace: true
      });
      continue;
    }
    if (!ts.isImportDeclaration(statement) || !statement.moduleSpecifier) continue;
    const specifier = statement.moduleSpecifier.text;
    if (!specifier) continue;

    const resolvedFile = resolveImportedFile(statement.moduleSpecifier, specifier, source, checker);

    const names = [];
    const clause = statement.importClause;
    if (clause) {
      if (clause.name) names.push(clause.name.text);
      const bindings = clause.namedBindings;
      if (bindings) {
        if (ts.isNamedImports(bindings)) {
          for (const element of bindings.elements) names.push(element.name.text);
        } else if (bindings.name) {
          // `import * as ns` binds the whole module, not a declaration named `ns` in it.
          results.push({ module: specifier, filePath: resolvedFile, symbolName: null, namespace: true });
        }
      }
    }
    if (names.length === 0 && clause && clause.namedBindings && !ts.isNamedImports(clause.namedBindings)) {
      continue;
    }
    if (names.length === 0) {
      results.push({ module: specifier, filePath: resolvedFile, symbolName: null });
      continue;
    }
    for (const name of names) {
      results.push({ module: specifier, filePath: resolvedFile, symbolName: name });
    }
  }
  collectLoadedModules(ts, source, checker, results);
  return results;
}

/**
 * Modules a file loads from inside its code rather than in an import statement: `import("pkg")` and
 * `require("pkg")`, wherever they appear. Each binds the whole module.
 */
function collectLoadedModules(ts, source, checker, results) {
  const seen = new Set(results.filter((entry) => entry.namespace).map((entry) => entry.module));
  function visit(node) {
    if (ts.isCallExpression(node) && node.arguments.length === 1 && ts.isStringLiteralLike(node.arguments[0])) {
      const callee = node.expression;
      const dynamicImport = callee.kind === ts.SyntaxKind.ImportKeyword;
      const requireCall = (ts.isIdentifier(callee) && callee.text === "require")
        || (ts.isPropertyAccessExpression(callee) && callee.name.text === "resolve"
          && ts.isIdentifier(callee.expression) && callee.expression.text === "require");
      if (dynamicImport || requireCall) {
        const specifierNode = node.arguments[0];
        const specifier = specifierNode.text;
        if (specifier && !seen.has(specifier)) {
          seen.add(specifier);
          results.push({
            module: specifier,
            filePath: resolveImportedFile(specifierNode, specifier, source, checker),
            symbolName: null,
            namespace: true
          });
        }
      }
    }
    ts.forEachChild(node, visit);
  }
  visit(source);
}

/**
 * The config that owns a file: the one listing it as a root file, otherwise the nearest config whose
 * directory encloses it. -1 when none does.
 */
function owningConfigIndex(filePath) {
  if (state.fileMap.has(filePath)) {
    return state.fileMap.get(filePath);
  }
  const candidates = [];
  for (let i = 0; i < state.configs.length; i += 1) {
    const config = state.configs[i];
    if (!config.rootNames.length) {
      continue;
    }
    if (filePath === config.dir || filePath.startsWith(config.dir + path.sep)) {
      candidates.push(i);
    }
  }
  candidates.sort(nearestWrittenFirst);
  if (candidates.length) {
    return candidates[0];
  }
  return -1;
}

function optionsForFile(filePath) {
  const index = owningConfigIndex(filePath);
  if (index < 0) {
    return {};
  }
  return state.configs[index].options;
}

/**
 * The repository source file a module specifier written in `fromFile` resolves to, using the
 * options of the config that owns `fromFile`, so `paths` and `baseUrl` apply. Path resolution only:
 * nothing is parsed. Null for a specifier that resolves outside the repository, into
 * `node_modules`, to a non-TypeScript file, or not at all.
 */
function resolveInternalModule(specifier, fromFile) {
  const ts = state.ts;
  let resolved;
  try {
    resolved = ts.resolveModuleName(specifier, fromFile, optionsForFile(fromFile), ts.sys).resolvedModule;
  } catch (err) {
    return null;
  }
  if (!resolved || !resolved.resolvedFileName) {
    return null;
  }
  const fileName = resolved.resolvedFileName;
  if (!isTypeScriptFile(fileName) || !isInternalFile(fileName)) {
    return null;
  }
  return normalizePath(ts, fileName);
}

function readSource(filePath) {
  const text = state.ts.sys.readFile(filePath);
  if (text === undefined) {
    return null;
  }
  return text;
}

/** Every module a file names: imports, `export ... from`, `require` and dynamic `import()`. */
function moduleSpecifiers(filePath) {
  const text = readSource(filePath);
  if (text === null) {
    return [];
  }
  return state.ts.preProcessFile(text, true, true).importedFiles.map((entry) => entry.fileName);
}

/** The modules a file re-exports from, read from its syntax alone. */
function reexportSpecifiers(filePath) {
  const ts = state.ts;
  const text = readSource(filePath);
  if (text === null) {
    return [];
  }
  const source = ts.createSourceFile(filePath, text, ts.ScriptTarget.Latest, false);
  const specifiers = [];
  for (const statement of source.statements) {
    if (ts.isExportDeclaration(statement) && statement.moduleSpecifier
      && ts.isStringLiteral(statement.moduleSpecifier)) {
      specifiers.push(statement.moduleSpecifier.text);
    }
  }
  return specifiers;
}

/**
 * The level-one files of the given focus files: every repository file a focus file's module
 * specifiers resolve to, closed over the re-exports of those files, since a name imported through
 * a barrel (`export * from "./impl"`) is declared in the file the barrel re-exports. Focus files are
 * excluded. Sorted.
 */
function handleDiscoverLevelOne(params) {
  requireReady();
  const ts = state.ts;
  const focus = new Set((params.focusFiles || []).map((file) => normalizePath(ts, file)));
  const levelOne = new Set();
  const queue = [];
  for (const file of focus) {
    for (const specifier of moduleSpecifiers(file)) {
      const resolved = resolveInternalModule(specifier, file);
      if (resolved && !focus.has(resolved) && !levelOne.has(resolved)) {
        levelOne.add(resolved);
        queue.push(resolved);
      }
    }
  }
  while (queue.length) {
    const file = queue.shift();
    for (const specifier of reexportSpecifiers(file)) {
      const resolved = resolveInternalModule(specifier, file);
      if (resolved && !focus.has(resolved) && !levelOne.has(resolved)) {
        levelOne.add(resolved);
        queue.push(resolved);
      }
    }
  }
  return { levelOne: Array.from(levelOne).sort() };
}

/**
 * The files a file brings into a program. Those its module specifiers (imports, `export ... from`,
 * `require`, dynamic `import()`) resolve to count when they are planned: the program is built
 * without resolution, so only planned files are held. Those named by `/// <reference path>` count
 * whether or not they are planned, since the compiler adds them to a program from the reference
 * alone; they are held so what they declare resolves, and are not modelled. A file that is not
 * planned brings in only the files it references.
 */
function plannedImports(file, plannedSet) {
  const text = readSource(file);
  if (text === null) {
    return [];
  }
  const info = state.ts.preProcessFile(text, true, true);
  const found = new Set();
  for (const entry of plannedSet.has(file) ? info.importedFiles : []) {
    const resolved = resolveInternalModule(entry.fileName, file);
    if (resolved && plannedSet.has(resolved)) {
      found.add(resolved);
    }
  }
  for (const entry of info.referencedFiles) {
    const referenced = normalizePath(state.ts, path.resolve(path.dirname(file), entry.fileName));
    if (isTypeScriptFile(referenced) && state.ts.sys.fileExists(referenced)) {
      found.add(referenced);
    }
  }
  return Array.from(found);
}

/**
 * Switches the daemon to one-level analysis of the given focus and level-one files.
 *
 * Each file is assigned to the config that owns it and is modelled in that config's program. The
 * program of every config that owns a focus file holds the planned files it owns and the planned
 * files those reach through imports, transitively, so a focus file's references into level one
 * resolve whichever config owns the target, and the files they name with `/// <reference path>`,
 * planned or not, transitively, as the compiler would add them. No program holds a file it cannot
 * reach. The
 * planned `.d.ts` files are held by each such program, since global declarations are reached
 * without an import. The program of a config that owns only level-one files holds those. Every
 * planned program also holds its config's own `.d.ts` root files, so global declarations and module
 * augmentations in the repository still resolve. Programs built before the plan are discarded, and
 * so are the source files they shared.
 */
function handlePlanOneLevel(params) {
  requireReady();
  const ts = state.ts;
  const focus = (params.focusFiles || []).map((file) => normalizePath(ts, file));
  const levelOne = (params.levelOneFiles || []).map((file) => normalizePath(ts, file));
  const planned = Array.from(new Set([...focus, ...levelOne]));
  const fileConfig = new Map();
  const rootsByConfig = new Map();
  const unowned = [];
  const addRoot = (index, file) => {
    if (!rootsByConfig.has(index)) {
      rootsByConfig.set(index, new Set());
    }
    rootsByConfig.get(index).add(file);
  };
  const focusConfigs = new Set();
  for (const file of planned) {
    const index = owningConfigIndex(file);
    if (index < 0) {
      unowned.push(file);
      continue;
    }
    fileConfig.set(file, index);
    addRoot(index, file);
  }
  for (const file of focus) {
    if (fileConfig.has(file)) {
      focusConfigs.add(fileConfig.get(file));
    }
  }
  const plannedSet = new Set(planned);
  const imports = new Map();
  const importsOf = (file) => {
    if (!imports.has(file)) {
      imports.set(file, plannedImports(file, plannedSet));
    }
    return imports.get(file);
  };
  const globalDeclarations = planned.filter((file) => file.toLowerCase().endsWith(".d.ts"));
  for (const index of focusConfigs) {
    const queue = Array.from(rootsByConfig.get(index));
    while (queue.length) {
      for (const imported of importsOf(queue.shift())) {
        if (!rootsByConfig.get(index).has(imported)) {
          addRoot(index, imported);
          queue.push(imported);
        }
      }
    }
    globalDeclarations.forEach((file) => addRoot(index, file));
  }
  for (let i = 0; i < state.configs.length; i += 1) {
    delete state.configs[i].oneLevelRoots;
    delete state.configs[i].absentFiles;
    delete state.configs[i].memberFiles;
  }
  for (const [index, roots] of rootsByConfig) {
    const config = state.configs[index];
    for (const root of config.rootNames) {
      if (root.toLowerCase().endsWith(".d.ts")) {
        roots.add(normalizePath(ts, root));
      }
    }
    config.oneLevelRoots = Array.from(roots).sort();
  }
  state.programCache = new Map();
  state.programOrder = [];
  state.sourceFiles = new Map();
  state.oneLevel = { fileConfig, residentPrograms: Math.min(rootsByConfig.size, MAX_ONE_LEVEL_RESIDENT_PROGRAMS) };
  return {
    plannedFiles: planned.length - unowned.length,
    programCount: rootsByConfig.size,
    unowned: unowned.sort()
  };
}

function requireReady() {
  if (!state.ts || !state.configs || !state.configs.length) {
    const err = new Error("PROGRAM_NOT_READY");
    err.code = ERROR_CODES.PROGRAM_CREATE_FAILED;
    throw err;
  }
}

async function handleRequest(request) {
  const { id, method, params } = request;
  if (method === "discoverLevelOne" || method === "planOneLevel") {
    try {
      const handler = method === "discoverLevelOne" ? handleDiscoverLevelOne : handlePlanOneLevel;
      writeResponse(id, handler(params || {}));
    } catch (err) {
      if (err.code) {
        writeError(id, err.code, err.message, err.data);
      } else {
        writeError(id, ERROR_CODES.PROGRAM_CREATE_FAILED, err.message);
      }
    }
    return;
  }
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
  if (method === "ownersOf") {
    try {
      writeResponse(id, handleOwnersOf(params));
    } catch (err) {
      writeError(id, err.code || ERROR_CODES.PROGRAM_CREATE_FAILED, err.message, err.data);
    }
    return;
  }
  if (method === "stats") {
    writeResponse(id, { programBuilds: state.programBuilds });
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
