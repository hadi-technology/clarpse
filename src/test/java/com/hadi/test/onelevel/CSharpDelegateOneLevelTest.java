package com.hadi.test.onelevel;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.hadi.test.onelevel.OneLevelTestSupport.component;
import static com.hadi.test.onelevel.OneLevelTestSupport.files;
import static com.hadi.test.onelevel.OneLevelTestSupport.project;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * One-level analysis of a C# repository declaring a delegate. A delegate's parameters become
 * components of the type it declares, and each is attributed to the file declaring it, so the
 * compile emits them alongside every other component of that file.
 */
public class CSharpDelegateOneLevelTest {

    private static final String HANDLER = "/Core/Handler.cs";
    private static final String SHAPE = "/Lib/Shape.cs";

    private static CompileResult oneLevel;
    private static OOPSourceCodeModel whole;

    private static Map<String, String> repository() {
        return files(
                HANDLER, "using Lib;\nnamespace App\n{\n"
                        + "    public delegate void Notify(string message, int count);\n"
                        + "    public class Handler\n    {\n"
                        + "        public Notify OnNotify;\n        public Shape Shape;\n    }\n}\n",
                SHAPE, "namespace Lib\n{\n    public class Shape { }\n}\n");
    }

    @BeforeClass
    public static void compile() throws Exception {
        oneLevel = new ClarpseProject(project(repository()), Lang.CSHARP, List.of(HANDLER),
                AnalysisOptions.oneLevel()).result();
        whole = new ClarpseProject(project(repository()), Lang.CSHARP).result().model();
    }

    @Test
    public void aFileDeclaringADelegateCompilesOneLevel() {
        assertNotNull(oneLevel.model());
        assertTrue(oneLevel.model().containsComponent("App.Handler"));
        assertTrue(oneLevel.model().containsComponent("App.Notify"));
    }

    @Test
    public void delegateParametersAreEmitted() {
        assertTrue(oneLevel.model().containsComponent("App.Notify.message"));
        assertTrue(oneLevel.model().containsComponent("App.Notify.count"));
    }

    @Test
    public void delegateParametersNameTheDeclaringFile() {
        assertEquals(HANDLER, component(oneLevel.model(), "App.Notify.message").sourceFile());
        assertEquals(HANDLER, component(oneLevel.model(), "App.Notify.count").sourceFile());
        assertEquals(HANDLER, component(whole, "App.Notify.message").sourceFile());
    }

    @Test
    public void levelOneStillReachesTheReferencedFile() {
        assertTrue(oneLevel.levelOne().levelOneFiles().contains(SHAPE));
    }
}
