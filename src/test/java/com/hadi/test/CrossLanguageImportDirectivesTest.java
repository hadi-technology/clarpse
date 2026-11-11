package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.StandardLibraryStatus;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.ImportDirective;
import com.hadi.clarpse.sourcemodel.ImportKind;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * An import directive says how a file imports, in every language: single, on demand or static, the
 * names it takes and binds, whether it imports a module as a whole, and whether it is dynamic.
 * Java, Kotlin and C# declare imports statically and have no relative or module form.
 */
public class CrossLanguageImportDirectivesTest {

    private static OOPSourceCodeModel model(final Lang lang, final ProjectFile... files) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        for (final ProjectFile file : files) {
            projectFiles.insertFile(file);
        }
        return new ClarpseProject(projectFiles, lang).result().model();
    }

    private static List<ImportDirective> directives(final OOPSourceCodeModel model, final String component) {
        return model.copyOfComponent(component).orElseThrow(() -> new AssertionError("no component " + component
                + " in " + model.components().map(Component::uniqueName).collect(Collectors.toList())))
                .importDirectives();
    }

    private static ImportDirective directive(final List<ImportDirective> all, final String target,
                                             final String... boundName) {
        final List<ImportDirective> found = all.stream().filter(d -> d.target().equals(target)
                && (boundName.length == 0 || d.binds().contains(boundName[0])
                        || boundName[0].equals(d.alias()))).collect(Collectors.toList());
        assertEquals(target + " in " + all, 1, found.size());
        return found.get(0);
    }

    private static void assertStaticOnly(final List<ImportDirective> all) {
        for (final ImportDirective each : all) {
            assertFalse(each.toString(), each.isDynamic());
            assertFalse(each.toString(), each.isRelative());
            assertFalse(each.toString(), each.importsModule());
        }
    }

    @Test
    public void javaSingleOnDemandAndStaticImports() throws Exception {
        final OOPSourceCodeModel model = model(Lang.JAVA, new ProjectFile("/app/Main.java",
                "package app;\nimport java.util.List;\nimport java.util.*;\nimport static java.lang.Math.max;\n"
                        + "import static java.lang.Math.*;\nimport org.acme.Thing;\n"
                        + "public class Main { List<Thing> items; }\n"));
        final List<ImportDirective> all = directives(model, "app.Main");
        assertEquals(5, all.size());

        final ImportDirective single = directive(all, "java.util.List");
        assertEquals(ImportKind.SINGLE, single.kind());
        assertEquals(List.of("List"), single.names());
        assertEquals(List.of("List"), single.binds());

        final ImportDirective onDemand = directive(all, "java.util");
        assertEquals(ImportKind.ON_DEMAND, onDemand.kind());
        assertTrue(onDemand.names().isEmpty());
        assertTrue(onDemand.binds().isEmpty());

        final ImportDirective staticMember = directive(all, "java.lang.Math.max");
        assertEquals(ImportKind.STATIC, staticMember.kind());
        assertEquals(List.of("max"), staticMember.binds());

        final ImportDirective staticAll = directive(all, "java.lang.Math");
        assertEquals(ImportKind.STATIC_ON_DEMAND, staticAll.kind());
        assertTrue(staticAll.binds().isEmpty());

        assertStaticOnly(all);
        assertEquals(StandardLibraryStatus.STANDARD, single.standardLibrary());
        assertEquals(StandardLibraryStatus.NOT_STANDARD, directive(all, "org.acme.Thing").standardLibrary());
    }

    @Test
    public void javaDirectivesAreOnTypesOnlyAndInWrittenOrder() throws Exception {
        final OOPSourceCodeModel model = model(Lang.JAVA, new ProjectFile("/app/Main.java",
                "package app;\nimport b.Second;\nimport a.First;\n"
                        + "public class Main { Second s; void m() { First f; } }\n"));
        assertEquals(List.of("b.Second", "a.First"), directives(model, "app.Main").stream()
                .map(ImportDirective::target).collect(Collectors.toList()));
        assertTrue(directives(model, "app.Main.m()").isEmpty());
        assertTrue(directives(model, "app.Main.s").isEmpty());
    }

    @Test
    public void kotlinNamedAliasedAndStarImports() throws Exception {
        final OOPSourceCodeModel model = model(Lang.KOTLIN, new ProjectFile("/app/Main.kt",
                "package app\nimport org.acme.Widget\nimport org.acme.Widget as W\nimport kotlin.collections.*\n"
                        + "import java.util.ArrayList\nclass Main { val w = W() }\n"));
        final List<ImportDirective> all = directives(model, "app.Main");
        assertEquals(4, all.size());

        final ImportDirective plain = directive(all, "org.acme.Widget", "Widget");
        assertEquals(ImportKind.SINGLE, plain.kind());
        assertEquals(List.of("Widget"), plain.names());

        final ImportDirective renamed = directive(all, "org.acme.Widget", "W");
        assertEquals(List.of("Widget"), renamed.names());
        assertEquals(List.of("W"), renamed.binds());

        final ImportDirective star = directive(all, "kotlin.collections");
        assertEquals(ImportKind.ON_DEMAND, star.kind());
        assertTrue(star.binds().isEmpty());
        assertEquals(StandardLibraryStatus.STANDARD, star.standardLibrary());
        assertEquals(StandardLibraryStatus.STANDARD, directive(all, "java.util.ArrayList").standardLibrary());
        assertEquals(StandardLibraryStatus.NOT_STANDARD, plain.standardLibrary());
        assertStaticOnly(all);
    }

    @Test
    public void csharpNamespaceStaticAndAliasUsings() throws Exception {
        final OOPSourceCodeModel model = model(Lang.CSHARP, new ProjectFile("/Main.cs",
                "using System;\nusing static System.Math;\nusing Io = System.IO;\nusing Acme.Tools;\n"
                        + "namespace App { public class Main { } }\n"));
        final List<ImportDirective> all = directives(model, "App.Main");
        assertEquals(4, all.size());

        final ImportDirective namespaceUsing = directive(all, "System");
        assertEquals(ImportKind.ON_DEMAND, namespaceUsing.kind());
        assertEquals(StandardLibraryStatus.STANDARD, namespaceUsing.standardLibrary());

        final ImportDirective staticUsing = directive(all, "System.Math");
        assertEquals(ImportKind.STATIC_ON_DEMAND, staticUsing.kind());

        final ImportDirective alias = directive(all, "System.IO");
        assertEquals("Io", alias.alias());
        assertEquals(ImportKind.SINGLE, alias.kind());
        assertTrue(alias.binds().isEmpty());

        assertEquals(StandardLibraryStatus.NOT_STANDARD, directive(all, "Acme.Tools").standardLibrary());
        assertStaticOnly(all);
    }

    @Test
    public void csharpImplicitUsingsAreNoDirectiveOfTheFile() throws Exception {
        final OOPSourceCodeModel model = model(Lang.CSHARP, new ProjectFile("/Main.cs",
                "namespace App { public class Main { } }\n"));
        assertTrue(directives(model, "App.Main").isEmpty());
    }

    @Test
    public void typescriptImportsRequiresAndDynamicLoads() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = model(Lang.TYPESCRIPT,
                new ProjectFile("/package.json", "{ \"name\": \"t\", \"version\": \"1.0.0\" }"),
                new ProjectFile("/tsconfig.json",
                        "{ \"compilerOptions\": { \"target\": \"ES2020\", \"module\": \"commonjs\" } }"),
                new ProjectFile("/src/util.ts",
                        "export class Helper { go(): void {} }\nexport function make(): number { return 1; }\n"
                                + "export default class Def {}\n"),
                new ProjectFile("/src/side.ts", "export const side = 1;\n"),
                new ProjectFile("/src/main.ts",
                        "import Def, { Helper, make as mk } from \"./util\";\n"
                                + "import * as util from \"./util\";\nimport fs from \"fs\";\n"
                                + "import { readFile } from \"node:fs/promises\";\nimport \"./side\";\n"
                                + "import lodash from \"lodash\";\nimport cjs = require(\"./util\");\n"
                                + "export { Helper as H } from \"./util\";\n"
                                + "const { Helper: HH } = require(\"./util\");\n"
                                + "const top = require(\"./side\");\n"
                                + "export class Main {\n  h = new Helper();\n"
                                + "  load() { const os = require(\"os\"); return import(\"./util\"); }\n}\n"));
        final List<ImportDirective> all = directives(model, "src.main.Main");
        assertEquals(all.toString(), 11, all.size());

        final ImportDirective named = all.get(0);
        assertEquals("./util", named.target());
        assertEquals(ImportKind.SINGLE, named.kind());
        assertTrue(named.isRelative());
        assertEquals(List.of("default", "Helper", "make"), named.names());
        assertEquals(List.of("Def", "Helper", "mk"), named.binds());
        assertFalse(named.importsModule());
        assertFalse(named.isDynamic());

        final ImportDirective namespace = all.get(1);
        assertEquals(ImportKind.ON_DEMAND, namespace.kind());
        assertEquals("util", namespace.alias());
        assertTrue(namespace.importsModule());
        assertTrue(namespace.binds().isEmpty());

        assertEquals(StandardLibraryStatus.STANDARD, all.get(2).standardLibrary());
        assertEquals("node:fs/promises", all.get(3).target());
        assertEquals(StandardLibraryStatus.STANDARD, all.get(3).standardLibrary());

        final ImportDirective sideEffect = all.get(4);
        assertEquals("./side", sideEffect.target());
        assertTrue(sideEffect.importsModule());
        assertTrue(sideEffect.binds().isEmpty());
        assertEquals(StandardLibraryStatus.NOT_STANDARD, all.get(5).standardLibrary());

        final ImportDirective equals = all.get(6);
        assertEquals("cjs", equals.alias());
        assertTrue(equals.importsModule());

        final ImportDirective destructured = all.get(7);
        assertEquals(List.of("Helper"), destructured.names());
        assertEquals(List.of("HH"), destructured.binds());
        assertFalse("require names, so it imports no module as a whole", destructured.importsModule());
        assertFalse("a require at the top of the file runs when the file loads", destructured.isDynamic());

        final ImportDirective topLevelRequire = all.get(8);
        assertEquals("top", topLevelRequire.alias());
        assertTrue(topLevelRequire.importsModule());
        assertFalse(topLevelRequire.isDynamic());

        final ImportDirective inFunction = all.get(9);
        assertEquals("os", inFunction.target());
        assertTrue("a require inside a function runs when the function does", inFunction.isDynamic());

        final ImportDirective dynamicImport = all.get(10);
        assertEquals("./util", dynamicImport.target());
        assertTrue(dynamicImport.isDynamic());
        assertTrue(dynamicImport.importsModule());
        assertNull(dynamicImport.alias());
    }

    @Test
    public void aRelativeSpecifierThatNamesANodeModuleIsNotStandard() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = model(Lang.TYPESCRIPT,
                new ProjectFile("/package.json", "{ \"name\": \"t\", \"version\": \"1.0.0\" }"),
                new ProjectFile("/tsconfig.json",
                        "{ \"compilerOptions\": { \"target\": \"ES2020\", \"module\": \"commonjs\" } }"),
                new ProjectFile("/src/fs.ts", "export const mine = 1;\n"),
                new ProjectFile("/src/main.ts",
                        "import { mine } from \"./fs\";\nexport class Main { v = mine; }\n"));
        final ImportDirective only = directives(model, "src.main.Main").get(0);
        assertTrue(only.isRelative());
        assertEquals(StandardLibraryStatus.NOT_STANDARD, only.standardLibrary());
    }

    @Test
    public void pythonImportsFromImportsAndDynamicLoads() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = model(Lang.PYTHON,
                new ProjectFile("/pkg/__init__.py", ""),
                new ProjectFile("/pkg/util.py", "class Helper:\n    pass\ndef make():\n    return 1\n"),
                new ProjectFile("/pkg/main.py",
                        "import os\nimport os.path as osp\nimport numpy as np\n"
                                + "from collections import OrderedDict, defaultdict as dd\n"
                                + "from . import util\nfrom .util import Helper, make\nfrom os import *\n"
                                + "import importlib\n\n"
                                + "def f():\n    import re\n    importlib.import_module(\"pkg.util\")\n"
                                + "    return Helper()\n\n"
                                + "class C:\n    def g(self):\n        return Helper()\n"));
        final List<ImportDirective> all = directives(model, "pkg.main.C");
        assertEquals(all.toString(), 10, all.size());

        final ImportDirective plain = all.get(0);
        assertEquals("os", plain.target());
        assertEquals(ImportKind.SINGLE, plain.kind());
        assertTrue(plain.importsModule());
        assertNull(plain.alias());
        assertTrue(plain.names().isEmpty());
        assertEquals(StandardLibraryStatus.STANDARD, plain.standardLibrary());

        final ImportDirective dotted = all.get(1);
        assertEquals("os.path", dotted.target());
        assertEquals("osp", dotted.alias());

        assertEquals(StandardLibraryStatus.NOT_STANDARD, all.get(2).standardLibrary());

        final ImportDirective from = all.get(3);
        assertEquals("collections", from.target());
        assertEquals(List.of("OrderedDict", "defaultdict"), from.names());
        assertEquals(List.of("OrderedDict", "dd"), from.binds());
        assertFalse(from.importsModule());

        final ImportDirective relativeModule = all.get(4);
        assertEquals(".", relativeModule.target());
        assertTrue(relativeModule.isRelative());
        assertEquals(List.of("util"), relativeModule.names());
        assertEquals(StandardLibraryStatus.NOT_STANDARD, relativeModule.standardLibrary());

        final ImportDirective relativeNames = all.get(5);
        assertEquals(".util", relativeNames.target());
        assertEquals(List.of("Helper", "make"), relativeNames.names());

        final ImportDirective star = all.get(6);
        assertEquals(ImportKind.ON_DEMAND, star.kind());
        assertTrue(star.names().isEmpty());
        assertFalse(star.importsModule());

        assertFalse("a module-level import runs when the file loads", all.get(7).isDynamic());

        final ImportDirective inFunction = all.get(8);
        assertEquals("re", inFunction.target());
        assertTrue(inFunction.isDynamic());

        final ImportDirective loaded = all.get(9);
        assertEquals("pkg.util", loaded.target());
        assertTrue(loaded.isDynamic());
        assertTrue(loaded.importsModule());
        assertEquals(StandardLibraryStatus.NOT_STANDARD, loaded.standardLibrary());
    }

    @Test
    public void aPythonModuleOfTheProjectShadowsTheStandardLibraryOfTheSameName() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = model(Lang.PYTHON,
                new ProjectFile("/json.py", "def loads(text):\n    return text\n"),
                new ProjectFile("/main.py", "import json\n\nclass C:\n    def g(self):\n        return json.loads('x')\n"));
        final ImportDirective only = directives(model, "main.C").get(0);
        assertEquals("json", only.target());
        assertEquals(StandardLibraryStatus.NOT_STANDARD, only.standardLibrary());
    }
}
