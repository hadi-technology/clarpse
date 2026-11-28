package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A C# member that returns or holds a reference carries the {@code ref} modifier, and the
 * {@code readonly} of a {@code ref readonly} return type is not reported as a modifier of the member.
 */
public class CSharpRefMemberModifierTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(new ProjectFile("/A.cs", """
                namespace N {
                  public class A {
                    int v;
                    public ref int R(ref int x) { return ref x; }
                    public ref readonly int RR(in int x) { return ref x; }
                    public ref int Prop => ref v;
                    public ref readonly int RProp => ref v;
                    public int Local(int x) { ref int l = ref x; return l; }
                    public int Param(ref int x) { return x; }
                    public static ref int Split(ref int x)
                #if NET
                    { return ref x; }
                #else
                    { return ref x; }
                #endif
                  }
                  public interface I { ref int IR(ref int x); ref readonly int IRR(in int x); }
                  public ref struct S { public ref int Rf; public readonly int Plain; }
                  public struct T { public readonly int M() { return 1; } }
                }
                """)).model();
    }

    private static Set<String> mods(final String uniqueName) {
        return model.copyOfComponent(uniqueName).get().modifiers();
    }

    @Test
    public void refReturningMethodHasRef() {
        assertEquals(Set.of("public", "ref"), mods("N.A.R(int)"));
    }

    @Test
    public void refReadonlyReturningMethodHasRefAndNoReadonly() {
        assertEquals(Set.of("public", "ref"), mods("N.A.RR(int)"));
    }

    @Test
    public void refReturningPropertyHasRef() {
        assertEquals(Set.of("public", "ref"), mods("N.A.Prop"));
        assertEquals(Set.of("public", "ref"), mods("N.A.RProp"));
    }

    @Test
    public void refFieldHasRef() {
        assertEquals(Set.of("public", "ref"), mods("N.S.Rf"));
    }

    @Test
    public void readonlyFieldAndReadonlyStructMemberKeepReadonly() {
        assertEquals(Set.of("public", "readonly"), mods("N.S.Plain"));
        assertEquals(Set.of("public", "readonly"), mods("N.T.M()"));
    }

    @Test
    public void refStructKeepsItsRef() {
        assertEquals(Set.of("public", "ref"), mods("N.S"));
    }

    @Test
    public void refParameterAndRefLocalAreNotMemberModifiers() {
        assertEquals(Set.of("public"), mods("N.A.Param(int)"));
        assertEquals(Set.of("public"), mods("N.A.Local(int)"));
    }

    @Test
    public void interfaceMethodReturningRefHasRef() {
        assertTrue(mods("N.I.IR(int)").contains("ref"));
        assertTrue(mods("N.I.IRR(int)").contains("ref"));
        assertFalse(mods("N.I.IRR(int)").contains("readonly"));
    }

    @Test
    public void methodSplitByADirectiveKeepsItsModifiers() {
        assertEquals(Set.of("public", "static", "ref"), mods("N.A.Split(int)"));
    }
}
