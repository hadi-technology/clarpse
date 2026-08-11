package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@code imports()} should mean one thing in every language: the imports of the file, carried by
 * the types that file declares and by nothing else.
 *
 * <p>It used to mean four things. Java populated it on types only; C# copied the file's using list
 * onto methods, fields, parameters and locals, so a field reported its file's imports as its own;
 * Python computed the imports, used them to resolve types, and then dropped them from the payload;
 * TypeScript never collected them at all. A consumer reading the field could not tell an empty list
 * apart from a language that does not fill it in. See issue #156.
 */
public class CrossLanguageImportsTest {

    private static OOPSourceCodeModel model(final Lang lang, final ProjectFile... files) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        for (final ProjectFile file : files) {
            projectFiles.insertFile(file);
        }
        return new ClarpseProject(projectFiles, lang).result().model();
    }

    private static Component component(final OOPSourceCodeModel model, final String name) {
        return model.getComponent(name).orElseThrow(() -> new AssertionError(
                "no component " + name + " in "
                        + model.components().map(Component::uniqueName).collect(Collectors.toList())));
    }

    private static List<Component> nonTypeComponents(final OOPSourceCodeModel model) {
        return model.components()
                .filter(component -> !component.componentType().isBaseComponent())
                .collect(Collectors.toList());
    }

    @Test
    public void javaCarriesImportsOnTypesOnly() throws Exception {
        final OOPSourceCodeModel model = model(Lang.JAVA,
                new ProjectFile("/src/main/java/app/util/Helper.java",
                        "package app.util;\npublic class Helper { }\n"),
                new ProjectFile("/src/main/java/app/Svc.java",
                        "package app;\nimport java.util.List;\nimport app.util.Helper;\n"
                        + "public class Svc {\n  private List<Helper> items;\n"
                        + "  public Helper helper() { return null; }\n}\n"));

        assertTrue(component(model, "app.Svc").imports().contains("app.util.Helper"));
        assertNoImportsOnMembers(model);
    }

    @Test
    public void csharpDoesNotCopyImportsOntoMembers() throws Exception {
        final OOPSourceCodeModel model = model(Lang.CSHARP,
                new ProjectFile("/Util/Helper.cs", "namespace App.Util;\npublic class Helper { }\n"),
                new ProjectFile("/Svc.cs",
                        "using System.Collections.Generic;\nusing App.Util;\n"
                        + "namespace App;\npublic class Svc {\n"
                        + "  private Helper item;\n  public Helper Helper() { return null; }\n}\n"));

        assertTrue(component(model, "App.Svc").imports().contains("App.Util"));
        assertNoImportsOnMembers(model);
    }

    @Test
    public void pythonPopulatesImports() throws Exception {
        // The Python and TypeScript compilers drive a Node daemon. The Docker builder image has no
        // Node, so an unguarded test here fails the image build rather than the language -- which
        // is what the existing cross-language tests skip for.
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = model(Lang.PYTHON,
                new ProjectFile("/src/helper.py", "class Helper:\n    pass\n"),
                new ProjectFile("/src/svc.py",
                        "import os\nfrom .helper import Helper\n\n"
                        + "class Svc:\n    def helper(self):\n        return Helper()\n"));

        final Component svc = component(model, "src.svc.Svc");
        assertTrue("a resolved internal import should be recorded",
                svc.imports().contains("src.helper.Helper"));
        assertTrue("a standard-library import should be recorded", svc.imports().contains("os"));
        assertNoImportsOnMembers(model);
    }

    /**
     * An internal import becomes the component name it refers to, so it lines up with the rest of
     * the model; an external one keeps its specifier, because the TypeScript compiler could not
     * resolve it to a file in the repository and guessing a path would invent one.
     */
    @Test
    public void typescriptPopulatesImports() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = model(Lang.TYPESCRIPT,
                new ProjectFile("/package.json", "{ \"name\": \"t\", \"version\": \"1.0.0\" }"),
                new ProjectFile("/tsconfig.json",
                        "{ \"compilerOptions\": { \"target\": \"ES2020\", \"module\": \"commonjs\" } }"),
                new ProjectFile("/src/helper.ts", "export class Helper { go(): void { } }\n"),
                new ProjectFile("/src/svc.ts",
                        "import { Helper } from \"./helper\";\nimport * as path from \"path\";\n\n"
                        + "export class Svc { helper(): Helper { return new Helper(); } }\n"));

        final Component svc = component(model, "src.svc.Svc");
        assertTrue("internal import should resolve to its component name",
                svc.imports().contains("src.helper.Helper"));
        assertTrue("external import should keep its specifier", svc.imports().contains("path"));
        assertNoImportsOnMembers(model);
    }

    private static void assertNoImportsOnMembers(final OOPSourceCodeModel model) {
        for (final Component component : nonTypeComponents(model)) {
            assertEquals(component.uniqueName() + " is not a type and must carry no imports",
                    0, component.imports().size());
        }
    }
}
