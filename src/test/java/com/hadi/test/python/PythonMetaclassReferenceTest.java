package com.hadi.test.python;

import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Assert;
import org.junit.Test;

import static com.hadi.test.python.PythonInlineProject.lines;

/** The metaclass a class names is a dependency of the class, as a plain reference and not a base. */
public class PythonMetaclassReferenceTest {

    private static PythonInlineProject project() throws Exception {
        return PythonInlineProject.compile("/pkg/meta.py", lines(
                "from pkg.models import Base, Bar, Baz",
                "class WithMeta(Base, metaclass=Bar):",
                "    pass",
                "class OtherKeyword(Base, flag=Baz):",
                "    pass",
                "class Mentioned(Base):",
                "    '''metaclass=Bar'''",
                "    # metaclass=Baz",
                ""));
    }

    @Test
    public void theMetaclassIsADependency() throws Exception {
        Assert.assertTrue(project().references("pkg.meta.WithMeta").contains("pkg.models.Bar"));
    }

    @Test
    public void theMetaclassIsNotABase() throws Exception {
        final Component component = project().component("pkg.meta.WithMeta");
        Assert.assertTrue(component.references().stream()
                .filter(ref -> ref instanceof TypeExtensionReference)
                .allMatch(ref -> ref.invokedComponent().equals("pkg.models.Base")));
    }

    @Test
    public void otherKeywordValuesAreNotDependencies() throws Exception {
        Assert.assertFalse(project().references("pkg.meta.OtherKeyword").contains("pkg.models.Baz"));
    }

    @Test
    public void aStringOrCommentNamingAMetaclassIsNotOne() throws Exception {
        final java.util.Set<String> references = project().references("pkg.meta.Mentioned");
        Assert.assertFalse(references.contains("pkg.models.Bar"));
        Assert.assertFalse(references.contains("pkg.models.Baz"));
    }
}
