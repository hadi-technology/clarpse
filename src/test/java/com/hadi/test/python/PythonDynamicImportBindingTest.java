package com.hadi.test.python;

import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.sourcemodel.ImportDirective;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.hadi.test.python.PythonInlineProject.lines;

/**
 * A module bound by {@code m = importlib.import_module("p.m")} is bound like {@code import p.m as m}: the
 * directive carries the alias and what is used through the name references the declarations loaded, never
 * the type Pyright gives a module object.
 */
public class PythonDynamicImportBindingTest {

    private static final String B = lines("class Keys:", "    def sign(self) -> str: return ''",
            "def hash(s: str) -> str: return s");

    private static List<String> refs(final PythonInlineProject project, final String component) {
        return project.component(component).references().stream()
                .map(r -> r.getClass().getSimpleName() + " " + r.invokedComponent()
                        + (r instanceof MemberReference ? " " + ((MemberReference) r).member() : ""))
                .sorted().collect(Collectors.toList());
    }

    private static PythonInlineProject project(final String importLine, final String... body) throws Exception {
        final String[] source = new String[body.length + 3];
        source[0] = importLine;
        source[1] = "class D:";
        source[2] = "    def run(self):";
        for (int i = 0; i < body.length; i++) {
            source[3 + i] = "        " + body[i];
        }
        return PythonInlineProject.compile("/pkg/b.py", B, "/dyn.py", lines(source));
    }

    private static void assertLoadedDeclarations(final List<String> found) {
        Assert.assertTrue(found.toString(), found.contains("SimpleTypeReference pkg.b.hash(s: str) : str"));
        Assert.assertTrue(found.toString(), found.contains("SimpleTypeReference pkg.b.Keys"));
        Assert.assertTrue(found.toString(), found.contains("ConstructionReference pkg.b.Keys"));
        Assert.assertTrue(found.toString(), found.contains("MemberReference pkg.b.Keys sign"));
        Assert.assertTrue(found.toString(), found.stream().noneMatch(r -> r.contains("types.ModuleType")));
    }

    @Test
    public void importModuleThroughImportlibIsBoundLikeAnAliasedImport() throws Exception {
        final PythonInlineProject dynamic = project("import importlib",
                "m = importlib.import_module('pkg.b')", "m.hash('x')", "return m.Keys().sign()");
        assertLoadedDeclarations(refs(dynamic, "dyn.D.run"));
        final PythonInlineProject fixed = project("import pkg.b as m", "m.hash('x')", "return m.Keys().sign()");
        Assert.assertEquals(refs(fixed, "dyn.D.run"), refs(dynamic, "dyn.D.run"));
    }

    @Test
    public void importModuleImportedByNameIsBoundToo() throws Exception {
        assertLoadedDeclarations(refs(project("from importlib import import_module",
                "m = import_module('pkg.b')", "m.hash('x')", "return m.Keys().sign()"), "dyn.D.run"));
    }

    @Test
    public void theDirectiveCarriesTheAlias() throws Exception {
        final PythonInlineProject project = project("import importlib",
                "m = importlib.import_module('pkg.b')", "return m.hash('x')");
        final ImportDirective directive = project.component("dyn.D").importDirectives().stream()
                .filter(ImportDirective::isDynamic).findFirst().orElseThrow();
        Assert.assertEquals("pkg.b", directive.target());
        Assert.assertEquals("m", directive.alias());
        Assert.assertTrue(directive.importsModule());
    }

    @Test
    public void aNonLiteralArgumentAddsNothing() throws Exception {
        final List<String> found = refs(project("import importlib", "name = 'pkg.b'",
                "m = importlib.import_module(name)", "return m.hash('x')"), "dyn.D.run");
        Assert.assertTrue(found.toString(), found.stream().noneMatch(r -> r.contains("pkg.b")));
    }

    @Test
    public void aNameBoundTwiceAddsNothing() throws Exception {
        final List<String> found = refs(project("import importlib",
                "m = importlib.import_module('pkg.b')", "m = object()", "return m.hash('x')"), "dyn.D.run");
        Assert.assertTrue(found.toString(), found.stream().noneMatch(r -> r.contains("pkg.b.hash")));
    }

    @Test
    public void aNestedAttributeChainReachesTheLoadedModule() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/b.py", B,
                "/dyn.py", lines("import importlib", "class D:", "    def run(self):",
                        "        m = importlib.import_module('pkg')", "        return m.b.hash('x')"));
        final List<ComponentReference> found = List.copyOf(project.component("dyn.D.run").references());
        Assert.assertTrue(found.toString(), found.stream().noneMatch(r -> r.invokedComponent().contains("ModuleType")));
    }
}
