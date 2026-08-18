package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Same cross-file scenarios as {@link CSharpCrossFileResolutionTest}, but written the way
 * real C# files are laid out: using directives at the top of the file BEFORE the namespace
 * declaration (both file-scoped and block-scoped forms), plus a generic type reference.
 */
public class CSharpRealisticLayoutResolutionTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Services/OrderService.cs", """
                        using Acme.Data;
                        using Acme.Logging;

                        namespace Acme.Services;

                        public class OrderService {
                          private readonly ILog<OrderService> log;
                          public OrderRepo Repo { get; set; }
                          public void Submit() { Repo.Persist(); }
                        }
                        """),
                new ProjectFile("/Legacy/ReportService.cs", """
                        using Acme.Data;

                        namespace Acme.Legacy
                        {
                            public class ReportService
                            {
                                public OrderRepo Source { get; set; }
                            }
                        }
                        """),
                new ProjectFile("/Data/OrderRepo.cs", """
                        namespace Acme.Data;
                        public class OrderRepo {
                          public void Persist() {}
                        }
                        """),
                new ProjectFile("/Logging/ILog.cs", """
                        namespace Acme.Logging;
                        public interface ILog<T> {
                        }
                        """)
        ).model();
    }

    private static boolean componentReferences(final String component, final String target) {
        return model.copyOfComponent(component).get().references().stream()
                .anyMatch(ref -> ref.invokedComponent().equals(target));
    }

    @Test
    public void usingsBeforeFileScopedNamespaceResolveCrossNamespaceReferences() {
        assertTrue("OrderService should reference Acme.Data.OrderRepo",
                componentReferences("Acme.Services.OrderService", "Acme.Data.OrderRepo"));
    }

    @Test
    public void usingsBeforeBlockScopedNamespaceResolveCrossNamespaceReferences() {
        assertTrue("ReportService should reference Acme.Data.OrderRepo",
                componentReferences("Acme.Legacy.ReportService", "Acme.Data.OrderRepo"));
    }

    @Test
    public void genericInterfaceReferenceResolvesToItsDeclaration() {
        assertTrue("OrderService should reference Acme.Logging.ILog",
                componentReferences("Acme.Services.OrderService", "Acme.Logging.ILog"));
    }
}
