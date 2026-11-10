package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A namespace a project imports implicitly decides which of two same-named types an unqualified name
 * means, exactly as a using directive in the file would.
 */
public class CSharpImplicitUsingsResolutionTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Directory.Build.props",
                        "<Project><PropertyGroup><ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>"),
                new ProjectFile("/src/Lib.csproj", "<Project Sdk=\"Microsoft.NET.Sdk\">"
                        + "<ItemGroup><Using Include=\"Domain.Models\" /></ItemGroup></Project>"),
                new ProjectFile("/src/Types.cs", """
                        namespace Domain.Models { public class Entity { } }
                        namespace Other { public class Entity { } }
                        namespace App { public class Svc { public Entity Get() { return null; } } }
                        """)).model();
    }

    private static boolean references(final String from, final String to) {
        return model.copyOfComponent(from).orElseThrow().references().stream()
                .map(ComponentReference::invokedComponent).anyMatch(to::equals);
    }

    @Test
    public void implicitlyImportedNamespaceDecidesBetweenSameNamedTypes() {
        assertTrue(references("App.Svc.Get()", "Domain.Models.Entity"));
        assertFalse(references("App.Svc.Get()", "Other.Entity"));
    }
}
