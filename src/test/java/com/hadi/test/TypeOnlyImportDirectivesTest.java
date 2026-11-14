package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.ImportDirective;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A directive is type-only when nothing it binds exists when the code runs: TypeScript
 * {@code import type}, {@code export type ... from} and an import whose every named binding is
 * written {@code type}, and a Python import under {@code if TYPE_CHECKING:}. Java, Kotlin and C#
 * never have one.
 */
public class TypeOnlyImportDirectivesTest {

    private static List<ImportDirective> directives(final Lang lang, final String component, final ProjectFile... files)
            throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        for (final ProjectFile file : files) {
            projectFiles.insertFile(file);
        }
        final OOPSourceCodeModel model = new ClarpseProject(projectFiles, lang).result().model();
        return model.copyOfComponent(component).orElseThrow(() -> new AssertionError("no component " + component
                + " in " + model.components().map(c -> c.uniqueName()).collect(Collectors.toList())))
                .importDirectives();
    }

    private static ImportDirective of(final List<ImportDirective> all, final String target) {
        final List<ImportDirective> found = all.stream().filter(d -> d.target().equals(target))
                .collect(Collectors.toList());
        assertEquals(target + " in " + all, 1, found.size());
        return found.get(0);
    }

    @Test
    public void aTypeScriptImportIsTypeOnlyWhenNothingItBindsIsAValue() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final List<ImportDirective> all = directives(Lang.TYPESCRIPT, "src.main.Main",
                new ProjectFile("/src/types.ts", "export interface A {}\nexport interface B {}\nexport const v = 1;\n"),
                new ProjectFile("/src/main.ts",
                        "import type { A } from \"./a\";\n"
                                + "import { type A as AA, type B } from \"./b\";\n"
                                + "import { type A as AB, v } from \"./mixed\";\n"
                                + "import Def, { type B as BB } from \"./default\";\n"
                                + "import type * as ns from \"./ns\";\n"
                                + "import { v as vv } from \"./values\";\n"
                                + "import \"./side\";\n"
                                + "export type { A as AC } from \"./reexport-type\";\n"
                                + "export { type B as BC } from \"./reexport-inline\";\n"
                                + "export { v as vc } from \"./reexport-value\";\n"
                                + "export class Main {}\n"));
        assertTrue(of(all, "./a").isTypeOnly());
        assertTrue("every named binding is a type", of(all, "./b").isTypeOnly());
        assertFalse("one binding is a value", of(all, "./mixed").isTypeOnly());
        assertFalse("a default import is a value", of(all, "./default").isTypeOnly());
        assertTrue(of(all, "./ns").isTypeOnly());
        assertFalse(of(all, "./values").isTypeOnly());
        assertFalse("a side-effect import runs", of(all, "./side").isTypeOnly());
        assertTrue("a type re-export is read by the type checker only", of(all, "./reexport-type").isTypeOnly());
        assertTrue(of(all, "./reexport-inline").isTypeOnly());
        assertEquals("a value re-export is not an import", 0,
                all.stream().filter(d -> d.target().equals("./reexport-value")).count());
    }

    @Test
    public void aPythonImportUnderTypeCheckingIsTypeOnlyAndItsElseBranchIsNot() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final List<ImportDirective> all = directives(Lang.PYTHON, "main.C",
                new ProjectFile("/main.py",
                        "import typing\nfrom typing import TYPE_CHECKING\nimport os\n\n"
                                + "if TYPE_CHECKING:\n    from a import A\n    import b\nelse:\n    import c\n\n"
                                + "if typing.TYPE_CHECKING:\n    from d import D\n\n"
                                + "if os.name == 'nt':\n    import e\n\n\n"
                                + "class C:\n    pass\n"));
        assertTrue(of(all, "a").isTypeOnly());
        assertTrue(of(all, "b").isTypeOnly());
        assertFalse("the else of a TYPE_CHECKING guard runs", of(all, "c").isTypeOnly());
        assertTrue(of(all, "d").isTypeOnly());
        assertFalse("another condition is not the guard", of(all, "e").isTypeOnly());
        assertFalse(of(all, "os").isTypeOnly());
        assertTrue("both typing imports run", all.stream().filter(d -> d.target().equals("typing"))
                .noneMatch(ImportDirective::isTypeOnly));
    }

    @Test
    public void javaKotlinAndCSharpImportsAreNeverTypeOnly() throws Exception {
        for (final ImportDirective each : directives(Lang.JAVA, "p.A",
                new ProjectFile("/p/A.java", "package p;\nimport java.util.List;\nimport static java.lang.Math.max;\n"
                        + "public class A {}\n"))) {
            assertFalse(each.toString(), each.isTypeOnly());
        }
        for (final ImportDirective each : directives(Lang.KOTLIN, "p.A",
                new ProjectFile("/p/A.kt", "package p\nimport kotlin.collections.List\nclass A\n"))) {
            assertFalse(each.toString(), each.isTypeOnly());
        }
        for (final ImportDirective each : directives(Lang.CSHARP, "N.A",
                new ProjectFile("/A.cs", "using System.Text;\nnamespace N { public class A {} }\n"))) {
            assertFalse(each.toString(), each.isTypeOnly());
        }
    }
}
