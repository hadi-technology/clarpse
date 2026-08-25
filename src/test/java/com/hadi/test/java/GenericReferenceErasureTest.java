package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A reference names a type, so what is recorded is the name of a type and not the expression the
 * source wrote it as. A reference recorded as {@code DTO<HttpRequest>} matches no component, and
 * the edge it stands for is absent from the model without being absent from the code.
 */
public class GenericReferenceErasureTest {

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

    private static Set<String> referenced(final OOPSourceCodeModel model, final String component,
                                          final TypeReferences kind) {
        return model.copyOfComponent(component).orElseThrow().references(kind).stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());
    }

    /**
     * The reproduction: implementers of a parameterised interface are invisible to it. The count is
     * what makes it worth a test of its own -- an interface with implementers reporting no incoming
     * references at all is a well-formed model that is simply missing every edge.
     */
    @Test
    public void implementersOfAParameterisedInterfaceAreVisibleToIt() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/DTO.java", "package com; public interface DTO<T> { }"),
                new ProjectFile("/com/A.java", "package com; public class A implements DTO<String> { }"),
                new ProjectFile("/com/B.java", "package com; public class B implements DTO<Integer> { }"));

        assertTrue(referenced(model, "com.A", TypeReferences.IMPLEMENTATION).contains("com.DTO"));
        assertTrue(referenced(model, "com.B", TypeReferences.IMPLEMENTATION).contains("com.DTO"));

        final long incoming = model.components()
                .filter(c -> c.references().stream()
                        .anyMatch(r -> "com.DTO".equals(r.invokedComponent())))
                .count();
        assertEquals(2, incoming);
    }

    @Test
    public void aParameterisedSupertypeIsNotExternalToItsOwnProject() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/DTO.java", "package com; public interface DTO<T> { }"),
                new ProjectFile("/com/A.java", "package com; public class A implements DTO<String> { }"));

        final Component a = model.copyOfComponent("com.A").orElseThrow();
        assertTrue(a.references().stream()
                .filter(r -> "com.DTO".equals(r.invokedComponent()))
                .noneMatch(ComponentReference::isExternal));
        assertTrue(a.externalDependencies().stream()
                .map(ComponentReference::invokedComponent)
                .noneMatch(name -> name.startsWith("com.DTO")));
    }

    /**
     * Extension references and declared-type references, not only implementation references.
     */
    @Test
    public void extensionAndDeclaredTypeReferencesEraseToo() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/Base.java", "package com; public class Base<T> { }"),
                new ProjectFile("/com/Bar.java", "package com; public class Bar { }"),
                new ProjectFile("/com/Foo.java", "package com; public class Foo<T> { }"),
                new ProjectFile("/com/A.java",
                        "package com; public class A extends Base<Bar> { private Foo<Bar> held; }"));

        assertTrue(referenced(model, "com.A", TypeReferences.EXTENSION).contains("com.Base"));
        assertTrue(referenced(model, "com.A.held").contains("com.Foo"));
    }

    /**
     * The mirror problem, and the direction that over-counts rather than under-counts: one type
     * mentioned in two spellings was two referenced types, because two strings are two strings.
     * Pinned at a parameter site, which is where the two spellings survived into the model as
     * {@code com.Foo} and {@code com.Foo<Bar>}.
     */
    @Test
    public void aParameterisedAndARawMentionOfOneTypeAreOneReferencedType() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/Foo.java", "package com; public class Foo<T> { }"),
                new ProjectFile("/com/Bar.java", "package com; public class Bar { }"),
                new ProjectFile("/com/A.java",
                        "package com; public class A {\n"
                        + "  public void m(Foo<Bar> parameterised, Foo raw, Foo<?> wildcard) { }\n"
                        + "}"));

        final Set<String> distinct = referenced(model, "com.A.m(Foo<Bar>, Foo, Foo<?>)").stream()
                .filter(name -> name.startsWith("com.Foo")).collect(Collectors.toSet());
        assertEquals(Set.of("com.Foo"), distinct);
    }

    @Test
    public void nestedTypeArgumentsEraseToTheOutermostType() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/Foo.java", "package com; public class Foo { }"),
                new ProjectFile("/com/A.java",
                        "package com; import java.util.List; import java.util.Map;\n"
                        + "public class A { private Map<String, List<Foo>> nested; }"));

        final Set<String> refs = referenced(model, "com.A.nested");
        assertTrue(refs.contains("java.util.Map"));
        assertFalse(refs.stream().anyMatch(name -> name.contains("<")));
    }

    @Test
    public void wildcardsEraseLikeAnyOtherArgument() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/Foo.java", "package com; public class Foo<T> { }"),
                new ProjectFile("/com/Bar.java", "package com; public class Bar { }"),
                new ProjectFile("/com/A.java",
                        "package com; public class A {\n"
                        + "  private Foo<?> unbounded;\n"
                        + "  private Foo<? extends Bar> bounded;\n"
                        + "}"));

        assertTrue(referenced(model, "com.A.unbounded").contains("com.Foo"));
        assertTrue(referenced(model, "com.A.bounded").contains("com.Foo"));
    }

    /**
     * Arrays and varargs of parameterised types -- and of raw ones, which were recorded as
     * {@code com.Bar[]} and matched nothing either. A parameter component's whole purpose is to
     * name its declared type, so this is the site where the loss is total.
     */
    @Test
    public void arraysAndVarargsNameTheirElementType() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/Bar.java", "package com; public class Bar { }"),
                new ProjectFile("/com/Foo.java", "package com; public class Foo<T> { }"),
                new ProjectFile("/com/A.java",
                        "package com; public class A {\n"
                        + "  public void rawArray(Bar[] xs) { }\n"
                        + "  public void parameterisedArray(Foo<Bar>[] xs) { }\n"
                        + "  public void parameterisedVarargs(Foo<Bar>... xs) { }\n"
                        + "}"));

        assertEquals(Set.of("com.Bar"), referenced(model, "com.A.rawArray(Bar[]).xs"));
        assertEquals(Set.of("com.Foo"), referenced(model, "com.A.parameterisedArray(Foo<Bar>[]).xs"));
        assertEquals(Set.of("com.Foo"), referenced(model, "com.A.parameterisedVarargs(Foo<Bar>).xs"));
    }

    /**
     * The near miss that erasure at the wrong layer would pass. The type arguments have to come off
     * before the name is <em>resolved</em>, not after: {@code DTO<String>} is absent from an import
     * map holding {@code DTO}, so every lookup misses and the fallback invents a current-package
     * name. Erasing only what is recorded turns {@code com.DTO<String>} into {@code com.DTO} -- a
     * component that does not exist, in a package the type was never in, now looking plausible.
     */
    @Test
    public void aParameterisedTypeImportedFromAnotherPackageKeepsThatPackage() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/other/DTO.java", "package other; public interface DTO<T> { }"),
                new ProjectFile("/com/A.java",
                        "package com; import other.DTO; public class A implements DTO<String> { }"));

        assertEquals(Set.of("other.DTO"), referenced(model, "com.A", TypeReferences.IMPLEMENTATION));
        assertFalse(model.containsComponent("com.DTO"));
    }

    /**
     * The other near miss. Erasing too eagerly passes every test that asks whether the type
     * arguments are gone, so a raw reference has to be pinned unchanged, and a real dependency has
     * to still be recorded rather than erased away with the arguments.
     */
    @Test
    public void rawReferencesAndOrdinaryDependenciesAreUntouched() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/Bar.java", "package com; public class Bar { }"),
                new ProjectFile("/com/Iface.java", "package com; public interface Iface { }"),
                new ProjectFile("/com/A.java",
                        "package com; public class A implements Iface { private Bar plain; }"));

        assertEquals(Set.of("com.Iface"), referenced(model, "com.A", TypeReferences.IMPLEMENTATION));
        assertEquals(Set.of("com.Bar"), referenced(model, "com.A.plain"));
    }

    @Test
    public void noReferenceInTheModelNamesATypeExpression() throws Exception {
        final OOPSourceCodeModel model = model(
                new ProjectFile("/com/Bar.java", "package com; public class Bar { }"),
                new ProjectFile("/com/Foo.java", "package com; public class Foo<T> { }"),
                new ProjectFile("/com/DTO.java", "package com; public interface DTO<T> { }"),
                new ProjectFile("/com/A.java",
                        "package com; import java.util.List;\n"
                        + "public class A implements DTO<Foo<Bar>> {\n"
                        + "  private List<Foo<Bar>> nested;\n"
                        + "  private Foo<Bar>[] arr;\n"
                        + "  public void m(Bar... xs) { }\n"
                        + "}"));

        assertTrue(model.components()
                .flatMap(c -> c.references().stream())
                .map(ComponentReference::invokedComponent)
                .noneMatch(name -> name.contains("<") || name.contains("[") || name.endsWith("...")));
    }
}
