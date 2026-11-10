package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The operand of {@code typeof} names a type, so the member that writes it depends on that type. */
public class CSharpTypeofReferenceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Types.cs", """
                        namespace App {
                          public class Alpha { }
                          public class Beta { }
                          public class Gamma<T> { }
                          public class Delta { }
                          public class Epsilon { }
                          public class Zeta { }
                          public class Quoted { }
                          public class Commented { }
                          public class Shadow { }
                        }
                        """),
                new ProjectFile("/Uses.cs", """
                        namespace App {
                          public class InMethod { public void Run() { var t = typeof(Alpha); } }
                          public class InProperty { public object Kind => typeof(Beta); }
                          public class InField { public object Kind = typeof(Gamma<Delta>); }
                          public class Unbound { public object Kind() { return typeof(Gamma<>); } }
                          public class AsArray { public object Kind() { return typeof(Epsilon[]); } }
                          public class Qualified { public object Kind() { return typeof(App.Zeta); } }
                          public class InString { public string Kind() { return "typeof(Quoted)"; } }
                          public class InComment { public void Run() { /* typeof(Commented) */ } }
                          public class Variable { public Shadow Shadow; public object Kind() { return typeof(Shadow); } }
                        }
                        """)).model();
    }

    private static boolean references(final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow(() -> new AssertionError("no component " + from))
                .references().stream().map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void typeofInAMethodBodyIsReferenced() {
        assertTrue(references("App.InMethod.Run()", "App.Alpha"));
        assertTrue(references("App.InMethod", "App.Alpha"));
    }

    @Test
    public void typeofInAPropertyAndAFieldInitializerIsReferenced() {
        assertTrue(references("App.InProperty.Kind", "App.Beta"));
        assertTrue(references("App.InField.Kind", "App.Gamma"));
        assertTrue(references("App.InField.Kind", "App.Delta"));
    }

    @Test
    public void unboundGenericArrayAndQualifiedOperandsAreReferenced() {
        assertTrue(references("App.Unbound.Kind()", "App.Gamma"));
        assertTrue(references("App.AsArray.Kind()", "App.Epsilon"));
        assertTrue(references("App.Qualified.Kind()", "App.Zeta"));
    }

    @Test
    public void typeofInAStringOrACommentIsNotCode() {
        assertFalse(references("App.InString.Kind()", "App.Quoted"));
        assertFalse(references("App.InComment.Run()", "App.Commented"));
    }

    @Test
    public void aTypeofOperandStillNamesTheTypeWhenAMemberSharesItsName() {
        assertTrue(references("App.Variable.Kind()", "App.Shadow"));
    }
}
