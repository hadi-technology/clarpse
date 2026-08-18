package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class JavaConstructorAssignedFieldsTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final String code = "class User {} class Service { User owner; Service(User owner) { this.owner = owner; "
                + "User temporary = owner; } }";
        final ProjectFiles rawData = new ProjectFiles();
        rawData.insertFile(new ProjectFile("/file1.java", code));
        model = new ClarpseProject(rawData, Lang.JAVA).result().model();
    }

    @Test
    public void constructorAssignmentPreservesDeclaredField() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.FIELD,
                model.copyOfComponent("Service.owner").orElseThrow().componentType());
    }

    @Test
    public void constructorLocalVariableIsNotModeledAsField() {
        Assert.assertFalse(model.copyOfComponent("Service.temporary").isPresent());
    }

    @Test
    public void constructorParameterIsStillModeled() {
        Assert.assertEquals(OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT,
                model.copyOfComponent("Service.Service(User).owner").orElseThrow().componentType());
    }
}
