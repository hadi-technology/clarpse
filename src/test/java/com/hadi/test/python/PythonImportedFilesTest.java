package com.hadi.test.python;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@code Component.importedFiles()} for Python: the repository files a file's imports resolve to,
 * as project file paths (the form of {@code ProjectFile.path()} and {@code Component.sourceFile()}).
 */
public class PythonImportedFilesTest {

    private static final String USER = "/app/main.py";

    /** A small library every test may import from: {@code a}, {@code a.b} and {@code a.b.c}. */
    private static final String[] LIBRARY = {
        "/a/__init__.py", "",
        "/a/b/__init__.py", "def f():\n    return 1\n",
        "/a/b/c.py", "class Thing:\n    pass\n",
        "/a/b/d.py", "def helper():\n    return 2\n",
    };

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static ProjectFiles project(final String[] library, final String... pathsAndSources) {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/app/__init__.py", ""));
        for (int i = 0; i < library.length; i += 2) {
            files.insertFile(new ProjectFile(library[i], library[i + 1]));
        }
        for (int i = 0; i < pathsAndSources.length; i += 2) {
            files.insertFile(new ProjectFile(pathsAndSources[i], pathsAndSources[i + 1]));
        }
        return files;
    }

    private static OOPSourceCodeModel compile(final ProjectFiles files) throws Exception {
        final CompileResult result = new ClarpseProject(files, Lang.PYTHON).result();
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        return result.model();
    }

    private static String moduleOf(final String path) {
        return path.substring(1, path.length() - ".py".length()).replace('/', '.');
    }

    /** The class {@code Use} that every test source declares after its import lines. */
    private static Component use(final OOPSourceCodeModel model, final String path) {
        return model.component(moduleOf(path) + ".Use").orElseThrow(() -> new AssertionError("no Use in " + path));
    }

    private static String source(final String... importLines) {
        return String.join("\n", importLines) + "\n\n\nclass Use:\n    pass\n";
    }

    /** What the class {@code Use} of the importing file reports, over the library and the given files. */
    private static List<String> importedBy(final String importer, final String source, final String... others)
            throws Exception {
        final String[] sources = new String[others.length + 2];
        sources[0] = importer;
        sources[1] = source;
        System.arraycopy(others, 0, sources, 2, others.length);
        return List.copyOf(use(compile(project(LIBRARY, sources)), importer).importedFiles());
    }

    private static List<String> importedBy(final String source) throws Exception {
        return importedBy(USER, source);
    }

    @Test
    public void importOfADottedModuleResolvesToItsFileAndNotToItsParents() throws Exception {
        assertEquals(List.of("/a/b/c.py"), importedBy(source("import a.b.c")));
    }

    @Test
    public void importOfAPackageResolvesToItsInitFile() throws Exception {
        assertEquals(List.of("/a/b/__init__.py"), importedBy(source("import a.b")));
    }

    @Test
    public void importOfATopLevelModuleFileResolvesToIt() throws Exception {
        assertEquals(List.of("/solo.py"), importedBy(USER, source("import solo"), "/solo.py", "X = 1\n"));
    }

    @Test
    public void fromImportOfASubmoduleResolvesToTheSubmoduleFile() throws Exception {
        assertEquals(List.of("/a/b/c.py"), importedBy(source("from a.b import c")));
    }

    @Test
    public void fromImportOfAFunctionDefinedInThePackageInitResolvesToTheInit() throws Exception {
        assertEquals(List.of("/a/b/__init__.py"), importedBy(source("from a.b import f")));
    }

    @Test
    public void fromImportOfANameDefinedInAModuleFileResolvesToThatFile() throws Exception {
        assertEquals(List.of("/a/b/d.py"), importedBy(source("from a.b.d import helper")));
    }

    @Test
    public void fromImportOfASubmoduleAndANameIsOneFileEachInTheOrderWritten() throws Exception {
        assertEquals(List.of("/a/b/c.py", "/a/b/__init__.py", "/a/b/d.py"),
                importedBy(source("from a.b import c, f, d")));
        assertEquals(List.of("/a/b/__init__.py", "/a/b/d.py"),
                importedBy(source("from a.b import (f,", "    d)")));
    }

    @Test
    public void relativeImportsAtDepthOneResolveAgainstTheImportingFilesPackage() throws Exception {
        assertEquals(List.of("/app/sibling.py", "/app/mod.py", "/app/__init__.py"),
                importedBy(USER, source("from . import sibling", "from .mod import y", "from . import setting"),
                        "/app/sibling.py", "class S:\n    pass\n",
                        "/app/mod.py", "y = 1\n"));
    }

    @Test
    public void relativeImportsAtDepthTwoResolveAgainstTheParentPackage() throws Exception {
        final String importer = "/app/sub/deep.py";
        assertEquals(List.of("/app/top.py", "/app/pkg/mod.py", "/app/pkg/__init__.py"),
                importedBy(importer, source("from .. import top", "from ..pkg.mod import z", "from ..pkg import w"),
                        "/app/sub/__init__.py", "",
                        "/app/top.py", "class T:\n    pass\n",
                        "/app/pkg/__init__.py", "w = 1\n",
                        "/app/pkg/mod.py", "z = 1\n"));
    }

    @Test
    public void aRelativeImportFromBeyondTheRepositoryAddsNothing() throws Exception {
        assertEquals(List.of(), importedBy(source("from ... import nothing")));
    }

    @Test
    public void aliasedImportsResolveLikeTheUnaliasedOnes() throws Exception {
        assertEquals(List.of("/a/b/c.py"), importedBy(source("import a.b.c as m")));
        assertEquals(List.of("/a/b/__init__.py"), importedBy(source("import a.b as ab")));
        assertEquals(List.of("/a/b/__init__.py"), importedBy(source("from a import b as bb")));
        assertEquals(List.of("/a/b/c.py", "/a/b/__init__.py"),
                importedBy(source("from a.b import c as cc, f as ff")));
    }

    @Test
    public void importOfTheSameFileTwiceIsListedOnceAtItsFirstPlace() throws Exception {
        assertEquals(List.of("/a/b/d.py", "/a/b/c.py"),
                importedBy(source("import a.b.d", "import a.b.c", "from a.b import d", "from a.b.c import Thing")));
    }

    @Test
    public void importlibImportModuleWithALiteralNameIsAnImport() throws Exception {
        assertEquals(List.of("/a/b/c.py"), importedBy(source(
                "import importlib", "x = importlib.import_module('a.b.c')")));
    }

    @Test
    public void importModuleImportedByNameWithALiteralNameIsAnImport() throws Exception {
        assertEquals(List.of("/a/b/d.py"), importedBy(source(
                "from importlib import import_module", "x = import_module(\"a.b.d\")")));
    }

    @Test
    public void theBuiltinImportWithALiteralNameIsAnImport() throws Exception {
        assertEquals(List.of("/a/b/__init__.py"), importedBy(source("m = __import__('a.b')")));
    }

    @Test
    public void aRelativeLiteralNameWithAPlainPackageIsAnImport() throws Exception {
        assertEquals(List.of("/app/mod.py"), importedBy(USER, source(
                "import importlib", "m = importlib.import_module('.mod', package=__package__)"),
                "/app/mod.py", "y = 1\n"));
    }

    @Test
    public void aDynamicImportThatIsNotALiteralOrDoesNotResolveAddsNothing() throws Exception {
        assertEquals(List.of(), importedBy(source(
                "import importlib",
                "name = 'a.b.c'",
                "x = importlib.import_module(name)",
                "y = importlib.import_module('a.' + 'b')",
                "z = importlib.import_module(f'a.{name}')",
                "w = importlib.import_module('a.b.missing')",
                "v = __import__(name)")));
    }

    @Test
    public void dynamicAndStaticImportsAreOrderedByWhereTheyAreWritten() throws Exception {
        assertEquals(List.of("/a/b/d.py", "/a/b/c.py", "/a/b/__init__.py"), importedBy(source(
                "import importlib",
                "import a.b.d",
                "def load():",
                "    return importlib.import_module('a.b.c')",
                "import a.b")));
    }

    @Test
    public void anImportInsideAFunctionOrAClassCounts() throws Exception {
        assertEquals(List.of("/a/b/c.py", "/a/b/d.py"), importedBy(
                "def load():\n    import a.b.c\n    return a.b.c.Thing\n\n\n"
                        + "class Use:\n    from a.b.d import helper\n"));
    }

    @Test
    public void anImportUnderTypeCheckingCounts() throws Exception {
        assertEquals(List.of("/a/b/c.py"), importedBy(source(
                "from typing import TYPE_CHECKING", "if TYPE_CHECKING:", "    from a.b.c import Thing")));
    }

    @Test
    public void aStarImportResolvesToTheModuleFile() throws Exception {
        assertEquals(List.of("/a/b/d.py"), importedBy(source("from a.b.d import *")));
        assertEquals(List.of("/a/b/__init__.py"), importedBy(source("from a.b import *")));
        assertEquals(List.of("/app/__init__.py"), importedBy("/app/mod.py", source("from . import *")));
    }

    @Test
    public void standardLibraryAndUninstalledPackagesAddNothingWhileImportsStillListsThem() throws Exception {
        final String[] lines = {"import os", "import requests", "from typing import List"};
        final Component use = use(compile(project(LIBRARY, USER, source(lines))), USER);

        assertTrue(use.importedFiles().isEmpty());
        assertTrue(use.imports().toString(), use.imports().containsAll(Set.of("os", "requests", "typing.List")));
    }

    @Test
    public void aPackageUnderADependencyDirectoryIsNotRecorded() throws Exception {
        // The file under the dependency directory is itself reported as excluded, so the failures
        // are not asserted on.
        final ProjectFiles files = project(LIBRARY, USER, source("import venv.lib", "import a.b.c"),
                "/venv/lib.py", "X = 1\n");
        final OOPSourceCodeModel model = new ClarpseProject(files, Lang.PYTHON).result().model();

        assertEquals(List.of("/a/b/c.py"), List.copyOf(use(model, USER).importedFiles()));
        assertTrue(use(model, USER).imports().contains("venv.lib"));
    }

    @Test
    public void aNamespacePackageDirectoryHasNoFileToRecord() throws Exception {
        assertEquals(List.of("/ns/inner/leaf.py"), importedBy(USER, source("import ns", "import ns.inner.leaf"),
                "/ns/inner/leaf.py", "X = 1\n"));
    }

    @Test
    public void anUnresolvedImportAddsNothing() throws Exception {
        assertEquals(List.of(), importedBy(source("import a.b.missing", "from a.nope import thing")));
    }

    @Test
    public void aFolderNameWithADotIsNeverReadAsTwoPackages() throws Exception {
        // A folder `b.c` cannot be named by an import statement, because the dot separates package
        // names, so `a/b.c/d.py` is not importable and no import is expected to record it. What
        // matters is that it is not mistaken for `a.b.c.d` either.
        final List<String> imported = importedBy(USER, source("import a.b.c.d", "import a.b.d"),
                "/a/b.c/d.py", "X = 1\n",
                "/a/b.c/__init__.py", "");

        assertEquals(List.of("/a/b/d.py"), imported);
    }

    @Test
    public void everyEntryIsTheSourceFormOfAComponentInThatFile() throws Exception {
        final OOPSourceCodeModel model = compile(project(LIBRARY, USER, source("from a.b.c import Thing")));
        final String thingFile = model.component("a.b.c.Thing").orElseThrow().sourceFile();

        assertTrue(use(model, USER).importedFiles().contains(thingFile));
    }

    @Test
    public void everyModuleLevelComponentOfTheFileCarriesTheFilesImportedFilesAndMembersDoNot() throws Exception {
        final OOPSourceCodeModel model = compile(project(LIBRARY, USER,
                "import a.b.c\n\n\n"
                        + "class Use:\n    attr = 1\n    def run(self):\n        local = 2\n        return local\n\n\n"
                        + "class Other:\n    pass\n\n\n"
                        + "def helper():\n    return 1\n\n\n"
                        + "LIMIT = 3\n"));
        final Set<String> expected = Set.of("/a/b/c.py");

        for (final Component component : model.components().collect(Collectors.toList())) {
            if (!USER.equals(component.sourceFile())) {
                continue;
            }
            if (component.componentType().isBaseComponent()) {
                assertEquals(component.uniqueName(), expected, component.importedFiles());
            } else {
                assertEquals(component.uniqueName() + " reports what imports() does: none",
                        component.imports(), component.importedFiles());
                assertTrue(component.uniqueName(), component.importedFiles().isEmpty());
            }
        }
    }

    @Test
    public void aFileDeclaringNoTypeGivesTheSetToItsFunctionsAndVariablesAsImportsDoes() throws Exception {
        final OOPSourceCodeModel model = compile(project(LIBRARY, "/app/util.py",
                "import a.b.c\n\n\ndef helper():\n    return 1\n\n\nLIMIT = 3\n"));
        final List<Component> carrying = model.components()
                .filter(c -> "/app/util.py".equals(c.sourceFile()) && !c.importedFiles().isEmpty())
                .collect(Collectors.toList());

        assertEquals(carrying.toString(), 2, carrying.size());
        for (final Component component : carrying) {
            assertTrue(component.uniqueName(), component.componentType() == ComponentType.FUNCTION
                    || component.componentType() == ComponentType.MODULE_FIELD);
            assertEquals(Set.of("/a/b/c.py"), component.importedFiles());
            assertFalse(component.imports().isEmpty());
        }
        model.components()
                .filter(c -> "/app/util.py".equals(c.sourceFile()))
                .forEach(c -> assertEquals(c.uniqueName(), c.imports().isEmpty(), c.importedFiles().isEmpty()));
    }

    @Test
    public void importsAreExactlyAsTheyWereAndHoldTheStringsOfEveryImportForm() throws Exception {
        final Component use = use(compile(project(LIBRARY, USER, source(
                "import os",
                "import a.b.c",
                "import a.b.d as dd",
                "from a.b import c, f",
                "from . import sibling",
                "import importlib",
                "x = importlib.import_module('a.b')",
                "def load():",
                "    import requests"), "/app/sibling.py", "class S:\n    pass\n")), USER);

        assertEquals(Set.of("os", "a.b.c", "a.b.d", "a.b.f", "app.sibling", "importlib", "a.b", "requests"),
                use.imports());
        assertEquals(List.of("/a/b/c.py", "/a/b/d.py", "/a/b/__init__.py", "/app/sibling.py"),
                List.copyOf(use.importedFiles()));
    }

    @Test
    public void theSetIsUnmodifiableAndNeverNull() throws Exception {
        final Component use = use(compile(project(LIBRARY, USER, source("import a.b.c"))), USER);

        assertThrows(UnsupportedOperationException.class, () -> use.importedFiles().add("/x.py"));
        assertNotNull(new Component().importedFiles());
        assertTrue(new Component().importedFiles().isEmpty());
        assertTrue(use(compile(project(LIBRARY, USER, source("import os"))), USER).importedFiles().isEmpty());
    }

    @Test
    public void aBoundaryFileReportsWhatImportResolutionGivesForIt() throws Exception {
        final ProjectFiles files = project(LIBRARY,
                USER, "from app.mid import Mid\n\n\nclass Use:\n    mid: Mid\n",
                "/app/mid.py", "from a.b.c import Thing\n\n\nclass Mid:\n    thing: Thing\n");
        final CompileResult result = new ClarpseProject(files, Lang.PYTHON, List.of(USER),
                AnalysisOptions.oneLevel()).result();
        final Component mid = result.model().component("app.mid.Mid").orElseThrow();

        assertTrue(mid.isBoundary());
        assertEquals(Set.of("/a/b/c.py"), mid.importedFiles());
        assertEquals(Set.of("/app/mid.py"), use(result.model(), USER).importedFiles());
    }

    @Test
    public void copiesAndPoolingKeepTheField() throws Exception {
        final OOPSourceCodeModel model = compile(project(LIBRARY, USER, source("import a.b.d", "import a.b.c")));
        final List<String> expected = List.of("/a/b/d.py", "/a/b/c.py");
        final String name = moduleOf(USER) + ".Use";

        assertEquals(expected, List.copyOf(model.copyOfComponent(name).orElseThrow().importedFiles()));
        assertEquals(expected, List.copyOf(model.copy().component(name).orElseThrow().importedFiles()));
    }

    @Test
    public void aStubThatIsNotAProjectFileIsNotRecordedAndTheModuleFileIsWhenNoStubIsOnDisk() throws Exception {
        // A `.pyi` file is not a source file of any language here, so a project never holds one. A
        // project persisted from files has none on disk, and the module's own file resolves.
        final ProjectFiles files = project(LIBRARY, USER, source("import x.s"),
                "/x/s.py", "X = 1\n", "/x/s.pyi", "X: int\n");
        assertEquals(List.of("/x/s.py"), List.copyOf(use(compile(files), USER).importedFiles()));
    }

    @Test
    public void aStubOnDiskThatTheResolutionPicksNamesTheModuleFileBesideIt() throws Exception {
        final Path dir = Files.createTempDirectory("clarpse-py-imported-files");
        try {
            write(dir, "x/__init__.py", "");
            write(dir, "x/s.py", "X = 1\n");
            write(dir, "x/s.pyi", "X: int\n");
            write(dir, "x/t.py", "Y = 1\n");
            write(dir, "x/pk/__init__.py", "Z = 1\n");
            write(dir, "x/pk/__init__.pyi", "Z: int\n");
            write(dir, "app/__init__.py", "");
            write(dir, "app/main.py", source("import x.s", "import x.t", "from x import pk"));
            final OOPSourceCodeModel model = compile(new ProjectFiles(dir.toString()));
            final Component use = model.components()
                    .filter(c -> c.uniqueName().endsWith("main.Use")).findFirst().orElseThrow();

            assertEquals(List.of("x/s.py", "x/t.py", "x/pk/__init__.py"), use.importedFiles().stream()
                    .map(file -> file.substring(file.indexOf("x/"))).collect(Collectors.toList()));
        } finally {
            deleteTree(dir);
        }
    }

    @Test
    public void aStubWithNoModuleFileBesideItNamesNoFile() throws Exception {
        final Path dir = Files.createTempDirectory("clarpse-py-imported-files");
        try {
            write(dir, "x/__init__.py", "");
            write(dir, "x/only.pyi", "X: int\n");
            write(dir, "x/t.py", "Y = 1\n");
            write(dir, "app/__init__.py", "");
            write(dir, "app/main.py", source("import x.only", "import x.t"));
            final OOPSourceCodeModel model = compile(new ProjectFiles(dir.toString()));
            final Component use = model.components()
                    .filter(c -> c.uniqueName().endsWith("main.Use")).findFirst().orElseThrow();

            assertEquals(1, use.importedFiles().size());
            assertTrue(use.importedFiles().iterator().next().endsWith("x/t.py"));
        } finally {
            deleteTree(dir);
        }
    }

    private static void deleteTree(final Path dir) throws Exception {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private static void write(final Path dir, final String relative, final String content) throws Exception {
        final Path target = dir.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    @Test
    public void aJavaComponentHasNone() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/src/a/A.java", "package a; import b.B; public class A { B b; }"));
        files.insertFile(new ProjectFile("/src/b/B.java", "package b; public class B {}"));
        final Component a = new ClarpseProject(files, Lang.JAVA).result().model().component("a.A").orElseThrow();

        assertFalse(a.imports().isEmpty());
        assertTrue(a.importedFiles().isEmpty());
    }
}
