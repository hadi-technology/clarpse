package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The type argument of a supertype is a dependency in its own right. {@code implements
 * Repository<Order>} says two things -- that the class implements {@code Repository}, and that it
 * depends on {@code Order} -- and only the first was recorded.
 */
public class GenericTypeArgumentReferenceTest {

    private static OOPSourceCodeModel model(final ProjectFile... files) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        for (final ProjectFile file : files) {
            projectFiles.insertFile(file);
        }
        return new ClarpseProject(projectFiles, Lang.JAVA).result().model();
    }

    private static Set<String> referenced(final OOPSourceCodeModel model, final String component) {
        return model.copyOfComponent(component).orElseThrow().references().stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());
    }

    @Test
    public void anImplementedTypeSArgumentIsADependency() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/DTO.java", "package com; public interface DTO<T> { }"),
                new ProjectFile("/com/HttpRequest.java", "package com; public class HttpRequest { }"),
                new ProjectFile("/com/A.java", "package com; public class A implements DTO<HttpRequest> { }"));

        assertTrue(referenced(model, "com.A").contains("com.DTO"));
        assertTrue(referenced(model, "com.A").contains("com.HttpRequest"));
    }

    @Test
    public void anExtendedTypeSArgumentIsADependency() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/Base.java", "package com; public class Base<T> { }"),
                new ProjectFile("/com/Order.java", "package com; public class Order { }"),
                new ProjectFile("/com/A.java", "package com; public class A extends Base<Order> { }"));

        assertTrue(referenced(model, "com.A").contains("com.Base"));
        assertTrue(referenced(model, "com.A").contains("com.Order"));
    }

    /**
     * The argument is a dependency, not a supertype: a class implementing {@code DTO<HttpRequest>}
     * does not implement {@code HttpRequest}.
     */
    @Test
    public void anArgumentIsRecordedAsAPlainDependencyNotAsHeritage() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/DTO.java", "package com; public interface DTO<T> { }"),
                new ProjectFile("/com/HttpRequest.java", "package com; public class HttpRequest { }"),
                new ProjectFile("/com/A.java", "package com; public class A implements DTO<HttpRequest> { }"));

        assertEquals(Set.of("com.DTO"), model.copyOfComponent("com.A").orElseThrow()
                .references(TypeReferences.IMPLEMENTATION).stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet()));
        assertTrue(model.copyOfComponent("com.A").orElseThrow()
                .references(TypeReferences.SIMPLE).stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet())
                .contains("com.HttpRequest"));
    }

    @Test
    public void nestedArgumentsAreEachADependency() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/DTO.java", "package com; public interface DTO<T> { }"),
                new ProjectFile("/com/Box.java", "package com; public class Box<T> { }"),
                new ProjectFile("/com/Order.java", "package com; public class Order { }"),
                new ProjectFile("/com/A.java", "package com; public class A implements DTO<Box<Order>> { }"));

        final Set<String> refs = referenced(model, "com.A");
        assertTrue(refs.contains("com.DTO"));
        assertTrue(refs.contains("com.Box"));
        assertTrue(refs.contains("com.Order"));
    }

    @Test
    public void aWildcardBoundIsADependencyAndABareWildcardIsNot() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/DTO.java", "package com; public interface DTO<T> { }"),
                new ProjectFile("/com/Bar.java", "package com; public class Bar { }"),
                new ProjectFile("/com/Bounded.java",
                        "package com; public class Bounded implements DTO<Bar> { }"),
                new ProjectFile("/com/Unbounded.java",
                        "package com; public class Unbounded implements DTO<?> { }"));

        assertTrue(referenced(model, "com.Bounded").contains("com.Bar"));
        assertEquals(Set.of("com.DTO"), referenced(model, "com.Unbounded"));
    }

    /**
     * The near miss. A type variable is not a type, and resolving one through the current-package
     * fallback would invent a component named after it -- a fact-shaped name for something that
     * does not exist, which is worse than the missing edge this change is closing.
     */
    @Test
    public void aTypeVariableIsNotRecordedAsADependency() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/Holder.java", "package com; public interface Holder<T> { }"),
                new ProjectFile("/com/Box.java", "package com; public class Box<T> implements Holder<T> { }"));

        assertEquals(Set.of("com.Holder"), referenced(model, "com.Box"));
        assertFalse(model.containsComponent("com.T"));
    }

    @Test
    public void anEnclosingTypeSVariableIsNotRecordedEither() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/Holder.java", "package com; public interface Holder<T> { }"),
                new ProjectFile("/com/Outer.java",
                        "package com; public class Outer<E> {\n"
                        + "  class Inner implements Holder<E> { }\n"
                        + "}"));

        assertFalse(referenced(model, "com.Outer.Inner").contains("com.E"));
        assertTrue(referenced(model, "com.Outer.Inner").contains("com.Holder"));
    }

    @Test
    public void aRecordSImplementedTypeSArgumentIsADependency() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/DTO.java", "package com; public interface DTO<T> { }"),
                new ProjectFile("/com/Order.java", "package com; public class Order { }"),
                new ProjectFile("/com/A.java", "package com; public record A(int x) implements DTO<Order> { }"));

        assertTrue(referenced(model, "com.A").contains("com.DTO"));
        assertTrue(referenced(model, "com.A").contains("com.Order"));
    }

    /**
     * A raw supertype has no arguments, and must gain no references it did not have.
     */
    @Test
    public void aRawSupertypeGainsNothing() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/Iface.java", "package com; public interface Iface { }"),
                new ProjectFile("/com/A.java", "package com; public class A implements Iface { }"));

        assertEquals(Set.of("com.Iface"), referenced(model, "com.A"));
    }
}
