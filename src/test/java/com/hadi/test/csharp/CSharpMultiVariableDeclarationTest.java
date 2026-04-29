package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpMultiVariableDeclarationTest {

    @Test
    public void fieldMultiVariableDeclarationCreatesMultipleComponents() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Fields.cs", """
                        namespace Demo;
                        public class User {
                          private Repo first, second;
                        }
                        public class Repo {}
                        """)
        ).model();

        assertTrue(model.containsComponent("Demo.User.first"));
        assertTrue(model.containsComponent("Demo.User.second"));
    }

    @Test
    public void fieldMultiVariableDeclarationRetainsTypeReference() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Fields.cs", """
                        namespace Demo;
                        public class User {
                          private Repo first, second;
                        }
                        public class Repo {}
                        """)
        ).model();

        assertEquals("Demo.Repo", model.getComponent("Demo.User.first").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
        assertEquals("Demo.Repo", model.getComponent("Demo.User.second").get()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).get(0).invokedComponent());
    }
}
