package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ConstructionReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Constructing a type is recorded as a construction reference beside the simple reference to the same
 * type, in every language, and only where the construction is explicit and the called name is a type.
 */
public class CrossLanguageConstructionTest {

    private static OOPSourceCodeModel model(final Lang lang, final ProjectFile... files) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        for (final ProjectFile file : files) {
            projectFiles.insertFile(file);
        }
        return new ClarpseProject(projectFiles, lang).result().model();
    }

    private static Component component(final OOPSourceCodeModel model, final String name) {
        return model.copyOfComponent(name).orElseThrow(() -> new AssertionError("no component " + name + " in "
                + model.components().map(Component::uniqueName).collect(Collectors.toList())));
    }

    private static Set<String> constructed(final Component component) {
        return component.references().stream().filter(ConstructionReference.class::isInstance)
                .map(ComponentReference::invokedComponent).collect(Collectors.toCollection(TreeSet::new));
    }

    private static void assertAlsoReferencedAsATypeOf(final Component component) {
        for (final String type : constructed(component)) {
            assertTrue(type + " keeps its simple reference", component.references().stream()
                    .anyMatch(ref -> ref instanceof SimpleTypeReference && ref.invokedComponent().equals(type)));
        }
    }

    @Test
    public void javaNewOfAProjectTypeAndALibraryType() throws Exception {
        final OOPSourceCodeModel model = model(Lang.JAVA,
                new ProjectFile("/app/lib/Widget.java", "package app.lib;\npublic class Widget { }\n"),
                new ProjectFile("/app/Main.java",
                        "package app;\nimport app.lib.Widget;\nimport java.util.ArrayList;\nimport java.util.List;\n"
                                + "public class Main {\n  List<Widget> items = new ArrayList<>();\n"
                                + "  Widget a = new Widget();\n  Widget b = new app.lib.Widget();\n"
                                + "  Runnable r = new Runnable() { public void run() { } };\n"
                                + "  int[] numbers = new int[3];\n  Widget[] widgets = new Widget[2];\n"
                                + "  void m() { Widget local = new Widget(); }\n}\n"));
        final Component main = component(model, "app.Main");
        assertEquals(new TreeSet<>(Set.of("app.lib.Widget", "java.util.ArrayList")), constructed(main));
        assertAlsoReferencedAsATypeOf(main);
        assertEquals(Set.of("app.lib.Widget"), constructed(component(model, "app.Main.m()")));
        assertEquals(Set.of("java.util.ArrayList"), constructed(component(model, "app.Main.items")));
        assertEquals("a type that is only mentioned is not constructed", Set.of(),
                constructed(component(model, "app.Main.numbers")));
    }

    @Test
    public void javaConstructionOfALibraryTypeIsExternal() throws Exception {
        final OOPSourceCodeModel model = model(Lang.JAVA, new ProjectFile("/app/Main.java",
                "package app;\npublic class Main { Object o = new java.util.ArrayList<String>(); }\n"));
        final ComponentReference ref = component(model, "app.Main").references().stream()
                .filter(ConstructionReference.class::isInstance).findFirst().orElseThrow();
        assertEquals("java.util.ArrayList", ref.invokedComponent());
        assertTrue(ref.isExternal());
    }

    @Test
    public void kotlinCallOfAClassIsAConstructionAndCallOfAFunctionIsNot() throws Exception {
        final OOPSourceCodeModel model = model(Lang.KOTLIN,
                new ProjectFile("/app/lib/Widget.kt", "package app.lib\nclass Widget\nfun helper() = 1\n"),
                new ProjectFile("/app/Main.kt",
                        "package app\nimport app.lib.Widget\nimport app.lib.Widget as W\nimport app.lib.helper\n"
                                + "import java.util.ArrayList\nimport org.acme.Thing\n"
                                + "class Main {\n  val a = Widget()\n  val b = W()\n  val c = ArrayList<String>()\n"
                                + "  val d = helper()\n  val e = Thing()\n}\n"));
        final Component main = component(model, "app.Main");
        assertEquals(new TreeSet<>(Set.of("app.lib.Widget", "java.util.ArrayList")), constructed(main));
        assertAlsoReferencedAsATypeOf(main);
        assertEquals("a call of an unresolved library name may be a function and records nothing", Set.of(),
                constructed(component(model, "app.Main.e")));
        assertEquals(Set.of(), constructed(component(model, "app.Main.d")));
    }

    @Test
    public void csharpNewOfATypeButNotAnArrayAnAnonymousTypeOrATargetTypedNew() throws Exception {
        final OOPSourceCodeModel model = model(Lang.CSHARP,
                new ProjectFile("/Widget.cs", "namespace App.Lib { public class Widget { } }\n"),
                new ProjectFile("/Main.cs",
                        "using App.Lib;\nnamespace App {\npublic class Main {\n  public Widget a = new Widget();\n"
                                + "  public Widget[] arr = new Widget[3];\n  public Widget c = new();\n"
                                + "  public void M() { var anonymous = new { Size = 1 }; var w = new Widget(); }\n}\n}\n"));
        final Component main = component(model, "App.Main");
        assertEquals(Set.of("App.Lib.Widget"), constructed(main));
        assertAlsoReferencedAsATypeOf(main);
        assertEquals(Set.of(), constructed(component(model, "App.Main.arr")));
        assertEquals(Set.of(), constructed(component(model, "App.Main.c")));
        assertEquals(Set.of("App.Lib.Widget"), constructed(component(model, "App.Main.M()")));
    }

    @Test
    public void typescriptNewWithoutItsTypeArguments() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = model(Lang.TYPESCRIPT,
                new ProjectFile("/package.json", "{ \"name\": \"t\", \"version\": \"1.0.0\" }"),
                new ProjectFile("/tsconfig.json",
                        "{ \"compilerOptions\": { \"target\": \"ES2020\", \"module\": \"commonjs\" } }"),
                new ProjectFile("/src/util.ts", "export class Helper { go(): void {} }\n"
                        + "export class Other {}\n"),
                new ProjectFile("/src/main.ts",
                        "import { Helper, Other } from \"./util\";\nexport class Main {\n"
                                + "  a = new Helper();\n  b = new Map<string, Other>();\n  c: Other | undefined;\n}\n"));
        assertEquals(Set.of("src.util.Helper", "Map"), constructed(component(model, "src.main.Main")));
        assertEquals("the type argument is referenced and not constructed", Set.of(),
                constructed(component(model, "src.main.Main.c")));
        assertAlsoReferencedAsATypeOf(component(model, "src.main.Main"));
    }

    @Test
    public void pythonCallOfAClassIsAConstructionAndCallOfAFunctionIsNot() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = model(Lang.PYTHON,
                new ProjectFile("/pkg/__init__.py", ""),
                new ProjectFile("/pkg/util.py", "class Helper:\n    pass\n\ndef make():\n    return 1\n"),
                new ProjectFile("/pkg/main.py",
                        "from pathlib import Path\nfrom .util import Helper, make\n\n"
                                + "def f():\n    h = Helper()\n    p = Path('x')\n    m = make()\n    return h\n"));
        final Component f = model.components().filter(c -> c.componentType().isMethodComponent() && c.uniqueName().startsWith("pkg.main.f("))
                .findFirst().orElseThrow();
        assertEquals(new TreeSet<>(Set.of("pkg.util.Helper", "pathlib.Path")), constructed(f));
        assertAlsoReferencedAsATypeOf(f);
    }
}
