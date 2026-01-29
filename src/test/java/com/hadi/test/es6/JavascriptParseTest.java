package com.hadi.test.es6;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class JavascriptParseTest {

    @Test
    public void ES6ClassExists() throws Exception {
        final String code = "class Polygon extends Test {get prop() {return 'getter'; }}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon"));
    }

    @Test
    public void ClassesExists() throws Exception {
        final String code = "class Polygon {} class LolCakes{}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon")
                && generatedSourceModel.containsComponent("LolCakes"));
    }

    @Test
    public void ClassExpressionComponentExists() throws Exception {
        final String code = "const test = class Polygon {};";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("test"));
    }

    @Test
    public void ConstructorFunctionExists() throws Exception {
        final String code = "class Polygon { constructor() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.constructor"));
    }

    @Test
    public void ConstructorFunctionNameIsCorrect() throws Exception {
        final String code = "class Polygon { constructor() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.constructor").get().name().equals("constructor"));
    }

    @Test
    public void ConstructorParamComponentExists() throws Exception {
        final String code = "class Polygon { constructor(height) {call();} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.constructor.height"));
    }

    @Test
    public void ConstructorParamComponentsExists() throws Exception {
        final String code = "class Polygon { constructor(height, length, width) {call();} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.constructor.height"));
        assertTrue(generatedSourceModel.containsComponent("Polygon.constructor.length"));
        assertTrue(generatedSourceModel.containsComponent("Polygon.constructor.width"));
    }

    @Test
    public void InstanceMethodExists() throws Exception {
        final String code = "class Polygon { say() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.say"));
    }

    @Test
    public void InstanceMethodNameIsCorrect() throws Exception {
        final String code = "class Polygon { say() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.say").get().name().equals("say"));
    }

    @Test
    public void StaticInstanceMethodExists() throws Exception {
        final String code = "class Polygon { static say() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.say"));
    }

    @Test
    public void AsyncInstanceMethodExists() throws Exception {
        final String code = "class Polygon { static say() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.say"));
    }

    @Test
    public void InstanceMethodParamComponentExists() throws Exception {
        final String code = "class Polygon { constructor() {} call(height) {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.call.height"));
    }

    @Test
    public void InstanceMethodParamComponentsExists() throws Exception {
        final String code = "class Polygon { constructor() {} call(height, length, width) {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.call.height"));
        assertTrue(generatedSourceModel.containsComponent("Polygon.call.length"));
        assertTrue(generatedSourceModel.containsComponent("Polygon.call.width"));
    }

    @Test
    public void GetterMethodExists() throws Exception {
        final String code = "class Polygon { get height() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.get_height"));
    }

    @Test
    public void SetterMethodExists() throws Exception {
        final String code = "class Polygon { set height(str) {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.set_height"));
    }

    @Test
    public void FieldVariableComponentExists() throws Exception {
        final String code = "class Polygon { constructor() {this.height = 4;} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.height"));
    }

    @Test
    public void FieldVariableComponentName() throws Exception {
        final String code = "class Polygon { constructor() {this.height = 4;} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.height").get().name().equals("height"));
    }

    @Test
    public void LocalVariableExists() throws Exception {
        final String code = "class React {} class Polygon { say() { var test = new React(); } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.say.test"));
    }

    @Test
    public void LocalLetVariableComponentExists() throws Exception {
        final String code = "class Polygon { say() { let test = new React(); } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.say.test"));
    }

    @Test
    public void MultipleLocalVariables() throws Exception {
        final String code = "class Polygon { say() { var test = new React(); var lol = 4; } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.say.test")
                && generatedSourceModel.containsComponent("Polygon.say.lol"));
    }

    @Test
    public void testDefaultExportClassHasCorrectUniqueName() throws Exception {
        final String code = "export default class { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/github/test.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("src.github.test"));
    }

    @Test
    public void ConstructorLocalVar() throws Exception {
        final String code = "class Polygon { constructor() {  this.width = 4;  var test = new React(); } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.constructor.test"));
    }
}
