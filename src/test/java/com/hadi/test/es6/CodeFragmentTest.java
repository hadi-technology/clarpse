package com.hadi.test.es6;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CodeFragmentTest {

    @Test
    public void ES6FieldVariableMultipleComponentTypes() throws Exception {
        final String code = "class Polygon { constructor() {this.height = 4; this.width = false;} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.containsComponent("Polygon.height")
                && generatedSourceModel.containsComponent("Polygon.width"));
        assertTrue(generatedSourceModel.getComponent("Polygon.width").get().codeFragment()
                .equals("width : Boolean"));
    }

    @Test
    public void ES6FieldVariableBooleanValue() throws Exception {
        final String code = "class Polygon { constructor(height) {this.height = true;} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.height").get().codeFragment()
                .equals("height : Boolean"));
    }

    @Test
    public void ES6FieldVariableStringValue() throws Exception {
        final String code = "class Polygon { constructor(height) {this.height = \"test\";} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.height").get().codeFragment()
                .equals("height : String"));
    }

    @Test
    public void ES6FieldVariableNumberValue() throws Exception {
        final String code = "class Polygon { constructor(height) {this.height = { num: 24 }} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.height").get().codeFragment()
                .equals("height : Object"));
    }

    @Test
    public void ES6FieldVariableTypeInstantiation() throws Exception {
        final String code = "class React {} class Polygon { constructor(height) {this.height = new React();} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.height").get().codeFragment()
                .equals("height : React"));
    }

    @Test
    public void ES6FieldVariableTypeInstantiationWithValues() throws Exception {
        final String code = "class React {} class Polygon { constructor(height) {this.height = new React(2,4,\"fe\");} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.height").get().codeFragment()
                .equals("height : React"));
    }

    @Test
    public void ES6FieldVariableNoTypeCodeFragment() throws Exception {
        final String code = "class Polygon { constructor(someVar) {this.height = someVar;} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.height").get().codeFragment()
                .equals("height"));
    }

    @Test
    public void ES6ClassConstructorCodeFragment() throws Exception {
        final String code = "class Polygon { constructor(height) {this.height = height;} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.constructor").get().codeFragment()
                .equals("constructor(height)"));
    }

    @Test
    public void ES6ClassMethodCodeFragment() throws Exception {
        final String code = "class Polygon { walk(height) {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.walk").get().codeFragment()
                .equals("walk(height)"));
    }

    @Test
    public void ES6ClassMethodMultipleParamsWithNoSpacesCodeFragment() throws Exception {
        final String code = "class Polygon { walk(height,length) {} }"; // no space b/w params
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.walk").get().codeFragment()
                .equals("walk(height, length)")); // Code fragment has space b/w params.
    }

    @Test
    public void ES6ClassMethodParamWithDefaultValueCodeFragment() throws Exception {
        final String code = "class Polygon { walk(height = 4) {} }"; // no space b/w params
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.walk").get().codeFragment()
                .equals("walk(height)")); // Code fragment has space b/w params.
    }

    @Test
    public void ES6ClassGetterMethodCodeFragment() throws Exception {
        final String code = "class Polygon extends Test {get prop() {return 'getter'; }}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.get_prop").get().codeFragment()
                .equals("get_prop()"));
    }

    @Test
    public void ES6ClassSetterMethodCodeFragment() throws Exception {
        final String code = "class Polygon extends Test { constructor() {this.fieldVar = 4;} set prop(newVal) " +
                "{this.fieldVar = newVal; }}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.set_prop").get().codeFragment()
                .equals("set_prop(newVal)"));
    }
}
