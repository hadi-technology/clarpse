package com.hadi.test.es6;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.ArrayList;

import static junit.framework.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChildComponentsTest {

    @Test
    public void ES6InstanceMethodParamComponentIsChildOfInstanceMethod() throws Exception {
        final String code = "class Polygon { constructor() {} say(height, length) {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.say").get().children().contains("Polygon.say.height"));
        assertTrue(generatedSourceModel.getComponent("Polygon.say").get().children().contains("Polygon.say.length"));
    }

    @Test
    public void noJsFilesParsedTest() throws Exception {
        final ProjectFiles rawData = new ProjectFiles();
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(0, generatedSourceModel.components().count());
    }

    @Test
    public void ES6GetterIsChildOfParentClass() throws Exception {
        final String code = "class Polygon { get height() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(new ArrayList<>(generatedSourceModel.getComponent(
            "Polygon").get().children()).contains("Polygon.get_height"));
    }

    @Test
    public void ES6FieldVariableIsChildOfClass() throws Exception {
        final String code = "class Polygon { constructor() {this.height = 4;} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(new ArrayList<>(generatedSourceModel.getComponent("Polygon"
        ).get().children()).contains("Polygon.height"));
    }

    @Test
    public void ES6ConstructorIsChildOfParentClass() throws Exception {
        final String code = "class Polygon { constructor() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("Polygon.constructor", new ArrayList<>(generatedSourceModel.getComponent(
            "Polygon").get().children()).get(0));
    }

    @Test
    public void ES6InstanceMethodIsChildOfParentClass() throws Exception {
        final String code = "class Polygon { say() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("Polygon.say",
                     new ArrayList<>(generatedSourceModel.getComponent("Polygon").get().children()).get(0));
    }

    @Test
    public void ES6ConstructorParamComponentsIsChildOfConstructor() throws Exception {
        final String code = "class Polygon { constructor(height, length) {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(new ArrayList<>(generatedSourceModel.getComponent("Polygon.constructor").get()
                                                       .children()).contains("Polygon.constructor.height"));
        assertTrue(new ArrayList<>(generatedSourceModel.getComponent("Polygon.constructor").get().children())
                .contains("Polygon.constructor.length"));
    }

    @Test
    public void ES6LocalVariableIsChildOfParentMethod() throws Exception {
        final String code = "class Polygon { say() { var test = new React(); var lol = 4; } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(2, generatedSourceModel.getComponent("Polygon.say").get().children().size());
        assertTrue(new ArrayList<>(generatedSourceModel.getComponent(
            "Polygon.say").get().children()).contains("Polygon.say.lol"));
    }
}
