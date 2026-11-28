package com.hadi.test.python;

import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.reference.SimpleTypeReference;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.hadi.test.python.PythonInlineProject.lines;

/**
 * A call through the attribute chain of an imported module that is not in the project, and that
 * Pyright cannot resolve, references the dotted name the way the same call through a
 * {@code from ... import} does.
 */
public class PythonModuleAttributeCallTest {

    private static final String TARGET = "gpulib.cuda.attach_external_stream";

    /** The references of {@code go} in the class {@code A} of {@code a.py}, as written. */
    private static List<String> goReferences(final PythonInlineProject project) {
        return project.component("a.A.go").references().stream()
                .map(ref -> ref.getClass().getSimpleName() + " " + ref.invokedComponent() + " "
                        + ref.resolutionKind())
                .sorted()
                .collect(Collectors.toList());
    }

    /** The references of {@code go} that name the imported package. */
    private static List<String> gpulibReferences(final PythonInlineProject project) {
        return goReferences(project).stream().filter(ref -> ref.contains("gpulib"))
                .collect(Collectors.toList());
    }

    private static PythonInlineProject withA(final String imports, final String call) throws Exception {
        return PythonInlineProject.compile("/a.py", lines(imports, "class A:", "    def go(self):",
                "        " + call));
    }

    private static final List<String> WANTED = List.of(
            "SimpleTypeReference " + TARGET + " " + ResolutionKind.UNRESOLVED);

    @Test
    public void importOfTheTopPackage() throws Exception {
        Assert.assertEquals(WANTED, goReferences(
                withA("import gpulib", "gpulib.cuda.attach_external_stream(1)")));
    }

    @Test
    public void importOfTheDottedModule() throws Exception {
        Assert.assertEquals(WANTED, goReferences(
                withA("import gpulib.cuda", "gpulib.cuda.attach_external_stream(1)")));
    }

    @Test
    public void importOfTheDottedModuleUnderAnAlias() throws Exception {
        Assert.assertEquals(WANTED, goReferences(
                withA("import gpulib.cuda as x", "x.attach_external_stream(1)")));
    }

    @Test
    public void importOfThePackageUnderAnAlias() throws Exception {
        Assert.assertEquals(WANTED, goReferences(
                withA("import gpulib as y", "y.cuda.attach_external_stream(1)")));
    }

    @Test
    public void importOfAModuleWithASingleNameStillWorks() throws Exception {
        Assert.assertEquals(List.of("SimpleTypeReference gpulib.attach UNRESOLVED"), goReferences(
                withA("import gpulib", "gpulib.attach(1)")));
    }

    @Test
    public void theFromImportFormRecordsTheSameReference() throws Exception {
        final PythonInlineProject chain = withA("import gpulib", "gpulib.cuda.attach_external_stream(1)");
        final PythonInlineProject from = withA("from gpulib.cuda import attach_external_stream",
                "attach_external_stream(1)");
        final List<ComponentReference> viaChain = List.copyOf(chain.component("a.A.go").references());
        final List<ComponentReference> viaFrom = List.copyOf(from.component("a.A.go").references());
        Assert.assertEquals(viaFrom.size(), viaChain.size());
        Assert.assertTrue(viaChain.get(0) instanceof SimpleTypeReference);
        Assert.assertEquals(viaFrom.get(0), viaChain.get(0));
    }

    @Test
    public void aLocalNamedLikeTheImportAddsNothing() throws Exception {
        Assert.assertEquals(List.of(), gpulibReferences(PythonInlineProject.compile("/a.py", lines(
                "import gpulib", "def make(): return 1", "class A:", "    def go(self):",
                "        gpulib = make()", "        gpulib.cuda.attach_external_stream(1)"))));
    }

    @Test
    public void aParameterNamedLikeTheImportAddsNothing() throws Exception {
        Assert.assertEquals(List.of(), goReferences(PythonInlineProject.compile("/a.py", lines(
                "import gpulib", "class A:", "    def go(self, gpulib):",
                "        gpulib.cuda.attach_external_stream(1)"))));
    }

    @Test
    public void anAttributeChainNotRootedInAnImportAddsNothing() throws Exception {
        Assert.assertEquals(List.of(), goReferences(
                withA("import gpulib", "self.gpulib.cuda.attach_external_stream(1)")));
    }

    @Test
    public void aModuleOfTheProjectIsRecordedAsBefore() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile(
                "/pkg/b.py", lines("def hash(s: str) -> str:", "    return s"),
                "/a.py", lines("import pkg.b as b", "class A:", "    def go(self):", "        b.hash('x')"));
        Assert.assertEquals(List.of("pkg.b.hash(s: str) : str"), project.component("a.A.go").references().stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toList()));
    }
}
