package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.MemberKind;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A call of a member a {@code using static} directive imports records the class and the call, as the
 * qualified call does, unless a nearer scope could bind the name or the class cannot be told apart.
 */
public class CSharpUsingStaticUseTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Util.cs", """
                        namespace Lib {
                          public static class Util {
                            public static int Max(int a, int b) { return a; }
                            public static int Zero = 0;
                          }
                          public static class Other { public static int Max(int a, int b) { return b; } }
                          public static class Ext { public static int Shout(this string s) { return 1; } }
                          public class Host { public static class Inner { public static int Deep() { return 1; } } }
                        }
                        namespace Lib.Nested {
                          public static class Tools { public static int Twice(int a) { return a * 2; } }
                        }
                        """),
                new ProjectFile("/A.cs", """
                        using static Lib.Util;
                        using static Lib.Nested.Tools;
                        using static Lib.Host.Inner;
                        using static Lib.Ext;
                        namespace App {
                          public class A {
                            public int F() { return Max(1, 2) + Zero; }
                            public int Nested() { return Twice(2); }
                            public int InnerType() { return Deep(); }
                            public int Plain(string s) { return Shout(s); }
                            public int Local() { int Max(int a, int b) { return b; } return Max(1, 2); }
                            public int Variable(System.Func<int, int, int> f) { System.Func<int, int, int> Max = f; return Max(1, 2); }
                            public int Parameter(System.Func<int, int, int> Max) { return Max(1, 2); }
                            public int NotAMethod() { return Zero(1); }
                          }
                          public class Declares {
                            public int Max(int a, int b) { return b; }
                            public int F() { return Max(1, 2); }
                          }
                          public class Derived : Unseen.Base {
                            public int F() { return Max(1, 2); }
                          }
                          public class Parsed : Inherited {
                            public int F() { return Max(1, 2); }
                          }
                          public class Inherited { protected int Max(int a, int b) { return b; } }
                          public class Qualified {
                            public int F() { return Lib.Util.Max(1, 2); }
                          }
                          public class Constructs {
                            public object F() { return new Max(1); }
                          }
                        }
                        """),
                new ProjectFile("/Two.cs", """
                        using static Lib.Util;
                        using static Lib.Other;
                        namespace App {
                          public class Ambiguous { public int F() { return Max(1, 2); } }
                        }
                        """),
                new ProjectFile("/Math.cs", """
                        using static System.Math;
                        namespace App {
                          public class Roots { public double F() { return Sqrt(4); } }
                        }
                        """),
                new ProjectFile("/Several.cs", """
                        using static System.Math;
                        using static System.Console;
                        namespace App {
                          public class Several { public double F() { return Sqrt(4); } }
                        }
                        """)).model();
    }

    private static List<ComponentReference> references(final String uniqueNamePrefix) {
        final Component method = model.components().filter(c -> c.uniqueName().startsWith(uniqueNamePrefix))
                .filter(c -> c.componentType().isMethodComponent()).findFirst().orElseThrow();
        return List.copyOf(method.references());
    }

    private static List<MemberReference> calls(final String uniqueNamePrefix) {
        return references(uniqueNamePrefix).stream().filter(MemberReference.class::isInstance)
                .map(MemberReference.class::cast).collect(Collectors.toList());
    }

    private static boolean referencesType(final String uniqueNamePrefix, final String type) {
        return references(uniqueNamePrefix).stream().anyMatch(ref -> ref instanceof SimpleTypeReference
                && ref.invokedComponent().equals(type));
    }

    @Test
    public void aCallOfAStaticallyImportedRepositoryMethodIsACallAndAReferenceToTheClass() {
        final List<MemberReference> found = calls("App.A.F(");
        assertEquals(found.toString(), 1, found.size());
        assertEquals("Lib.Util", found.get(0).ownerType());
        assertEquals("Max", found.get(0).member());
        assertEquals(MemberKind.CALL, found.get(0).memberKind());
        assertEquals(ResolutionKind.EXACT, found.get(0).resolutionKind());
        assertNull(found.get(0).externalUnit());
        assertTrue(referencesType("App.A.F(", "Lib.Util"));
    }

    @Test
    public void aFieldReadIsNotAMemberReference() {
        assertTrue(calls("App.A.F(").stream().noneMatch(ref -> ref.member().equals("Zero")));
    }

    @Test
    public void aStaticClassInANestedNamespaceIsFound() {
        final List<MemberReference> found = calls("App.A.Nested(");
        assertEquals(found.toString(), 1, found.size());
        assertEquals("Lib.Nested.Tools", found.get(0).ownerType());
        assertTrue(referencesType("App.A.Nested(", "Lib.Nested.Tools"));
    }

    @Test
    public void aStaticImportOfANestedTypeIsFound() {
        final List<MemberReference> found = calls("App.A.InnerType(");
        assertEquals(found.toString(), 1, found.size());
        assertEquals("Lib.Host.Inner", found.get(0).ownerType());
    }

    @Test
    public void aLibraryClassIsRecordedWithItsNamespaceAsTheUnit() {
        final List<MemberReference> found = calls("App.Roots.F(");
        assertEquals(found.toString(), 1, found.size());
        assertEquals("System.Math", found.get(0).ownerType());
        assertEquals("Sqrt", found.get(0).member());
        assertEquals("System", found.get(0).externalUnit());
        assertTrue(referencesType("App.Roots.F(", "System.Math"));
    }

    @Test
    public void severalLibraryClassesCannotBeToldApart() {
        assertTrue(calls("App.Several.F(").isEmpty());
        assertFalse(referencesType("App.Several.F(", "System.Math"));
    }

    @Test
    public void anExtensionMethodCalledInPlainFormAddsNothing() {
        assertTrue(calls("App.A.Plain(").stream().noneMatch(ref -> ref.ownerType().equals("Lib.Ext")));
    }

    @Test
    public void aLocalFunctionAVariableAndAParameterHideTheImport() {
        assertTrue(calls("App.A.Local(").isEmpty());
        assertTrue(calls("App.A.Variable(").isEmpty());
        assertTrue(calls("App.A.Parameter(").isEmpty());
    }

    @Test
    public void aNameThatIsNotAStaticMethodOfTheClassAddsNothing() {
        assertTrue(calls("App.A.NotAMethod(").isEmpty());
    }

    @Test
    public void aMethodOfTheEnclosingClassOrOfAnInheritedBaseWins() {
        assertTrue(calls("App.Declares.F(").stream().noneMatch(ref -> ref.ownerType().equals("Lib.Util")));
        assertTrue(calls("App.Parsed.F(").stream().noneMatch(ref -> ref.ownerType().equals("Lib.Util")));
    }

    @Test
    public void aBaseTheParseCannotSeeMayDeclareTheName() {
        assertTrue(calls("App.Derived.F(").isEmpty());
    }

    @Test
    public void twoClassesDeclaringTheNameAreAmbiguous() {
        assertTrue(calls("App.Ambiguous.F(").isEmpty());
    }

    @Test
    public void aQualifiedCallStillRecordsTheClassAndTheCall() {
        final List<MemberReference> found = calls("App.Qualified.F(");
        assertEquals(found.toString(), 1, found.size());
        assertEquals("Lib.Util", found.get(0).ownerType());
    }

    @Test
    public void aConstructionIsNotACall() {
        assertTrue(calls("App.Constructs.F(").isEmpty());
    }
}
