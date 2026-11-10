# The clarpse model contract

This document is for users of the library. It says what an `OOPSourceCodeModel` contains, what
each part means, and what clarpse promises about it across Java, Kotlin, C#, TypeScript and
Python. It describes the intended contract. Where a point is not yet settled, it is marked
**TODO: decide**, with the current behaviour stated beside it.

What clarpse is for: an **architecture-level** model of a codebase. That means the declared
types, their members, and the uses of one named thing by another. It is not a syntax tree, a type
checker or a call graph with statement-level precision. It is built from source text alone. The
build is not run, dependencies are not installed, and no generated code is seen.

---

## 1. Components

A **component** (`com.hadi.clarpse.sourcemodel.Component`) is one declaration in the analysed
source: a type, a member of a type, a module-level declaration, a parameter or a local.

Every component has:

| accessor | meaning |
|---|---|
| `uniqueName()` | The component's identity in the model. Unique within one model. |
| `name()` | The declared name, without qualification. |
| `componentType()` | One of the types in §1.2. |
| `pkg()` | The packaging unit the declaration belongs to (§1.3). |
| `module()` | The module/file-level unit, where the language has one (TS, Python). |
| `sourceFile()` | The project path of the declaring file (§6). |
| `children()` | Unique names of the components declared directly inside this one, in declaration order. |
| `modifiers()` | Declared modifiers, lower-cased, from a closed vocabulary (`OOPSourceModelConstants.AccessModifiers`). |
| `references()` | What this component uses (§2). |
| `imports()`, `importedFiles()` | What the declaring file imports (§5). |
| `comment()`, `codeFragment()`, `cyclo()`, `codeHash()` | Documentation comment, declaration text, cyclomatic complexity, content hash. |
| `isBoundary()` | Only in a one-level analysis (§7). |

### 1.1 Unique names

- A type is named by its qualified name, with nested types joined by `.`. Examples: `com.acme.Order`,
  `com.acme.Order.Line`.
  - TypeScript and Python qualify by the module's dotted path (`src.billing.Order`).
  - Kotlin uses the name the JVM sees, so top-level functions and properties are members of
    `<File>Kt`, or of the `@file:JvmName` name.
  - C# leaves a generic type's arity out (`Acme.Repo`), unless two arities share the name
    (``Acme.Converter`1``).
- A method or constructor is the owning type's name, `.`, the member's name, and its parameter
  types in parentheses: `com.acme.Order.total(Currency, boolean)`.
- A field, property or enum constant is the owner's name plus `.name`.
- A parameter or local is the owning callable's unique name plus `.name`.
- `parentUniqueName()` is derived from the unique name. It is not a stored link.

### 1.2 Component types

`OOPSourceModelConstants.ComponentType`. Three flags classify each type: *base* (a type that
declares members), *method* (a callable) and *variable* (holds a value).

| type | base | method | variable | what it is | languages |
|---|:-:|:-:|:-:|---|---|
| `CLASS` | ✓ | | | a class; also a Kotlin `object`/companion, a Java `record`, a Python class | all |
| `STRUCT` | ✓ | | | a value type | C# |
| `INTERFACE` | ✓ | | | an interface or protocol-like declaration | Java, Kotlin, C#, TS |
| `ENUM` | ✓ | | | an enumeration (a Python `Enum` subclass included) | all |
| `ANNOTATION` | ✓ | | | an annotation or attribute type declaration | Java, Kotlin, C# |
| `METHOD` | | ✓ | | a member function | all |
| `CONSTRUCTOR` | | ✓ | | a constructor | all |
| `FUNCTION` | | ✓ | | a module-level function | TS, Python |
| `FIELD` | | | ✓ | a field or property of a type | all |
| `MODULE_FIELD` | | | ✓ | a module-level binding (`const`, module variable) | TS, Python |
| `ENUM_CONSTANT` | | | ✓ | an enum member | all with enums |
| `INTERFACE_CONSTANT` | | | ✓ | a constant declared on an interface | Java |
| `TYPE_ALIAS` | | | | a name for a type expression (`type X = …`); declares no members | TS |
| `NAMESPACE` | | | | a named scope declared in code (`namespace N { }`, `module N { }`) that holds declarations as children | TS |
| `METHOD_PARAMETER_COMPONENT` | | | ✓ | a parameter of a callable | all |
| `CONSTRUCTOR_PARAMETER_COMPONENT` | | | ✓ | a parameter of a constructor | all |
| `LOCAL` | | | ✓ | a local variable | all |

New values may be added in minor versions. Code that switches over `ComponentType` must have a
default branch.

**TODO: decide** the exact per-language coverage of each row. The table above states what the
current front ends emit as far as this document's authors know. It should be generated from tests.

### 1.3 Packaging units are not components

A language's packaging unit is a `Package` (`Component.pkg()`), never a component. That covers
Java and Kotlin packages, C# namespaces, Python packages and TypeScript directories, and for TS
and Python the module as well (`Component.module()`).

`NAMESPACE` is the one exception, and it is deliberate. A TypeScript `namespace` block is a
declaration in code: it is a runtime value, it can merge with a class, function or enum of the
same name, and its exported members need their own names (`src.shapes.Geometry.Point`). C#
namespaces, Java and Kotlin packages and Python modules are not components. They appear only as
name prefixes (`Acme.Billing.Order`, `com.acme.Order`, `billing.Order`) and in `pkg()` and
`module()`.

Consequences:
- The parent of a type is either another component (an enclosing type, or a `NAMESPACE` in TS)
  or nothing.
- `OOPSourceCodeModel.parentBaseComponent(name)` walks through non-base parents and throws when
  no type encloses the component, as it does for a module-level function.

### 1.4 Merged declarations

One logical declaration written in several places is one component:
- C# `partial` types;
- Kotlin `expect`/`actual` pairs;
- TypeScript declaration merging (interface + interface, class + namespace, the namespace
  blocks of one name in one file).

A TypeScript namespace declared in several files is one component per file, named by the file.

**TODO: decide** how a reference to such a declaration binds when several are candidates. Today
TS binds a reference to a namespace made only of blocks to the block in the file whose path
sorts first, and Kotlin binds a call to an `expect` function to the first declaring file in path
order. Both are deterministic, but they are choices the source does not make. Under §8 the
reference should either carry `ResolutionKind.AMBIGUOUS` or be withheld.

---

## 2. References

A **reference** (`com.hadi.clarpse.reference.ComponentReference`) records that a component
**uses** a named thing. The thing is a type, a module-level declaration, or (§2.3) a member of a
type. The source component is the one whose `references()` holds it; `invokedComponent()` names
the target.

### 2.1 Kinds

| kind (`TypeReferences`) | class | JSON `type` | meaning |
|---|---|---|---|
| `SIMPLE` | `SimpleTypeReference` | `simple` | an ordinary use: a declared type, a constructor call, a static access, a call on a typed receiver, a type argument, a `typeof` operand, a bound or default of a type parameter |
| `EXTENSION` | `TypeExtensionReference` | `extension` | inheritance: `extends`, a base class, the first entry of a Kotlin supertype list with a constructor call, an interface extending interfaces |
| `IMPLEMENTATION` | `TypeImplementationReference` | `implementation` | interface implementation |
| `ANNOTATION` | `AnnotationReference` | `annotation` | an annotation, attribute or decorator applied to this declaration, with literal arguments where read. Not a use dependency. |

Rules that hold for every kind:
- **Identity.** Two references are equal when they have the same class and the same target name.
  `resolutionKind()` and annotation arguments take no part in equality.
- **Targets are names, not type expressions.** `List<Order>` is recorded as a reference to the
  erased `List` and a separate reference to `Order`. `Order[]` is recorded as `Order`.
- **Propagation.** A component's `SIMPLE` references include those of every component it
  encloses, so a class carries what its methods use. `EXTENSION`, `IMPLEMENTATION` and
  `ANNOTATION` stay on the declaration they are written on.
- **A type variable is never a target.** `T` in `class Box<T>` names no type. Its bounds,
  constraints and defaults are `SIMPLE` references of the declaring component.
- A component never references itself.

### 2.2 What a target may name

- an internal component: a type, a module-level function or field, a `NAMESPACE`, or a member
  (§2.3);
- an external name (§4);
- the name as written, when nothing in scope declares it (§3).

**TODO: decide** the class name. `SimpleTypeReference` says "type", but TS targets module
functions and values, and C# targets extension methods. Renaming it is a 12.0 change. Until then
read "type" as "named thing".

### 2.3 Member references

Documented with the MemberReference type.

### 2.4 Extension edges

A C# type in the repository references each extension method declared for it
(`App.IShape` → `App.ShapeExt.Describe(IShape)`). That edge points from the extended type to the
extension, the reverse of the use. **TODO: decide** whether it becomes a query
(`extensionsOf(type)`) or a distinct kind that dependency consumers can skip.

---

## 3. Resolution

### 3.1 Resolution kinds

`ComponentReference.resolutionKind()` says how the target name was arrived at. It is not part of
equality. Every language reports it.

| value | meaning |
|---|---|
| `EXACT` | Exactly one declaration is visible under the name, by the language's scoping rules: an import, an alias, a qualified name, an enclosing scope, the file's own package or namespace, a default import, or a compiler symbol. The target is that declaration. |
| `AMBIGUOUS` | Several declarations are **visible** under the name and the source does not choose between them, so nothing is bound. The target is the name as written. |
| `UNRESOLVED` | No declaration is visible under the name. The target is the name as written, and it may still be a repository type that was not visible from the file. |
| `UNSPECIFIED` | The language does not report how a reference was resolved. A front end that reports the other three never produces it. |
| `UNIQUE_SIMPLE_NAME` | Deprecated and no longer produced. A short-name match outside the scoping rules is not a resolution. Do not branch on it. |

"Visible" is the language's own scope: a type that exists somewhere in the repository but cannot
be named from this file is not visible, so it makes a reference neither `EXACT` nor `AMBIGUOUS`.

The Java and Python front ends are being brought to this contract in the same release as the
others; a front end that has not yet been is a deviation from the contract, not a second meaning.

**TODO: decide:**
- C# reports `AMBIGUOUS` when the *repository* holds two or more types of the short name, even
  when none is visible. Two `using` directives that both supply the name resolve to the first,
  as `EXACT`. The contract above is the opposite on both counts.
- In TypeScript, `UNRESOLVED` is also used in a one-level analysis for a type lost to `any`, and
  such references are classified not loaded. The contract gives `UNRESOLVED` one meaning (nothing
  visible declares it), with "lost" carried by `isNotLoaded()` alone, or by a separate value.

### 3.2 Internal, external, not loaded

Every reference of a component is in exactly one of three states, and the three sets together
are `references()`:

| state | test | accessor | meaning |
|---|---|---|---|
| internal | the model holds a component named by the target | `internalDependencies()` | the target is in this model |
| not loaded | `isNotLoaded()` | `notLoadedDependencies()` | the target is declared in the repository but this model does not hold it. One-level analysis only (§7). |
| external | `isExternal()` | `externalDependencies()` | **anything else** |

External is the remainder. It holds:
- library and platform names (§4);
- names kept as written because nothing in scope declares them, which may be repository
  types that were not visible (`ResolutionKind.UNRESOLVED`/`AMBIGUOUS`);
- member targets of the `pkg:Type.member` form (§2.3).

So "external" means "not a component of this model". It does not prove the target lies
outside the repository. A user who wants "library dependencies" should take `externalDependencies()`
and drop those whose `resolutionKind()` is `UNRESOLVED` or `AMBIGUOUS`. Where the language reports
`UNSPECIFIED`, the user cannot make this distinction (§3.1).

**TODO: decide** whether the javadoc of `isExternal()` ("a type outside the codebase") is
corrected to this meaning, or the classification is changed so that `UNRESOLVED`/`AMBIGUOUS`
names get a state of their own. The latter is a 12.0 change.

### 3.3 Merging models

`OOPSourceCodeModel.merge` reclassifies: a reference whose target the merged model holds becomes
internal, in either merge order. This is how Java and Kotlin compiled separately from one
`ProjectFiles` join up.

### 3.4 What resolution reads

Resolution reads the source files in `ProjectFiles` and nothing else. Two exceptions:
- **JDK types** are resolved against the JDK that clarpse runs on. This is a property of both Java
  and Kotlin resolution. Java uses JavaParser with a reflection-based type solver, and Kotlin looks
  up the names a `java.*` or `javax.*` star import brings in, and JDK supertypes, by reflection.
  The same source can therefore give a different model on a different JDK version, for example
  when a type exists in one JDK and not in another. The one fixed table is the implicit
  `java.lang` import (§9), which lists the `java.lang` types of Java 21 and does not depend on the
  running JDK.
- **TypeScript and Python** use the bundled compiler's and type checker's own library
  declarations (`lib.*.d.ts`, the typeshed standard library).

Installed dependencies (`node_modules` contents beyond what is in the project, Maven or Gradle
jars, NuGet packages, site-packages) are not read.

---

## 4. External names

An external name is the best name the source gives for something outside the model. The form
depends on the language.

| language | external type | example | notes |
|---|---|---|---|
| Java | fully qualified, nested types with `.` | `java.util.Map.Entry`, `org.lib.Client` | Implicitly imported: `java.lang` top-level types only. A name two on-demand imports both supply (java.lang counts as one) is not recorded. |
| Kotlin | fully qualified | `kotlin.collections.List`, `io.ktor.server.Application` | Default imports (`kotlin.*`, `kotlin.collections.*`, …) apply last. Type aliases resolve to what they alias. |
| C# | qualified only where an alias or a keyword says so; otherwise the name as written | `System.Int32` (from `int`), `List` | A framework type named through a namespace `using` stays as written, with `UNRESOLVED`. |
| TypeScript | `<package>:<exported name>` | `react:Component`, `@scope/pkg:Thing`, `node:fs:readFile` | The package is the import specifier's package (`@scope/pkg/sub` → `@scope/pkg`). Compiler-library globals (`Error`, `Map`) are written bare. |
| Python | `<module path>.<name>` | `requests.Session` | Labels from imports. The library is not inspected. |

**TODO: decide:**
- **One shape across languages.** TS uses `package:name`; the other four use dotted names. A
  general form would keep the dotted name in `invokedComponent()` and expose the origin
  separately (for example `externalOrigin()`: the package, module or assembly when known).
- **The `node:` prefix** gives `node:fs:readFile` beside `fs:readFile` for the same module.
  Intended: normalise to one package name.
- **Member targets.** Until the member reference kind (§2.3) is the only form, a call on an
  external receiver may be recorded as a string of the form `<package>:<Type>.<member>`. That
  string cannot be told apart from a TS nested name, and its split is ambiguous for nested types.

---

## 5. Imports

### 5.1 `imports()`

`Component.imports()` is what the declaring file's import directives say, as names. It is
attached to the file's types and, in TS and Python, to its module-level declarations.

- **Java, Kotlin:** the imported names, including wildcards (`a.b.*`) and static imports.
- **C#:** `using` targets, including namespaces, `using static` and the targets of aliases. The
  global usings of the project apply to every file.
- **TypeScript:** the resolved dotted name of what is imported, or the specifier for a package.
  Static imports, `export … from`, literal `import()`, `require()` and the function `createRequire`
  returns are all included.
- **Python:** imported module paths and names. `importlib.import_module`, `import_module` and
  `__import__` with a literal argument are included.

`imports()` is descriptive. It is not resolved against the model and makes no claim that the
imported thing exists.

### 5.2 `importedFiles()`

`Component.importedFiles()` is the set of **project files that declare what the declaring file's
import directives name exactly**, in the order the imports first appear, without repeats. Each entry
is a path in the form of `Component.sourceFile()` (§6), so it compares directly with the source file
of another component. The set is unmodifiable and never `null`. It is attached to the same
components as `imports()`. No path is derived from the text of an import.

It is **not** the set of files a file depends on. Uses that need no import never contribute:
same package, same namespace, enclosing namespace, implicit and default imports. Imports that do
not name a file never contribute either.

| language | an entry comes from | gives nothing |
|---|---|---|
| TypeScript | a static import, `export … from`, literal `import()` or `require()`, resolved by relative path, `paths` alias or workspace link | packages in `node_modules`, unresolved specifiers |
| Python | `import a.b.c` → the file of `a.b.c`; `from a.b import c` → the submodule `c`'s file, else the file of `a.b`; star and relative imports; literal dynamic imports | stdlib, stubs, installed packages, unresolved imports, non-literal dynamic imports, namespace-package directories |
| Java | a single-type import → the file of the top-level type it falls under; a static import, or a static on-demand import over a type, → the file of that type; reaches Kotlin files | an on-demand import of a package |
| Kotlin | a name import → every file declaring a type, alias or top-level callable of that name; a member name → the file of its type; reaches Java files | a star import of a package |
| C# | `using static` and type aliases → every file declaring the type (all partial parts) | a namespace `using`, a namespace alias, global and implicit usings applied from elsewhere |

A library, framework, JDK or unresolved target, a file outside the project, and the importing
file's own path never contribute. A component of a boundary file (§7) reports what the resolution
of its file gives, exactly as for an analysed file.

**TODO: decide** how a user tells an incomplete set from an empty one. Today an empty set may mean
any of these:
- the file imports nothing;
- everything it imports is external;
- it uses only wildcard or namespace imports;
- its imports did not resolve.

Intended: a flag such as `importedFilesComplete()`, false when the file has an import directive
whose targets were not expanded (wildcard, namespace, unresolved, non-literal dynamic). Or a
structured import record (written text, kind, resolved files, status) that subsumes `imports()`
and `importedFiles()`.

---

## 6. Paths

A path clarpse reports is the path the file was loaded under. In 11.x two forms exist, and which
one you get depends on how the project was loaded:

- **A directory load** (`new ProjectFiles("/path/to/repo")`) reports absolute disk paths, for
  example `/home/u/repo/src/Order.java`.
- **An archive load** reports `/`-rooted in-archive paths, for example `/src/Order.java`. An
  archive whose entries share one wrapping folder gives `/<folder>/src/...` unless
  `ProjectFiles.shiftSubDirsLeft()` is called.
- **In-memory files** keep the path they were given, normalised to forward slashes and one leading
  `/`.

A single in-tree form (the file's path inside the project, the same for all three) is planned for
12.0.0. Changing the form earlier would break code that compares or stores paths, so 11.x keeps
today's forms. Until then, compare paths from one load with each other, and do not mix paths from a
directory load with paths from an archive load of the same tree.

The path is used by:
- `Component.sourceFile()`;
- `ProjectFile.path()`;
- `ProjectFiles.files()`;
- `UnreadSourceFiles.paths()`;
- `LevelOneReport` paths;
- `CompileFailure` file paths;
- `Component.importedFiles()`, in the same form as `sourceFile()`.

`ProjectFile` accepts relative or absolute input, normalises it to forward slashes and rejects
`..` traversal. `new ProjectFile(File)` stores the file's absolute path.

**TODO: decide** (for 12.0.0, with the in-tree form):
- **On-disk paths as input.** Analysed-file paths accept an on-disk path under the loaded
  directory. `PreparedAnalysis.extendFocus` and the level-one path options do not, and silently
  ignore one. Intended: every path-taking API accepts both forms.
- **Getting back to disk.** Intended: provide `ProjectFiles.diskPathOf(projectPath)`.

---

## 7. One-level analysis and boundary components

With `AnalysisOptions.oneLevel()`, clarpse models the analysed files in full, plus the
repository files they reference (**level one**), and nothing further.

- A component of a level-one file has `isBoundary() == true`. Its declarations are complete.
  **Its outgoing references are not.** A dependency past level one may be missing, or may appear
  as not loaded. A missing edge on a boundary component is not evidence of absence.
- A reference to a repository declaration the model does not hold is not loaded
  (`isNotLoaded()`), never external.
- Only depth 1 is offered.
- Java and TypeScript model level-one files without reading their bodies, so references that
  need a body or the type solver (member targets, §2.3; inferred local types; calls on variables)
  are absent from their boundary components. Kotlin and C# parse level-one files in full.

---

## 8. Precision policy

clarpse records a reference only when the declaration it names can be determined from the source
and the project. When it cannot, clarpse records nothing, or keeps the name as written with
`ResolutionKind.UNRESOLVED`. It never guesses a repository target: a missing edge is preferred to a
wrong one, so a model can be missing references but should not hold false ones.

### 8.1 What this means in practice

- **A receiver's type must be known** before a call on it adds anything beyond the receiver's
  own type. It is known when:
  - the receiver is a parameter, local, field or property whose declaration writes its type; or
  - the receiver is `this`; or
  - the receiver is a local initialised by a plain constructor call, or by a declaration whose
    return or property type is written.

  It is unknown when:
  - the receiver is the result of a call chain;
  - the receiver's name is declared again in the body (a local function parameter, a pattern
    variable, a loop variable);
  - the receiver is tested, cast or reassigned (Kotlin);
  - the receiver's type is an array or pointer, which is never read as its element type.
- **Member lookup needs a complete ancestry.** An extension call or an inherited name is credited
  only when every supertype of the receiver, or of the enclosing class, is parsed. Otherwise an
  unseen supertype could declare the member, and the language would bind to it first.
- **The closest scope wins.** Locals and parameters come first, then members of the class and its
  supertypes, then explicit imports, the file's own package, on-demand imports, and default imports
  last. A name a closer scope binds adds no reference to an import of the same name.
- **No guessing across scopes.** A short name is never bound to a repository type just because
  that type is the only one with the name. It binds only when the type is visible from the file.
- **Ambiguity is not resolved by order.** Two on-demand imports that both supply a name, or two
  candidate extension classes, give no binding.
- **Values are not types.** A dotted chain that starts with a parameter, local or member is not
  read as a package-qualified type. A type variable is never a type. Checker-rendered type text
  (`(typeof Bar)[]`) is never a target.
- **Platform types count as known.** JDK types read from the running JVM, and the TS and Python
  standard libraries, count as known (§3.4).

### 8.2 Telling "no reference" from "could not tell"

A missing reference means one of two things: the source does not use the thing, or clarpse could not
tell. The signals below separate them: `ResolutionKind.UNRESOLVED` and `AMBIGUOUS`,
`isNotLoaded()`, a `CompileFailure` and `unreadSourceFiles()`.

| situation | signal |
|---|---|
| a name was seen and nothing in scope declares it | a reference to the written name, `ResolutionKind.UNRESOLVED` |
| a name was seen and several visible declarations match | a reference to the written name, `ResolutionKind.AMBIGUOUS` |
| a repository declaration was not loaded | `isNotLoaded()` (one-level only) |
| a component's outgoing references may be incomplete | `isBoundary()` (one-level only) |
| a whole file could not be analysed | `CompileResult.failures()`, a `CompileFailure` with a language-agnostic code |
| a source file in a language clarpse does not read | `ProjectFiles.unreadSourceFiles()` (`YES`/`NO`/`UNKNOWN`) |
| an annotation's arguments were not read | `AnnotationReference.arguments() == null` |
| **a use was seen but its target withheld** (untyped receiver, ambiguity, unknown ancestry) | **none** |

**TODO: decide** the last row, and until it is decided do not read a missing edge as proof of
absence. Intended: a per-component count of withheld attributions, by
reason. A user can then distinguish "this method calls nothing in X" from "this method made calls
that could not be attributed", and the model still invents no edge.

**TODO: decide** the remaining heuristics that do not follow §8.1 as written:
- TS namespace-only and Kotlin `expect` binding by path order (§1.4);
- Python reading a capitalised imported callee as a class constructor;
- TS reading a fixed list of library global names as heritage bases when the program has no
  library;
- Java taking a `java.lang` name when a wildcard-imported library package cannot be inspected
  (Kotlin does the opposite for non-JDK star imports);
- Java splitting an unseen qualified name into package and type at the first segment that does
  not start with a lower-case letter;
- C# matching two unparsed types by simple name for an extension call.

Each should either be brought under the policy or be documented as a named exception with its
`ResolutionKind`.

---

## 9. Language notes

Behaviour that legitimately differs, because the languages differ.

### Java
- Records are `CLASS` with the `record` modifier.
- Implicit imports are the public top-level types of `java.lang` (the Java 21 list, fixed in the
  library), case-sensitive, and on-demand per the JLS. A same-package or explicitly imported type
  wins over `java.lang`.
- JDK types are resolved against the JDK clarpse runs on (§3.4).
- A bare name binds to the current package only when the project declares a type of that name
  there, whatever directory the file sits in.
- `var x = …` depends on the type its initializer resolves to. An array is recorded through its
  element type.
- The types of annotations nested in annotation arguments are references.
- Java and Kotlin can be compiled separately and merged (§3.3). Each compile reads the other's
  declarations to name types exactly.

### Kotlin
- The JVM view applies: `<File>Kt` classes, a companion named `Companion`, an extension function
  as a member of its file class with the receiver as first parameter, and `val`/`var` constructor
  parameters as fields.
- A call `x.f()` that reaches an extension function references the file class declaring it,
  under the conditions of §8.1.
- A local without a written type depends on the type its initializer's declaration writes,
  library types included.
- Names in a `java.*`/`javax.*` star import are looked up in the JDK clarpse runs on (§3.4).

### C#
- Partial types are one component.
- Global and implicit usings (`<ImplicitUsings>`) apply to every file.
- A bare type name binds only through the scoping rules: enclosing types, the file's namespace
  and its enclosing namespaces, `using`, aliases and global usings.
- A generic type's unique name omits its arity unless two arities share the name.
- Extension methods:
  - classic `this` methods and C# 14 `extension(T x) { }` block members are members of the
    declaring static class;
  - a call on a typed receiver references that class (§8.1);
  - the extended repository type references each extension (§2.4, **TODO: decide**).
- `typeof(T)`, explicitly typed lambda parameters and expression-bodied members are read.

### TypeScript
- Module-level functions, bindings, type aliases and namespaces are components of their module.
- `declare module "x" { … }` blocks: their declarations are declarations of the file that holds
  them, named like the file's own and exported. **TODO: decide** whether an augmentation of a
  package (`declare module "express"`) should also reference the augmented external type. Today
  nothing links the two.
- `declare global` is not read.
- Decorators are `ANNOTATION` references to the resolved declaration. Their arguments' names
  are references of the decorated component.
- `typeof x` in any type position references the declaration of `x`.
- Files no `tsconfig.json` names are read with default options. A program without a library
  still records `extends Error` and similar as a base.

### Python
- Module-level functions and variables are `FUNCTION`/`MODULE_FIELD`. Nested classes are
  modelled.
- External names are import labels (`module.Name`). Libraries are not inspected.
- These all record references: the value of a class or module attribute, read like a function
  body in the scope of the class body; `metaclass=`, as `SIMPLE` and not an extension; `TypeVar`
  bounds and constraints; and PEP 695 parameter bounds.
- Constructing an imported external class records it like an annotation would. **TODO: decide**
  the capitalisation rule (§8.2).
- Imports are resolved from the repository root, `src/`, configured `extraPaths`, and every
  directory holding the top of a package.

---

## 10. Stability

- **Additive in a minor version:** new component types, reference kinds, resolution kinds and
  accessors, and references the source determines that earlier versions missed.
- **Breaking, so a major version:** a changed unique-name or path form; a changed meaning of an
  existing state, kind or accessor; removing an enum value.
- **Reference sets** change between minor versions as front ends improve, in both directions.
  Wrong references are removed, and missed references are added. Pin the version if exact
  reproducibility matters, and run on the same JDK as well (§3.4).

