# TypeScript Architecture (Clarpse)

This document describes the TypeScript implementation in Clarpse and the design choices
that keep it compiler-accurate, deterministic, and aligned with the Java model.

# Scope
TypeScript support is implemented as a compiler-backed integration that produces the same
architecture-level model as Java, with strict correctness guarantees and explicit failure modes.

# Key Assumptions
- Node.js is required at runtime; the TypeScript compiler API is bundled with Clarpse.
- The daemon resolves only the bundled TypeScript runtime (no local/global fallback).
- A valid `tsconfig.json` exists in the project tree.
- Only `.ts`, `.tsx`, and `.d.ts` files are parsed; JavaScript is not parsed.
- The TypeScript compiler is the single source of truth for resolution.
- If TypeScript cannot resolve a file or program, the compiler fails explicitly (no heuristics).
- Monorepo setups with multiple `tsconfig.json` files are supported.

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
TypeScript follows the same failure model as Python:
- Recoverable language/runtime/config/file errors are recorded in `CompileResult.failures()` with an error code.
- `CompileException` is reserved for non-recoverable compiler failures.

Examples:
- Node missing: file failures with `CODE_NODE_NOT_FOUND`.
- No valid `tsconfig.json`: file failures with `CODE_NO_TSCONFIG`.
- File not in program: file failure with `CODE_FILE_NOT_IN_PROGRAM`.
- Invalid configs in a mixed repo: bad configs are skipped when at least one valid config exists.

Standardized language-agnostic codes used by TypeScript:
- `1000` Node runtime not available.
- `1001` Bundled TypeScript runtime not found.
- `1002` No valid `tsconfig.json`.
- `1003` `tsconfig.json` parse/validation error.
- `1004` Program/repository initialization error.
- `2001` File not in active program scope.
- `2002` File not found on disk.
- `2004` Daemon transport/runtime error.

# Reference Resolution
All relationships are resolved through the TypeScript `TypeChecker`:
- `extends`, `implements`
- field types
- parameter types
- return types
- constructor parameter properties (fields declared in constructor parameters)

Targets are classified as internal or external:
- Internal references resolve to Clarpse uniqueNames.
- External references are marked explicitly.

# Constructor Parameter Properties
TypeScript supports parameter properties in constructors, which declare and initialize fields in one step:
```typescript
class Example {
  constructor(public name: string, private age: number) {}
}
```
These are properly modeled as fields with their corresponding visibility modifiers, enabling accurate architectural analysis.

# Efficiency Notes
- Programs are built once per `tsconfig.json` and reused for all files in that config.
- A file->program map is built during daemon initialization for faster lookups.
- The daemon is single-threaded; Java parsing can run in parallel.
- Monorepo support: Multiple `tsconfig.json` files are handled by building separate programs and correctly scoping files to their appropriate config.

# Core Classes and Locations
- Compiler: `src/main/java/com/hadi/clarpse/compiler/typescript/ClarpseTypeScriptCompiler.java`
- Model assembler: `src/main/java/com/hadi/clarpse/compiler/typescript/TypeScriptModelAssembler.java`
- Daemon bridge: `src/main/java/com/hadi/clarpse/compiler/typescript/TypeScriptDaemon.java`
- Daemon runtime: `src/main/resources/typescript/daemon.js`
- TypeScript models: `src/main/java/com/hadi/clarpse/compiler/typescript/model/*`
- Compile failures: `src/main/java/com/hadi/clarpse/compiler/CompileFailure.java`

# Additional Features
## Monorepo Support
Clarpse supports monorepo setups with multiple `tsconfig.json` files:
- Each `tsconfig.json` creates a separate TypeScript program instance.
- Files are correctly scoped to their appropriate program based on `tsconfig` references.
- Project references (`composite: true`, `references` arrays) are properly handled.
- Files not in any valid program scope are reported with appropriate error codes.

## Constructor Parameter Properties
TypeScript's parameter properties are fully supported:
- Fields declared in constructor parameters are extracted as separate field components.
- Visibility modifiers (public/private/protected/readonly) are preserved.
- This enables accurate dependency analysis for classes using this TypeScript feature.

## Code Fragment Support
Method and function signatures are captured as code fragments for display and analysis purposes.

## Code Hashing
Every component carries a non-zero code hash for change detection:
- The daemon hashes the whole declaration - a class body, a method including its signature, a property,
  a parameter - so an implementation edit changes the hash while a reformat does not.
- If there was nothing to hash, the code fragment is used as a fallback, then the component's name, so
  a hash of zero is never emitted and means "never computed".

## Export as Visibility
`export` puts a declaration on the module's public surface, so an exported declaration reports both
`export` and `public`. A module-private declaration reports neither, matching the way a package-private
Java type reports no visibility at all.

# Non-Goals
- No heuristic or string-based fallback resolution; symbol/type resolution is compiler-backed only.
- No fallback parsing.
- No modification to Striffs relation logic.
