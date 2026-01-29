package com.hadi.test.es6;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests to ensure module imports are resolved properly. An excellent refresher on ES6 import/export
 * functionality: https://exploringjs.com/es6/ch_modules.html
 */
public class ResolveImportsTest {

    /**
     * Test case:
     * Exporting Module: export class export1 {}
     * Importing Module: import { export1 } from "module-name/path/to/specific/un-exported/ProjectFile";
     */
    @Test
    public void SingleNamedLocalImportTest() throws Exception {
        final String codeA = "let polygon = class Polygon {} \n export default polygon;";
        final String codeB = "import Muffin from './../shapes/polygon'; \n class Cake { constructor() {new Muffin(); } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/shapes/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/com/types/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("com.types.Cake").get().imports().contains("com.shapes.polygon"));
    }

    /**
     * Test case:
     * Exporting Module: class export1 {} export { export1 as export2}
     * Importing Module: import { export2 } from "/../ProjectFile";
     */
    @Test
    public void AliasExportAndNamedLocalImportTest() throws Exception {
        final String codeA = "class Polygon { }; export { Polygon as Triangle };";
        final String codeB = "import { Triangle } from '../shapes/polygon'; \n class Cake { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/com/shapes/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/com/types/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("com.types.Cake").get().imports().contains("com.shapes.Polygon"));
    }


    /**
     * Test case:
     * Exporting Module: export default class {}
     * Importing Module: import export2 from "/../ProjectFile";
     */
    @Test
    public void UnnamedDefaultExport() throws Exception {
        final String code = "export default class { }";
        final String codeB = "import Cakes from './test'; class Muffin {}";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/github/test.js", code));
        rawData.insertFile(new ProjectFile("/src/github/muffin.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("src.github.Muffin").get().imports()
                .contains("src.github.test"));
    }


    /**
     * Test case
     * *   Exporting Module: export class export1 {} export class export2 {}
     * *   Importing Module: import { export1, .. } from ".././ProjectFile";
     */
    @Test
    public void MultipleNamedLocalImportTest() throws Exception {
        final String codeA = "export class Polygon { }; \n export class Cuppy {};";
        final String codeB = "import { Polygon, Cuppy } from 'polygon.js';  \n class Cake { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Cake").get().imports().contains("Polygon"));
        assertTrue(generatedSourceModel.getComponent("Cake").get().imports().contains("Cuppy"));
    }

    /**
     * Test case
     * *   Exporting Module: class export1 {} class export2 {} export {export1, export2 as export3, export4}
     * *   Importing Module: import { export3, export4 } from ".././ProjectFile";
     */
    @Test
    public void AliasExportAndMultipleNamedLocalImportTest() throws Exception {
        final String codeA = "class Lemo { } class Choco {} export {Lemo as Nade, Choco as Late};";
        final String codeB = "import { Nade, Late } from '/polygon.js';  \n class Cake { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Cake").get().imports().contains("Lemo"));
        assertTrue(generatedSourceModel.getComponent("Cake").get().imports().contains("Choco"));
    }

    @Test
    public void MultipleSimilarNamedLocalImportTest() throws Exception {
        final String codeA = "export class Polygon { };";
        final String codeB = "import { Polygon } from 'test//polygon.js';  \n class Cake { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/test/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/test/test/polygon.js", codeA));
        rawData.insertFile(new ProjectFile("/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Cake").get().imports().contains("test.Polygon"));
        assertFalse(generatedSourceModel.getComponent("Cake").get().imports().contains("test.test.Polygon"));
    }

    /**
     * Test case
     *     Exporting Module: export class classA {};
     * *   Importing Module: import Dog from "module-name";
     */
    @Test
    public void SimpleDefaultImportTest() throws Exception {
        final String codeA = "export default class ClassA { };";
        final String codeB = "import Button from '../../classa'; \n class Cake { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/classa.js", codeA));
        rawData.insertFile(new ProjectFile("/src/test/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("src.test.Cake").get().imports().contains("ClassA"));
    }

    /**
     * Test case
     * *   Importing Module: import defaultExport, {export1 as alias1} from "module-name";
     */
    @Test
    public void DefaultImportWithNamedImportWithAliasTest() throws Exception {
        final String codeA = "let b = class Button { }; export {b as default}; export class E { };";
        final String codeB = "import Coin, { E as V } from './button'; \n class Cake { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/src/lol/button.js", codeA));
        rawData.insertFile(new ProjectFile("/src/lol/cake.js", codeB));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("src.lol.Cake").get().imports().contains("src.lol.b"));
        assertTrue(generatedSourceModel.getComponent("src.lol.Cake").get().imports().contains("src.lol.E"));
    }

    /**
     * Test case
     * *   Exporting Module: export default class export1 {}
     * *   Importing Module: import export2 from "module-name";
     */
    @Test
    public void DefaultExportAliasImportTest() throws Exception {
        final String codeB = "export default class Cake { };";
        final String codeC = "import Muffin from 'tester/ingredients/cake.js'; class Dessert { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/tester/ingredients/cake.js", codeB));
        rawData.insertFile(new ProjectFile("/dessert.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Dessert").get().imports().contains("tester.ingredients.Cake"));
    }

    /**
     * Test case
     * *   Exporting Module: export default export1 = class {}
     * *   Importing Module: import export2 from "module-name";
     */
    @Test
    public void DefaultExportWithAnonClassExpressionTest() throws Exception {
        final String codeB = "export default Cake = class { };";
        final String codeC = "import Muffin from './tester/ingredients/cake.js'; class Dessert { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/tester/ingredients/cake.js", codeB));
        rawData.insertFile(new ProjectFile("/dessert.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Dessert").get().imports().contains("tester.ingredients.Cake"));
    }

    /**
     * Test case
     * *   Exporting Module: export default export1 = class classA{}
     * *   Importing Module: import export2 from "module-name";
     */
    @Test
    public void DefaultExportWithClassExpressionTest() throws Exception {
        final String codeB = "export default Cake = class ClassA { };";
        final String codeC = "import Muffin from 'tester/ingredients/cake.js'; class Dessert { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/tester/ingredients/cake.js", codeB));
        rawData.insertFile(new ProjectFile("/dessert.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Dessert").get().imports().contains("tester.ingredients.Cake"));
    }

    /**
     * Test case
     * *   Exporting Module: let Cake = class {}; export default Cake
     * *   Importing Module: import export2 from "module-name";
     */
    @Test
    public void DefaultExportWithVarReferencingClassExpressionTest() throws Exception {
        final String codeB = "let Cake = class {}; \n export default Cake;";
        final String codeC = "import Muffin from './tester/ingredients/cake.js'; class Dessert { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/tester/ingredients/cake.js", codeB));
        rawData.insertFile(new ProjectFile("/dessert.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Dessert").get().imports().contains("tester.ingredients.Cake"));
    }

    /**
     * Test case
     * *   Exporting Module: let Cake = class Random {}; export default Cake
     * *   Importing Module: import export2 from "module-name";
     */
    @Test
    public void DefaultExportWithVarReferencingClassExpressionTestv2() throws Exception {
        final String codeB = "let Cake = class Random {}; \n export default Cake;";
        final String codeC = "import Muffin from 'tester/ingredients/cake.js'; class Dessert { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/tester/ingredients/cake.js", codeB));
        rawData.insertFile(new ProjectFile("/dessert.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Dessert").get().imports().contains("tester.ingredients.Cake"));
    }

    /**
     * Test case
     * *   Exporting Module: class export1{} export { export1 as default}
     * *   Importing Module: import export2 from "module-name";
     */
    @Test
    public void ComplexDefaultExportAndImportTest() throws Exception {
        final String codeB = "class Cake { } \n export { Cake as default } ";
        final String codeC = "import Muffin from 'cake.js'; class Dessert { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/cake.js", codeB));
        rawData.insertFile(new ProjectFile("/dessert.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Dessert").get().imports().contains("Cake"));
    }

    /**
     * Test case
     * *   Exporting Module: class export1{}; let Muffin = class{}; export { export1, Muffin as export2}
     * *   Importing Module: import export2 from "module-name";
     */
    @Test
    public void MultipleExportAndImportTest() throws Exception {
        final String codeB = "class Cake { }; \n let Muffin = class LoL{}; \n export { Cake, Muffin as Test} ";
        final String codeC = "import {Test as Test2} from 'cake.js'; class Dessert { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/cake.js", codeB));
        rawData.insertFile(new ProjectFile("/dessert.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Dessert").get().imports().contains("Muffin"));
    }

    /**
     * Test case
     * *   First Module: export class Dog {};
     * *   Second Module: export * from first-module
     * *   Third Module: import { Dog } from "second-module";
     */
    @Test
    public void ExportAllChainTest() throws Exception {
        final String codeA = "export class Cake { }";
        final String codeB = "export * from './test/cake';";
        final String codeC = "import { Cake } from 'codeb'; class Test { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/test/cake.js", codeA));
        rawData.insertFile(new ProjectFile("/codeb.js", codeB));
        rawData.insertFile(new ProjectFile("/codec.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Test").get().imports().contains("test.Cake"));
    }

    @Test
    public void ExportAllWithDefaultChainTest() throws Exception {
        final String codeA = "export default class Cake { }";
        final String codeB = "export * from './test/cake';";
        final String codeC = "import Puppy from 'codeb'; class Test { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/test/cake.js", codeA));
        rawData.insertFile(new ProjectFile("/codeb.js", codeB));
        rawData.insertFile(new ProjectFile("/codec.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Test").get().imports().contains("test.Cake"));
    }

    @Test
    public void ExportAllWithDefaultChainv2Test() throws Exception {
        final String codeA = "export default class Cake { }";
        final String codeB = "import * as fruit from '/test/cake'; export { fruit as mango };";
        final String codeC = "import * as kiwi from 'codeb'; class Test { constructor() { new kiwi.Cake(); } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/test/cake.js", codeA));
        rawData.insertFile(new ProjectFile("/codeb.js", codeB));
        rawData.insertFile(new ProjectFile("/codec.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Test").get().imports().contains("test.Cake"));
    }

    @Test
    public void ExportChainTestv2() throws Exception {
        final String codeA = "let k = class test { }; \n export { k as default };";
        final String codeB = "export { default } from './test/cake';";
        final String codeC = "import Muffin from 'codeb'; class Test { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/test/cake.js", codeA));
        rawData.insertFile(new ProjectFile("/codeb.js", codeB));
        rawData.insertFile(new ProjectFile("/codec.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Test").get().imports().contains("test.k"));
    }

    @Test
    public void ExportChainTestv3() throws Exception {
        final String codeA = "let k = class Cake {}; export {k as default};";
        final String codeB = "import Bob from 'test/cake'; export { Bob as default } ";
        final String codeC = "import Puppy from 'codeb'; class Test { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/test/cake.js", codeA));
        rawData.insertFile(new ProjectFile("/codeb.js", codeB));
        rawData.insertFile(new ProjectFile("/codec.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Test").get().imports().contains("test.k"));
    }

    @Test
    public void ExportChainTestv5() throws Exception {
        final String codeA = "let k = class {}; export {k as default};";
        final String codeB = "import Bob from 'test/cake'; export { Bob as default } ";
        final String codeC = "import Puppy from 'codeb'; class Test { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/test/cake.js", codeA));
        rawData.insertFile(new ProjectFile("/codeb.js", codeB));
        rawData.insertFile(new ProjectFile("/codec.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Test").get().imports().contains("test.k"));
    }


    @Test
    public void ExportChainTestv4() throws Exception {
        final String codeA = "export default class Cake { }";
        final String codeB = "import Muffins from './test/cake';  export { Muffins as default };";
        final String codeC = "import Cupcakes from 'codeb'; class Test { }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/test/cake.js", codeA));
        rawData.insertFile(new ProjectFile("/codeb.js", codeB));
        rawData.insertFile(new ProjectFile("/codec.js", codeC));
        final ClarpseProject parseService = new ClarpseProject(rawData, Lang.JAVASCRIPT);
        final OOPSourceCodeModel generatedSourceModel = parseService.result().model();
        assertTrue(generatedSourceModel.getComponent("Test").get().imports().contains("test.Cake"));
    }
}
