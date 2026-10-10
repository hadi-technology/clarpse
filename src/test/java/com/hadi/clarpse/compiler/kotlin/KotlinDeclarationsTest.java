package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Kotlin declarations are read a package at a time, when a name could be declared in it, and every
 * answer is the one reading all the files up front gives.
 */
public class KotlinDeclarationsTest {

    private static List<ProjectFile> files() {
        return List.of(
                new ProjectFile("/src/a/Order.kt", "package com.acme.orders\nclass Order { class Line }\nobject Orders\n"),
                new ProjectFile("/src/a/util.kt", "@file:JvmName(\"OrderUtil\")\npackage com.acme.orders\nfun total() = 1\n"),
                new ProjectFile("/src/b/Pay.kt", "package com.acme.pay\ninterface Gateway\n"),
                new ProjectFile("/src/b/sub.kt", "package com.acme.pay.sub\nclass Card\nfun charge() = 1\n"),
                new ProjectFile("/src/Root.kt", "class Root { interface Nested }\n"),
                new ProjectFile("/src/Spaced.kt", "package com .acme\n  .spaced\nclass Spaced\n"),
                new ProjectFile("/src/Ticked.kt", "package com.`in`.ticked\nclass Ticked\n"),
                new ProjectFile("/src/Template.kt", "package {{name}}\nclass Templated\n"),
                new ProjectFile("/src/Broken.kt", "package com.acme.broken\nclass {\n"));
    }

    private static List<String> questions() {
        final List<String> names = new ArrayList<>(List.of(
                "com.acme.orders.Order", "com.acme.orders.Order.Line", "com.acme.orders.Order.Line.Deeper",
                "com.acme.orders.Order.CONSTANT", "com.acme.orders.Orders", "com.acme.orders.OrderUtil",
                "com.acme.orders.UtilKt", "com.acme.orders.Missing", "com.acme.orders",
                "com.acme.pay.Gateway", "com.acme.pay.sub.Card", "com.acme.pay.sub.SubKt", "com.acme.pay.Card",
                "Root", "Root.Nested", "Nested", "com.acme.spaced.Spaced", "com.in.ticked.Ticked",
                "Templated", "com.Templated", "com.acme.broken.Anything", "java.util.List", "String", "", "a"));
        names.add(null);
        return names;
    }

    @Test
    public void everyAnswerIsTheOneReadingEveryFileGives() throws Exception {
        final KotlinDeclarationIndex eager = new KotlinDeclarationIndex(ClarpseKotlinCompiler.models(
                ClarpseKotlinCompiler.parseFiles(new ArrayList<>(files()), false), new HashSet<>()), null);
        final KotlinDeclarations lazy = KotlinDeclarations.of(files());
        for (final String name : questions()) {
            final boolean declares = name != null && !name.isEmpty() && !eager.kotlinFilesDeclaring(name).isEmpty();
            assertEquals("isType " + name, name != null && eager.isType(name), lazy.declaresType(name));
            assertEquals("declares " + name, declares, lazy.declares(name));
        }
        assertTrue(lazy.declaresType("com.acme.orders.OrderUtil"));
        assertTrue(lazy.declaresType("com.acme.spaced.Spaced"));
        assertTrue(lazy.declaresType("com.in.ticked.Ticked"));
        assertTrue(lazy.declares("com.acme.orders.Order.CONSTANT"));
        assertFalse(lazy.declaresType("com.acme.orders.Order.CONSTANT"));
    }

    @Test
    public void everyHeaderItReadsIsThePackageTheParserReads() throws Exception {
        for (final KotlinModel.KotlinFileModel model : ClarpseKotlinCompiler.models(
                ClarpseKotlinCompiler.parseFiles(new ArrayList<>(files()), false), new HashSet<>())) {
            final String header = KotlinPackageHeader.of(
                    files().stream().filter(file -> file.path().equals(model.path())).findFirst()
                            .orElseThrow().content());
            if (header != null) {
                assertEquals(model.path(), model.packageName == null ? "" : model.packageName, header);
            }
        }
    }

    @Test
    public void aPackageIsReadOnlyWhenANameCouldBeDeclaredInIt() {
        final KotlinDeclarations lazy = KotlinDeclarations.of(files());
        assertTrue(lazy.packagesRead().isEmpty());

        lazy.declaresType("com.acme.pay.Gateway");
        final Set<String> read = lazy.packagesRead();
        assertEquals(new HashSet<>(java.util.Arrays.asList(null, "", "com.acme.pay")), read);

        lazy.declares("java.util.List");
        assertEquals(read, lazy.packagesRead());
    }

    @Test
    public void aFailedReadIsReportedRatherThanTakenForNoDeclaration() {
        final KotlinDeclarations lazy = KotlinDeclarations.of(files());
        Thread.currentThread().interrupt();
        try {
            lazy.declaresType("com.acme.orders.Order");
            fail("an interrupted read should not answer");
        } catch (final IllegalStateException expected) {
            assertNotNull(lazy.failure());
        } finally {
            Thread.interrupted();
        }
    }
}
