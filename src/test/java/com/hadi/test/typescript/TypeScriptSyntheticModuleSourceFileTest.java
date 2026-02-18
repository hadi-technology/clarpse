package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TypeScriptSyntheticModuleSourceFileTest {

    private static final String FIXTURE = "module-vars";
    private static final String PACKAGE_PATH = "src/foo/bar";
    private static final String MODULE = "Config";

    private static OOPSourceCodeModel model;
    private static Path fixtureRoot;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
        fixtureRoot = TypeScriptTestUtil.fixturePath(FIXTURE);
    }

    @Test
    public void moduleFieldUsesSourceFilePathFromOwningFile() {
        Component cmp = model.getComponent(name("API_URL")).orElseThrow();
        String sourceFile = cmp.sourceFile();
        assertFalse(sourceFile == null || sourceFile.isEmpty());
        assertEquals(filePath("src/foo/bar/Config.ts"), sourceFile);
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }

    private static String filePath(final String relativePath) {
        return fixtureRoot.resolve(relativePath).toAbsolutePath().toString();
    }
}
