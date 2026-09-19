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
final ProjectFiles files = new ProjectFiles("/path/to/repository");
final List<String> analysed = List.of("/src/main/java/app/OrderController.java");

final CompileResult result = new ClarpseProject(files, Lang.JAVA, analysed,
        AnalysisOptions.oneLevel()).result();

final OOPSourceCodeModel model = result.model();
final LevelOneReport levelOne = result.levelOne();
levelOne.levelOneFiles();         // level-one files, modelled
levelOne.heldByBudget();          // level-one files the budget kept out
levelOne.notLoadedReferences();   // references left in the not-loaded state
```

Every file of the language must be in `ProjectFiles`, not only the analysed ones. The analysed
paths use the form of `ProjectFile.path()`. A compile given no analysed paths (`null`) analyses
every file, and is then an ordinary compile.

### Comparing two revisions

A caller comparing two revisions needs the same level-one set in both. If a type were loaded in
one revision and not the other, it would look added or deleted when it was neither. So each
revision's level one is computed first, the two are joined, and both revisions are compiled with
the union:

```java
final AnalysisOptions options = AnalysisOptions.oneLevel();
final Set<String> union = new TreeSet<>();
union.addAll(new ClarpseProject(base, lang, analysed, options).levelOneFiles());
union.addAll(new ClarpseProject(head, lang, analysed, options).levelOneFiles());

final AnalysisOptions both = options.withLevelOnePaths(union);
final CompileResult baseResult = new ClarpseProject(base, lang, analysed, both).result();
final CompileResult headResult = new ClarpseProject(head, lang, analysed, both).result();
```

`levelOneFiles()` resolves the analysed files but models nothing past them. Paths passed to
`withLevelOnePaths` that name no file of the language, or that name an analysed file, are ignored.
A path of a file deleted in one revision therefore drops out of that revision's compile, as it
must.

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

A consumer that treats every reference that is not external as internal must check
`isNotLoaded()` first.

Both new fields are left out of JSON at their default values, so the serialised output of an
ordinary compile is unchanged.

## The budget

`AnalysisOptions.withLevelOneBudget(n)` caps how many level-one files are modelled. The default is
1000. The candidates, both discovered and caller-supplied, are sorted by path before the cap is
applied, so two compiles given the same candidates model the same files. Files beyond the cap are
reported by `LevelOneReport.heldByBudget()`, and references into them are left not loaded.

The cap follows path order, not relevance. In C#, it can separate the parts of a level-one partial
type.

The Java solver also caps how many distinct files it loads to resolve names: four times the budget,
and at least 1000. A lookup past that cap answers unsolved, and the listener falls back to the names
the source writes. `LevelOneReport.loadedBeyondLevelOne()` lists the files the solver read without
modelling them, typically supertypes and the return types of chained calls.

## Rules

These rules hold of the implementation. A change that breaks one changes what the model promises.

1. **An ordinary compile is unaffected.** A compile whose options are not `ONE_LEVEL`, or whose
   analysed paths are `null`, runs exactly `ClarpseCompiler.compile(ProjectFiles, Collection)`. Every
   language's `compile(ProjectFiles, Collection, AnalysisOptions)` starts by delegating to it in that
   case.
2. **Only `CompilerSupport.classifyReferences` decides that a reference is not loaded.** `Component`
   carries that state through copies and serialisation, but no front end sets it.
3. **Only `CompilerSupport.markBoundary` marks a component boundary**, and it marks exactly the
   components whose source file is one of the modelled level-one files.
4. **Only `LevelOneSelection.select` applies the level-one budget**, and every language's one-level
   compile chooses its level-one files through it.
5. **Only `IndexedTypeSolver` resolves repository types in a one-level Java compile.** In that mode
   `ClarpseJavaCompiler` builds its parser contexts through
   `JavaParserFactory.setupIndexedTypeSolver`, and never constructs a `JavaParserTypeSolver`.
6. **`IndexedTypeSolver` never scans a directory.** It reads only files that `JavaDeclarationIndex`
   names for the type being resolved, and every file it loads first passes
   `JavaLoadTracker.admit`.
7. **A one-level TypeScript program holds only planned files.** Once `planOneLevel` has run,
   `daemon.js` builds programs only from each config's `oneLevelRoots`, with `noResolve` and without
   project references, and answers `getFileModel` only for planned files.
8. **`CSharpModelAssembler` never emits a stub.** `buildModel(parsed, stubs, emittedPaths)` emits
   only components declared in `emittedPaths`, which never includes a file given as a stub.
9. **Level one never includes an analysed file.** `LevelOneSelection` drops analysed paths from the
   candidates, and `levelOneFiles()` returns none.

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
  languages writes the whole `ProjectFiles` to a temporary directory, as an ordinary compile does.
  Java and C# read the files from memory and write nothing.
