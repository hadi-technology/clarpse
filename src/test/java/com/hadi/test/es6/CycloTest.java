package com.hadi.test.es6;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests accuracy of Component cyclomatic complexity attribute. See {@link Component}.
 */
public class CycloTest {

    @Test
    public void testES6AndIfCyclo() throws Exception {
        final String code = "class Polygon { constructor() {  if (true == false && false == true || true) {} } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/test//polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        Assert.assertTrue(generatedSourceModel.getComponent("src.test.Polygon.constructor")
                .get().cyclo() == 4);
    }

    @Test
    public void testES6HookCyclo() throws Exception {
        final String code = "class Polygon { constructor() {  x = true ? true : false } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/test//polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        Assert.assertTrue(generatedSourceModel.getComponent("src.test.Polygon.constructor")
                .get().cyclo() == 2);
    }

    @Test
    public void testEmptyMethodCyclo() throws Exception {
        final String code = "class Polygon { constructor() { } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/test//polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        Assert.assertTrue(generatedSourceModel.getComponent("src.test.Polygon")
                .get().cyclo() == 1);
    }

    @Test
    public void testES6ClassCyclo() throws Exception {
        final String code = "class Polygon { say() {  if (true == false && false == true || true) {} }  " +
                "bye() {  x = true ? true : false } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/test//polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        Assert.assertTrue(generatedSourceModel.getComponent("src.test.Polygon")
                .get().cyclo() == 3);
    }

    @Test
    public void testES6SwitchStatementCyclo() throws Exception {
        final String code = "class Polygon { say() {  switch(expression) {\n" +
                "  case x:\n" +
                "    // code block\n" +
                "    break;\n" +
                "  case y:\n" +
                "    // code block\n" +
                "    break;\n" +
                "  default:\n" +
                "    // code block\n" +
                "} } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/test//polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        Assert.assertTrue(generatedSourceModel.getComponent("src.test.Polygon.say")
                .get().cyclo() == 4);
    }
}
