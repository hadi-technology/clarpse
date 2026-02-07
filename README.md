# :rocket: Clarpse 
Clarpse is a multi-language architectural code analysis library for building better software tools.

[![maintained-by](https://img.shields.io/badge/Maintained%20by-Hadi%20Technology-violet.svg)](https://haditechnology.com) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.hadi-technology/clarpse/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.hadi-technology/clarpse) [![Java CI](https://github.com/hadi-technology/clarpse/actions/workflows/ci-cd.yml/badge.svg?branch=master)](https://github.com/hadi-tech/clarpse/actions/workflows/ci-cd.yml) [![codecov](https://codecov.io/github/hadi-technology/clarpse/graph/badge.svg?token=7uf2jQMlH1)](https://codecov.io/github/hadi-technology/clarpse) [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg?style=flat-square)](http://makeapullrequest.com)

# Maven Dependency
```xml
<dependency>
  <groupId>io.github.hadi-technology</groupId>
  <artifactId>clarpse</artifactId>
<version>8.2.0</version>
</dependency>
```

Clarpse facilitates the development of tools that operate over the higher level, architectural details of source code, which are exposed via an easy to use, object oriented API. Checkout the power of Clarpse in [striff-lib](https://github.com/hadi-tech/striff-lib).

# What is Clarpse?
Clarpse is a multi-language parsing and analysis library that converts source code into a language-agnostic, object-oriented model. That model makes it easy to build tooling on top of architecture-level details like components, references, and structure without dealing with raw ASTs.

# Features

 - Supports **Java** with a lightweight, architecture-focused parser.
 - Supports **TypeScript** with compiler-accurate, tsconfig-aware parsing and resolution.
 - Light weight
 - Performant
 - Easy to use
 - Clean API built on top of AST
 - Support for parsing comments

# Requirements
 - Java 17
 - Maven 3.x
 - Node.js 18/20/22 (required for TypeScript parsing)
 - TypeScript compiler: `npm install -g typescript`

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

Parse a zip (Java or TypeScript):
```bash
curl -s -X POST "http://localhost:8080/parse?lang=typescript" \
  -H "Content-Type: application/zip" \
  --data-binary @project.zip
```

Notes:
- TypeScript parsing requires a valid `tsconfig.json` in the project input.
- Environment variables: `CLARPSE_PORT`, `CLARPSE_MAX_BYTES`, `CLARPSE_PARALLELISM`.

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
Clarpse supports a global parallelism setting for language compilers that can parse files in parallel.

- `CLARPSE_PARALLELISM` controls the max number of parser threads.
- Values `1` or lower force serial parsing.
- If unset, Clarpse uses `min(availableProcessors, fileCount)`.

Example:
`CLARPSE_PARALLELISM=4 mvn test`

# Repo Tour
Key areas of the repository:

- `src/main/java/com/hadi/clarpse/compiler` - Language compilers, project file handling, and orchestration.
- `src/main/java/com/hadi/clarpse/compiler/typescript` - TypeScript compiler bridge and models.
- `src/main/java/com/hadi/clarpse/listener` - Parse tree listeners that build the source model (Java).
- `src/main/java/com/hadi/clarpse/sourcemodel` - Component and package models.
- `src/main/java/com/hadi/clarpse/reference` - Component reference types.
- `src/main/resources` - Parser helpers and tool configuration (TypeScript daemon lives here).
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
- Compiler selection and results: `src/main/java/com/hadi/clarpse/compiler/CompilerFactory.java`, `src/main/java/com/hadi/clarpse/compiler/ClarpseCompiler.java`, `src/main/java/com/hadi/clarpse/compiler/CompileResult.java`
- Language compilers: `src/main/java/com/hadi/clarpse/compiler/ClarpseJavaCompiler.java`, `src/main/java/com/hadi/clarpse/compiler/typescript/ClarpseTypeScriptCompiler.java`
- Parse listeners: `src/main/java/com/hadi/clarpse/listener/JavaTreeListener.java`
- Source model: `src/main/java/com/hadi/clarpse/sourcemodel/OOPSourceCodeModel.java`, `src/main/java/com/hadi/clarpse/sourcemodel/Component.java`, `src/main/java/com/hadi/clarpse/sourcemodel/Package.java`
- References: `src/main/java/com/hadi/clarpse/reference/ComponentReference.java` and related types in `src/main/java/com/hadi/clarpse/reference`
- TypeScript daemon: `src/main/resources/typescript/daemon.js`

Architecture docs:
- `docs/typescript-architecture.md`

## Using The API
Clarpse abstracts source code into a higher level model in a **language-agnostic** way. This 
model focuses on the architectural properties of the original code. The code snippet below 
illustrates how this model can be generated from a `ProjectFiles` object which represents the 
source code to be analyzed.
```java
final String code = " package com.foo;  "
		       +  " public class SampleClass extends AbstractClass {                                                 "
		       +  "     /** Sample Doc Comment */                                              "
		       +  "     @SampleAnnotation                                                      "
		       +  "     public void sampleMethod(String sampleMethodParam) throws AnException {"   
		       +  "     SampleClassB.fooMethod();
		       +  "     }                                                                      "
		       +  " }                                                                          ";;
final ProjectFiles projectFiles = new ProjectFiles();
projectFiles.insertFile(new ProjectFile("SampleClass.java", code));
final ClarpseProject project = new ClarpseProject(projectFiles, Lang.JAVA);
CompileResult compileResult = project.result();
// Get the code model
OOPSourceCodeModel codeModel = compileResult.model();
// View any compile errors for any files
Collection<CompileFailure> failures = compileResult.failures();
```
Note, the `ProjectFiles` object can be initialized from a local directory, a local zip file, or an 
input stream to a zip file - see `ProjectFilesTest.java` for more information.

TypeScript usage follows the same API, but requires Node.js and a valid `tsconfig.json`:
```java
final ProjectFiles projectFiles = new ProjectFiles("/path/to/typescript-project");
final ClarpseProject project = new ClarpseProject(projectFiles, Lang.TYPESCRIPT);
CompileResult compileResult = project.result();
OOPSourceCodeModel codeModel = compileResult.model();
```

Next, the compiled 
`OOPSourceCodeModel` is the polygot representation of our source code through a 
collection of `Component` objects. Details about these components and the relationships 
between them can be fetched in the following way:
```java
codeModel.components().forEach(component -> {
        System.out.println(component.name());
	System.out.println(component.type());           
	System.out.println(component.comment());        
	System.out.println(component.modifiers());      
	System.out.println(component.children());       
	System.out.println(component.sourceFile());
	...
	// Check out the Component class for a full list of component attributes that can be retrieved
    });
```
We can also get specific components by their unique name:
```java
Component mainClassComponent = codeModel.get("com.foo.java.SampleClass");
mainclassComponent.name();           // --> "SampleClass"
mainClassComponent.type();           // --> CLASS
mainClassComponent.comment();        // --> "Sample Doc Comment"
mainClassComponent.modifiers();      // --> ["public"]
mainClassComponent.children();       // --> ["foo.java.SampleClass.sampleMethod(java.lang.String)"]
mainClassComponent.sourceFile();     // --> "foo.java"
mainClassComponent.references();     // --> ["SimpleTypeReference: String", "TypeExtensionReference: com.foo.AbstractClass", "SimpleTypeReference: com.foo.SampleClassB"]
// Fetch the the inner method component
methodComponent = codeModel.get(mainClassComponent.children().get(0));
methodComponent.name();              // --> "sampleMethod"
methodComponent.type();              // --> METHOD
methodComponent.modifiers();         // --> ["public"]
methodComponent.children();          // --> ["com.foo.java.SampleClass.sampleMethod(String).sampleMethodParam"]
methodComopnent.codeFragment();      // --> "sampleMethod(String)"
methodComponent.sourceFile();        // --> "foo.java"
methodComponent.references();		 // --> ["SimpleTypeReference: String"]
```
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
