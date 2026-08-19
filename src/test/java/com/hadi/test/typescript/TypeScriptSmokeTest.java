package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TypeScriptSmokeTest {

    private static final String FIXTURE = "smoke";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void spotCheckClass() {
        Assert.assertTrue(model.containsComponent(name("src/core", "Service", "Service")));
    }

    @Test
    public void spotCheckClassExtension() {
        Assert.assertTrue(model.copyOfComponent(name("src/core", "Service", "Service"))
                .orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference(name("src/core", "Base", "Base"))));
    }

    @Test
    public void spotCheckClassDocs() {
        Assert.assertTrue(model.copyOfComponent(name("src/core", "Service", "Service"))
                .orElseThrow()
                .comment()
                .contains("Service doc"));
    }

    @Test
    public void spotCheckClassImplementation() {
        Assert.assertTrue(model.copyOfComponent(name("src/core", "Service", "Service"))
                .orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference(name("src/core", "Repo", "Repo"))));
    }

    @Test
    public void spotCheckMethod() {
        Assert.assertTrue(model.containsComponent(name("src/core", "Filter", "Filter." +
                TypeScriptTestUtil.signature("included", "string"))));
    }

    @Test
    public void spotCheckListWriterReference() {
        Assert.assertTrue(model.copyOfComponent(name("src/util", "ListWriter", "ListWriter.items"))
                .orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                .contains(new SimpleTypeReference(name("src/util", "ListWriter", "List"))));
    }

    private static String name(final String packagePath, final String moduleName, final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(packagePath, moduleName, symbolPath);
    }
}
