package com.hadi.test.es6;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class AccessModifiersTest {

    @Test
    public void ES6StaticInstanceMethodAccessModifier() throws Exception {
        final String code = "class Polygon { static say() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.say").get().modifiers().contains("static"));
    }

    @Test
    public void ES6ClassAccessModifier() throws Exception {
        final String code = "class Polygon { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon").get().modifiers().isEmpty());
    }

    @Test
    public void ES6InstanceMethodVarAccessModifier() throws Exception {
        final String code = "class Polygon { say() { let test = 4; } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.say.test").get().modifiers().isEmpty());
    }

    @Test
    public void ES6ClassFieldAccessModifier() throws Exception {
        final String code = "class Polygon { constructor() {this.height = 4;} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.height").get().modifiers().contains("private"));
    }

    @Test
    public void ES6ClassConstructorAccessModifier() throws Exception {
        final String code = "class Polygon { constructor() {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Polygon.constructor").get().modifiers().isEmpty());
    }
}