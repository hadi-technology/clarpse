package com.hadi.test.es6;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommentsParsingTest {

    @Test
    public void ES6ClassDoc() throws Exception {
        final String code = "/**Test*/ class Polygon extends Test {get prop() {return 'getter'; }}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/**Test*/", generatedSourceModel.getComponent("Polygon").get().comment());
    }

    @Test
    public void ES6InstanceMethodDoc() throws Exception {
        final String code = "class Polygon { /** say doc \n comment */ say() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/** say doc \n comment */", generatedSourceModel.getComponent("Polygon.say").get().comment());
    }

    @Test
    public void ES6ClassFieldVarDoc() throws Exception {
        final String code = "class Polygon { constructor() {/** the height of /n some stuff \n */ \nthis.height = 4;} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/** the height of /n some stuff \n" +
                " */", generatedSourceModel.getComponent("Polygon.height").get().comment());
    }

    @Test
    public void ES6LocalVarDoc() throws Exception {
        final String code = "class Polygon { constructor() { /** some local var docs */ \n var test;} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/** some local var docs */", generatedSourceModel.getComponent("Polygon.constructor.test").get().comment());
    }

    @Test
    public void ES6ConstructorDoc() throws Exception {
        final String code = "class Polygon { /** constructor doc */ constructor() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/** constructor doc */", generatedSourceModel.getComponent("Polygon.constructor").get().comment());
    }
}
