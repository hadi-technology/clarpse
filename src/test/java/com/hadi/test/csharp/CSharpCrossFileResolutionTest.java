package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Cross-FILE reference resolution: the dominant shape in real repositories, where a class
 * references types declared in other files, both in the same namespace (no using directive
 * needed in C#) and in a different namespace (via a using directive).
 */
public class CSharpCrossFileResolutionTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Services/OrderService.cs", """
                        namespace Acme.Services;
                        using Acme.Data;
                        public class OrderService {
                          private readonly Validator validator = new Validator();
                          public OrderRepo Repo { get; set; }
                          public void Submit() { validator.Check(); Repo.Persist(); }
                        }
                        """),
                new ProjectFile("/Services/Validator.cs", """
                        namespace Acme.Services;
                        public class Validator {
                          public void Check() {}
                        }
                        """),
                new ProjectFile("/Data/OrderRepo.cs", """
                        namespace Acme.Data;
                        public class OrderRepo {
                          public void Persist() {}
                        }
                        """)
        ).model();
    }

    private static boolean componentReferences(final String component, final String target) {
        return model.copyOfComponent(component).get().references().stream()
                .anyMatch(ref -> ref.invokedComponent().equals(target));
    }

    @Test
    public void sameNamespaceCrossFileReferenceIsResolved() {
        // Validator lives in another file but the same namespace: no using directive exists
        // or is required, which is the single most common reference shape in C# codebases.
        assertTrue("OrderService should reference Acme.Services.Validator",
                componentReferences("Acme.Services.OrderService", "Acme.Services.Validator"));
    }

    @Test
    public void crossNamespaceCrossFileReferenceIsResolved() {
        assertTrue("OrderService should reference Acme.Data.OrderRepo",
                componentReferences("Acme.Services.OrderService", "Acme.Data.OrderRepo"));
    }
}
