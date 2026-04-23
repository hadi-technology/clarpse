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

let state = {
  repoRoot: null,
  ts: null,
  programs: [],
  fileMap: new Map()
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
  let hash = 0;
  for (let i = 0; i < normalized.length; i += 1) {
    hash = ((hash * 31) + normalized.charCodeAt(i)) | 0;
  }
  return hash;
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

function buildPrograms(ts, repoRoot, configPaths) {
  const programs = [];
  const invalidConfigs = [];

  // Wrapper around ts.sys.readFile that normalizes tsconfig JSON
  const readAndNormalizeConfig = (filePath) => {
    const content = ts.sys.readFile(filePath);
    if (content === undefined) {
      return undefined;
    }
    // Remove trailing commas from JSON to handle non-standard tsconfig files
    // Pattern: comma followed by optional whitespace then closing brace or bracket
    const normalized = content.replace(/,(\s*[}\]])/g, '$1');
    return normalized;
  };

  for (const configPath of configPaths) {
    let configFile;
    try {
      configFile = ts.readConfigFile(configPath, readAndNormalizeConfig);
    } catch (err) {
      // Check if the read failure is extends-related
      const errStr = err.toString();
      if (errStr.includes("extends") || err.code === 6075 || err.code === 18003 || err.code === 6053) {
        // Treat as extends error - will use minimal options
        configFile = null;
      } else {
        invalidConfigs.push({ configPath, error: "CONFIG_READ_FAILED" });
        continue;
      }
    }

    // If there's a configFile.error, check if it's extends-related
    if (configFile && configFile.error) {
      const errStr = configFile.error.toString();
      if (errStr.includes("extends") || (configFile.error.code && (configFile.error.code === 6075 || configFile.error.code === 18003 || configFile.error.code === 6053))) {
        // Treat as extends error - will use minimal options
      } else {
        invalidConfigs.push({ configPath, error: "CONFIG_PARSE_FAILED" });
        continue;
      }
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
        hasExtendErrors = config && config.errors && config.errors.some(e =>
          e.messageText && (e.messageText.includes("extends") || e.code === 6075 || e.code === 18003 || e.code === 6053)
        );
      } catch (err) {
        // parseJsonConfigFileContent threw an exception - check if it's extends-related
        const errStr = err.toString();
        if (errStr.includes("extends") || err.code === 6075 || err.code === 18003 || err.code === 6053) {
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
    try {
      const program = ts.createProgram({
        rootNames,
        options,
        projectReferences
      });
      programs.push({ configPath, program, options, checker: program.getTypeChecker() });
    } catch (err) {
      console.error("[CLARPSE-DEBUG] PROGRAM_CREATE_FAILED for", configPath, err.message);
      invalidConfigs.push({ configPath, error: "PROGRAM_CREATE_FAILED" });
    }
  }
  return { programs, invalidConfigs };
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

function findProgramEntryForFile(filePath) {
  if (state.fileMap && state.fileMap.has(filePath)) {
    const entry = state.fileMap.get(filePath);
    const source = entry.program.getSourceFile(filePath);
    if (source) {
      return { entry, source };
    }
  }
  for (const entry of state.programs) {
    const source = entry.program.getSourceFile(filePath);
    if (source) {
      return { entry, source };
    }
  }
  return null;
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
    modifiers: collectModifiers(state.ts, node),
    jsDoc: getJsDoc(node),
    references: buildReferenceModelsFromType(fieldType, checker, "type")
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
    implementationHash: stableImplementationHash(node.body ? node.body.getText() : ""),
    returnType: kindLabel === "constructor" ? "" : getReturnType(checker, node),
    modifiers: collectModifiers(state.ts, node),
    jsDoc: getJsDoc(node),
    cyclo: computeCyclo(state.ts, node),
    members: paramModels.concat(bodyDetails.locals),
    references: mergeReferences(
      returnTypeObject ? buildReferenceModelsFromType(returnTypeObject, checker, "type") : [],
      bodyDetails.references
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
    implementationHash: stableImplementationHash(node.body ? node.body.getText() : ""),
    returnType: accessorKind === "set" ? "" : getReturnType(checker, node),
    modifiers,
    jsDoc: getJsDoc(node),
    cyclo: computeCyclo(state.ts, node),
    members: buildParameterModels(node.parameters || [], checker).concat(bodyDetails.locals),
    references: mergeReferences(bodyDetails.references)
  };
}

function buildEnumModel(node) {
  const members = node.members.map((member) => ({
    kind: "enumMember",
    name: member.name.getText()
  }));
  return {
    kind: "enum",
    name: node.name.text,
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
    modifiers: collectModifiers(state.ts, node),
    jsDoc: getJsDoc(node),
    members,
    references: buildHeritageReferences(node, checker)
  };
}

function buildClassModel(node, checker) {
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
  const typeParams = node.typeParameters && node.typeParameters.length
    ? `<${node.typeParameters.map((param) => param.getText()).join(", ")}>`
    : "";
  return {
    kind: "class",
    name: node.name.text,
    signature: typeParams,
    modifiers: collectModifiers(state.ts, node),
    jsDoc: getJsDoc(node),
    members,
    references: buildHeritageReferences(node, checker)
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

function collectTopLevelDeclarations(ts, sourceFile, checker) {
  const declarations = [];
  sourceFile.forEachChild((node) => {
    if (ts.isClassDeclaration(node) && node.name) {
      declarations.push(buildClassModel(node, checker));
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
  const configs = findTsconfigs(repoRoot).sort();
  if (!configs.length) {
    const err = new Error("NO_TSCONFIG");
    err.code = ERROR_CODES.NO_TSCONFIG;
    throw err;
  }
  let programs = [];
  let invalidConfigs = [];
  try {
    const result = buildPrograms(ts, repoRoot, configs);
    programs = result.programs || [];
    invalidConfigs = result.invalidConfigs || [];
  } catch (err) {
    const errObj = new Error("CONFIG_PARSE_FAILED");
    errObj.code = ERROR_CODES.CONFIG_PARSE_FAILED;
    errObj.data = err.message;
    throw errObj;
  }
  if (!programs.length) {
    const errObj = new Error("CONFIG_PARSE_FAILED");
    errObj.code = ERROR_CODES.CONFIG_PARSE_FAILED;
    errObj.data = invalidConfigs.map((entry) => entry.configPath);
    throw errObj;
  }
  const fileMap = new Map();
  for (const entry of programs) {
    for (const sourceFile of entry.program.getSourceFiles()) {
      const normalized = normalizePath(ts, sourceFile.fileName);
      if (!isInternalFile(normalized, repoRoot)) {
        continue;
      }
      if (!fileMap.has(normalized)) {
        fileMap.set(normalized, entry);
      }
    }
  }
  state = {
    repoRoot,
    ts,
    programs,
    fileMap
  };
  const fileCount = programs.reduce((sum, entry) => sum + entry.program.getRootFileNames().length, 0);
  return {
    tsVersion: ts.version || "",
    configCount: programs.length,
    fileCount,
    invalidConfigCount: invalidConfigs.length,
    invalidConfigs
  };
}

async function handleGetFileModel(params) {
  if (!state.ts || !state.programs || !state.programs.length) {
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
  let declarations;
  try {
    declarations = collectTopLevelDeclarations(ts, entryInfo.source, checker);
  } catch (err) {
    if (err instanceof RangeError || (err.message && err.message.includes("Maximum call stack size exceeded"))) {
      console.warn(`[clarpse] Stack overflow during type resolution for ${filePath}. Returning partial model.`);
      declarations = [];
    } else {
      throw err;
    }
  }
  return {
    filePath: normalized,
    declarations
  };
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
