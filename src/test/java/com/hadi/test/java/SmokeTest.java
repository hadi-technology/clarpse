package com.hadi.test.java;

import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.test.ClarpseTestUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.Set;

public class SmokeTest {

    private static OOPSourceCodeModel junit5Model;
    private static OOPSourceCodeModel clarpseCodeModel;

    @BeforeClass
    public static void setup() throws Exception {
        junit5Model = ClarpseTestUtil.sourceCodeModel("/junit5-main.zip", Lang.JAVA);
        clarpseCodeModel = ClarpseTestUtil.sourceCodeModel("/clarpse.zip", Lang.JAVA);
    }

    @Test
    public void spotCheckClass() {
        Assert.assertTrue(junit5Model.containsComponent("org.junit.jupiter.api.AssertNotNull"));
    }

    @Test
    public void spotCheckClassExtension() {
        Assert.assertTrue(junit5Model.getComponent("org.junit.platform.engine.discovery" +
                                                       ".ExcludeClassNameFilter").get().references(OOPSourceModelConstants.TypeReferences.EXTENSION).contains(new TypeExtensionReference("org.junit.platform.engine.discovery" + ".AbstractClassNameFilter")));
    }

    @Test
    public void spotCheckClassDocs() {
        Assert.assertEquals("/**\n" + " * Register a concrete " + "implementation of this interface " + "with a\n" + " * {@link org.junit.platform" + ".launcher.core" + ".LauncherDiscoveryRequestBuilder} " + "or\n" + " * {@link Launcher} to be notified" + " of events that occur during test " + "discovery.\n" + " *\n" + " * <p>All methods in this " + "interface have empty " + "<em>default</em> implementations" + ".\n" + " * Concrete implementations may " + "therefore override one or more of " + "these methods\n" + " * to be notified of the selected " + "events.\n" + " *\n" + " * <p>JUnit provides default " + "implementations that are created " + "via the factory\n" + " * methods in\n" + " * {@link org.junit.platform" + ".launcher.listeners.discovery" + ".LauncherDiscoveryListeners}.\n" + " *\n" + " * <p>The methods declared in this" + " interface are called by the " + "{@link Launcher}\n" + " * created via the {@link org" + ".junit.platform.launcher.core" + ".LauncherFactory}\n" + " * during test discovery.\n" + " *\n" + " * @see org.junit.platform" + ".launcher.listeners.discovery" + ".LauncherDiscoveryListeners\n" + " * @see LauncherDiscoveryRequest" + "#getDiscoveryListener()\n" + " * @see org.junit.platform" + ".launcher.core.LauncherConfig" + ".Builder" + "#addLauncherDiscoveryListeners\n" + " * @since 1.6\n" + " */\n", junit5Model.getComponent("org.junit.platform.launcher" +
                ".LauncherDiscoveryListener").get().comment());
    }

    @Test
    public void spotCheckClassImplementation() {
        Assert.assertTrue(junit5Model.getComponent("org.junit.platform.engine.discovery" +
                                                       ".ExcludePackageNameFilter").get().references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION).contains(new TypeImplementationReference("org.junit.platform.engine.discovery" + ".PackageNameFilter")));
    }

    @Test
    public void spotCheckMethod() {
        Assert.assertTrue(junit5Model.containsComponent("org.junit.platform.engine.FilterResult" +
                                                            ".included(String)"));
    }

    @Test
    public void spotCheckSingletonListOriginalClassTypeReference() {
        Assert.assertTrue(junit5Model.getComponent("example.util.ListWriter").get().references(OOPSourceModelConstants.TypeReferences.SIMPLE).contains(new SimpleTypeReference("java.util.Collections")));
    }

    @Test
    public void spotCheckClarpseClassExtension() {
        Assert.assertEquals("com.hadi.clarpse.reference" + ".ComponentReference", clarpseCodeModel.getComponent("com.hadi.clarpse.reference" +
                ".SimpleTypeReference").get().references(OOPSourceModelConstants.TypeReferences.EXTENSION).get(0).invokedComponent());
    }

    @Test
    public void spotCheckClarpseLocalVarsExists() {
        Assert.assertEquals(6, clarpseCodeModel.getComponent("com.hadi.clarpse.compiler" +
                ".ClarpseES6Compiler" +
                ".parseAllSourceCode" +
                "(List<ProjectFile>, " +
                "OOPSourceCodeModel, Compiler, " +
                "ModulesMap)").get().children().size());
    }


    @Test
    public void spotCheckClarpseInterfaceImplementation() {
        Assert.assertEquals("com.hadi.clarpse.compiler.ClarpseCompiler", clarpseCodeModel.getComponent("com.hadi.clarpse.compiler.go" +
                ".ClarpseGoCompiler").get().references(OOPSourceModelConstants.TypeReferences.IMPLEMENTATION).get(0).invokedComponent());
    }

    @Test
    public void spotCheckClarpseMethodDoc() {
        Assert.assertEquals("/**\n" + " * Returns all the Go packages contained " + "in the given " + "code sorted by package path\n" + " * length from smallest to greatest.\n" + " */\n", clarpseCodeModel.getComponent("com.hadi.clarpse.compiler.go" +
                ".ClarpseGoCompiler" + ".sourcePkgs" +
                "(List<ProjectFile>, boolean)").get().comment());
    }

    @Test
    public void spotCheckClarpseClassDoc() {
        Assert.assertEquals("/**\n" + " * Represents source files to be parsed.\n" + " */\n", clarpseCodeModel.getComponent("com.hadi.clarpse.compiler.go.GoModule").get().comment());
    }

    @Test
    public void spotCheckClarpseClassImports() {
        Assert.assertEquals(Set.of(
                "com.hadi.clarpse.reference.ComponentReference",
                "java.util.List",
                "com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType",
                "com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences",
                "com.fasterxml.jackson.annotation.JsonInclude",
                "java.util.Set",
                "com.fasterxml.jackson.annotation.JsonInclude.Include",
                "java.util.LinkedHashSet",
                "java.io.Serializable",
                "java.util.ArrayList"
        ), clarpseCodeModel.getComponent("com.hadi.clarpse.sourcemodel.Component").get().imports());
    }


}
