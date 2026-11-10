package com.hadi.test.python;

import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

import static com.hadi.test.python.PythonInlineProject.lines;

/**
 * The bound or constraints of a type variable are dependencies of the declaration that makes it, and a
 * type variable used in an annotation names no type of its own.
 */
public class PythonTypeVariableReferenceTest {

    private static PythonInlineProject project() throws Exception {
        return PythonInlineProject.compile(
                "/pkg/tv.py", lines(
                        "from typing import TypeVar",
                        "from pkg.models import Cfg, Bar, Baz, Qux",
                        "T = TypeVar('T', bound=Cfg)",
                        "U = TypeVar('U', Bar, Baz)",
                        "S = TypeVar('S', bound='Qux')",
                        "N = TypeVar('N')",
                        "def f(x: T, y: U | None, z: S) -> T:",
                        "    return x",
                        "class Box:",
                        "    held: T",
                        "    def get(self, item: N) -> N:",
                        "        return item",
                        ""),
                "/pkg/pep695.py", lines(
                        "from pkg.models import Cfg, Qux, Baz",
                        "def g[T: Cfg](x: T) -> T:",
                        "    return x",
                        "class K[V: Qux]:",
                        "    def m(self, v: V) -> V:",
                        "        return v",
                        "def h[A: (Cfg, Baz)](x: A):",
                        "    pass",
                        ""),
                "/pkg/shadow.py", lines(
                        "from typing import TypeVar",
                        "from pkg.models import Cfg",
                        "T = TypeVar('T')",
                        "# T = TypeVar('T', bound=Cfg)",
                        "NOTE = \"TypeVar('X', bound=Cfg)\"",
                        ""));
    }

    @Test
    public void aBoundIsADependencyOfTheModuleField() throws Exception {
        Assert.assertEquals(Set.of("pkg.models.Cfg"), project().references("pkg.tv.T"));
    }

    @Test
    public void constraintsAreDependenciesOfTheModuleField() throws Exception {
        Assert.assertEquals(Set.of("pkg.models.Bar", "pkg.models.Baz"), project().references("pkg.tv.U"));
    }

    @Test
    public void aBoundWrittenAsAStringIsADependency() throws Exception {
        Assert.assertEquals(Set.of("pkg.models.Qux"), project().references("pkg.tv.S"));
    }

    @Test
    public void anUnboundedTypeVariableDependsOnNothing() throws Exception {
        Assert.assertEquals(Set.of(), project().references("pkg.tv.N"));
    }

    @Test
    public void aParameterOrReturnAnnotatedWithATypeVariableRecordsNoPhantomType() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertEquals(Set.of(), project.references("pkg.tv.f"));
        Assert.assertEquals(Set.of(), project.parameterReferences("pkg.tv.f", "x"));
        Assert.assertEquals(Set.of(), project.parameterReferences("pkg.tv.f", "z"));
        Assert.assertEquals(Set.of(), project.references("pkg.tv.Box.held"));
        Assert.assertEquals(Set.of(), project.references("pkg.tv.Box.get"));
    }

    @Test
    public void aTypeParameterBoundOnAFunctionIsADependency() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertEquals(Set.of("pkg.models.Cfg"), project.references("pkg.pep695.g"));
        Assert.assertEquals(Set.of("pkg.models.Cfg", "pkg.models.Baz"), project.references("pkg.pep695.h"));
    }

    @Test
    public void aTypeParameterBoundOnAClassIsADependency() throws Exception {
        Assert.assertEquals(Set.of("pkg.models.Qux"), project().references("pkg.pep695.K"));
    }

    @Test
    public void aTypeParameterOfAClassIsNotATypeInItsMethods() throws Exception {
        Assert.assertEquals(Set.of(), project().references("pkg.pep695.K.m"));
    }

    @Test
    public void aCommentOrStringNamingATypeVariableDeclarationIsNotOne() throws Exception {
        Assert.assertEquals(Set.of(), project().references("pkg.shadow.T"));
        Assert.assertEquals(Set.of(), project().references("pkg.shadow.NOTE"));
    }
}
