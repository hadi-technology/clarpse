package com.hadi.test.python;

import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.hadi.test.python.PythonInlineProject.lines;

/**
 * A decorator an import binds from a module outside the repository is named by that import, the way a
 * reference to the same name in a call is, whatever the import's form; any other decorator is kept as
 * written.
 */
public class PythonDecoratorOriginTest {

    /** The annotation references of the component, as {@code target resolution}. */
    private static List<String> decorators(final PythonInlineProject project, final String uniqueName) {
        return project.component(uniqueName).references().stream()
                .filter(ref -> ref instanceof AnnotationReference)
                .map(ref -> ref.invokedComponent() + " " + ref.resolutionKind())
                .sorted()
                .collect(Collectors.toList());
    }

    @Test
    public void aNameImportedFromAModule() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/a.py", lines(
                "from functools import wraps", "from notinstalled import deco",
                "@wraps(print)", "def w1(): pass",
                "@deco", "def w2(): pass"));
        Assert.assertEquals(List.of("functools.wraps EXACT"), decorators(project, "a.w1"));
        Assert.assertEquals(List.of("notinstalled.deco UNRESOLVED"), decorators(project, "a.w2"));
    }

    @Test
    public void aNameReachedThroughAnImportedModule() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/a.py", lines(
                "import click", "import functools", "import notinstalled",
                "@click.command()", "def cmd(): pass",
                "@functools.lru_cache", "def w2(): pass",
                "@notinstalled.deco", "def w3(): pass"));
        Assert.assertEquals(List.of("click.command UNRESOLVED"), decorators(project, "a.cmd"));
        Assert.assertEquals(List.of("functools.lru_cache EXACT"), decorators(project, "a.w2"));
        Assert.assertEquals(List.of("notinstalled.deco UNRESOLVED"), decorators(project, "a.w3"));
    }

    @Test
    public void aNameReachedThroughAModuleAlias() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/a.py", lines(
                "import functools as ft", "import notinstalled as x",
                "@ft.lru_cache", "def w2(): pass",
                "@x.deco", "def w3(): pass"));
        Assert.assertEquals(List.of("functools.lru_cache EXACT"), decorators(project, "a.w2"));
        Assert.assertEquals(List.of("notinstalled.deco UNRESOLVED"), decorators(project, "a.w3"));
    }

    @Test
    public void anImportedNameUnderAnotherLocalNameIsNamedByTheImportedName() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/a.py", lines(
                "from notinstalled import deco as d", "@d", "def w(): pass"));
        Assert.assertEquals(List.of("notinstalled.deco UNRESOLVED"), decorators(project, "a.w"));
    }

    @Test
    public void methodsAndClassesAreNamedToo() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/a.py", lines(
                "from dataclasses import dataclass", "import notinstalled",
                "@dataclass", "class K:", "    @notinstalled.deco", "    def m(self): pass"));
        Assert.assertEquals(List.of("dataclasses.dataclass EXACT"), decorators(project, "a.K"));
        Assert.assertEquals(List.of("notinstalled.deco UNRESOLVED"), decorators(project, "a.K.m"));
    }

    @Test
    public void aDecoratorDefinedInTheSameFileKeepsItsWrittenName() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/a.py", lines(
                "def mine(f): return f", "@mine", "def w(): pass"));
        Assert.assertEquals(List.of("mine UNRESOLVED"), decorators(project, "a.w"));
    }

    @Test
    public void aDecoratorOfAnObjectKeepsItsLastName() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/a.py", lines(
                "from notinstalled import Blueprint", "bp = Blueprint('x')", "@bp.route('/')",
                "def index(): pass"));
        Assert.assertEquals(List.of("route UNRESOLVED"), decorators(project, "a.index"));
    }

    @Test
    public void twoReferencesToTheSameDecoratorAreEqual() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/a.py", lines(
                "from notinstalled import deco", "@deco", "def a(): pass", "@deco", "def b(): pass"));
        final ComponentReference first = project.component("a.a").references().iterator().next();
        final ComponentReference second = project.component("a.b").references().iterator().next();
        Assert.assertEquals(first, second);
    }

    @Test
    public void argumentsAreUnchanged() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/a.py", lines(
                "from notinstalled import deco", "@deco('x', retries=3)", "def a(): pass"));
        final AnnotationReference reference = (AnnotationReference) project.component("a.a").references().stream()
                .filter(ref -> ref instanceof AnnotationReference).findFirst().orElseThrow();
        Assert.assertEquals(2, reference.arguments().size());
        Assert.assertEquals("x", reference.arguments().values().iterator().next().value());
    }
}
