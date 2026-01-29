package com.hadi.test.es6;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests to ensure package name attribute of parsed components are correct.
 */
public class PackageAttributeTest {

    @Test
    public void ES6FieldVariablePackageName() throws Exception {
        final String code = "class React {} class Polygon { constructor(height) {this.height = new React();} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/github/http/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/github/http", generatedSourceModel.getComponent("github.http.Polygon.height").get().pkg().path());
        assertEquals("github.http", generatedSourceModel.getComponent("github.http.Polygon.height").get().pkg().ellipsisSeparatedPkgPath());
        assertEquals("http", generatedSourceModel.getComponent("github.http.Polygon.height").get().pkg().name());
    }

    @Test
    public void ES6ClassPackageName() throws Exception {
        final String code = "class React {} \n class Polygon { constructor(height) { this.height = new React(); } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/github/http/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/github/http", generatedSourceModel.getComponent("github.http.Polygon.height").get().pkg().path());
        assertEquals("github.http", generatedSourceModel.getComponent("github.http.Polygon.height").get().pkg().ellipsisSeparatedPkgPath());
        assertEquals("http", generatedSourceModel.getComponent("github.http.Polygon.height").get().pkg().name());
    }

    @Test
    public void ES6LocalVariablePackageName() throws Exception {
        final String code = "class Polygon { say() { var test = new React(); var lol = 4; } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/src", generatedSourceModel.getComponent("src.Polygon.say.test").get().pkg().path());
        assertEquals("src", generatedSourceModel.getComponent("src.Polygon.say.test").get().pkg().name());
        assertEquals("src", generatedSourceModel.getComponent("src.Polygon.say.test").get().pkg().ellipsisSeparatedPkgPath());
    }

    @Test
    public void ES6MethodParamPackageName() throws Exception {
        final String code = "class Polygon { say(x) {} }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/cupcake/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/src/cupcake", generatedSourceModel.getComponent("src.cupcake.Polygon.say.x").get().pkg().path());
        assertEquals("cupcake", generatedSourceModel.getComponent("src.cupcake.Polygon.say.x").get().pkg().name());
        assertEquals("src.cupcake", generatedSourceModel.getComponent("src.cupcake.Polygon.say.x").get().pkg().ellipsisSeparatedPkgPath());
    }

    @Test
    public void ES6MethodPackageName() throws Exception {
        final String code = "class Polygon { constructor() {  new React().test(); } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/github/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("/github", generatedSourceModel.getComponent("github.Polygon.constructor").get().pkg().path());
        assertEquals("github", generatedSourceModel.getComponent("github.Polygon.constructor").get().pkg().name());
        assertEquals("github", generatedSourceModel.getComponent("github.Polygon.constructor").get().pkg().ellipsisSeparatedPkgPath());
    }
}
