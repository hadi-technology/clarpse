package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The invariant that a reference names a type rather than a type expression holds for every
 * language, not only the one the defect was found in. Java, C#, TypeScript and Python all write
 * type arguments, and all four resolve a type and then hand the resolved name on unchanged, so
 * the shape is available to all of them wherever a resolution misses.
 */
public class CrossLanguageGenericReferenceTest {

    private static Set<String> allReferenceNames(final OOPSourceCodeModel model) {
        return model.components().flatMap(c -> c.references().stream())
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());
    }

    private static Set<String> referenced(final OOPSourceCodeModel model, final String component,
                                          final TypeReferences kind) {
        return model.copyOfComponent(component).orElseThrow().references(kind).stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());
    }

    private static void assertNoTypeExpressions(final OOPSourceCodeModel model) {
        final Set<String> offending = allReferenceNames(model).stream()
                .filter(name -> name.contains("<") || name.contains("[") || name.endsWith("..."))
                .collect(Collectors.toSet());
        assertEquals("references naming a type expression rather than a type",
                Set.of(), offending);
    }

    private static OOPSourceCodeModel fixture(final String path, final Lang lang) throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final CompileResult result = new ClarpseProject(
                new ProjectFiles(Paths.get(path).toAbsolutePath().toString()), lang).result();
        assertTrue(result.failures().isEmpty());
        return result.model();
    }

    @Test
    public void csharpGenericHeritageAndArraysNameTypes() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/Com/IDto.cs", "namespace Com { public interface IDto<T> { } }"));
        files.insertFile(new ProjectFile("/Com/Base.cs", "namespace Com { public class Base<T> { } }"));
        files.insertFile(new ProjectFile("/Com/Bar.cs", "namespace Com { public class Bar { } }"));
        files.insertFile(new ProjectFile("/Com/Foo.cs", "namespace Com { public class Foo<T> { } }"));
        files.insertFile(new ProjectFile("/Com/Impl.cs",
                "namespace Com {\n"
              + " public class Impl : Base<Bar>, IDto<Bar> {\n"
              + "   private Foo<Bar> parameterised;\n"
              + "   private Bar raw;\n"
              + "   private Foo<Bar>[] arr;\n"
              + " }\n"
              + "}"));
        final OOPSourceCodeModel model = new ClarpseProject(files, Lang.CSHARP).result().model();

        assertTrue(referenced(model, "Com.Impl", TypeReferences.EXTENSION).contains("Com.Base"));
        assertTrue(referenced(model, "Com.Impl", TypeReferences.IMPLEMENTATION).contains("Com.IDto"));
        assertNoTypeExpressions(model);
    }

    /**
     * TypeScript is where the shape survives outside heritage clauses: a heritage clause resolves
     * to a symbol and is named after it, but an array of a parameterised type has no symbol of its
     * own that the assembler reaches for, so the reference was named {@code Foo<Bar>[]} -- the type
     * rendered back as source.
     */
    @Test
    public void typescriptArraysOfParameterisedTypesNameTypes() throws Exception {
        final OOPSourceCodeModel model =
                fixture("src/test/resources/typescript/generic-references", Lang.TYPESCRIPT);

        assertTrue(referenced(model, "src.impl.Impl", TypeReferences.EXTENSION).contains("src.types.Base"));
        assertTrue(referenced(model, "src.impl.Impl", TypeReferences.IMPLEMENTATION).contains("src.types.Dto"));
        assertTrue(allReferenceNames(model).contains("src.types.Foo"));
        assertNoTypeExpressions(model);
    }

    @Test
    public void pythonSubscriptedBasesNameTypes() throws Exception {
        final OOPSourceCodeModel model =
                fixture("src/test/resources/python/generic-references", Lang.PYTHON);

        assertTrue(referenced(model, "src.impl.Impl", TypeReferences.EXTENSION).contains("src.types.Base"));
        assertTrue(referenced(model, "src.impl.Impl", TypeReferences.EXTENSION).contains("src.types.Dto"));
        assertTrue(allReferenceNames(model).contains("src.types.Foo"));
        assertNoTypeExpressions(model);
    }
}
