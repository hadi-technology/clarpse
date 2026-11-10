package com.hadi.test.python;

import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.Assert;
import org.junit.Test;

import static com.hadi.test.python.PythonInlineProject.lines;

/**
 * A Python type reference reports how its target was arrived at: {@code EXACT} for a repository
 * class or a name an import binds, {@code UNRESOLVED} for a name that is only written.
 */
public class PythonResolutionKindTest {

    private static PythonInlineProject project() throws Exception {
        return PythonInlineProject.compile("/pkg/kinds.py", lines(
                "from collections import OrderedDict",
                "from pkg.models import Foo",
                "def f(repo: Foo, imported: OrderedDict, unknown: Mystery):",
                "    return repo",
                ""));
    }

    private static ResolutionKind kindOf(final PythonInlineProject project, final String parameter) {
        final ComponentReference[] only = project.model().components()
                .filter(cmp -> cmp.componentType() == ComponentType.METHOD_PARAMETER_COMPONENT
                        && cmp.uniqueName().startsWith("pkg.kinds.f(") && parameter.equals(cmp.name()))
                .flatMap(cmp -> cmp.references().stream()).toArray(ComponentReference[]::new);
        Assert.assertEquals(parameter + " references " + java.util.Arrays.toString(only), 1, only.length);
        return only[0].resolutionKind();
    }

    @Test
    public void aRepositoryClassIsExact() throws Exception {
        Assert.assertEquals(ResolutionKind.EXACT, kindOf(project(), "repo"));
    }

    @Test
    public void aNameAnImportBindsIsExact() throws Exception {
        Assert.assertEquals(ResolutionKind.EXACT, kindOf(project(), "imported"));
    }

    @Test
    public void aNameNothingBindsIsKeptAsWrittenAndUnresolved() throws Exception {
        Assert.assertEquals(ResolutionKind.UNRESOLVED, kindOf(project(), "unknown"));
    }
}
