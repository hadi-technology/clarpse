package com.hadi.test.go;

import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import com.hadi.test.ClarpseTestUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MultiModuleParsingTest {

    private static OOPSourceCodeModel multiGoModuleModel;

    @BeforeClass
    public static void setup() throws Exception {
        multiGoModuleModel = ClarpseTestUtil.sourceCodeModel("/multi-go-module-proj.zip",
                                                             Lang.GOLANG);
    }

    @Test
    public void spotCheckMultiGoModuleConfigTestStruct() {
        assertTrue(multiGoModuleModel.containsComponent("service.config.ConfigTestStruct"));
    }

    @Test
    public void spotCheckMultiGoModuleStructSourceFileName() {
        assertEquals("/storage/storage.go",
                     multiGoModuleModel.getComponent("storage.TypeAndKey").get().sourceFile());
    }

    @Test
    public void spotCheckMultiGoModuleTypeAndKeyStruct() {
        assertTrue(multiGoModuleModel.containsComponent("storage.TypeAndKey"));
    }

    @Test
    public void spotCheckMultiGoModuleTypeAndKeyFieldVars() {
        assertTrue(multiGoModuleModel.containsComponent("storage.TypeAndKey.Key"));
        assertTrue(multiGoModuleModel.containsComponent("storage.TypeAndKey.Type"));
    }

    @Test
    public void spotCheckMultiGoModuleTypeAndKeyStructMethod() {
        assertTrue(multiGoModuleModel.containsComponent("storage.TypeAndKey.String() : (string)"));
    }

    @Test
    public void spotCheckMultiGoModuleMethodReferenceExternalStruct() {
        assertTrue(multiGoModuleModel.getComponent(
            "services.cwf.servicers.builderServicer.Build(context.Context, *builder_protos.BuildRequest) : (*builder_protos.BuildResponse, error)")
                       .get().references(TypeReferences.SIMPLE).stream()
                       .anyMatch(reference -> reference.invokedComponent()
                           .equals("services.cwf.obsidian.models.CwfHaPairConfigs")));
    }

    @Test
    public void spotCheckMultiGoModuleUUIDGeneratorStruct() {
        assertTrue(multiGoModuleModel.containsComponent("storage.UUIDGenerator"));
    }

    @Test
    public void spotCheckMultiGoModuleUUIDGeneratorImplementsInterface() {
        assertTrue(multiGoModuleModel.getComponent("storage.UUIDGenerator").get()
                                     .references(TypeReferences.IMPLEMENTATION).get(0).invokedComponent().equals("storage.IDGenerator"));
    }

    @Test
    public void spotCheckMultiGoModuleBinarySerdeStructImplementsInterface() {
        assertTrue(multiGoModuleModel.getComponent("serde.binarySerde").get()
                                     .references(TypeReferences.IMPLEMENTATION).get(0).invokedComponent().equals("serde.Serde"));
    }
}
