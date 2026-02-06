# TypeScript Architecture (Clarpse)

This document describes the TypeScript implementation in Clarpse and the design choices
that keep it compiler-accurate, deterministic, and aligned with the Java model.

# Scope
TypeScript support is implemented as a compiler-backed integration that produces the same
architecture-level model as Java, with strict correctness guarantees and explicit failure modes.

# Key Assumptions
- Node.js and the TypeScript compiler API are available at runtime.
- A valid `tsconfig.json` exists in the project tree.
- Only `.ts`, `.tsx`, and `.d.ts` files are parsed; JavaScript is not parsed.
- The TypeScript compiler is the single source of truth for resolution.
- If TypeScript cannot resolve a file or program, the compiler fails explicitly (no heuristics).

Supported Node.js versions: 18, 20, 22.

# Technologies Used
- Java (Clarpse core, compiler integration)
- Node.js (TypeScript daemon runtime)
- TypeScript compiler API (parsing, symbols, and type resolution)
- JSON-RPC over stdin/stdout (Java <-> Node communication)
- JUnit (test coverage and parity checks)

# Architecture Layers
Layer 1: Clarpse core (Java)
- Owns: packages, components, references, and the `OOPSourceCodeModel`.
- Does not implement TypeScript semantics.

Layer 2: Resolver bridge (Java)
- Starts/stops the TypeScript daemon.
- Translates daemon output into Clarpse components and references.
- Enforces explicit failure contract.

Layer 3: TypeScript daemon (Node)
- Parses source files using the TypeScript compiler API.
- Resolves symbols and types via `TypeChecker`.
- Returns a structured semantic model back to Java.

# Parsing Pipeline (TypeScript)
1) `ProjectFiles` collects `.ts/.tsx/.d.ts` files.
2) `ClarpseProject` selects `ClarpseTypeScriptCompiler`.
3) `ClarpseTypeScriptCompiler` starts the TypeScript daemon.
4) The daemon builds programs for all `tsconfig.json` files.
5) Each file is resolved via `getFileModel(file)` and mapped into Clarpse components.

# Identity and Naming
Clarpse relies on stable, unique names:

`uniqueName = packageName + componentName`

TypeScript component naming incorporates module identity:

`componentName = <module>.<symbol>[.<member>]`

Examples:
- Class: `src.domain.user.User`
- Method: `src.domain.user.User.create`
- Function: `src.utils.date.format`

Package names are derived from the repo-relative directory path, consistent with Java.

# Failure Contract
If Node or the TypeScript compiler is unavailable, or the program cannot be built:
- The compiler throws an error and no partial output is produced.

If a specific file cannot be resolved (e.g., not included in `tsconfig.json`):
- The file is recorded in `CompileResult.failures()` with a message and optional error code.

If one or more `tsconfig.json` files are invalid:
- Invalid configs are skipped and parsing continues with valid configs.
- If none are valid, initialization fails.

# Reference Resolution
All relationships are resolved through the TypeScript `TypeChecker`:
- `extends`, `implements`
- field types
- parameter types
- return types

Targets are classified as internal or external:
- Internal references resolve to Clarpse uniqueNames.
- External references are marked explicitly.

# Efficiency Notes
- Programs are built once per `tsconfig.json` and reused for all files in that config.
- A file->program map is built during daemon initialization for faster lookups.
- The daemon is single-threaded; Java parsing can run in parallel.

# Core Classes and Locations
- Compiler: `src/main/java/com/hadi/clarpse/compiler/typescript/ClarpseTypeScriptCompiler.java`
- Daemon bridge: `src/main/java/com/hadi/clarpse/compiler/typescript/TypeScriptDaemon.java`
- Daemon runtime: `src/main/resources/typescript/daemon.js`
- TypeScript models: `src/main/java/com/hadi/clarpse/compiler/typescript/model/*`
- Compile failures: `src/main/java/com/hadi/clarpse/compiler/CompileFailure.java`

# Non-Goals
- No heuristic symbol or type resolution.
- No fallback parsing.
- No modification to Striffs relation logic.
