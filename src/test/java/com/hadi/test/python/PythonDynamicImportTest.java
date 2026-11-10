package com.hadi.test.python;

import org.junit.Assert;
import org.junit.Test;

import static com.hadi.test.python.PythonInlineProject.lines;

/**
 * A module loaded by name from a string literal -- {@code importlib.import_module("pkg.mod")},
 * {@code import_module("pkg.mod")} or {@code __import__("pkg.mod")} -- is imported, and is listed among
 * the file's imports beside the ones statements name.
 */
public class PythonDynamicImportTest {

    private static boolean imports(final PythonInlineProject project, final String function, final String module) {
        return project.component(function).imports().contains(module);
    }

    @Test
    public void importModuleThroughImportlibIsAnImport() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/dyn.py", lines(
                "import importlib",
                "def load():",
                "    return importlib.import_module('pkg.models')",
                ""));
        Assert.assertTrue(imports(project, "pkg.dyn.load", "pkg.models"));
    }

    @Test
    public void importModuleImportedByNameIsAnImport() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/dyn.py", lines(
                "from importlib import import_module",
                "def load():",
                "    return import_module(\"pkg.models\")",
                ""));
        Assert.assertTrue(imports(project, "pkg.dyn.load", "pkg.models"));
    }

    @Test
    public void importModuleImportedInsideTheFunctionIsAnImport() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/dyn.py", lines(
                "def load():",
                "    from importlib import import_module as im",
                "    return im('pkg.models')",
                ""));
        Assert.assertTrue(imports(project, "pkg.dyn.load", "pkg.models"));
    }

    @Test
    public void theBuiltinImportIsAnImport() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/dyn.py", lines(
                "def load():",
                "    return __import__('pkg.models')",
                ""));
        Assert.assertTrue(imports(project, "pkg.dyn.load", "pkg.models"));
    }

    @Test
    public void aModuleOutsideTheRepoIsListedLikeAStaticImportOfIt() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/dyn.py", lines(
                "import importlib",
                "def load():",
                "    return importlib.import_module('yaml')",
                ""));
        Assert.assertTrue(imports(project, "pkg.dyn.load", "yaml"));
    }

    @Test
    public void aRelativeNameWithAPlainPackageIsResolved() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/dyn.py", lines(
                "import importlib",
                "def load():",
                "    return importlib.import_module('.models', package=__package__)",
                ""));
        Assert.assertTrue(imports(project, "pkg.dyn.load", "pkg.models"));
    }

    @Test
    public void aRelativeNameWithoutAPlainPackageIsLeftOut() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/dyn.py", lines(
                "import importlib",
                "def load(where):",
                "    a = importlib.import_module('.models')",
                "    return importlib.import_module('.models', package=where)",
                ""));
        Assert.assertFalse(project.component("pkg.dyn.load").imports().contains("pkg.models"));
        Assert.assertFalse(project.component("pkg.dyn.load").imports().contains(".models"));
    }

    @Test
    public void anFStringAVariableOrAConcatenationIsNotAnImport() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/dyn.py", lines(
                "import importlib",
                "def load(name):",
                "    a = importlib.import_module(f'pkg.{name}')",
                "    b = importlib.import_module(name)",
                "    c = importlib.import_module('pkg.' + name)",
                "    return __import__(name)",
                ""));
        Assert.assertTrue(project.component("pkg.dyn.load").imports().stream()
                .noneMatch(imported -> imported.startsWith("pkg.") || imported.equals("name")));
    }

    @Test
    public void aStringOrCommentContainingTheCallIsNotAnImport() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/dyn.py", lines(
                "import importlib",
                "def load():",
                "    text = \"importlib.import_module('pkg.models')\"  # __import__('pkg.models')",
                "    return text",
                ""));
        Assert.assertFalse(imports(project, "pkg.dyn.load", "pkg.models"));
    }

    @Test
    public void anImportModuleTheFileNeverImportedIsNotAnImport() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/dyn.py", lines(
                "import importlib",
                "def import_module(name):",
                "    return name",
                "def load():",
                "    return import_module('pkg.models')",
                ""));
        Assert.assertFalse(imports(project, "pkg.dyn.load", "pkg.models"));
    }
}
