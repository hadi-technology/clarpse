package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpTypeExtensionReferenceTest {

    @Test
    public void accurateExtendedType() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Base.cs", "namespace Demo; public class BaseUser {}"),
                new ProjectFile("/User.cs", "namespace Demo; public class User : BaseUser {}")
        ).model();

        assertTrue(model.getComponent("Demo.User").get()
                .references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference("Demo.BaseUser")));
    }

    @Test
    public void extendedTypeReferenceCount() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Base.cs", "namespace Demo; public class BaseUser {}"),
                new ProjectFile("/User.cs", "namespace Demo; public class User : BaseUser {}")
        ).model();

        assertEquals(1, model.getComponent("Demo.User").get()
                .references(OOPSourceModelConstants.TypeReferences.EXTENSION).size());
    }

    @Test
    public void nestedClassExtensionIsResolved() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Base.cs", "namespace Demo; public class BaseUser {}"),
                new ProjectFile("/User.cs", "namespace Demo; public class User { public class Admin : BaseUser {} }")
        ).model();

        assertTrue(model.getComponent("Demo.User.Admin").get()
                .references(OOPSourceModelConstants.TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference("Demo.BaseUser")));
    }
}
