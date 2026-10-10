package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.internalTargets;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A project of Java and Kotlin files, compiled once per language and merged. Each compile names
 * the other language's types exactly, and the merge makes those references internal, so a Java
 * type referencing a Kotlin type and a Kotlin type referencing a Java type both resolve to the
 * other's component.
 */
public class KotlinJavaMixedProjectTest {

    private static CompileResult kotlin;
    private static CompileResult java;

    static ProjectFiles project() {
        return KotlinTestUtil.projectOf(
                new ProjectFile("/src/main/java/com/acme/base/Entity.java",
                        "package com.acme.base;\npublic abstract class Entity { }\n"),
                new ProjectFile("/src/main/java/com/acme/base/Auditable.java",
                        "package com.acme.base;\npublic interface Auditable { }\n"),
                new ProjectFile("/src/main/java/com/acme/app/OrderService.java", String.join("\n",
                        "package com.acme.app;",
                        "import com.acme.domain.Order;",
                        "import com.acme.domain.OrdersKt;",
                        "public class OrderService {",
                        "  private Order order;",
                        "  public long total() { return OrdersKt.total(order); }",
                        "}")),
                new ProjectFile("/src/main/java/com/acme/web/OrderView.java", String.join("\n",
                        "package com.acme.web;",
                        "import com.acme.domain.*;",
                        "public class OrderView { private Order order; }")),
                new ProjectFile("/src/main/java/com/acme/domain/Invoice.java",
                        "package com.acme.domain;\npublic class Invoice { private Order order; }\n"),
                new ProjectFile("/src/main/kotlin/com/acme/domain/Order.kt", String.join("\n",
                        "package com.acme.domain",
                        "import com.acme.base.*",
                        "class Order(val id: Long) : Entity(), Auditable {",
                        "    val invoice: Invoice? = null",
                        "}")),
                new ProjectFile("/src/main/kotlin/com/acme/domain/Orders.kt", String.join("\n",
                        "package com.acme.domain",
                        "fun total(order: Order): Long = order.id")));
    }

    @BeforeClass
    public static void compile() throws Exception {
        kotlin = new ClarpseProject(project(), Lang.KOTLIN).result();
        java = new ClarpseProject(project(), Lang.JAVA).result();
    }

    private static OOPSourceCodeModel merged(final CompileResult first, final CompileResult second) {
        final OOPSourceCodeModel model = new OOPSourceCodeModel();
        model.merge(first.model());
        model.merge(second.model());
        return model;
    }

    @Test
    public void kotlinNamesJavaTypesExactly() {
        final var order = component(kotlin.model(), "com.acme.domain.Order");
        assertTrue(order.references(TypeReferences.EXTENSION)
                .contains(new TypeExtensionReference("com.acme.base.Entity")));
        assertTrue(order.references(TypeReferences.IMPLEMENTATION)
                .contains(new TypeImplementationReference("com.acme.base.Auditable")));
        assertTrue("a Java type of the same package resolves without an import",
                targets(order).contains("com.acme.domain.Invoice"));
    }

    @Test
    public void javaNamesKotlinTypesAndFileClassesExactly() {
        assertTrue(targets(component(java.model(), "com.acme.app.OrderService")).contains("com.acme.domain.Order"));
        assertTrue(targets(component(java.model(), "com.acme.app.OrderService"))
                .contains("com.acme.domain.OrdersKt"));
        assertTrue(targets(component(java.model(), "com.acme.domain.Invoice")).contains("com.acme.domain.Order"));
    }

    @Test
    public void aJavaOnDemandImportFindsAKotlinType() {
        final Set<String> targets = targets(component(java.model(), "com.acme.web.OrderView"));
        assertTrue(targets.toString(), targets.contains("com.acme.domain.Order"));
        assertFalse(targets.toString(), targets.contains("com.acme.web.Order"));
    }

    @Test
    public void aOneLevelJavaCompileLeavesAKotlinTypeNotLoadedRatherThanExternal() throws Exception {
        final CompileResult oneLevel = new ClarpseProject(project(), Lang.JAVA,
                List.of("/src/main/java/com/acme/domain/Invoice.java"), AnalysisOptions.oneLevel()).result();
        final var invoice = component(oneLevel.model(), "com.acme.domain.Invoice");
        assertTrue(invoice.notLoadedDependencies().stream()
                .anyMatch(reference -> reference.invokedComponent().equals("com.acme.domain.Order")));
        assertFalse(invoice.externalDependencies().stream()
                .anyMatch(reference -> reference.invokedComponent().equals("com.acme.domain.Order")));
    }

    @Test
    public void eachCompileAloneLeavesTheOtherLanguagesTypesExternal() {
        assertFalse(internalTargets(component(kotlin.model(), "com.acme.domain.Order"))
                .contains("com.acme.base.Entity"));
        assertFalse(internalTargets(component(java.model(), "com.acme.domain.Invoice"))
                .contains("com.acme.domain.Order"));
    }

    @Test
    public void mergingResolvesAKotlinReferenceToAJavaComponent() {
        for (final OOPSourceCodeModel model : new OOPSourceCodeModel[] {merged(kotlin, java), merged(java, kotlin)}) {
            final var order = component(model, "com.acme.domain.Order");
            assertTrue(internalTargets(order).contains("com.acme.base.Entity"));
            assertTrue(internalTargets(order).contains("com.acme.base.Auditable"));
            assertTrue(internalTargets(order).contains("com.acme.domain.Invoice"));
            assertTrue(internalTargets(component(model, "com.acme.domain.Order.invoice"))
                    .contains("com.acme.domain.Invoice"));
        }
    }

    @Test
    public void mergingResolvesAJavaReferenceToAKotlinComponent() {
        for (final OOPSourceCodeModel model : new OOPSourceCodeModel[] {merged(kotlin, java), merged(java, kotlin)}) {
            assertTrue(internalTargets(component(model, "com.acme.app.OrderService"))
                    .contains("com.acme.domain.Order"));
            assertTrue(internalTargets(component(model, "com.acme.app.OrderService.order"))
                    .contains("com.acme.domain.Order"));
            assertTrue(internalTargets(component(model, "com.acme.app.OrderService.total()"))
                    .contains("com.acme.domain.OrdersKt"));
            assertTrue(internalTargets(component(model, "com.acme.domain.Invoice"))
                    .contains("com.acme.domain.Order"));
        }
    }

    @Test
    public void mergingLeavesLibraryTypesExternal() {
        final var order = component(merged(kotlin, java), "com.acme.domain.Order.id");
        assertTrue(order.externalDependencies().stream()
                .anyMatch(reference -> reference.invokedComponent().equals("kotlin.Long")));
    }
}
