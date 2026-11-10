package com.hadi.test.python;

import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;

import static com.hadi.test.python.PythonInlineProject.lines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Python references that must follow what the source says: a string annotation is the type it
 * holds, an inferred local depends on its type, a class decorator's arguments count, and
 * declarations inside a module-level {@code if} or {@code try} are components.
 */
public class PythonCrossLanguageParityTest {

    private static final String HOLDER = lines(
            "from typing import Optional",
            "from requests import Session",
            "from pkg.models import Foo",
            "",
            "class Holder:",
            "    attr: \"Late\"",
            "    opt: Optional[\"Late\"]",
            "    lst: list[\"Late\"]",
            "    ext: \"Session\"",
            "    imp: \"Foo\"",
            "    nope: \"Nowhere\"",
            "",
            "    def make(self, p: \"Late\", q: Optional[\"Holder.Deep.Leaf\"] = None) -> \"Late\":",
            "        return Holder.Late()",
            "",
            "    def deep(self) -> \"Holder.Deep.Leaf\":",
            "        return None",
            "",
            "    def missing(self, z: \"Nowhere\") -> \"list[Nowhere]\":",
            "        return []",
            "",
            "    class Late:",
            "        pass",
            "",
            "    class Deep:",
            "        class Leaf:",
            "            pass",
            "",
            "def top(a: \"Holder\", b: \"Holder.Late\") -> \"Holder\":",
            "    return a");

    private static PythonInlineProject holder() throws Exception {
        return PythonInlineProject.compile("/pkg/holder.py", HOLDER);
    }

    @Test
    public void aStringReturnAnnotationNamesTheTypeItHolds() throws Exception {
        final PythonInlineProject project = holder();
        assertTrue(project.model().components().anyMatch(
                c -> c.uniqueName().equals("pkg.holder.Holder.make(p: Late, q: Optional[Holder.Deep.Leaf]) : Late")));
        final Set<String> refs = project.references("pkg.holder.Holder.make");
        assertTrue(refs.toString(), refs.contains("pkg.holder.Holder.Late"));
        assertFalse(refs.toString(), refs.contains("str[ext]"));
    }

    @Test
    public void aDottedStringResolvesToTheNestedClass() throws Exception {
        final Set<String> refs = holder().references("pkg.holder.Holder.deep");
        assertEquals("[pkg.holder.Holder.Deep.Leaf]", refs.toString());
    }

    @Test
    public void aStringParameterAndAttributeResolveAsTheUnquotedFormDoes() throws Exception {
        final PythonInlineProject project = holder();
        assertTrue(project.parameterReferences("pkg.holder.Holder.make", "p").contains("pkg.holder.Holder.Late"));
        assertTrue(project.references("pkg.holder.Holder.attr").contains("pkg.holder.Holder.Late"));
        assertTrue(project.references("pkg.holder.Holder.ext").contains("requests.Session[ext]"));
        assertTrue(project.references("pkg.holder.Holder.imp").contains("pkg.models.Foo"));
    }

    @Test
    public void aStringInsideAGenericIsResolved() throws Exception {
        final PythonInlineProject project = holder();
        assertTrue(project.references("pkg.holder.Holder.opt").contains("pkg.holder.Holder.Late"));
        assertTrue(project.references("pkg.holder.Holder.lst").contains("pkg.holder.Holder.Late"));
        assertTrue(project.parameterReferences("pkg.holder.Holder.make", "q")
                .contains("pkg.holder.Holder.Deep.Leaf"));
    }

    @Test
    public void aStringThatResolvesToNothingAddsNothing() throws Exception {
        final PythonInlineProject project = holder();
        assertEquals("[]", project.references("pkg.holder.Holder.nope").toString());
        assertEquals("[]", project.references("pkg.holder.Holder.missing").toString());
        assertEquals("[]", project.parameterReferences("pkg.holder.Holder.missing", "z").toString());
    }

    @Test
    public void aStringAtModuleLevelNamesTheClass() throws Exception {
        final PythonInlineProject project = holder();
        assertTrue(project.parameterReferences("pkg.holder.top", "b").contains("pkg.holder.Holder.Late"));
        assertTrue(project.parameterReferences("pkg.holder.top", "a").contains("pkg.holder.Holder"));
    }

    @Test
    public void noReferenceIsTheQuotedLiteral() throws Exception {
        final PythonInlineProject project = holder();
        final Set<String> all = new TreeSet<>();
        project.model().components().forEach(c -> c.references().forEach(r -> all.add(r.invokedComponent())));
        assertTrue(all.toString(), all.stream().noneMatch(name -> name.contains("\"")));
    }

    @Test
    public void aClassDecoratorsArgumentIsADependency() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile("/pkg/use.py", lines(
                "from pkg.models import Baz, Foo",
                "",
                "def register(kind):",
                "    return kind",
                "",
                "@register(Baz)",
                "class Use:",
                "    pass",
                "",
                "@register(Foo)",
                "def handler():",
                "    return 1"));
        final Set<String> onClass = project.references("pkg.use.Use");
        assertTrue(onClass.toString(), onClass.contains("pkg.models.Baz"));
        assertFalse(onClass.toString(), onClass.contains("pkg.models.Foo"));
        assertTrue(project.references("pkg.use.handler").contains("pkg.models.Foo"));
    }

    @Test
    public void aDeclarationInAModuleLevelIfOrTryIsAComponent() throws Exception {
        final PythonInlineProject project = PythonInlineProject.compile(
                "/pkg/compat.py", lines(
                        "import sys",
                        "",
                        "if sys.version_info >= (3, 8):",
                        "    class Compat:",
                        "        \"\"\"new\"\"\"",
                        "else:",
                        "    class Compat:",
                        "        \"\"\"old\"\"\"",
                        "",
                        "try:",
                        "    from fastlib import Speedy",
                        "except ImportError:",
                        "    class Speedy:",
                        "        pass",
                        "finally:",
                        "    def cleanup():",
                        "        return 1"),
                "/pkg/use.py", lines(
                        "from pkg.compat import Compat, Speedy",
                        "",
                        "def go(c: Compat, s: Speedy):",
                        "    return c"));
        assertEquals("new", project.component("pkg.compat.Compat").comment().trim());
        assertEquals(1, project.model().components()
                .filter(c -> c.uniqueName().equals("pkg.compat.Compat")).count());
        project.component("pkg.compat.Speedy");
        project.component("pkg.compat.cleanup");
        final Set<String> refs = project.references("pkg.use.go");
        assertTrue(refs.toString(), refs.contains("pkg.compat.Compat"));
        assertTrue(refs.toString(), refs.contains("pkg.compat.Speedy"));
        assertTrue(refs.toString(), refs.stream().noneMatch(name -> name.endsWith("[ext]")));
    }
}
