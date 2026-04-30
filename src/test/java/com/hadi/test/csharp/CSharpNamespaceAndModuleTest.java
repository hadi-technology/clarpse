package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpNamespaceAndModuleTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/src/App/User.cs", """
                        namespace Demo.Feature;
                        public class User {
                          public string Name { get; set; }
                        }
                        """)
        ).model();
    }

    @Test
    public void namespaceBecomesPackage() {
        final Component component = model.getComponent("Demo.Feature.User").get();
        assertEquals("Demo.Feature", component.pkg().name());
        assertEquals("Demo.Feature", component.pkg().path());
    }

    @Test
    public void fileNameBecomesModule() {
        final Component component = model.getComponent("Demo.Feature.User").get();
        assertEquals("User", component.module());
    }

    @Test
    public void nestedNamespaceBlocksPreserveParentNamespace() throws Exception {
        final OOPSourceCodeModel nestedNamespaceModel = CSharpTestUtil.compileInline(
                new ProjectFile("/src/App/Nested.cs", """
                        namespace Demo {
                          namespace Feature {
                            public class NestedUser {}
                          }
                        }
                        """)
        ).model();

        assertTrue(nestedNamespaceModel.getComponent("Demo.Feature.NestedUser").isPresent());
    }
}
