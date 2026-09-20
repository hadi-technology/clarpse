# :rocket: Clarpse

**Parse Java, C#, TypeScript and Python into one language-agnostic model of your codebase.**

[![Maven Central](https://img.shields.io/maven-central/v/io.github.hadi-technology/clarpse?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.hadi-technology/clarpse) [![Java CI](https://github.com/hadi-technology/clarpse/actions/workflows/ci-cd.yml/badge.svg?branch=master)](https://github.com/hadi-technology/clarpse/actions/workflows/ci-cd.yml) [![codecov](https://codecov.io/github/hadi-technology/clarpse/graph/badge.svg?token=7uf2jQMlH1)](https://codecov.io/github/hadi-technology/clarpse) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE) [![maintained-by](https://img.shields.io/badge/Maintained%20by-Hadi%20Technology-violet.svg)](https://haditechnology.com) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](http://makeapullrequest.com)

Writing a tool that reasons about code means writing four different AST walkers, one per language, and maintaining them forever. Clarpse gives you one instead: point it at a directory, a zip, or in-memory files, and get back classes, methods, fields, and the references between them, with the same API no matter what language the source was written in.

It is deliberately architecture-level. Clarpse tells you that `OrderService` calls `PaymentGateway` and lives in package `com.acme.billing`; it does not hand you a statement-level syntax tree. That tradeoff is what makes one model work across four languages.

Clarpse powers [striff-lib](https://github.com/hadi-technology/striff-lib), which turns pull request diffs into architectural diagrams.

## Quickstart

Add the dependency (check the badge above for the latest version):

```xml
<dependency>
  <groupId>io.github.hadi-technology</groupId>
  <artifactId>clarpse</artifactId>
  <version>11.8.0</version>
</dependency>
```

Parse a codebase and walk the model:

```java
ProjectFiles files = new ProjectFiles("/path/to/repo");
CompileResult result = new ClarpseProject(files, Lang.JAVA).result();
OOPSourceCodeModel model = result.model();

model.components().forEach(cmp ->
        System.out.println(cmp.componentType() + " " + cmp.uniqueName()));
```

The same three lines work for `Lang.CSHARP`, `Lang.TYPESCRIPT`, and `Lang.PYTHON`. See [Using The API](#using-the-api) for the full surface.

## Language Support

| Language   | Parser                          | Node.js required | Notes                                                                                      |
|------------|---------------------------------|:----------------:|--------------------------------------------------------------------------------------------|
| Java       | ANTLR, architecture-focused     | No               | Includes records.                                                                            |
| C#         | JVM-based                       | No               | Partial type merging, namespace-aware indexing, fast in-repo symbol resolution.              |
| TypeScript | Bundled TypeScript compiler     | Yes              | tsconfig-aware resolution, constructor parameter properties, monorepo support. Needs a valid `tsconfig.json`. |
| Python     | Bundled Pyright                 | Yes              | Nested classes, comment parsing, cyclomatic complexity, code hashing, visibility inference.  |

Across every language you also get comment extraction, a clean object-oriented API over the AST, parallel parsing with configurable worker counts, and runtime configuration via environment variables, system properties, or a bundled properties file.

# Requirements
 - Java 17
 - Maven 3.x
 - Node.js 18/20/22/25, only for TypeScript and Python parsing
 - No global `typescript` or `pyright` install is required (both are bundled)
 - No local Python interpreter is required for Python parsing

# Running Locally
Build the jar:
`mvn clean package assembly:single`

Start the HTTP API:
`java -cp target/clarpse-<version>.jar com.hadi.clarpse.server.ClarpseServer`

Health check:
`curl -s http://localhost:8080/health`

Parse a JSON request:
```bash
curl -s -X POST http://localhost:8080/parse \
  -H "Content-Type: application/json" \
  -d '{"language":"java","files":[{"path":"src/Foo.java","content":"package test; class Foo { void m() {} }"}]}'
```

Parse a zip (Java, TypeScript, or Python):
```bash
curl -s -X POST "http://localhost:8080/parse?lang=typescript" \
  -H "Content-Type: application/zip" \
  --data-binary @project.zip
```

Notes:
- TypeScript parsing requires a valid `tsconfig.json` in the project input.
- Python parsing uses bundled Pyright plus project imports/config for internal type linking.
- TypeScript and Python daemons resolve only bundled compiler/type-checker runtimes.
- Environment variables: `CLARPSE_PORT`, `CLARPSE_MAX_BYTES`, `CLARPSE_PARALLELISM`, `CLARPSE_PYTHON_PARALLELISM`, `CLARPSE_NODE_PATH`.
- Node override system properties: `clarpse.node.path`, `clarpse.node.disabled`.

# Docker API for Non-Java Consumers
Build and run the container (no local jar required):
```bash
docker build -t clarpse-api .
docker run -p 8080:8080 clarpse-api
```

Then call the API the same way as the local server:
```bash
curl -s -X POST http://localhost:8080/parse \
  -H "Content-Type: application/json" \
  -d '{"language":"java","files":[{"path":"src/Foo.java","content":"package test; class Foo { void m() {} }"}]}'
```

# Runtime Tuning
Clarpse supports runtime configuration through environment variables, system properties, and a bundled properties file.

## Parallelism Control
- `CLARPSE_PARALLELISM` controls Java parser thread count.
- `CLARPSE_PYTHON_PARALLELISM` or `-Dclarpse.python.parallelism=<n>` controls Python worker count.
- Values `1` or lower force serial parsing.
- If unset, Clarpse auto-selects a bounded value based on CPU count and file count.

Example:
`CLARPSE_PARALLELISM=4 mvn test`

## Zip Entry Limits
Clarpse includes configurable limits for zip processing to prevent resource exhaustion. These can be overridden via system properties or by modifying `src/main/resources/clarpse.properties`:
- `clarpse.zip.maxEntries` (default: 100000) - Maximum number of entries in a zip file
- `clarpse.zip.maxTotalUncompressedBytes` (default: 209715200, ~200MB) - Maximum total uncompressed size
- `clarpse.zip.maxEntryUncompressedBytes` (default: 10485760, ~10MB) - Maximum size per entry

Entries handed to a `DiscardedEntryObserver` count against these limits like any other entry.

## Node.js Configuration
- `CLARPSE_NODE_PATH` or `-Dclarpse.node.path=<path>` sets a custom Node.js executable path.
- `CLARPSE_NODE_DISABLED` or `-Dclarpse.node.disabled=true` disables Node.js (TypeScript and Python parsing will fail).
- `CLARPSE_NODE_HEAP_SIZE` or `-Dclarpse.node.heapSize=<MB>` sets Node.js heap size in MB (default: 4096). Increase for large TypeScript/Python projects.
- `CLARPSE_TS_MAX_PROGRAMS` or `-Dclarpse.typescript.maxPrograms=<n>` caps how many TypeScript
  programs the daemon holds at once (default: 2). A program retains every source file it reaches and
  a type checker over them, so this, rather than the heap size, is what bounds a repository with many
  `tsconfig.json` files. Raise it to trade memory for fewer rebuilds when files are interleaved
  across projects.

Example for large projects:
```bash
CLARPSE_NODE_HEAP_SIZE=8192 mvn test
# or
java -Dclarpse.node.heapSize=8192 -jar app.jar
```

# Repo Tour
Key areas of the repository:

- `src/main/java/com/hadi/clarpse/compiler` - Language compilers, project file handling, and orchestration.
- `src/main/java/com/hadi/clarpse/compiler/typescript` - TypeScript compiler bridge and models.
- `src/main/java/com/hadi/clarpse/compiler/python` - Python compiler bridge and models.
- `src/main/java/com/hadi/clarpse/compiler/ClarpseProperties.java` - Runtime properties loader.
- `src/main/java/com/hadi/clarpse/listener` - Parse tree listeners that build the source model (Java).
- `src/main/java/com/hadi/clarpse/sourcemodel` - Component and package models.
- `src/main/java/com/hadi/clarpse/reference` - Component reference types.
- `src/main/resources` - Parser helpers, daemon scripts, and configuration (TypeScript and Python daemons, properties file).
- `src/test/java` - Unit and integration tests.
- `src/test/resources` - Test fixtures and zipped codebases used by tests.

# Terminology
| Term                | Definition                                                                                                                                                                  |
|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Component           | A language independent source unit of the code, typically represented by a class, method, interface, field variable, local variable, enum, etc ..                                                       |
| OOPSourceCodeModel  |                                                  A representation of a codebase through a collection of Component objects.                                                  |
| Component Reference | A reference between an original component to a target component, which typically exist in the form of import statements, variable declarations, method calls, and so on. |

# Getting Started
Build and test in three steps:

1) Generate ANTLR sources: `mvn generate-resources`
2) Run tests: `mvn test`
3) Build the full artifact: `mvn clean package assembly:single`

Run a single test class:
`mvn -Dtest=com.hadi.test.java.SmokeTest test`

# Parsing Pipeline
The parsing flow is:

`ProjectFiles` -> `ClarpseProject` -> `ClarpseCompiler` -> Language Listener -> `OOPSourceCodeModel`

High level steps:
1) Collect files in `ProjectFiles` (directory, zip, or in-memory).
2) `ClarpseProject` selects a language compiler.
3) The compiler parses files and walks the parse tree.
4) The language listener builds `Component` objects and references.
5) The resulting `OOPSourceCodeModel` is used by downstream tooling.

# Design and Architecture
Core classes and where they live:

- Project entry and orchestration: `src/main/java/com/hadi/clarpse/compiler/ClarpseProject.java`
- Project inputs: `src/main/java/com/hadi/clarpse/compiler/ProjectFiles.java`, `src/main/java/com/hadi/clarpse/compiler/ProjectFile.java`
- Runtime properties: `src/main/java/com/hadi/clarpse/compiler/ClarpseProperties.java`, `src/main/resources/clarpse.properties`
- Compiler selection and results: `src/main/java/com/hadi/clarpse/compiler/CompilerFactory.java`, `src/main/java/com/hadi/clarpse/compiler/ClarpseCompiler.java`, `src/main/java/com/hadi/clarpse/compiler/CompileResult.java`
- Language compilers: `src/main/java/com/hadi/clarpse/compiler/ClarpseJavaCompiler.java`, `src/main/java/com/hadi/clarpse/compiler/typescript/ClarpseTypeScriptCompiler.java`, `src/main/java/com/hadi/clarpse/compiler/python/ClarpsePythonCompiler.java`
- Parse listeners: `src/main/java/com/hadi/clarpse/listener/JavaTreeListener.java`
- Source model: `src/main/java/com/hadi/clarpse/sourcemodel/OOPSourceCodeModel.java`, `src/main/java/com/hadi/clarpse/sourcemodel/Component.java`, `src/main/java/com/hadi/clarpse/sourcemodel/Package.java`
- References: `src/main/java/com/hadi/clarpse/reference/ComponentReference.java` and related types in `src/main/java/com/hadi/clarpse/reference`
- TypeScript daemon: `src/main/resources/typescript/daemon.js`
- Python daemon: `src/main/resources/python/daemon.js`


Note: TypeScript and Python parsing require Node.js.

Architecture docs:
- `docs/typescript-architecture.md`
- `docs/python-architecture.md`
- `docs/one-level-analysis.md`

## Using The API
Clarpse abstracts source code into a higher level model in a **language-agnostic** way.  
The snippet below shows how to generate the model from in-memory files.

```java
final String code =
        "package com.foo; " +
        "public class SampleClass { " +
        "  public void sampleMethod(String sampleMethodParam) { } " +
        "}";
final ProjectFiles projectFiles = new ProjectFiles();
projectFiles.insertFile(new ProjectFile("src/SampleClass.java", code));
final ClarpseProject project = new ClarpseProject(projectFiles, Lang.JAVA);
CompileResult compileResult = project.result();
OOPSourceCodeModel codeModel = compileResult.model();
Collection<CompileFailure> failures = compileResult.failures();
```

Path rules for `ProjectFile`:
- Relative paths are supported and normalized (for example `src/Foo.java`).
- Absolute paths are supported.
- Parent traversal (`..`) is rejected.

`ProjectFiles` can be initialized from:
- a local directory path
- a local zip file path
- a zip input stream
- in-memory `ProjectFile` entries

See `src/test/java/com/hadi/test/ProjectFilesTest.java` for examples.

### Reading a repository's other files in the same pass

`ProjectFiles` keeps only the files whose extension maps to a language, plus the configuration
files a compiler needs. Everything else — documentation, licences, data — is read past and dropped,
and reaching an entry means inflating the ones before it, so collecting a repository's documents
from a second walk of the same archive inflates it twice.

`ProjectFiles.fromZip(archive, observer)` hands those entries over as extraction reaches them:

```java
final Map<String, String> documents = new LinkedHashMap<>();
try (InputStream archive = Files.newInputStream(Path.of("/path/to/repo.zip"))) {
    ProjectFiles files = ProjectFiles.fromZip(archive, new DiscardedEntryObserver() {
        @Override
        public boolean observesPath(String path) {
            return path.endsWith(".md");           // asked before the entry's bytes are read
        }

        @Override
        public void observe(String path, byte[] content, Instant lastModified) {
            documents.put(path, new String(content, StandardCharsets.UTF_8));
        }
    });
}
```

What an observer is and is not:

- It sees an entry's path relative to the archive root, its uncompressed bytes, and its
  last-modified time, and it owns the array it is handed.
- It decides nothing about parsing. The `ProjectFiles` an observed extraction produces is the one
  it produces without an observer.
- It is asked `observesPath` before an entry is read, so declining a path costs the archive's own
  read and nothing else.
- It never sees a kept entry, and never sees a path extraction refused: normalisation and the
  refusal of entries that point outside the archive root happen first.
- Observed entries are extraction's entries, counted against the zip entry limits like any other.
- An exception it throws propagates to the caller with the archive closed.

`new ProjectFiles(archive)` without an observer keeps the files it always kept, and reads past the
entries nothing asked for.

TypeScript usage follows the same API, but requires Node.js and a valid `tsconfig.json`:
```java
final ProjectFiles projectFiles = new ProjectFiles("/path/to/typescript-project");
final ClarpseProject project = new ClarpseProject(projectFiles, Lang.TYPESCRIPT);
CompileResult compileResult = project.result();
OOPSourceCodeModel codeModel = compileResult.model();
```

Next, inspect components:
```java
codeModel.components().forEach(component -> {
    System.out.println(component.uniqueName());
    System.out.println(component.componentType());
    System.out.println(component.comment());
    System.out.println(component.modifiers());
    System.out.println(component.children());
    System.out.println(component.sourceFile());
});
```

Fetch a specific component by unique name:
```java
Component classComponent = codeModel.copyOfComponent("com.foo.SampleClass")
        .orElseThrow();
System.out.println(classComponent.name());
System.out.println(classComponent.componentType());
System.out.println(classComponent.references());

String childUniqueName = classComponent.children().get(0);
Component methodComponent = codeModel.copyOfComponent(childUniqueName).orElseThrow();
System.out.println(methodComponent.name());
System.out.println(methodComponent.codeFragment());
```

## One-Level Analysis
To model a few files of a large repository together with the repository files they reference,
without compiling the rest of it, give the files to analyse and a depth of 1. The referenced files
are the boundary level: their components are marked `isBoundary()`.

```java
final List<String> analysed = List.of("/src/main/java/app/OrderController.java");
final AnalysisOptions options = AnalysisOptions.full().withDepth(1);   // same as AnalysisOptions.oneLevel()
try (ProjectFiles files = new ProjectFiles("/path/to/repository")) {
    final CompileResult result = new ClarpseProject(files, Lang.JAVA, analysed, options).result();
    result.levelOne().levelOneFiles();   // the referenced files that were modelled
}
```

To compare two revisions, prepare each, join their level-one sets, and complete each with the
union, so a type loaded in one revision is loaded in the other. Preparing resolves the analysed
files once; completing reuses that work.

```java
try (PreparedAnalysis base = new ClarpseProject(baseFiles, lang, analysed, options).prepare();
     PreparedAnalysis head = new ClarpseProject(headFiles, lang, analysed, options).prepare()) {
    final Set<String> union = new TreeSet<>(base.levelOneFiles());
    union.addAll(head.levelOneFiles());
    final CompileResult baseResult = base.compile(union);
    final CompileResult headResult = head.compile(union);
}
```

A caller that learns from the first result which other files it needs in full can add them to
the same analyses with `extendFocus`, then compile again. Only the added files are resolved: the
declaration index, the files already parsed and the analysed files' model are reused, and nothing is
written to disk again. A file that was level one and is added becomes analysed: modelled in full,
no longer boundary. The result equals a fresh preparation of all the analysed files.

```java
try (PreparedAnalysis base = new ClarpseProject(baseFiles, lang, analysed, options).prepare();
     PreparedAnalysis head = new ClarpseProject(headFiles, lang, analysed, options).prepare()) {
    final Set<String> union = new TreeSet<>(base.levelOneFiles());
    union.addAll(head.levelOneFiles());
    final CompileResult first = head.compile(union);
    base.compile(union);

    final List<String> more = filesToModelInFull(first.model());   // the caller's choice
    base.extendFocus(more);
    head.extendFocus(more);
    final Set<String> union2 = new TreeSet<>(base.levelOneFiles());
    union2.addAll(head.levelOneFiles());
    final CompileResult baseResult = base.compile(union2);
    final CompileResult headResult = head.compile(union2);
}
```

A reference is internal, external, or **not loaded**: declared in the repository but absent from
the model. The three sets are disjoint, so in this mode a consumer that read
`internalDependencies()` and `externalDependencies()` must also read `notLoadedDependencies()`.
Only depth 1 is offered. See `docs/one-level-analysis.md` for how each language finds the
referenced files, what the model promises, why deeper levels are not offered, and the limits.

## Cleanup
Nothing an analysis creates outlives it:
- **Close what you open.** `ProjectFiles` and `PreparedAnalysis` are `AutoCloseable`; use
  try-with-resources. Closing is idempotent and also happens on failure and interruption.
- **Copies of sources.** TypeScript and Python resolve against files on disk, so `ProjectFiles`
  writes itself to a temporary directory on first use and reuses it; `close()` deletes it. A copy a
  one-level analysis caused is deleted when that analysis closes, or when preparing it fails. Java and
  C# one-level compiles write nothing to disk.
- **Resolver processes** (the Node daemons for TypeScript and Python) are stopped and waited for
  when their session ends, including when the calling thread is interrupted.
- **Temporary directories** are all named `clarpse-<kind>-<pid>-<start>-<random>` under
  `java.io.tmpdir`, naming the process that owns them, and are deleted by a JVM shutdown hook if
  still open at exit. A process killed without running its hooks can leave some behind:
  `ProjectFiles.deleteStaleTempDirs(Duration.ofHours(6))` deletes the `clarpse-` directories older
  than the given age whose owning process is no longer running, and returns them. A directory of
  another running JVM is never deleted, however old; a directory of an earlier process that had the
  same id, such as a restarted container's JVM, is. Run it at startup.

## Failure Contract
- Java/C#/TypeScript/Python all report recoverable issues in `CompileResult.failures()` using
  language-agnostic error codes.
- `CompileException` is reserved for non-recoverable compiler errors.

Standardized error codes:
- `1000` Node runtime not available.
- `1001` Language runtime bundle not available.
- `1002` Required project config is missing (for example `tsconfig.json`).
- `1003` Project config parse/validation failed.
- `1004` Program/repository initialization failed.
- `2001` File is outside active program/repository scope.
- `2002` File not found on disk.
- `2003` File parse/model extraction failed.
- `2004` Daemon transport/runtime error.
- `2005` File skipped due to excluded path rules.

## Cancellation
Clarpse honors `Thread.interrupt()` cooperatively, so a caller enforcing a time budget can abort a
runaway parse without killing the JVM. When the parsing thread is interrupted:
- **Java and C#** (in-process): queued per-file tasks stop draining and outstanding tasks are
  cancelled.
- **Python and TypeScript** (out-of-process Node daemon): the daemon process is destroyed, which
  unblocks the transport read that `Thread.interrupt()` alone cannot — the daemon computes in a
  separate process, so the interrupt is delivered by killing it.
- Model merging checks the interrupt flag periodically.

Cancellation is best-effort and safe: a cancelled parse throws (`CompileException` /
`CancellationException`) rather than returning a partial model. An interrupt is a request, not a
guarantee — work already deep inside a single file's parse or the external daemon stops at the next
checkpoint (per-file boundary, response line, or process teardown), typically within a few hundred
milliseconds.

# Adding or Updating a Language
Checklist for adding or updating a language implementation:

- Add or update the grammar in `src/main/antlr4/...`.
- Run `mvn generate-resources` to regenerate parser sources.
- Add a compiler in `src/main/java/com/hadi/clarpse/compiler`.
- Add a listener in `src/main/java/com/hadi/clarpse/listener`.
- Register the language and file extensions in `src/main/java/com/hadi/clarpse/compiler/Lang.java`.
- Add tests under `src/test/java` and fixtures under `src/test/resources`.

# Contributing A Patch

- Submit an issue describing your proposed change.
- Fork the repo, develop and test your code changes.
- Run `mvn test` and ensure all tests pass.
- If your change requires a version bump, update `pom.xml` and `README.md` using the x.y.z scheme:
  - x = main version number (breaking changes)
  - y = feature number (new features, optional bug fixes)
  - z = hotfix number (bug fixes only)
- Submit a pull request.

# License

Clarpse is released under the [MIT License](LICENSE). You are free to use it in commercial and closed-source products.

Maintained by [Hadi Technology](https://haditechnology.com), which also builds [Striff](https://striff.io), an architecture-aware pull request reviewer built on top of this library.
