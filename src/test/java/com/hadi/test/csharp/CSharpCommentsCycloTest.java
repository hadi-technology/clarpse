package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpCommentsCycloTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Flow.cs", """
                        namespace Demo;
                        /// <summary>User aggregate.</summary>
                        public class User {
                          /// <summary>Saves a user.</summary>
                          public void Save() {
                            if (true && true) {
                              for (var i = 0; i < 1; i++) {}
                            }
                          }
                        }
                        """)
        ).model();
    }

    @Test
    public void commentsAreAttached() {
        assertTrue(model.getComponent("Demo.User").get().comment().contains("summary"));
        assertTrue(model.getComponent("Demo.User.Save()").get().comment().contains("Saves a user"));
    }

    @Test
    public void codeFragmentsAreCaptured() {
        assertEquals("public void Save()", model.getComponent("Demo.User.Save()").get().codeFragment());
    }

    @Test
    public void methodCycloIsComputed() {
        assertEquals(4, model.getComponent("Demo.User.Save()").get().cyclo());
    }
}
