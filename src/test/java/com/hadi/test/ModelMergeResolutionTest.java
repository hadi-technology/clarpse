package com.hadi.test;

import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import com.hadi.clarpse.sourcemodel.Package;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Merging models settles the references neither compile could: a reference whose target the merged
 * model holds is internal, whichever model it came from, and nothing else changes state.
 */
public class ModelMergeResolutionTest {

    private static Component type(final String pkg, final String name, final String sourceFile) {
        final Component component = new Component();
        component.setPkg(new Package(pkg, pkg));
        component.setComponentName(name);
        component.setName(name);
        component.setComponentType(ComponentType.CLASS);
        component.setSourceFilePath(sourceFile);
        return component;
    }

    private static ComponentReference external(final String target) {
        final SimpleTypeReference reference = new SimpleTypeReference(target);
        reference.setExternal(true);
        return reference;
    }

    private static ComponentReference notLoaded(final String target) {
        final SimpleTypeReference reference = new SimpleTypeReference(target);
        reference.setNotLoaded(true);
        return reference;
    }

    private static Set<String> names(final Set<ComponentReference> references) {
        return references.stream().map(ComponentReference::invokedComponent).collect(Collectors.toSet());
    }

    private static OOPSourceCodeModel javaModel() {
        final OOPSourceCodeModel model = new OOPSourceCodeModel();
        final Component service = type("app", "Service", "/app/Service.java");
        service.insertCmpRef(external("domain.Order"));
        service.insertCmpRef(external("java.util.List"));
        model.insertComponent(service);
        return model;
    }

    private static OOPSourceCodeModel kotlinModel() {
        final OOPSourceCodeModel model = new OOPSourceCodeModel();
        final Component order = type("domain", "Order", "/domain/Order.kt");
        order.insertCmpRef(notLoaded("app.Service"));
        order.insertCmpRef(notLoaded("app.Missing"));
        model.insertComponent(order);
        return model;
    }

    @Test
    public void referencesBetweenLanguagesBecomeInternalInEitherMergeOrder() {
        for (final boolean javaFirst : new boolean[] {true, false}) {
            final OOPSourceCodeModel merged = new OOPSourceCodeModel();
            if (javaFirst) {
                merged.merge(javaModel());
                merged.merge(kotlinModel());
            } else {
                merged.merge(kotlinModel());
                merged.merge(javaModel());
            }
            final Component service = merged.component("app.Service").orElseThrow();
            final Component order = merged.component("domain.Order").orElseThrow();
            assertEquals(Set.of("domain.Order"), names(service.internalDependencies()));
            assertEquals(Set.of("java.util.List"), names(service.externalDependencies()));
            assertEquals(Set.of("app.Service"), names(order.internalDependencies()));
            assertEquals(Set.of("app.Missing"), names(order.notLoadedDependencies()));
        }
    }

    @Test
    public void anIncomingReferenceToAComponentAlreadyHeldBecomesInternal() {
        final OOPSourceCodeModel merged = new OOPSourceCodeModel();
        final OOPSourceCodeModel first = new OOPSourceCodeModel();
        first.insertComponent(type("domain", "Order", "/domain/Order.java"));
        merged.merge(first);
        merged.merge(javaModel());

        assertTrue(names(merged.component("app.Service").orElseThrow().internalDependencies())
                .contains("domain.Order"));
    }
}
