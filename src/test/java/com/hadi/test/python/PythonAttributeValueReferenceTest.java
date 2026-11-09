package com.hadi.test.python;

import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

import static com.hadi.test.python.PythonInlineProject.lines;

/**
 * What the value of a class attribute or a module-level name refers to is a dependency of that
 * attribute, found the way the same expression is found in a function body: a repo class called,
 * listed or read from, including inside a lambda.
 */
public class PythonAttributeValueReferenceTest {

    private static PythonInlineProject project() throws Exception {
        return PythonInlineProject.compile("/pkg/refs.py", lines(
                "from pkg.models import Foo, Bar, Baz, Qux, Corge, Grault, Cfg, Base",
                "objects = Corge()",
                "handlers = [Grault]",
                "limit = Foo.LIMIT",
                "factory = lambda self: Cfg()",
                "plain = 1",
                "same: Bar = Bar()",
                "class Holder:",
                "    a = Corge()",
                "    b = [Grault]",
                "    c = lambda self: Cfg()",
                "    d = Baz.LIMIT",
                "    e: Qux = Qux()",
                "    f = 'Corge()'  # Grault()",
                "    Base = None",
                "    g = Base",
                ""));
    }

    @Test
    public void aCallAtClassLevelIsADependencyOfTheAttribute() throws Exception {
        Assert.assertEquals(Set.of("pkg.models.Corge"), project().references("pkg.refs.Holder.a"));
    }

    @Test
    public void aClassInAListAtClassLevelIsADependencyOfTheAttribute() throws Exception {
        Assert.assertEquals(Set.of("pkg.models.Grault"), project().references("pkg.refs.Holder.b"));
    }

    @Test
    public void aCallInsideALambdaAtClassLevelIsADependencyOfTheAttribute() throws Exception {
        Assert.assertEquals(Set.of("pkg.models.Cfg"), project().references("pkg.refs.Holder.c"));
    }

    @Test
    public void aMemberReadFromAClassAtClassLevelIsADependencyOnTheClass() throws Exception {
        Assert.assertEquals(Set.of("pkg.models.Baz"), project().references("pkg.refs.Holder.d"));
    }

    @Test
    public void theSameFormsAtModuleLevelAreDependenciesOfTheModuleField() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertEquals(Set.of("pkg.models.Corge"), project.references("pkg.refs.objects"));
        Assert.assertEquals(Set.of("pkg.models.Grault"), project.references("pkg.refs.handlers"));
        Assert.assertEquals(Set.of("pkg.models.Foo"), project.references("pkg.refs.limit"));
        Assert.assertEquals(Set.of("pkg.models.Cfg"), project.references("pkg.refs.factory"));
    }

    @Test
    public void aClassNamedByBothTheAnnotationAndTheValueIsRecordedOnce() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertEquals(Set.of("pkg.models.Qux"), project.references("pkg.refs.Holder.e"));
        Assert.assertEquals(1, project.component("pkg.refs.Holder.e").references().stream()
                .filter(ref -> ref.invokedComponent().equals("pkg.models.Qux")).count());
        Assert.assertEquals(1, project.component("pkg.refs.same").references().stream()
                .filter(ref -> ref.invokedComponent().equals("pkg.models.Bar")).count());
    }

    @Test
    public void aClassCalledAndReadFromInOneValueIsRecordedOnce() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/twice.py", lines(
                "from pkg.models import Foo",
                "both = [Foo(), Foo.LIMIT, Foo]",
                ""));
        Assert.assertEquals(1, project.component("pkg.twice.both").references().stream()
                .filter(ref -> ref.invokedComponent().equals("pkg.models.Foo")).count());
    }

    @Test
    public void aLiteralValueOrAStringOrCommentNamingAClassIsNotADependency() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertEquals(Set.of(), project.references("pkg.refs.plain"));
        Assert.assertEquals(Set.of(), project.references("pkg.refs.Holder.f"));
    }

    @Test
    public void aNameTheClassBodyBindsItselfIsNotTheImportedClass() throws Exception {
        Assert.assertEquals(Set.of(), project().references("pkg.refs.Holder.g"));
    }

    @Test
    public void aLambdaParameterNamedLikeAClassIsNotTheClass() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/shadow.py", lines(
                "from pkg.models import Cfg",
                "pick = lambda Cfg: Cfg",
                ""));
        Assert.assertEquals(Set.of(), project.references("pkg.shadow.pick"));
    }
}
