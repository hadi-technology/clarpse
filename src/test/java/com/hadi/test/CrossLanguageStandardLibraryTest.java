package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.StandardLibraryStatus;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** A reference to a type outside the codebase says whether the language's standard library holds it. */
public class CrossLanguageStandardLibraryTest {

    private static OOPSourceCodeModel model(final Lang lang, final ProjectFile... files) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        for (final ProjectFile file : files) {
            projectFiles.insertFile(file);
        }
        return new ClarpseProject(projectFiles, lang).result().model();
    }

    private static StandardLibraryStatus statusOf(final OOPSourceCodeModel model, final String component,
                                                  final String target) {
        final Component found = model.copyOfComponent(component).orElseThrow();
        return found.references().stream().filter(ref -> ref.invokedComponent().equals(target))
                .map(ComponentReference::standardLibrary).findFirst()
                .orElseThrow(() -> new AssertionError(target + " not referenced by " + component));
    }

    @Test
    public void java() throws Exception {
        final OOPSourceCodeModel model = model(Lang.JAVA,
                new ProjectFile("/app/Mine.java", "package app;\npublic class Mine { }\n"),
                new ProjectFile("/app/Main.java", "package app;\nimport java.util.List;\nimport org.acme.Thing;\n"
                        + "public class Main { List<Thing> items; Mine mine; }\n"));
        assertEquals(StandardLibraryStatus.STANDARD, statusOf(model, "app.Main", "java.util.List"));
        assertEquals(StandardLibraryStatus.NOT_STANDARD, statusOf(model, "app.Main", "org.acme.Thing"));
        assertEquals("a type of the codebase is never standard", StandardLibraryStatus.NOT_STANDARD,
                statusOf(model, "app.Main", "app.Mine"));
    }

    @Test
    public void kotlin() throws Exception {
        final OOPSourceCodeModel model = model(Lang.KOTLIN, new ProjectFile("/app/Main.kt",
                "package app\nimport java.io.File\nimport org.acme.Thing\nclass Main { val f: File? = null\n"
                        + "  val t: Thing? = null\n  val s: String = \"\" }\n"));
        assertEquals(StandardLibraryStatus.STANDARD, statusOf(model, "app.Main", "java.io.File"));
        assertEquals(StandardLibraryStatus.STANDARD, statusOf(model, "app.Main", "kotlin.String"));
        assertEquals(StandardLibraryStatus.NOT_STANDARD, statusOf(model, "app.Main", "org.acme.Thing"));
    }

    @Test
    public void csharp() throws Exception {
        final OOPSourceCodeModel model = model(Lang.CSHARP, new ProjectFile("/Main.cs",
                "namespace App { public class Main { public int Count; public object Item; } }\n"));
        assertEquals(StandardLibraryStatus.STANDARD, statusOf(model, "App.Main", "System.Int32"));
        assertEquals(StandardLibraryStatus.STANDARD, statusOf(model, "App.Main", "System.Object"));
    }

    @Test
    public void aNameKeptAsWrittenIsNotClaimedEitherWay() throws Exception {
        final OOPSourceCodeModel model = model(Lang.CSHARP, new ProjectFile("/Main.cs",
                "using System.Collections.Generic;\nnamespace App { public class Main { public List<int> Items; } }\n"));
        assertEquals(StandardLibraryStatus.UNKNOWN, statusOf(model, "App.Main", "List"));
    }

    @Test
    public void typescript() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = model(Lang.TYPESCRIPT,
                new ProjectFile("/package.json", "{ \"name\": \"t\", \"version\": \"1.0.0\" }"),
                new ProjectFile("/tsconfig.json",
                        "{ \"compilerOptions\": { \"target\": \"ES2020\", \"module\": \"commonjs\" } }"),
                new ProjectFile("/node_modules/lib/package.json",
                        "{ \"name\": \"lib\", \"version\": \"1.0.0\", \"types\": \"index.d.ts\" }"),
                new ProjectFile("/node_modules/lib/index.d.ts", "export declare class Client {}\n"),
                new ProjectFile("/src/main.ts", "import { Client } from \"lib\";\n"
                        + "export class Main { c: Client | undefined; }\n"));
        assertEquals(StandardLibraryStatus.NOT_STANDARD, statusOf(model, "src.main.Main", "lib:Client"));
    }

    @Test
    public void python() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final OOPSourceCodeModel model = model(Lang.PYTHON, new ProjectFile("/main.py",
                "from pathlib import Path\nfrom acme import Thing\n\n"
                        + "class Main:\n    def f(self, p: Path, t: Thing):\n        return p\n"));
        assertEquals(StandardLibraryStatus.STANDARD, statusOf(model, "main.Main", "pathlib.Path"));
        assertEquals(StandardLibraryStatus.NOT_STANDARD, statusOf(model, "main.Main", "acme.Thing"));
    }
}
