package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A static call or member read names its type wherever the expression sits, and a chain resolves to the type in it. */
public class CSharpStaticReceiverReferenceTest {

    private static final String SOURCE = """
            namespace Demo.Tools
            {
              public static class Factory { public static int Make() { return 1; } public static int Value = 2; }
              public static class Registry { public static class Inner { public static int Run() { return 1; } } }
              public static class Host { public static Helper Helper { get; } }
              public class Helper { public static int Go() { return 1; } }
              public static class Stringly { public static string Text = "Banned.Call()"; }
              public class Banned { public static int Call() { return 1; } }
            }
            namespace Demo
            {
              using Demo.Tools;
              public class Statement { public void M() { Factory.Make(); } }
              public class LocalInit { public void M() { var x = Factory.Make(); } }
              public class Returning { public int M() { return Factory.Value; } }
              public class Argument { public void M() { Consume(Factory.Make()); } void Consume(int v) {} }
              public class Nested { public int M() { return 1 + Math.Abs(Factory.Make()); } }
              public class Assigned { int f; public void M() { f = Factory.Value; } }
              public class Chain { public void M() { Host.Helper.ToString(); } }
              public class QualifiedChain { public void M() { var r = Tools.Registry.Inner.Run(); } }
              public class InString { public string M() { return "Banned.Call()"; } }
              public class InstanceChain { public void M(Other other) { var x = other.Helper.Go(); } }
              public class Other { public Helper Helper { get; } }
            }
            """;

    private static OOPSourceCodeModel model() throws Exception {
        return CSharpTestUtil.compileInline(new ProjectFile("/All.cs", SOURCE)).model();
    }

    private static boolean references(final OOPSourceCodeModel model, final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow()
                .references(OOPSourceModelConstants.TypeReferences.SIMPLE).stream()
                .map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void statementStillWorks() throws Exception {
        assertTrue(references(model(), "Demo.Statement.M()", "Demo.Tools.Factory"));
    }

    @Test
    public void localInitializerCall() throws Exception {
        assertTrue(references(model(), "Demo.LocalInit.M()", "Demo.Tools.Factory"));
    }

    @Test
    public void returnedMemberRead() throws Exception {
        assertTrue(references(model(), "Demo.Returning.M()", "Demo.Tools.Factory"));
    }

    @Test
    public void callArgument() throws Exception {
        assertTrue(references(model(), "Demo.Argument.M()", "Demo.Tools.Factory"));
    }

    @Test
    public void nestedExpression() throws Exception {
        assertTrue(references(model(), "Demo.Nested.M()", "Demo.Tools.Factory"));
    }

    @Test
    public void assignedValue() throws Exception {
        assertTrue(references(model(), "Demo.Assigned.M()", "Demo.Tools.Factory"));
    }

    @Test
    public void chainResolvesToTheTypeAtItsHead() throws Exception {
        assertTrue(references(model(), "Demo.Chain.M()", "Demo.Tools.Host"));
    }

    @Test
    public void qualifiedChainResolvesToTheLongestTypePrefix() throws Exception {
        assertTrue(references(model(), "Demo.QualifiedChain.M()", "Demo.Tools.Registry.Inner"));
    }

    @Test
    public void textInAStringLiteralIsNotAReference() throws Exception {
        assertFalse(references(model(), "Demo.InString.M()", "Demo.Tools.Banned"));
    }

    @Test
    public void memberOfAnInstanceIsNotATypeReference() throws Exception {
        assertFalse(references(model(), "Demo.InstanceChain.M(Other)", "Demo.Tools.Helper"));
    }
}
