package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/** Keyword arguments in a class's base list configure the class; they are not bases. */
public class PythonClassKeywordArgumentTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture("class-keyword-arguments");
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    private static List<String> bases(final String cls) {
        return model.copyOfComponent(PythonTestUtil.uniqueName("src", "sample", cls)).orElseThrow()
                .references(TypeReferences.EXTENSION).stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toList());
    }

    @Test
    public void positionalBasesAreKept() {
        Assert.assertEquals(List.of("src.base.Base", "src.base.Mixin"), bases("Plain"));
    }

    @Test
    public void metaclassIsNotABase() {
        Assert.assertEquals(List.of("src.base.Base"), bases("WithMetaclass"));
    }

    @Test
    public void otherKeywordValuesAreNotBases() {
        Assert.assertEquals(List.of("src.base.Mixin"), bases("WithOtherKeyword"));
    }
}
