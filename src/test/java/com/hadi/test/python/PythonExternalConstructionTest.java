package com.hadi.test.python;

import org.junit.Assert;
import org.junit.Test;

import static com.hadi.test.python.PythonInlineProject.lines;

/**
 * Constructing a class that an import binds to a package outside the repo is the dependency an
 * annotation naming it already records, under the same label. A name no import binds is never guessed at.
 */
public class PythonExternalConstructionTest {

    private static PythonInlineProject project() throws Exception {
        return PythonInlineProject.compile("/pkg/ext.py", lines(
                "from requests import Session",
                "from os import getcwd",
                "from pkg.models import Foo",
                "def annotated(s: Session):",
                "    return s",
                "def constructs():",
                "    s = Session()",
                "    return s",
                "def callsAFunction():",
                "    return getcwd()",
                "def unimported():",
                "    return Mystery()",
                "def shadowed(Session):",
                "    return Session()",
                "def rebound():",
                "    Session = make()",
                "    return Session()",
                "def repoClass():",
                "    return Foo()",
                "def mentions():",
                "    text = 'Session()'  # Session()",
                "    return text",
                ""));
    }

    @Test
    public void constructingAnImportedExternalClassRecordsTheLabelAnAnnotationRecords() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertTrue(project.references("pkg.ext.annotated").contains("requests.Session[ext]"));
        Assert.assertTrue(project.references("pkg.ext.constructs").contains("requests.Session[ext]"));
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
        Assert.assertTrue(project().references("pkg.ext.mentions").isEmpty());
    }
}
