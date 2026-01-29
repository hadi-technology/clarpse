package com.hadi.test.es6;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Ensure component type extensions invocations are accurate.
 */
public class TypeExtensionReferenceTest {

    @Test
    public void testIfParseClassHasCorrectExtendsAttr() throws Exception {
        final String code = "class Shape {} \n class Polygon extends Shape { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        // assert the Polygon class component has one type extension component
        // invocation
        assertEquals(1, generatedSourceModel.getComponent("Polygon").get().references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .size());
        // assert the component being extended is the Shape class
        assertEquals("Shape", generatedSourceModel.getComponent("Polygon").get().references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .get(0).invokedComponent());
    }

    @Test
    public void testIfParseClassHasCorrectExtendsAttrComplex() throws Exception {
        final String codea = "export default class Shape { }";
        final String codeb = "import {Shape as Shape} from 'test/shape' \n class Polygon extends Shape { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/test/shape.js", codea));
        rawData.insertFile(new ProjectFile("/polygon.js", codeb));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        // assert the Polygon class component has one type extension component
        // invocation
        assertEquals(1, generatedSourceModel.getComponent("Polygon").get().references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .size());
        // assert the component being extended is the Shape class
        assertEquals("test.Shape", generatedSourceModel.getComponent("Polygon").get().references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .get(0).invokedComponent());
    }


    @Test
    public void testIfParseClassHasCorrectExtendsAttrComplexv2() throws Exception {
        final String codea = "export default shape = class Shape { }";
        final String codeb = "import {shape as Shape} from 'test/shape' \n class Polygon extends Shape { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/test/shape.js", codea));
        rawData.insertFile(new ProjectFile("/polygon.js", codeb));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        // assert the Polygon class component has one type extension component
        // invocation
        assertEquals(1, generatedSourceModel.getComponent("Polygon").get().references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .size());
        // assert the component being extended is the Shape class
        assertEquals("test.shape", generatedSourceModel.getComponent("Polygon").get().references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .get(0).invokedComponent());
    }

}
