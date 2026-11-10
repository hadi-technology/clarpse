package com.hadi.test.python;

import com.hadi.clarpse.reference.MemberKind;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A call on a receiver Pyright types as exactly one class references the member it calls, on the
 * owning class: the repository class that declares it, or the receiver's class, with the defining
 * module as the unit of an external class.
 */
public class PythonMemberReferenceTest {

    private static final String SHAPES = PythonInlineProject.lines(
            "class Base:",
            "    def run(self):",
            "        pass",
            "    @classmethod",
            "    def make(cls):",
            "        return cls()",
            "class Sub(Base):",
            "    def own(self):",
            "        pass");

    private static final String USE = PythonInlineProject.lines(
            "import pathlib",
            "from pkg.shapes import Sub, Base",
            "def go(s: Sub, p: pathlib.Path, u):",
            "    s.run()",
            "    s.own()",
            "    p.exists()",
            "    Base.make()",
            "    u.nothing()",
            "    local = Sub()",
            "    local.own()");

    private static List<MemberReference> members() throws Exception {
        final Component go = PythonInlineProject.compile("/pkg/shapes.py", SHAPES, "/pkg/use.py", USE)
                .component("pkg.use.go");
        return go.references().stream().filter(MemberReference.class::isInstance)
                .map(MemberReference.class::cast).collect(Collectors.toList());
    }

    private static MemberReference member(final List<MemberReference> all, final String name) {
        final List<MemberReference> found = all.stream().filter(ref -> ref.member().equals(name))
                .collect(Collectors.toList());
        Assert.assertEquals(all.toString(), 1, found.size());
        Assert.assertEquals(found.get(0).ownerType(), found.get(0).invokedComponent());
        Assert.assertEquals(MemberKind.CALL, found.get(0).memberKind());
        return found.get(0);
    }

    @Test
    public void aMethodInheritedFromARepositoryBaseIsOwnedByTheBase() throws Exception {
        final List<MemberReference> all = members();
        Assert.assertEquals("pkg.shapes.Base", member(all, "run").ownerType());
        Assert.assertNull(member(all, "run").externalUnit());
        Assert.assertEquals("pkg.shapes.Sub", member(all, "own").ownerType());
    }

    @Test
    public void aLibraryReceiverIsOwnedByItsClassAndNamesItsModule() throws Exception {
        final MemberReference exists = member(members(), "exists");
        Assert.assertEquals("pathlib.Path", exists.ownerType());
        Assert.assertEquals("pathlib", exists.externalUnit());
        Assert.assertTrue(exists.isExternal());
        Assert.assertEquals(ResolutionKind.EXACT, exists.resolutionKind());
    }

    @Test
    public void aCallThroughAClassNameIsOwnedByTheClass() throws Exception {
        Assert.assertEquals("pkg.shapes.Base", member(members(), "make").ownerType());
    }

    @Test
    public void aReceiverOfUnknownTypeAddsNothing() throws Exception {
        Assert.assertTrue(members().stream().noneMatch(ref -> ref.member().equals("nothing")));
    }
}
