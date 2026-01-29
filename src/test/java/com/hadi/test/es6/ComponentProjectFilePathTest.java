package com.hadi.test.es6;


import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Ensure components are displaying the correct associated source file path.
 */
public class ComponentProjectFilePathTest {

    @Test
    public void ES6ClassHasCorrectSourceFileAttr() throws Exception {
        final String code = "class Polygon {}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/polygon.js", generatedSourceModel.getComponent("Polygon").get().sourceFile());
    }

    @Test
    public void ES6ClassMethodHasCorrectSourceFileAttr() throws Exception {
        final String code = "class Polygon { say() {}}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/polygon.js", generatedSourceModel.getComponent("Polygon.say").get().sourceFile());
    }

    @Test
    public void ES6ClassConstructorHasCorrectSourceFileAttr() throws Exception {
        final String code = "class Polygon { constructor() {}}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/polygon.js", generatedSourceModel.getComponent("Polygon.constructor").get().sourceFile());
    }

    @Test
    public void ES6ClassFieldVarHasCorrectSourceFileAttr() throws Exception {
        final String code = "class Polygon { constructor() {this.height = 4;}}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/polygon.js", generatedSourceModel.getComponent("Polygon.height").get().sourceFile());
    }
}
