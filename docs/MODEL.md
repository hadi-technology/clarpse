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

| type | base | method | variable | what it is |
|---|:-:|:-:|:-:|---|
| `CLASS` | ✓ | | | a class; also a Kotlin `object`/companion, a Java or C# `record`, a Python class |
| `STRUCT` | ✓ | | | a value type (a C# `struct` or `record struct`) |
| `INTERFACE` | ✓ | | | an interface |
| `ENUM` | ✓ | | | an enumeration (a Python `Enum` subclass included) |
| `ANNOTATION` | ✓ | | | an annotation type declaration |
| `METHOD` | | ✓ | | a member function |
| `CONSTRUCTOR` | | ✓ | | a constructor |
| `FUNCTION` | | ✓ | | a module-level function; a C# delegate declaration |
| `FIELD` | | | ✓ | a field or property of a type (a C# event included) |
| `MODULE_FIELD` | | | ✓ | a module-level binding (`const`, module variable) |
| `ENUM_CONSTANT` | | | ✓ | an enum member |
| `INTERFACE_CONSTANT` | | | ✓ | a constant declared on an interface |
| `TYPE_ALIAS` | | | | a name for a type expression (`type X = …`); declares no members |
| `NAMESPACE` | | | | a named scope declared in code (`namespace N { }`, `module N { }`) that holds declarations as children |
| `METHOD_PARAMETER_COMPONENT` | | | ✓ | a parameter of a callable |
| `CONSTRUCTOR_PARAMETER_COMPONENT` | | | ✓ | a parameter of a constructor |
| `LOCAL` | | | ✓ | a local variable |

Which language produces which type. A blank cell means the front end never produces that type; it
is read from the front ends' code and checked against the tests.

| type | Java | Kotlin | C# | TypeScript | Python |
|---|:-:|:-:|:-:|:-:|:-:|
| `CLASS` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `STRUCT` | | | ✓ | | |
| `INTERFACE` | ✓ | ✓ | ✓ | ✓ | |
| `ENUM` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ANNOTATION` | ✓ | ✓ | | | |
| `METHOD` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `CONSTRUCTOR` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `FUNCTION` | | | ✓ | ✓ | ✓ |
| `FIELD` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `MODULE_FIELD` | | | | ✓ | ✓ |
| `ENUM_CONSTANT` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `INTERFACE_CONSTANT` | ✓ | | | | |
| `TYPE_ALIAS` | | | | ✓ | ✓ |
| `NAMESPACE` | | | | ✓ | |
| `METHOD_PARAMETER_COMPONENT` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `CONSTRUCTOR_PARAMETER_COMPONENT` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `LOCAL` | ✓ | ✓ | ✓ | ✓ | ✓ |

C# attribute classes are `CLASS`, and Python protocols are `CLASS`. A Kotlin interface property is a
`FIELD`.

New values may be added in minor versions. Code that switches over `ComponentType` must have a
default branch.

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

How a reference binds to such a declaration, when several files hold it:
- **TypeScript namespace.** A reference to the namespace itself binds to a block only when exactly
  one file declares it (any number of blocks in that file). When several files declare it, nothing in
  the source says which block is meant, so the reference keeps the name as written, is external, and
  carries `ResolutionKind.AMBIGUOUS`. A member read from the namespace (`Geometry.b`) still binds to
  the block that declares the member.
- **Kotlin `expect`/`actual`.** A call to a top-level function that several files declare binds to
  the file class of the `expect` declaration, the one common code resolves to. When the declarations
  are all `actual` and no `expect` is in the project, no declaration is the one meant and the call
  records nothing. A name declared in several files without `expect`/`actual` (overloads) is not
  affected by this rule.

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
| `MEMBER` | `MemberReference` | `member` | a call of one named member of a type (§2.3). Its target is the owning type, so it is also a use of that type. |
| `CONSTRUCTION` | `ConstructionReference` | `construction` | the construction of an instance of a type (§2.5). Its target is the constructed type, so it is also a use of that type. |

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

**Planned for 12.0:** renaming `SimpleTypeReference`. It says "type", but TS targets module
functions and values, and C# targets extension methods. Until then read "type" as "named thing".

### 2.3 Member references

A `MemberReference` (`MEMBER`) records that a component **calls one named member of a type**:
`order.total()` is a reference to the member `total` of `Order`. It is the finer companion of the
type reference to `Order`, never a replacement for it.

**What is recorded.**
- `invokedComponent()` and `ownerType()` are the same value: the owning type, written the way a type
  reference to that type is written in the language (the internal component name for a type of the
  codebase, the external name form of §4 for a library type). The reference is classified
  (§3.2) and resolved as a type reference to the owner is, and `resolutionKind()` is the receiver
  type's. A consumer that ignores this kind sees the type-level graph it would see without it, plus
  at most a reference to a type it already has.
- `member()` is the member's simple name as written. No parameters, so overloads are one member: the
  member name is the unit.
- `memberKind()` is `CALL`. `FIELD_READ` exists in `MemberKind` and is not produced yet.
- `externalUnit()` is the unit that supplies an external owner: the npm package (TypeScript), the
  package (Java, Kotlin), the namespace (C#) or the module that defines the class (Python). It is
  `null` for an owner inside the codebase. A Node built-in module is always `node:<name>`, so
  `import "fs"` and `import "node:fs"` give the one unit `node:fs`.
- Equality is the class, the owner, the member and the kind. One component can reference several
  members of one type; the unit and the resolution kind take no part.

**The owner.**
- *A type of the codebase:* the type that declares the member when the parse finds it on the
  receiver's type or on a supertype it parsed, and the receiver's type otherwise (inherited from a
  library, or not found).
- *A library type:* the receiver's type. The library's own declaring type is not read.
- *An extension call* (C# extension methods, Kotlin extension functions): the owner is the static
  class or file class that declares the extension, the member is the extension's name and the kind
  is `CALL`. The existing type reference to that class stays.

**When it is recorded.** For `r.m(...)` only when the receiver `r` has a type known exactly from the
source (a written type, a parameter or field type, a local the front end types, or a static call
through a type name) and that type is a type of the codebase, or a library type whose name and unit
are both known exactly (from an import that names the package, or a resolved declaration; never from
capitalisation). A receiver of unknown type adds nothing, and neither does one the front end cannot
rule out being shadowed, such as a body that may redeclare the name, an array or pointer, a supertype
the parse cannot read that could supply the name, or `this`/`self`.

| language | receiver types read | library owners |
|---|---|---|
| Java | the symbol solver: variables, fields, `var` locals, type names | declarations the solver resolves (the JDK) |
| Kotlin | written or constructor-made parameters, locals and properties; objects and companions | a JDK type that has a member of that name |
| C# | declared or `var x = new T()` locals, parameters, fields; type names | the built-in types (`string`, `int`, ...), when no using can add an extension method |
| TypeScript | the checker's type of the receiver expression | a class or interface a package declares, or a type imported from a package that is not installed |
| Python | Pyright: annotated, or inferred to a single class; a class by its name | the defining module of the class |

**TypeScript, deprecated string form.** Through 11.x a call on a library-typed receiver is also
recorded, as before, as a `SimpleTypeReference` whose target is the string `<package>:<Type>.<member>`.
That form is **deprecated and is removed in 12.0**: it cannot be told from a nested type name, and its
split is ambiguous for nested types and Node built-ins. Use the `MemberReference` instead.

### 2.5 Construction references

*Migration:* a consumer that serialises every reference of a component (for example by `toString`) now gets
one more per construction, beside the `SIMPLE` reference to the same type.

A `ConstructionReference` (`CONSTRUCTION`) records that a component **constructs an instance of a type**.
It is the finer companion of the `SIMPLE` reference to the same type, never a replacement: the
`SIMPLE` reference stays, and a consumer that ignores this kind sees the graph it would see without it.
Its target is the constructed type, written and classified as a type reference to it is; type arguments are
not part of it. Equality is the class and the target.

**When it is recorded.** Only where the construction is explicit in the source and the called name is
known to be a type. Not recording one means *unknown*, never *absent*.

| language | recorded | not recorded |
|---|---|---|
| Java | `new T(...)`, qualified or not, diamond included | a creation with a class body (an anonymous subtype), array creation |
| Kotlin | a call `T(...)` whose name resolves to a type of the repository or to a class of the Java platform, aliases included | a call of a library name the parse cannot tell from a function, a qualified call |
| C# | `new T(...)` | `new()` (target-typed), anonymous types, array creation |
| TypeScript | `new T(...)`, `T` being the checker's type of the expression | a creation the checker types as `any` or `unknown` |
| Python | a call `T(...)` or `m.T(...)` whose callee Pyright resolves to a class | a callee Pyright cannot resolve, or resolves to a function; `super()` and calls on an expression |

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

What each language reports:

| language | `EXACT` | `AMBIGUOUS` | `UNRESOLVED` |
|---|---|---|---|
| Java | the member type, import, same-package type, solver type, `java.lang` or on-demand type that is the one visible; a fully qualified name | a name two on-demand imports (java.lang counting as one) both supply | a bare name nothing in scope supplies |
| Kotlin | enclosing scope, explicit import, same package, star import, default import | a name several star imports supply (repository or JDK) | a name in a type position nothing supplies |
| C# | keyword, alias, enclosing type, namespace, one `using` | a name several `using`s supply | a name no scope supplies |
| TypeScript | a repository declaration, an imported package's symbol, a library global the checker resolves | a namespace several files declare | a decorator or heritage name nothing declares |
| Python | a repository class, a name an import binds | (not produced: a name binds to one declaration by flow) | a name that is only written, bare or dotted |

A type that a one-level analysis lost to `any` because a file was not loaded is not `UNRESOLVED`.
It is `UNSPECIFIED` and is reported by `isNotLoaded()` (§3.2). `UNSPECIFIED` is otherwise produced
only for a reference whose kind the language cannot say, such as a TypeScript type with no symbol.

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
- member references (§2.3) to a library owner, and, through 11.x, the deprecated TypeScript string
  form `<package>:<Type>.<member>`, which is removed in 12.0.

So "external" means "not a component of this model". It does not prove the target lies
outside the repository. A user who wants "library dependencies" should take `externalDependencies()`
and drop those whose `resolutionKind()` is `UNRESOLVED` or `AMBIGUOUS`. Where the language reports
`UNSPECIFIED`, the user cannot make this distinction (§3.1).

`isExternal()` therefore means "not a component of this model". It does not say the target is
outside the repository: an `UNRESOLVED` or `AMBIGUOUS` name may be a repository type. To tell them
apart read `ResolutionKind` and `isNotLoaded()`.

**Planned for 12.0:** a separate state for unresolved and ambiguous names, so that `isExternal()`
holds only for names known to lie outside the repository.

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
| Java | fully qualified, nested types with `.` | `java.util.Map.Entry`, `org.lib.Client` | Implicitly imported: `java.lang` top-level types only. A name two on-demand imports both supply (java.lang counts as one) is kept as written with `AMBIGUOUS`. |
| Kotlin | fully qualified | `kotlin.collections.List`, `io.ktor.server.Application` | Default imports (`kotlin.*`, `kotlin.collections.*`, …) apply last. Type aliases resolve to what they alias. |
| C# | qualified only where an alias or a keyword says so; otherwise the name as written | `System.Int32` (from `int`), `List` | A framework type named through a namespace `using` stays as written, with `UNRESOLVED`. |
| TypeScript | `<package>:<exported name>` | `react:Component`, `@scope/pkg:Thing`, `node:fs:readFile` | The package is the import specifier's package (`@scope/pkg/sub` → `@scope/pkg`). Compiler-library globals (`Error`, `Map`) are written bare. |
| Python | `<module path>.<name>` | `requests.Session` | Labels from imports. The library is not inspected. |

**Planned for 12.0:** one external-name shape across languages. TS uses `package:name` and the
other four use dotted names. The planned form keeps the dotted name in `invokedComponent()` and
exposes the origin (package, module or assembly) separately. It includes normalising the `node:`
prefix, which gives `node:fs:readFile` beside `fs:readFile` for the same module.

**Member targets.** A call on an external receiver is a `MemberReference` (§2.3): the owner is
written in the external name form above, and the unit is `externalUnit()`. Through 11.x TypeScript
also records the deprecated string `<package>:<Type>.<member>`. It is a duplicate of the
`MemberReference`, cannot be told apart from a nested type name, and is removed in 12.0. Read
`MemberReference`, not the string.

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

### 5.1.1 `importDirectives()`

`Component.importDirectives()` is the structured companion of `imports()`: one `ImportDirective` per
import directive the file writes, in written order, attached to the same components. It describes the
file's own text and is not resolved against the model. A directive has:

- `target()`: what it names, as written, without a trailing wildcard (`java.util.List`, `java.util` for
  `java.util.*`, `./m`, `.util` for `from .util import x`, `System.Text`);
- `kind()`: `SINGLE` (names what it takes), `ON_DEMAND` (a wildcard: `a.*`, `using Ns;`, `from m import *`,
  `import * as ns`), `STATIC`, `STATIC_ON_DEMAND` (Java `import static`, C# `using static`);
- `isRelative()`: written relative to the importing file;
- `names()` and `binds()`: the members taken by name as the target exports them, and the local names they
  are bound to (`from m import a as b` gives `[a]` and `[b]`; TypeScript's default import is `default`).
  Both are empty for a whole-module import and for a wildcard. Java and Kotlin write the member into the
  target, so `names` holds its last segment;
- `alias()`: the local name given to the target as a whole (`import numpy as np`, `import * as ns`,
  `using Io = System.IO`);
- `importsModule()`: the directive binds or loads a module as a value (Python `import m`, TypeScript
  `import * as ns`, side-effect import, `import x = require()`, `require()` and `import()` without
  destructuring). A `from` import and a named import take names and are false;
- `isDynamic()`: the import runs when its code runs: TypeScript `import()` and a `require()` inside a
  function; Python an import statement inside a function body, and `importlib.import_module` and
  `__import__` with a literal argument;
- `standardLibrary()`: see §5.3.

| language | read | `isRelative` | `importsModule` | `isDynamic` |
|---|---|---|---|---|
| Java | `import`, single, on-demand, static | always false (no relative form) | always false (no module value) | always false (static) |
| Kotlin | `import`, aliases and stars | always false | always false | always false |
| C# | `using` written in the file: namespaces, `using static`, aliases. Implicit usings and the global usings of other files are not directives of the file | always false | always false | always false |
| TypeScript | `import` declarations, `import x = require()`, literal `import()` and `require()`; `export ... from` is **not** an import | specifier starts with `.` | per the list above | per the list above |
| Python | `import`, `from ... import` wherever written, literal `importlib.import_module` / `__import__` | leading dots | per the list above | per the list above |

"Always false" means the concept does not exist in the language, not that it was not looked for. An empty
list has the same ambiguity as an empty `imports()` (§5.2): a file that was not read in full has none recorded.
A `from m import name` where `name` is a submodule is a names import; the directive does not say which.
A `require()` whose result is used another way than a plain binding or object destructuring is
`importsModule() == true` with no alias.

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

An empty set is not always "no imports". For a file listed in a `CompileFailure`, or among
`ProjectFiles.unreadSourceFiles()`, an empty set means "unknown": the file was not read, or not
read in full, so its imports were not resolved. Check those two before reading an empty set as a
file with nothing to import.

**TODO: decide** how a user tells an incomplete set from an empty one in a file that was read.
Today an empty set may mean any of these:
- the file imports nothing;
- everything it imports is external;
- it uses only wildcard or namespace imports;
- its imports did not resolve.

Intended: a flag such as `importedFilesComplete()`, false when the file has an import directive
whose targets were not expanded (wildcard, namespace, unresolved, non-literal dynamic). Or a
structured import record (written text, kind, resolved files, status) that subsumes `imports()`
and `importedFiles()`.

### 5.3 Standard library

`ComponentReference.standardLibrary()` and `ImportDirective.standardLibrary()` say whether the target belongs to
the language's standard library: `STANDARD`, `NOT_STANDARD`, or `UNKNOWN`. `NOT_STANDARD` is claimed only where
the language settles it; a name kept as written because nothing in scope declared it (`UNRESOLVED`,
`AMBIGUOUS`) is `UNKNOWN`. A reference to a type of the codebase is `NOT_STANDARD`. The rules read the name only:

| language | standard | not standard | unknown |
|---|---|---|---|
| Java | `java.*` (reserved by the platform), and every package in `stdlib/java-packages.txt`, the packages the Java SE 17 platform (OpenJDK 17.0.21) exports (`javax.swing`, `org.w3c.dom`) | other packages, including `javax.*` packages the platform does not export | a name with no package |
| Kotlin | `kotlin`, `kotlin.*`, and the Java rule for the JVM platform | `kotlinx.*`, other packages | `kotlin.reflect.full`, `kotlin.reflect.jvm`, `kotlin.test` (separate artifacts), a name with no package |
| C# | a namespace in `stdlib/dotnet-namespaces.txt`, a type in `stdlib/dotnet-types.txt`, or a nested type of one: the public API of the Microsoft.NETCore.App.Ref 8.0.34 reference pack (nuget.org package SHA-512 in each list header) | a qualified name under any other root (`Newtonsoft.Json.JsonConvert`) | any name under `System`, `Microsoft` or `Windows` that the lists do not hold (`System.CommandLine`, `System.Reactive`, ASP.NET Core and Windows Desktop frameworks, NuGet packages), a name with no namespace |
| TypeScript | a `node:` specifier or the specifier of a Node.js 22 built-in module (`fs`, `fs/promises`), from `stdlib/node-modules.txt` | every other specifier or package; a relative specifier; a specifier that resolves to a file of the project | a bare global (`Error`) |
| Python | a module whose top-level package is in `stdlib/python-modules.txt`, the typeshed bundled with pyright | any other module; a relative import; a module that resolves to a file of the project (it shadows the library) | a bare name |

The lists are fixed resources, each starting with a comment line that names its source and version, so an answer
never depends on the runtime clarpse runs on. The Python list is the union over the Python versions typeshed
covers (`distutils` is listed though later versions drop it). The C# type list drops generic arity, so
`List<T>` is `List`. Each language supplies its rule as a small strategy (`LanguageStandardLibrary`) with its
own list.


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

**Planned for 12.0:** a single path form, the file's path inside the project, for all three
loads. With it:
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

**Planned for 12.0:** withheld-attribution counts. Until then do not read a missing edge as proof
of absence. The plan is a per-component count of withheld attributions, by reason. A user can then
distinguish "this method calls nothing in X" from "this method made calls that could not be
attributed", and the model still invents no edge.

The guesses that used to break §8.1 are gone: a TypeScript namespace several files declare is
`AMBIGUOUS` (§1.4); a Kotlin call to an `expect` function binds to the `expect` declaration (§1.4);
a Python callee is a constructed class only when Pyright resolves it to one (§9); and a TypeScript
heritage name is a library global only when the checker resolves it to a declaration in a default
library or `@types` file, and otherwise is kept as written with `UNRESOLVED`.

**TODO: decide** the remaining heuristics that do not follow §8.1 as written:
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
- `var x = …` depends on the type its initializer resolves to, as the type written out would:
  the type and every type in its type arguments. An array is recorded through its element type.
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
  library types included, and on the type of a string, character, boolean or plain number literal.
  It records what the same written type would (§9, inferred locals).
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
  them, named like the file's own and exported. Nothing links an augmentation of a package
  (`declare module "express"`) to the augmented external type. **Planned for 12.0:** a reference
  from the augmentation to the type it augments.
- `declare global` is not read.
- Decorators are `ANNOTATION` references to the resolved declaration. Their arguments' names
  are references of the decorated component. Decorators on classes, members and parameters, and
  decorated constructor parameter properties (`constructor(@Inject(T) private readonly x: X)`),
  are read whatever framework writes them; the class's fields and members come from what is
  written in its body and its constructor's parameter properties.
- A class whose base is a call (`class A extends Mixin(Base)`, `extends Factory("name")`) names the
  called function as its base: an `EXTENSION` reference to the repository function, or to
  `package:Mixin` for a package's function, even when the package is not installed, and the names
  in the call's arguments are plain references. What a package's function adds to the class is not
  modelled, and the file is not reported as partly read. A base written as a call whose arguments
  could themselves declare members (`Schema.Class<A>("A")({ id: ... })`, an object or arrow
  function argument, type arguments) or whose callee is a repository function the checker cannot
  see members through is reported as a `CompileFailure` (`UNRESOLVED_BASE_EXPRESSION`) when no
  member is found through it.
- `typeof x` in any type position references the declaration of `x`.
- A project config (`tsconfig.json`, `tsconfig.<name>.json`, `jsconfig.json`) read from an archive,
  a directory or `ProjectFiles.insertFile` brings the files its relative `extends` names (a string or
  an array, written with or without `.json`, whatever the file is called), and theirs in turn, so the
  program sees the `paths` and `compilerOptions` the compiler would. `configFiles()` and `size()`
  include these presets. The set follows the input: a preset that is inserted again replaces the old
  text, one that is removed is waited for again, and a preset no project config reaches any more is
  released. A package-name `extends` and a Windows-style `..\\base` are not followed, a target the
  input does not hold is logged and left (a target skipped for size, or for lying outside the
  project directory, is logged as that), a cycle ends where it returns, and a chain is followed at
  most 32 links (`clarpse.config.maxExtendsChain`). From a directory only the named targets are read,
  after the walk, and only when their real path is inside the real project directory. From an archive
  or `insertFile`, JSON files not yet reached are held as candidates until a config names them, up to
  1 MiB each (`clarpse.config.maxCandidateJsonBytes`) and 16 MiB in all; they live as long as the
  instance and are copied with it.
- Files no `tsconfig.json` names are read with default options and no library. In such a program
  `extends Error` finds no declaration of `Error`, so it is recorded as written with `UNRESOLVED`.
  A heritage name is a library global (`EXACT`) only when the checker resolves it to a declaration
  in a default library or `@types` file.
- A reference to a namespace several files declare is `AMBIGUOUS` (§1.4).

### Python
- Module-level functions and variables are `FUNCTION`/`MODULE_FIELD`. Nested classes are
  modelled.
- External names are import labels (`module.Name`). Libraries are not inspected.
- These all record references: the value of a class or module attribute, read like a function
  body in the scope of the class body; `metaclass=`, as `SIMPLE` and not an extension; `TypeVar`
  bounds and constraints; and PEP 695 parameter bounds.
- Constructing an imported external class records it like an annotation would, but only when
  Pyright resolves the callee to a class. A function, whatever its name, and a name Pyright cannot
  resolve construct nothing.
- A local without an annotation records what the annotation of the class Pyright infers would,
  including the language's own classes: `x = 1` records what `x: int` records.
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

---

## 11. Changes in 11.17.0

User-visible changes in this release.

- **New: `Component.importedFiles()`** (§5.2). The project files an import directive names exactly,
  for all five languages.
- **New: `ComponentType.NAMESPACE`** (§1.3). A TypeScript `namespace` block is a component. A
  `switch` over `ComponentType` with no default branch no longer compiles, and a reader built
  against an older version fails on the new value in serialised JSON.
- **Deprecated: `ResolutionKind.UNIQUE_SIMPLE_NAME`** (§3.1). It is no longer produced.
- **`ResolutionKind` means the same in every language** (§3.1). Java and Python now report
  `EXACT`, `AMBIGUOUS` or `UNRESOLVED` for type references. Java records a name two on-demand
  imports both supply as written, with `AMBIGUOUS`, where it used to record nothing. Kotlin reports
  `AMBIGUOUS` for a name several star imports supply, where it used to bind the first. TypeScript
  reports a type lost to `any` in a one-level analysis through `isNotLoaded()` alone, and no longer
  marks it `UNRESOLVED`.
- **Guesses removed** (§8.2). A TypeScript namespace several files declare is `AMBIGUOUS`; a Kotlin
  call binds to the `expect` declaration; a Python callee is a constructed class only when Pyright
  says it is one; a TypeScript heritage name is a library global only when the checker resolves it.
- **An inferred local records what its written type would** (§9) in Java, Kotlin, C#, TypeScript and
  Python. Python records the language's own classes (`x = 1` is `x: int`); Java records the type
  arguments of a `var`; Kotlin and C# type a literal initializer.
- **Documentation:** the per-language table of component types (§1.2), the meaning of
  `isExternal()` (§3.2) and of an empty `importedFiles()` (§5.2).
- **New: the member reference kind `MemberReference`** (§2.3), in all five languages. It records the
  member a call uses, on the owning type, with `member()`, `memberKind()` and `externalUnit()`.
  `TypeReferences` gains `MEMBER`, and a `switch` over it or a reader built against an older version
  meets the new `member` value in serialised JSON. The TypeScript `<package>:<Type>.<member>` string
  is deprecated and is removed in 12.0.
- **Reference fixes** (§8). Each narrows or adds only references the source determines:
  - Java: only `java.lang` is imported implicitly, after every import the file names, and the
    table lists every public `java.lang` type; a bare name binds to the current package only when
    the project declares a type of that name there; a call receiver that is not a type in scope
    names no type; a directory loads to the same model as the archive of the same tree.
  - Kotlin: a member of the class or its supertypes binds a call before an import does; calls on
    typed receivers, `?.` calls, properties, `this` and companion members are read; a local without
    a written type depends on the declared type of its initializer; names a `java.*` or `javax.*`
    star import brings in resolve against the JDK; class annotation arguments are references.
  - TypeScript: namespaces are components; `typeof` of a value, type-parameter defaults, the types
    of explicitly typed lambda parameters, and decorators with their arguments are references;
    inferred type text and type parameters are not recorded as references.
  - Python: string annotations, class decorator arguments, declarations inside a module-level `if`
    or `try`, and locals with an inferred class are read; an import that resolves to a stub names
    the module file beside it.
  - C#: a bare type name binds only to a type its namespace makes visible; an extension call
    references the static class declaring the extension, and an extension edge needs the
    receiver's own members to be known; `typeof`, explicitly typed lambda parameters and
    expression-bodied members are read; an array or pointer type is not read as its element type;
    an inferred `var` local is typed only by a plain `new` initializer; a receiver name the body may
    declare again is not taken as the outer parameter.
- **C#, more fixes.** Type tests, casts and patterns (`is`, `as`, `(T)x`, `nameof`, `default`,
  `case T t`), `[Mark]` for `MarkAttribute`, `typeof` inside attribute arguments and method and
  delegate constraints are references. An inherited member is not read as a repository type, a
  namespace qualifier is not a type, and a name two visible namespaces supply is `AMBIGUOUS`.
- **Changed: a C# type no longer references its extension methods.** The extension class references
  the type it extends, and a caller references the extension class.
- **One failing file fails alone.** In every language, a file whose parse throws is reported as a
  `CompileFailure` for that file, and the rest of the project is modelled.
- **Performance.** Python parsing evaluates types with Pyright for inferred locals and member
  references, and is two to four times slower than in 11.16.0 on large projects. Java, Kotlin, C#
  and TypeScript are within about 20% of 11.16.0.
- **Release size.** The bundled Pyright and TypeScript runtimes now hold only the files their
  daemons load, and the sources jar leaves the runtime bundles out.
- **Dependency updates.** `jackson-databind` 2.22.0 to 2.22.3; `log4j-api` and `log4j-core` to
  2.25.5.
