package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A type outside the parsed files, written with its qualified name as the receiver of a static call
 * or field access, is a dependency on that type, as it is when the same name is a parameter or
 * return type.
 */
public class JavaExternalQualifiedReferenceTest {

    private static final String BODY = String.join("\n",
            "package app;",
            "public class Client {",
            "  boolean call() { return org.vendor.plugins.Manager.isInstalled(\"a\"); }",
            "  void argument(Bus bus) { bus.subscribe(org.vendor.vfs.Files.CHANGES, this); }",
            "  Object local() { Object o = org.vendor.vfs.Files.instance(); return o; }",
            "  int nestedConstant() { return org.vendor.vfs.Files.Kind.SIZE; }",
            "  void param(org.vendor.vfs.Declared d) { }",
            "  Object chain(Request request) { return request.headers.Accept.value(); }",
            "  Object field(Request request) { return request.a.b.Name; }",
            "}");

    private static OOPSourceCodeModel model() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/src/Client.java", BODY));
        files.insertFile(new ProjectFile("/src/Bus.java", "package app;\npublic class Bus { }\n"));
        files.insertFile(new ProjectFile("/src/Request.java", "package app;\npublic class Request { }\n"));
        return new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static Set<String> refs(final OOPSourceCodeModel model, final String component) {
        final Component cmp = model.copyOfComponent(component).orElseThrow(
                () -> new AssertionError("no component " + component));
        return cmp.references().stream().map(r -> r.invokedComponent()).collect(Collectors.toSet());
    }

    @Test
    public void staticCallReceiverIsReferenced() throws Exception {
        assertTrue(refs(model(), "app.Client.call()").contains("org.vendor.plugins.Manager"));
    }

    @Test
    public void staticFieldInAnArgumentIsReferenced() throws Exception {
        assertTrue(refs(model(), "app.Client.argument(Bus)").contains("org.vendor.vfs.Files"));
    }

    @Test
    public void staticCallInALocalInitializerIsReferenced() throws Exception {
        assertTrue(refs(model(), "app.Client.local()").contains("org.vendor.vfs.Files"));
    }

    @Test
    public void nestedConstantNamesTheOutermostTypeOfTheName() throws Exception {
        assertTrue(refs(model(), "app.Client.nestedConstant()").contains("org.vendor.vfs.Files"));
    }

    @Test
    public void parameterTypeIsStillReferenced() throws Exception {
        assertTrue(refs(model(), "app.Client.param(org.vendor.vfs.Declared)").contains("org.vendor.vfs.Declared"));
    }

    @Test
    public void fieldChainOnAVariableIsNotAType() throws Exception {
        final Set<String> chain = refs(model(), "app.Client.chain(Request)");
        final Set<String> field = refs(model(), "app.Client.field(Request)");
        assertFalse(chain.stream().anyMatch(r -> r.contains("headers")));
        assertFalse(field.stream().anyMatch(r -> r.contains("request.a")));
        assertEquals(Set.of("app.Request"),
                chain.stream().filter(r -> r.startsWith("app.") || r.contains(".Request")).collect(Collectors.toSet()));
    }
}
