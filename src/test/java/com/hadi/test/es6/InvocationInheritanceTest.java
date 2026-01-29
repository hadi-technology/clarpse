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
 * Ensure component invocations for a given component are inherited by its
 * parents.
 */
public class InvocationInheritanceTest {

    @Test
    public void parentClassHasConstructorSimpleReferenceTest() throws Exception {
        final String codeA = "let polygon = class Polygon {} \n export default polygon;";
        final String codeB = "import Muffin from './../shapes/polygon'; \n class Cake { constructor() { new Muffin();" +
                " } }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/shapes/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/com/types/cake.js", codeB));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("com.types.Cake").get().references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                                       .get(0).invokedComponent().equals("com.shapes.polygon"));
    }


    @Test
    public void constructorHasSimpleReferenceTest() throws Exception {
        final String codeA = "let polygon = class Polygon {} \n export default polygon;";
        final String codeB = "import Muffin from './../shapes/polygon'; \n class Cake { constructor() { new Muffin();" +
                " } }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/shapes/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/com/types/cake.js", codeB));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("com.types.Cake.constructor").get()
                                       .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                                       .get(0).invokedComponent().equals("com.shapes.polygon"));
    }


    @Test
    public void parentClassHasFieldVarSimpleReferenceTest() throws Exception {
        final String codeA = "let polygon = class Polygon {} \n export default polygon;";
        final String codeB = "import Muffin from './../shapes/polygon'; \n class Cake { constructor() { this.muffin =" +
                " new Muffin(); } }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/shapes/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/com/types/cake.js", codeB));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("com.types.Cake.muffin").get().references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                                       .get(0).invokedComponent().equals("com.shapes.polygon"));
    }


    @Test
    public void parentConstructorDoesNotHaveFieldVarSimpleReferenceTest() throws Exception {
        final String codeA = "let polygon = class Polygon {} \n export default polygon;";
        final String codeB = "import Muffin from './../shapes/polygon'; \n class Cake { constructor() { this.muffin =" +
                " new Muffin(); } }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/shapes/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/com/types/cake.js", codeB));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("com.types.Cake.constructor").get()
                                       .references(OOPSourceModelConstants.TypeReferences.SIMPLE).size() == 1);
    }

    @Test
    public void parentMethodHasLocalVarSimpleReferenceTest() throws Exception {
        final String codeA = "let polygon = class Polygon {} \n export default polygon;";
        final String codeB = "import Muffin from './../shapes/polygon'; \n class Cake { test() { var muffin = new " +
                "Muffin(); } }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/shapes/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/com/types/cake.js", codeB));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("com.types.Cake.test").get().references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                                       .get(0).invokedComponent().equals("com.shapes.polygon"));
    }

    @Test
    public void parentClassHasLocalVarSimpleReferenceTest() throws Exception {
        final String codeA = "let polygon = class Polygon {} \n export default polygon;";
        final String codeB = "import Muffin from './../shapes/polygon'; \n class Cake { test() { var muffin = new " +
                "Muffin(); } }";
        ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/shapes/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/com/types/cake.js", codeB));
        ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("com.types.Cake").get().references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                                       .get(0).invokedComponent().equals("com.shapes.polygon"));
    }


}
