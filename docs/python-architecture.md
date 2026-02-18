# Python Architecture (Clarpse)

This document describes the Python implementation in Clarpse, aligned with the
Node based resolver design. The goal is accurate internal type resolution for
class diagrams while treating external libraries as labels only.

# Executive Summary
- Python support is provided by a Node subprocess invoked by the Java compiler.
- Internal types resolve to stable Clarpse unique names.
- External types are preserved as labels and are not resolved.
- Pyright is bundled with the library; only Node.js is required at runtime.
- The daemon resolves only the bundled Pyright runtime (no local/global fallback).
- No external server dependency is required.

# Requirements
- Discover .py files.
- Extract classes, methods, and fields with type annotations.
- Resolve internal types across modules.
- Treat external types as labels only.
- Skip large dependency folders like .venv, venv, __pycache__, .tox, build, dist.

# Identity and Naming
Unique names follow the existing Clarpse rule.

uniqueName = packageName + componentName

Where:
- packageName is the repo relative directory path with dots instead of slashes.
- moduleName is the file name without .py.

Component naming:
- Class: componentName = moduleName.ClassName
- Method: componentName = moduleName.ClassName.signature
- Field: componentName = moduleName.ClassName.field

Method signature format:
methodName(p1: T1, p2: T2) -> R

self and cls are excluded from the signature. Missing annotations default to Any.

# Architecture Layers
Layer 1: Clarpse core (Java)
- Owns OOPSourceCodeModel, packages, components, references.

Layer 2: Python compiler bridge (Java)
- Starts the Node resolver.
- Requests file models.
- Records recoverable file-level failures in `CompileResult.failures()`.
- Builds Clarpse components and references through `PythonModelAssembler`.

Layer 3: Node resolver (Node.js)
- Scans the repo, excluding dependency folders.
- Uses bundled Pyright parser to parse Python source.
- Resolves internal type targets using module indexing plus import/annotation mapping.
- Emits external labels for unresolved types.

# Node Resolver Behavior
Internal declarations are recognized when their file path lives under the repo root
and can be mapped through imports or local class declarations.

- Internal types resolve when the declaration file is under the repo root and
  not under an excluded directory.
- External types retain their label.
- A missing or invalid file yields a file level error that the compiler records.
- Module-level functions are emitted as `FUNCTION` components.
- Module-level variables are emitted as `MODULE_FIELD` components.

# JSON Protocol
Requests use line delimited JSON.

Request example:
{ "id": 1, "op": "initRepo", "params": { "repoRoot": "/repo", "options": { "pythonVersion": "3.10" } } }

Request example:
{ "id": 2, "op": "getFileModel", "params": { "filePath": "/repo/src/domain/user/models.py" } }

Success response:
{ "id": 1, "ok": true, "result": { ... } }

Error response:
{ "id": 1, "ok": false, "error": { "code": 2002, "message": "File not found" } }

# Failure Contract
Recoverable failures are returned as `CompileFailure` entries with error codes.
`CompileException` is reserved for non-recoverable compiler errors.

Common codes:
- `1000` Node runtime not available.
- `1004` Resolver/repository initialization failure.
- `2002` File missing/not in analyzed repo.
- `2003` Parse/type extraction failed.
- `2004` Daemon transport/runtime failure.
- `2005` File skipped due to excluded directories.

# External Type Policy
If a type annotation does not resolve to an internal declaration:
- targetUniqueName = null
- externalLabel = best effort label

Label precedence:
1) Qualified annotation text like pkg.Type
2) Import based label like pydantic.BaseModel
3) Raw annotation text

# Excludes
Default excluded directories:
- .venv, venv, __pycache__, .tox, build, dist, node_modules, .mypy_cache, .pytest_cache

# Non Goals
- Runtime execution or dynamic import evaluation.
- Full type inference beyond annotations.
- External library type resolution.

# Core Classes and Locations
- Compiler: src/main/java/com/hadi/clarpse/compiler/python/ClarpsePythonCompiler.java
- Model assembler: src/main/java/com/hadi/clarpse/compiler/python/PythonModelAssembler.java
- Daemon bridge: src/main/java/com/hadi/clarpse/compiler/python/PythonDaemon.java
- Daemon runtime: src/main/resources/python/daemon.js
- Python models: src/main/java/com/hadi/clarpse/compiler/python/model
