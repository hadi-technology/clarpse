# One-Level Analysis

One-level analysis models a few files of a large repository together with the files they
reference, without compiling the rest of the repository.

The caller names the **analysed files**. Clarpse models them in full, finds the repository files
declaring what they reference (**level one**), models those as well, and stops. Every other file
of the repository is available for looking up names, and is never modelled.

## What it is for

A caller interested in part of a repository, such as the files a change touches, has had two
options, and neither suits a large repository.

- **Hand over only the files of interest.** Compiling is fast, but references that leave those
  files are lost or mislabelled. A reference to a repository type that was not handed over is
  classified `external`, the same as a library type, and some front ends drop such references
  outright. Measured against the best resolution Clarpse produces for the same files, this kept
  85% of the references of five apache/kafka files, 64% of five dotnet/orleans files, and 25% of
  five django files.
- **Hand over the whole repository and analyse a subset.** The references resolve, but the cost
  follows the repository. TypeScript builds a program from every file a `tsconfig.json` includes.
  Java's type solver parses a whole directory whenever a lookup misses. On microsoft/vscode that was
  about 37 s and 4 GB for one file.

A one-level compile costs what the analysed and level-one files cost, whatever the size of the
repository.

## Using it

```java
final List<String> analysed = List.of("/src/main/java/app/OrderController.java");
final AnalysisOptions options = AnalysisOptions.full().withDepth(1);   // or AnalysisOptions.oneLevel()

try (ProjectFiles files = new ProjectFiles("/path/to/repository")) {
    final CompileResult result = new ClarpseProject(files, Lang.JAVA, analysed, options).result();

    final OOPSourceCodeModel model = result.model();
    final LevelOneReport levelOne = result.levelOne();
    levelOne.levelOneFiles();         // level-one files, modelled
    levelOne.heldByBudget();          // level-one files the budget kept out
    levelOne.notLoadedReferences();   // references left in the not-loaded state
}
```

Every file of the language must be in `ProjectFiles`, not only the analysed ones. The analysed
paths use the form of `ProjectFile.path()`. A compile given no analysed paths (`null`) analyses
every file, and is then an ordinary compile.

### Depth

`AnalysisOptions.depth()` counts the levels of referenced files modelled past the analysed files.
Depth 0 is an ordinary compile, `AnalysisOptions.full()`. Depth 1 is this mode, and `withDepth`
accepts nothing else. Two things keep deeper levels out:

- **Fan-out.** A single analysed file of apache/kafka references 75 to 102 repository files. Level
  two is the files those reference, which on a repository of that shape approaches the whole
  repository, the cost this mode exists to avoid.
- **Boundary files are parsed shallowly.** Level one is modelled without the resolution that would
  say what it references in full: Java boundary files skip method-call resolution and TypeScript
  boundary files skip function bodies. Level two would need level one parsed in full first, so a
  depth of two is not the depth-one work repeated but a different, more expensive, first level.

The level-one names in the API (`LevelOneReport`, `LevelOneSelection`, `levelOneBudget`) name the
boundary level, the last level a compile models, which at depth 1 is level one.

### Comparing two revisions

A caller comparing two revisions needs the same level-one set in both. If a type were loaded in
one revision and not the other, it would look added or deleted when it was neither. So each
revision is prepared, the two level-one sets are joined, and each revision's compile is completed
with the union:

```java
final AnalysisOptions options = AnalysisOptions.oneLevel();
try (ProjectFiles baseFiles = new ProjectFiles(baseZip);
     ProjectFiles headFiles = new ProjectFiles(headZip);
     PreparedAnalysis base = new ClarpseProject(baseFiles, lang, analysed, options).prepare();
     PreparedAnalysis head = new ClarpseProject(headFiles, lang, analysed, options).prepare()) {
    final Set<String> union = new TreeSet<>(base.levelOneFiles());
    union.addAll(head.levelOneFiles());
    final CompileResult baseResult = base.compile(union);
    final CompileResult headResult = head.compile(union);
}
```

`prepare()` resolves the analysed files and discovers their level-one files, and `compile(union)`
models level one from that work: no analysed file is resolved twice and no index is built twice.
Paths passed to `compile`, or to `AnalysisOptions.withLevelOnePaths`, that name no file of the
language, or that name an analysed file, are ignored. A path of a file deleted in one revision
therefore drops out of that revision's compile, as it must.

### The prepared analysis and its lifetime

A `PreparedAnalysis` holds what completing the compile reuses:

| language | held between `prepare()` and `compile()` |
|---|---|
| Java | the declaration index, the units read to resolve names, and the analysed files' model |
| TypeScript | the discovered level-one set |
| Python | the analysed files' model and the module index |
| C# | the declaration index and the parsed file models |

It never holds a resolver process between calls. TypeScript and Python take a daemon session for
each phase and end it with the phase, so several prepared analyses may be open at once however few
daemons may run concurrently (`clarpse.node.maxConcurrentDaemons`, 1 by default). The cost is that
the TypeScript and Python daemons start, and read their configuration, once per phase.

Open it in a try-with-resources block. `compile` may be called more than once. `close()` is
idempotent, and after it every other method throws `IllegalStateException`. The `CompileResult`
holds none of the prepared state and outlives it. A prepared analysis is not thread-safe.

### Extending with more analysed files

A caller often learns only from a first result which other files it needs in full: files whose
outgoing references a check depends on, for example. `PreparedAnalysis.extendFocus(paths)` adds them
to the analysed files between compiles, and the analysis becomes the one `prepare()` would have
given for all of them: the same `levelOneFiles()`, and a `compile` whose result is equal to that of
a fresh preparation, component by component, boundary flags, reference sets, level-one report and
failures included. Paths already analysed, and paths that are not files of the language, are
ignored.

- **What is reused.** Only the added files are resolved. Java keeps its declaration index, the
  units read to resolve names, and the analysed files' model, and resolves the added files against
  the same index and unit cache. C# keeps its declaration index and every parsed file model, parses only the added
  files and the other parts of their partial types, and assembles the analysed files again from
  copies. Python keeps the module index and the analysed files' model, and models the added files in
  a daemon session of their own. TypeScript discovers level one again, over every analysed file, in
  a daemon session of its own; discovery resolves module specifiers without building a program, and
  programs are built by each `compile` for the configs that own its files, so nothing held needs
  rebuilding.
- **Level one is rediscovered over every analysed file**, not only the added ones, so the set is
  exactly a fresh preparation's.
- **Promotion.** A level-one file that is added becomes analysed: from then on it is modelled in
  full and is not boundary, and its own references are followed to level one. No prepared analysis
  keeps a level-one file's model between calls, so there is no shallow model to discard.
- **The budget** applies to the whole level-one set after the extension, as it does to a fresh
  preparation.
- **Daemons.** As in `prepare` and `compile`, TypeScript and Python open a daemon session only for
  the duration of `extendFocus`, so two analyses of two revisions can both be extended while only
  one daemon may run.
- **Disk.** An extension never writes the sources again. It reuses the copy the analysis already
  resolves against; if the analysis had no copy yet (it was prepared with no files of its language),
  the copy the extension causes belongs to the analysis and is deleted when it closes, even when the
  extension fails.
- **Failure.** An extension that throws leaves the analysis as it was; it may still be compiled,
  extended again or closed. An extension started on an interrupted thread throws
  `CompileException` before doing anything; one interrupted part-way throws `CompileException` or,
  as an interrupted compile can, `CancellationException`.
- **Memory** grows with the analysed files: each extension adds the added files' models, and in Java
  the units their resolution reads, up to the solver's load cap. All of it is released on `close()`.

### Cleanup

Nothing an analysis creates outlives it.

- **Resolver processes.** Every TypeScript and Python daemon is stopped when its session ends, by
  asking it to shut down, then killing it, and in both cases waiting for it to exit. That happens on
  success, on failure, and when the calling thread is interrupted, as an analysis deadline
  interrupts it: the interrupt kills the daemon at once.
- **The copy of the sources.** TypeScript and Python resolve against files on disk, so their
  `ProjectFiles` writes itself to a temporary directory, once, on first use, and every later
  compile of the same `ProjectFiles` reuses that copy. When preparing a one-level analysis caused
  the copy, the analysis owns it: the copy is deleted if preparing fails, and when the analysis is
  closed, whether the compile succeeded, failed or was interrupted. A copy that existed before is
  left to the `ProjectFiles`, whose `close()` deletes it. Java and C# one-level compiles work from
  the files in memory and write nothing to disk.
- **Temporary directories.** Every temporary directory Clarpse creates is named
  `clarpse-<kind>-<pid>-<start>-<random>` under `java.io.tmpdir`, where `pid` and `start` are the
  owning process's id and start time in epoch milliseconds. The kinds are `src` for copies of
  sources, and `ts-daemon` and `py-daemon` for extracted runtimes. Each stays registered until it is
  deleted, and a JVM shutdown hook deletes whatever is still registered. The Python runtime is
  extracted once per JVM and shared by every Python daemon, so it lives until the JVM exits; if it
  is gone when a daemon starts, it is extracted again.
- **After a crash.** A process killed by a signal it cannot handle, or by the kernel for memory,
  runs no shutdown hook. `ProjectFiles.deleteStaleTempDirs(Duration olderThan)` deletes the
  `clarpse-` directories under `java.io.tmpdir` that this JVM does not have open, that are older
  than the given age, and whose owner is no longer running, and returns them. The owner counts as
  running only when a live process has both the id and the start time in the name, so a directory of
  an earlier JVM that happened to get the same id, as a restarted container's JVM does, is still
  removed, while a directory another running JVM holds, however old, is not. A `clarpse-` directory
  whose name carries no owner is judged by its age alone.

## How level one is found

Level one is always found without compiling the repository.

| language | level one is | how it is found | how resolution stops at one level |
|---|---|---|---|
| Java | the files declaring the types the analysed components' references name | `JavaDeclarationIndex`: every file's package plus its file name and the types declared at the start of a line, read without parsing. A reference's fully qualified name is looked up by its longest indexed prefix, so a nested type leads to its top-level type's file. | Types resolve only through `IndexedTypeSolver`, which reads the files the index names and never scans a directory. Level-one files are parsed with method calls attributed from the names the source writes, without resolving the call or its receiver. |
| TypeScript | the files the analysed files' module specifiers resolve to, closed over the re-exports of those files | `ts.preProcessFile` and `ts.resolveModuleName` with the options of the config owning each file, so `paths` and `baseUrl` apply. Re-exports (`export * from`, `export {…} from`) are followed with a syntax-only parse, since a name imported through a barrel is declared in the file the barrel re-exports. | Programs are built from the planned files only, with `noResolve` and without project references. Level-one files are modelled without reading their bodies. |
| Python | the modules declaring the names the analysed components' references resolve to, including names re-exported through a package's `__init__.py`, module-level functions, and names imported inside function bodies | The resolver names a repository symbol by its declaring module's dotted path, so `PythonModuleIndex` recovers the module from the name. | The resolver already reads a referenced module only for what it declares, and caches it. |
| C# | the files declaring the types the analysed components' references name, with every part of their partial types | `CSharpDeclarationScanner` reads each file's namespaces and type declarations lexically. From it `CSharpDeclarationIndex` builds declaration-only stub file models, through which the assembler resolves names against the whole repository with its usual rules. | Only the analysed, level-one and `global using` files are parsed; the rest are stubs. |

Some files are always handled with the analysed files:

- **C# partial types.** The other parts of an analysed file's partial types are analysed too, since
  a type is only complete with all its parts.
- **C# `global using` files.** Every file carrying `global using` directives is parsed, because
  those directives apply to every file of the project. Its components are emitted only when the
  file is analysed or level one.
- **TypeScript `.d.ts` roots.** Every program also holds the `.d.ts` root files of its config, so
  global declarations and module augmentations in the repository still resolve.

In C#, a type declared both in files marked `<auto-generated>` and in files that are not is taken
to be declared by the hand-written ones. Generated files commonly redeclare a project's whole
public surface; on dotnet/orleans, counting them grew one file's level one from 93 files to 711.

## What the model promises

- **An analysed component's outgoing references** are as complete as the best resolution Clarpse
  produces for that file, with the exceptions under *Limits*.
- **A boundary component's declarations** are complete: its members, supertypes and modifiers.
  In TypeScript, local variables declared inside a boundary file's function bodies are the
  exception.
- **A boundary component's outgoing references are not complete.** An edge to something past level
  one can be missing, or can appear in the not-loaded state.
- **Incoming references** exist only from files that were modelled. Finding every file that
  references a type needs a search of the whole repository, which this mode does not do.

### Boundary components

`Component.isBoundary()` is `true` for every component of a level-one file. For a boundary
component, a missing edge says nothing: "the model has no edge from B to X" must not be read as
"B does not depend on X". A question about a boundary component's own dependencies cannot be
answered from a one-level model.

### The not-loaded reference state

A reference is in exactly one of three states:

| state | meaning | how to read it |
|---|---|---|
| internal | the model holds a component by the reference's name | `component.internalDependencies()` |
| external | the name is not declared in the repository | `component.externalDependencies()`, `reference.isExternal()` |
| not loaded | the name is declared in the repository but the model holds no component for it | `component.notLoadedDependencies()`, `reference.isNotLoaded()` |

Only a one-level compile produces the not-loaded state. A reference ends up in it when:

- it points past level one, from a boundary component;
- the budget held back the file declaring its target;
- in TypeScript, its type was lost. With `noResolve`, a type declared past level one has no
  declaration, and becomes `any` once combined with another type. Such references keep the name
  the compiler gives them, are marked `ResolutionKind.UNRESOLVED`, and may name `any`.

The three sets are disjoint: `internalDependencies()`, `externalDependencies()` and
`notLoadedDependencies()` never share a reference, and together they are `references()`. In
particular `notLoadedDependencies()` is disjoint from `externalDependencies()`. A consumer that read
a component's dependencies as internal plus external must also read `notLoadedDependencies()` in this
mode, or it will miss every reference to a repository type that was not loaded. A consumer that
treats every reference that is not external as internal must check `isNotLoaded()` first.

Both new fields are left out of JSON at their default values, so the serialised output of an
ordinary compile is unchanged.

## The budget

`AnalysisOptions.withLevelOneBudget(n)` caps how many level-one files are modelled. The default is
1000. The candidates, both discovered and caller-supplied, are sorted by path before the cap is
applied, so two compiles given the same candidates model the same files. Files beyond the cap are
reported by `LevelOneReport.heldByBudget()`, and references into them are left not loaded. After
`extendFocus` the cap applies to the whole level-one set, not to what each extension adds.

The cap follows path order, not relevance. In C#, it can separate the parts of a level-one partial
type.

The Java solver also caps how many distinct files it loads to resolve names: four times the budget,
and at least 1000. A lookup past that cap answers unsolved, and the listener falls back to the names
the source writes. `LevelOneReport.loadedBeyondLevelOne()` lists the files the solver read without
modelling them, typically supertypes and the return types of chained calls.

## Rules

These rules hold of the implementation. A change that breaks one changes what the model promises.

1. **An ordinary compile is unaffected.** A compile whose depth is 0, or whose analysed paths are
   `null`, runs exactly `ClarpseCompiler.compile(ProjectFiles, Collection)`:
   `ClarpseCompiler.compile(ProjectFiles, Collection, AnalysisOptions)` delegates to it in that case,
   and otherwise runs `prepare` and `PreparedAnalysis.compile`.
2. **Only `CompilerSupport.classifyReferences` decides that a reference is not loaded.** `Component`
   carries that state through copies and serialisation, but no front end sets it.
3. **Only `CompilerSupport.markBoundary` marks a component boundary**, and it marks exactly the
   components whose source file is one of the modelled level-one files.
4. **Only `LevelOneSelection.select` applies the level-one budget**, and every language's one-level
   compile chooses its level-one files through it, from `AbstractPreparedAnalysis.compile`.
5. **Only `IndexedTypeSolver` resolves repository types in a one-level Java compile.** In that mode
   `ClarpseJavaCompiler` builds its parser contexts through
   `JavaParserFactory.setupIndexedTypeSolver`, and never constructs a `JavaParserTypeSolver`.
6. **`IndexedTypeSolver` never scans a directory.** It reads only files that `JavaDeclarationIndex`
   names for the type being resolved, through `JavaUnitCache.resolved`, which parses a file only
   once `JavaLoadTracker.admit` admits it.
7. **A unit shared between Java parser threads is never modified.** The compile's one
   `JavaUnitCache` holds only units read to resolve names, parsed once, without a symbol resolver,
   and shared read-only by every thread's solver across both phases. A file walked into the model is
   parsed by the walking thread alone, with that thread's own resolver, and is never taken from or
   put into the cache. A file both walked and read to resolve names is therefore parsed once for
   each role. Releasing the prepared analysis clears the cache and calls
   `JavaParserFacade.clearInstances()`.
8. **A one-level TypeScript program holds only planned files.** Once `planOneLevel` has run,
   `daemon.js` builds programs only from each config's `oneLevelRoots`, with `noResolve` and without
   project references, and answers `getFileModel` only for planned files.
9. **`CSharpModelAssembler` never emits a stub.** `buildModel(parsed, stubs, emittedPaths)` emits
   only components declared in `emittedPaths`, which never includes a file given as a stub.
10. **A C# file model is assembled only through a copy.** `ClarpseCSharpCompiler` assembles
    `CSharpFileModel.assemblyCopy()` of each parsed model, so a prepared analysis can assemble
    again.
11. **Level one never includes an analysed file.** `LevelOneSelection` drops analysed paths from the
    candidates, and `PreparedAnalysis.levelOneFiles()` returns none.
12. **A prepared analysis holds no resolver process between calls.** The TypeScript and Python
    prepared analyses open a daemon only inside `prepare`, `extend` and `complete`, each in a
    try-with-resources block.
13. **Only `ClarpseTempDirs` creates or deletes a Clarpse temporary directory**, always named
    `clarpse-<kind>-<pid>-<start>-<random>`; `ProjectFiles` and `DaemonResourceExtractor` go through
    it, and its sweep never deletes a directory whose owner is running.
14. **A prepared analysis deletes only the copy it caused.** `AbstractPreparedAnalysis.prepareCleanly`
    takes ownership of the `ProjectFiles` temporary directory only when it did not exist before
    preparing, and deletes it when preparing fails or the analysis is closed.
    `AbstractPreparedAnalysis.extendFocus` likewise takes ownership of a copy that did not exist
    before the extension, whether or not the extension succeeds.
15. **An extension commits only when it succeeds.** `AbstractPreparedAnalysis.extendFocus` passes
    the added files and every analysed file to the language's `extend`, which rediscovers level one
    over every analysed file, and replaces the analysed files and the level-one set only after
    `extend` returns.

## Limits

- **TypeScript types reached by inference.** A type the analysed file never imports, reached only
  through a level-one type, is lost when it is declared past level one. An example is the result
  of a method called on a level-one object. On microsoft/vscode this was 1.4–2.6% of an analysed
  file's internal references. Such references are not loaded, never silently dropped. An
  explicitly written `any` is flagged the same way, since the two cannot be told apart.
- **TypeScript discovery** does not follow `/// <reference path>` directives, and does not find
  `declare global` blocks in modules that are not `.d.ts` files.
- **Java boundary files** attribute method calls from written names. A call on a type named as its
  receiver (`Util.make()`, `deep.D.make()`) is attributed. A call on a variable (`c.go()`) is not,
  since naming the variable's type needs the resolution the boundary pass skips, and neither is any
  edge found only by resolving a chain of calls. The variable's own declaration still references its
  type.
- **C# extension methods.** An extension method's link onto the type it extends is made by the file
  declaring the extension. An extension declared in a file that is not modelled links nothing.
- **C# preprocessor conditionals** are not evaluated by the declaration scanner, which, like the
  parser, sees every branch.
- **Disk.** TypeScript and Python resolve against the files on disk, so a one-level compile in those
  languages writes the whole `ProjectFiles` to a temporary directory, as an ordinary compile does, for
  the lifetime of the analysis (see *Cleanup*). Java and C# read the files from memory and write
  nothing.
