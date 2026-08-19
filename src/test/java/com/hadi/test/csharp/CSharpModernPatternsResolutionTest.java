package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Resolution coverage for the reference shapes that dominate modern C# codebases: constructor
 * injection, generic inheritance, nested generic type arguments, fully-qualified references,
 * global usings, and C# 12 primary constructors. Each shape must produce a resolved edge to
 * the declared component, since striff's coupling and boundary detectors are starved when any
 * of them silently fail.
 */
public class CSharpModernPatternsResolutionTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/GlobalUsings.cs", """
                        global using Acme.Contracts;
                        """),
                new ProjectFile("/Contracts/IHandler.cs", """
                        namespace Acme.Contracts;
                        public interface IHandler<TCommand> {
                          void Handle(TCommand command);
                        }
                        """),
                new ProjectFile("/Contracts/ServiceBase.cs", """
                        namespace Acme.Contracts;
                        public abstract class ServiceBase<T> {
                        }
                        """),
                new ProjectFile("/Domain/Order.cs", """
                        namespace Acme.Domain;
                        public class Order {
                        }
                        """),
                new ProjectFile("/Services/OrderHandler.cs", """
                        using System.Collections.Generic;
                        using Acme.Domain;

                        namespace Acme.Services;

                        public class OrderHandler : ServiceBase<Order>, IHandler<Order> {
                          private readonly IDictionary<string, List<Order>> cache;
                          private readonly Acme.Domain.Order template = new Acme.Domain.Order();
                          public OrderHandler(IHandler<Order> inner) { }
                          public void Handle(Order command) { }
                        }
                        """),
                new ProjectFile("/Services/AuditService.cs", """
                        using Acme.Domain;

                        namespace Acme.Services;

                        public class AuditService(Order lastOrder) {
                          public Order LastOrder => lastOrder;
                        }
                        """)
        ).model();
    }

    private static boolean anyRefTo(final String component, final String target) {
        return model.copyOfComponent(component)
                .map(c -> c.references().stream().anyMatch(r -> r.invokedComponent().equals(target)))
                .orElse(false);
    }

    @Test
    public void genericBaseClassProducesExtensionReference() {
        assertTrue("OrderHandler should extend Acme.Contracts.ServiceBase",
                model.copyOfComponent("Acme.Services.OrderHandler").get()
                        .references(com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences.EXTENSION)
                        .contains(new TypeExtensionReference("Acme.Contracts.ServiceBase")));
    }

    @Test
    public void genericInterfaceProducesImplementationReference() {
        assertTrue("OrderHandler should implement Acme.Contracts.IHandler",
                model.copyOfComponent("Acme.Services.OrderHandler").get()
                        .references(com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences.IMPLEMENTATION)
                        .contains(new TypeImplementationReference("Acme.Contracts.IHandler")));
    }

    @Test
    public void constructorInjectionParameterResolves() {
        assertTrue("ctor injection should reference Acme.Contracts.IHandler",
                anyRefTo("Acme.Services.OrderHandler", "Acme.Contracts.IHandler"));
    }

    @Test
    public void nestedGenericTypeArgumentResolves() {
        assertTrue("IDictionary<string, List<Order>> should reference Acme.Domain.Order",
                anyRefTo("Acme.Services.OrderHandler.cache", "Acme.Domain.Order"));
    }

    @Test
    public void fullyQualifiedReferenceResolves() {
        assertTrue("Acme.Domain.Order inline qualified ref should resolve",
                anyRefTo("Acme.Services.OrderHandler.template", "Acme.Domain.Order"));
    }

    @Test
    public void globalUsingFromAnotherFileResolvesContracts() {
        // ServiceBase/IHandler are visible in OrderHandler.cs only through GlobalUsings.cs.
        assertTrue("global using should make Acme.Contracts resolvable without a local using",
                anyRefTo("Acme.Services.OrderHandler", "Acme.Contracts.ServiceBase"));
    }

    @Test
    public void primaryConstructorParameterResolves() {
        assertTrue("C# 12 primary ctor parameter should reference Acme.Domain.Order",
                anyRefTo("Acme.Services.AuditService", "Acme.Domain.Order"));
    }

    @Test
    public void arityOverloadedTypesDoNotBreakTheModel() throws Exception {
        // IFoo and IFoo<T> legally coexist; after erasure they collapse onto one component.
        // The model must stay consistent and keep the type reachable as a reference target.
        OOPSourceCodeModel arity = CSharpTestUtil.compileInline(
                new ProjectFile("/A.cs", """
                        namespace Demo;
                        public interface IFoo { }
                        public interface IFoo<T> { }
                        public class User {
                          public IFoo Plain { get; set; }
                          public IFoo<int> Generic { get; set; }
                        }
                        """)
        ).model();
        assertTrue(arity.copyOfComponent("Demo.IFoo").isPresent());
        assertTrue(arity.copyOfComponent("Demo.User.Plain").get().references().stream()
                .anyMatch(r -> r.invokedComponent().equals("Demo.IFoo")));
        assertTrue(arity.copyOfComponent("Demo.User.Generic").get().references().stream()
                .anyMatch(r -> r.invokedComponent().equals("Demo.IFoo")));
    }
}
