package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeScriptPathsHeavyTest {

    private static final String FIXTURE = "paths-heavy";
    private static final String APP_PACKAGE = "src/app";
    private static final String APP_MODULE = "App";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void resolvesAliasesAndBarrels() {
        String runName = name("App." + TypeScriptTestUtil.signature("run", "User", "Profile", "Id"));
        String userRef = TypeScriptTestUtil.uniqueName("src/domain/user", "User", "User");
        String profileRef = TypeScriptTestUtil.uniqueName("src/domain/user/profile", "Profile", "Profile");
        String idRef = TypeScriptTestUtil.uniqueName("src/shared", "types", "Id");

        assertTrue(model.copyOfComponent(runName).orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                .contains(new SimpleTypeReference(userRef)));
        assertTrue(model.copyOfComponent(runName).orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                .contains(new SimpleTypeReference(profileRef)));
        assertTrue(model.copyOfComponent(runName).orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE)
                .contains(new SimpleTypeReference(idRef)));
    }

    @Test
    public void outputIsDeterministic() throws Exception {
        CompileResult first = TypeScriptTestUtil.compileFixture(FIXTURE);
        CompileResult second = TypeScriptTestUtil.compileFixture(FIXTURE);
        assertEquals(TypeScriptTestUtil.modelSnapshot(first.model()),
                TypeScriptTestUtil.modelSnapshot(second.model()));
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(APP_PACKAGE, APP_MODULE, symbolPath);
    }
}
