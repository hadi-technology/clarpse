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

    private static final String TYPES = lines(
            "class Bar:",
            "    pass",
            "",
            "class Item:",
            "    class Inner:",
            "        pass",
            "",
            "class Foo:",
            "    def bar(self) -> Bar:",
            "        return Bar()",
            "",
            "    def plain(self):",
            "        return Bar()",
            "",
            "    def inner(self):",
            "        return Item.Inner()",
            "",
            "class Holder:",
            "    item: Item",
            "",
            "def make_qux():",
            "    return Item()");

    private static final String INFERRING = lines(
            "from pathlib import Path",
            "from typing import Optional, TypeVar",
            "from pkg.types import Bar, Foo, Holder, Item, make_qux",
            "",
            "T = TypeVar('T')",
            "",
            "def from_method(f: Foo):",
            "    b = f.bar()",
            "    return b",
            "",
            "def from_unannotated_method(f: Foo):",
            "    b = f.plain()",
            "    return b",
            "",
            "def from_function():",
            "    q = make_qux()",
            "    return q",
            "",
            "def from_attribute(h: Holder):",
            "    i = h.item",
            "    return i",
            "",
            "def from_nested(f: Foo):",
            "    n = f.inner()",
            "    return n",
            "",
            "def from_library():",
            "    p = Path('.')",
            "    return p",
            "",
            "def from_optional(o: Optional[Bar]):",
            "    kept = o",
            "    return kept",
            "",
            "def annotated(f: Foo):",
            "    a: Item = f.bar()",
            "    return a",
            "",
            "def names_nothing(a: Bar, c: T, flag: bool, nope):",
            "    number = 1",
            "    words = []",
            "    text = 'x'",
            "    unknown = nope.make()",
            "    either = a if flag else Item()",
            "    typevar = c",
            "    missing = Missing()",
            "    return number");

    private static PythonInlineProject inferring() throws Exception {
        return PythonInlineProject.compile("/pkg/types.py", TYPES, "/pkg/use.py", INFERRING);
    }

    @Test
    public void aLocalAssignedFromACallDependsOnTheClassPyrightInfersForIt() throws Exception {
        final PythonInlineProject project = inferring();
        assertEquals("[pkg.types.Bar]", project.localReferences("pkg.use.from_method", "b").toString());
        assertEquals("[pkg.types.Bar]", project.localReferences("pkg.use.from_unannotated_method", "b").toString());
        assertEquals("[pkg.types.Item]", project.localReferences("pkg.use.from_function", "q").toString());
    }

    @Test
    public void aLocalAssignedFromAnAttributeDependsOnItsType() throws Exception {
        assertEquals("[pkg.types.Item]", inferring().localReferences("pkg.use.from_attribute", "i").toString());
    }

    @Test
    public void aNestedClassIsNamedByItsPathInItsFile() throws Exception {
        assertEquals("[pkg.types.Item.Inner]", inferring().localReferences("pkg.use.from_nested", "n").toString());
    }

    @Test
    public void aLibraryClassIsRecordedAsTheExternalClass() throws Exception {
        assertEquals("[pathlib.Path[ext]]", inferring().localReferences("pkg.use.from_library", "p").toString());
    }

    @Test
    public void anOptionalIsItsClassAndTheFunctionDependsOnWhatItsLocalsDo() throws Exception {
        final PythonInlineProject project = inferring();
        assertEquals("[pkg.types.Bar]", project.localReferences("pkg.use.from_optional", "kept").toString());
        assertTrue(project.references("pkg.use.from_method").contains("pkg.types.Bar"));
    }

    @Test
    public void aLocalWithAnAnnotationKeepsTheAnnotation() throws Exception {
        assertEquals("[pkg.types.Item]", inferring().localReferences("pkg.use.annotated", "a").toString());
    }

    @Test
    public void aTypeThatIsNotExactlyOneClassAddsNothing() throws Exception {
        final PythonInlineProject project = inferring();
        for (final String local : new String[] {"unknown", "either", "typevar", "missing"}) {
            assertEquals(local, "[]", project.localReferences("pkg.use.names_nothing", local).toString());
        }
    }
}
