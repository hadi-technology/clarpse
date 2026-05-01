package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpTypeImplementationReferenceTest {

    @Test
    public void accurateImplementedType() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Runner.cs", "namespace Demo; public interface IRunner {}"),
                new ProjectFile("/User.cs", "namespace Demo; public class User : IRunner {}")
        ).model();

        assertTrue(model.getComponent("Demo.User").get()
                .references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("Demo.IRunner")));
    }

    @Test
    public void multipleImplementedTypes() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Runner.cs", "namespace Demo; public interface IRunner {}"),
                new ProjectFile("/Persist.cs", "namespace Demo; public interface IPersisted {}"),
                new ProjectFile("/User.cs", "namespace Demo; public class User : IRunner, IPersisted {}")
        ).model();

        assertTrue(model.getComponent("Demo.User").get()
                .references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("Demo.IRunner")));
        assertTrue(model.getComponent("Demo.User").get()
                .references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("Demo.IPersisted")));
    }

    @Test
    public void implementedTypeReferenceCount() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Runner.cs", "namespace Demo; public interface IRunner {}"),
                new ProjectFile("/Persist.cs", "namespace Demo; public interface IPersisted {}"),
                new ProjectFile("/User.cs", "namespace Demo; public class User : IRunner, IPersisted {}")
        ).model();

        assertEquals(2, model.getComponent("Demo.User").get()
                .references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION).size());
    }

    @Test
    public void nestedClassImplementationIsResolved() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Runner.cs", "namespace Demo; public interface IRunner {}"),
                new ProjectFile("/User.cs", "namespace Demo; public class User { public class Admin : IRunner {} }")
        ).model();

        assertTrue(model.getComponent("Demo.User.Admin").get()
                .references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("Demo.IRunner")));
    }
}
