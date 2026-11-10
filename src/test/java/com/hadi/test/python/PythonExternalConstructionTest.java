package com.hadi.test.python;

import org.junit.Assert;
import org.junit.Test;

import static com.hadi.test.python.PythonInlineProject.lines;

/**
 * Constructing a class that an import binds to a package outside the repo is the dependency an
 * annotation naming it already records, under the same label. Only what Pyright resolves to a class
 * counts as a construction: a capitalised function constructs nothing, and a name no import binds is
 * never guessed at. An imported name Pyright cannot resolve at all, as for a package that is not
 * installed, is not known to be a class and is referenced as the plain external name, unresolved.
 */
public class PythonExternalConstructionTest {

    private static PythonInlineProject project() throws Exception {
        return PythonInlineProject.compile("/pkg/ext.py", lines(
                "from collections import OrderedDict, deque",
                "from ctypes import POINTER",
                "from nowhere import Widget",
                "from requests import Session",
                "import requests",
                "import os",
                "from os import getcwd",
                "from pkg.models import Foo",
                "def annotated(s: OrderedDict):",
                "    return s",
                "def constructs():",
                "    s = OrderedDict()",
                "    return s",
                "def callsAFunction():",
                "    return getcwd()",
                "def unimported():",
                "    return Mystery()",
                "def shadowed(OrderedDict):",
                "    return OrderedDict()",
                "def rebound():",
                "    OrderedDict = make()",
                "    return OrderedDict()",
                "def repoClass():",
                "    return Foo()",
                "def capitalisedFunction():",
                "    return POINTER(int)",
                "def unresolvable():",
                "    return Widget()",
                "def uninstalledClass():",
                "    return Session()",
                "def throughUninstalledModule():",
                "    return requests.Session()",
                "def throughResolvedModule():",
                "    return os.getcwd()",
                "def shadowedModule(requests):",
                "    return requests.Session()",
                "def lowercaseClass():",
                "    return deque()",
                "def mentions():",
                "    text = 'OrderedDict()'  # OrderedDict()",
                "    return text",
                ""));
    }

    @Test
    public void constructingAnImportedExternalClassRecordsTheLabelAnAnnotationRecords() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertTrue(project.references("pkg.ext.annotated").contains("collections.OrderedDict[ext]"));
        Assert.assertTrue(project.references("pkg.ext.constructs").contains("collections.OrderedDict[ext]"));
    }

    @Test
    public void aCapitalisedImportedFunctionIsNotAConstruction() throws Exception {
        Assert.assertTrue(project().references("pkg.ext.capitalisedFunction").isEmpty());
    }

    @Test
    public void aNamePyrightCannotResolveReferencesTheImportedNameUnresolved() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertEquals(java.util.Collections.singleton("nowhere.Widget[ext]"),
                project.references("pkg.ext.unresolvable"));
        Assert.assertEquals(java.util.Collections.singleton("requests.Session[ext]"),
                project.references("pkg.ext.uninstalledClass"));
        assertUnresolved(project, "pkg.ext.uninstalledClass", "requests.Session");
    }

    @Test
    public void aCallThroughAnImportedModulePyrightCannotResolveReferencesTheImportedName() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertEquals(java.util.Collections.singleton("requests.Session[ext]"),
                project.references("pkg.ext.throughUninstalledModule"));
        assertUnresolved(project, "pkg.ext.throughUninstalledModule", "requests.Session");
    }

    @Test
    public void aCallThroughAModulePyrightResolvesToAFunctionReferencesNothing() throws Exception {
        Assert.assertTrue(project().references("pkg.ext.throughResolvedModule").isEmpty());
    }

    @Test
    public void aModuleNameTheFunctionBindsItselfIsNotTheImport() throws Exception {
        Assert.assertTrue(project().references("pkg.ext.shadowedModule").isEmpty());
    }

    private static void assertUnresolved(final PythonInlineProject project, final String function, final String name) {
        final com.hadi.clarpse.reference.ComponentReference reference = project.component(function).references()
                .stream().filter(ref -> ref.invokedComponent().equals(name)).findFirst().get();
        Assert.assertEquals(com.hadi.clarpse.reference.ResolutionKind.UNRESOLVED, reference.resolutionKind());
        Assert.assertTrue(reference.getClass().toString(),
                reference.getClass() == com.hadi.clarpse.reference.SimpleTypeReference.class);
    }

    @Test
    public void aLowercaseImportedClassPyrightResolvesIsAConstruction() throws Exception {
        Assert.assertTrue(project().references("pkg.ext.lowercaseClass").contains("collections.deque[ext]"));
    }

    @Test
    public void callingAnImportedLowercaseFunctionIsNotAConstruction() throws Exception {
        Assert.assertTrue(project().references("pkg.ext.callsAFunction").isEmpty());
    }

    @Test
    public void aNameNoImportBindsIsNotGuessedAt() throws Exception {
        Assert.assertTrue(project().references("pkg.ext.unimported").isEmpty());
    }

    @Test
    public void aNameTheFunctionBindsItselfIsNotTheImport() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertTrue(project.references("pkg.ext.shadowed").isEmpty());
        Assert.assertTrue(project.references("pkg.ext.rebound").isEmpty());
    }

    @Test
    public void aRepoClassStaysAnInternalDependency() throws Exception {
        final java.util.Set<String> references = project().references("pkg.ext.repoClass");
        Assert.assertTrue(references.contains("pkg.models.Foo"));
        Assert.assertEquals(1, references.size());
    }

    @Test
    public void aStringOrCommentNamingTheCallIsNotAConstruction() throws Exception {
        // The local `text` holds a str, which is all the function depends on.
        Assert.assertFalse(project().references("pkg.ext.mentions").contains("collections.OrderedDict[ext]"));
    }
}
