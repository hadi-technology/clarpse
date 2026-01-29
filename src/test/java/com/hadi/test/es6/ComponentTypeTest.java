package com.hadi.test.es6;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Tests to ensure component type attribute of parsed components are accurate.
 */
public class ComponentTypeTest {

    @Test
    public void ES6ClassHasCorrectComponentType() throws Exception {
        final String code = "\n\n class Polygon { }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon").get()
                                       .componentType() == OOPSourceModelConstants.ComponentType.CLASS);
    }


    @Test
    public void ES6ClassSetterHasCorrectComponentName() throws Exception {
        final String code = "class Polygon { set area(value) { this.area = value; } get area()" +
                " { return this.height * this.width; } }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.set_area").get()
                                       .componentType() == OOPSourceModelConstants.ComponentType.METHOD);
        assertTrue(generatedSourceModel.getComponent("Polygon.get_area").get()
                                       .componentType() == OOPSourceModelConstants.ComponentType.METHOD);
    }

    @Test
    public void testParsedES6InstanceMethodParamComponentType() throws Exception {
        final String code = "class Polygon { constructor() {} say(height) {}}";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.say.height")
                                       .get().componentType() == OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT);
    }

    @Test
    public void ES6ConstructorComponentTypeIsCorrect() throws Exception {
        final String code = "class Polygon { constructor() {} }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(
                generatedSourceModel.getComponent("Polygon.constructor").get()
                                    .componentType() == OOPSourceModelConstants.ComponentType.CONSTRUCTOR);
    }

    @Test
    public void ES6LocalVariableComponentType() throws Exception {
        final String code = "class Polygon { say() { var test = new React(); } }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.say.test").get()
                                       .componentType() == OOPSourceModelConstants.ComponentType.LOCAL);
    }

    @Test
    public void ES6LocalLetVariableComponentType() throws Exception {
        final String code = "class Polygon { say() { let test = new React(); } }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.say.test")
                                       .get().componentType() == OOPSourceModelConstants.ComponentType.LOCAL);
    }

    @Test
    public void ES6FieldVariableComponentType() throws Exception {
        final String code = "class Polygon { constructor() {this.height = 4;} }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.height")
                                       .get().componentType() == OOPSourceModelConstants.ComponentType.FIELD);
    }

    @Test
    public void ES6SetterMethodComponentTypeIsCorrect() throws Exception {
        final String code = "class Polygon { set height(value) {} }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.set_height").get()
                                       .componentType() == OOPSourceModelConstants.ComponentType.METHOD);
    }

    @Test
    public void testParsedES6ConstructorParamComponentType() throws Exception {
        final String code = "class Polygon { constructor(height) {} }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.constructor.height")
                                       .get().componentType() == OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT);
    }

    @Test
    public void ES6InstanceMethodComponentTypeIsCorrect() throws Exception {
        final String code = "class Polygon { say() {} }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.say").get()
                                       .componentType() == OOPSourceModelConstants.ComponentType.METHOD);
    }
}
