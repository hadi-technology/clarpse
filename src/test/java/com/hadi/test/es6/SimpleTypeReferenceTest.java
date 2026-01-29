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
* Tests to ensure module imports are resolved properly.
*/
public class SimpleTypeReferenceTest {

    /**
     * Simple type reference from single named import
     */
   @Test
   public void ConstructorMethodCallTest() throws Exception {
       final String codeA = "export class Polygon { }";
       final String codeB = "import { Polygon } from \'../shapes/polygon\'; \n class Cake { constructor() {  Polygon.test(); } }";
       final ProjectFiles rawData = new ProjectFiles();
       rawData.insertFile(new ProjectFile("/com/shapes/polygon.js", codeA));
       rawData.insertFile(new ProjectFile("/com/types/cake.js", codeB));
       final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
       final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
       assertEquals("com.shapes.Polygon", generatedSourceModel.getComponent("com.types.Cake.constructor").get()
               .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
   }

    /**
     * Simple type reference from double named import
     */
    @Test
    public void ConstructorMethodCallComplexTest() throws Exception {
        final String codeA = "export class Polygon { test() { } }";
        final String codeB = "import { Polygon } from \'/polygon.js\';  \n class Cake { constructor() {  Polygon.test(); } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("Polygon", generatedSourceModel.getComponent("Cake.constructor").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    /**
     * Test case: import { export1, export2 } from "module-name";
     */
    @Test
    public void ES6SimpleNamedExternalImportTest() throws Exception {
        final String codeA = "export default class Button { }";
        final String codeB = "import Button from './components/button'; \n class Cake { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/components/button.js", codeA));
        rawData.insertFile(new ProjectFile("/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Cake").get().imports().contains("components.Button"));
    }

    /**
     * Test case: import defaultExport, {export1 as alias1} from "module-name";
     */
    @Test
    public void ES6DefaultImportWithNamedImportWithAliasTest() throws Exception {
        final String codeA = "export default class LoL {}; export class Button {};";
        final String codeB = "import Coin, { Button as button } from \'components/test\'; \n class Cake { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/components/test.js", codeA));
        rawData.insertFile(new ProjectFile("/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Cake").get().imports().contains("components.Button"));
        assertTrue(generatedSourceModel.getComponent("Cake").get().imports().contains("components.LoL"));
    }

    /**
     * Test case: import * as name from "module-name";
     */
    @Test
    public void ES6AliasedAsteriskImportNotSupportedTest() throws Exception {
        final String codeB = "import * as Test from \'components/Button\'; \n class Cake { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(0, generatedSourceModel.getComponent("Cake").get().imports().size());
    }

    /**
     * Test case: import "module-name";
     */
    @Test
    public void ES6ModuleImportOnlyNotSupportedTest() throws Exception {
        final String codeB = "import \'components/Button\'; \n class Cake { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals(0, generatedSourceModel.getComponent("Cake").get().imports().size());
    }

    @Test
    public void LocalLetVariableTypeDeclaration() throws Exception {
        final String code = "import { React } from \'github/react.js\'; \n class Polygon { say() { let test = new React(); } }";
        final String codeB = "export class React {}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/test/polygon.js", code));
        rawData.insertFile(new ProjectFile("/src/test/github/react.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("src.test.github.React", generatedSourceModel.getComponent("src.test.Polygon.say.test")
                .get().references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void LocalVariableTypeInstantiation() throws Exception {
        final String code = "class React {} \n class Polygon { say() { var test = new React(); var lol = 4; } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", code));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("React", generatedSourceModel.getComponent("Polygon.say.test")
                .get().references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void MethodTypeDeclarationFromStaticMethodCall() throws Exception {
        final String code = "import { React } from \'github/react.js\'; \n class Polygon { constructor() {  React.test(); } }";
        final String codeB = "export class React {}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/test/polygon.js", code));
        rawData.insertFile(new ProjectFile("/src/test/github/react.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("src.test.github.React", generatedSourceModel.getComponent("src.test.Polygon.constructor")
                .get().references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void testResolvingOfAbsoluteImportPath() throws Exception {
        final String code = "import { React } from \'/src/test/github/react.js\'; \n class Polygon { constructor() {  React.test(); } }";
        final String codeB = "export class React {}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/test/polygon.js", code));
        rawData.insertFile(new ProjectFile("/src/test/github/react.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("src.test.github.React", generatedSourceModel.getComponent("src.test.Polygon.constructor")
                .get().references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }

    @Test
    public void testResolvingOfAliasImportType() throws Exception {
        final String code = "import { React as LoL } from \'/src/test/github/react.js\'; \n class Polygon { constructor() {  LoL.test(); } }";
        final String codeB = "export class React {}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/test/polygon.js", code));
        rawData.insertFile(new ProjectFile("/src/test/github/react.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertEquals("src.test.github.React", generatedSourceModel.getComponent("src.test.Polygon.constructor")
                .get().references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }
}
