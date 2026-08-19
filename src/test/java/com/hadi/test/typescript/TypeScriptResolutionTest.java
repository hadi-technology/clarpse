package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

public class TypeScriptResolutionTest {

    @Test
    public void resolvesExtendsImplementsAndTypesThroughPathsAndBarrels() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture("paths");

        Component service = result.model().copyOfComponent("src.app.service.Service").orElseThrow();
        Set<Class<? extends ComponentReference>> refTypes = service.references().stream()
                .map(ComponentReference::getClass)
                .collect(Collectors.toSet());
        assertTrue(refTypes.contains(TypeExtensionReference.class));
        assertTrue(refTypes.contains(TypeImplementationReference.class));
        assertTrue(service.references().stream().anyMatch(ref ->
                ref instanceof TypeExtensionReference && ref.invokedComponent().equals("src.app.base.Base")));
        assertTrue(service.references().stream().anyMatch(ref ->
                ref instanceof TypeImplementationReference && ref.invokedComponent().equals("src.app.service.Repo")));

        Component getMethod = result.model().copyOfComponent("src.app.service.Service.get()").orElseThrow();
        assertTrue(getMethod.references().stream().anyMatch(ref ->
                ref instanceof SimpleTypeReference && ref.invokedComponent().equals("src.lib.user.User")));

        Component userParam = result.model().components()
                .filter(component -> "user".equals(component.name()))
                .filter(component -> component.uniqueName().contains("Service.constructor"))
                .findFirst()
                .orElseThrow();
        assertTrue(userParam.references().stream().anyMatch(ref ->
                ref instanceof SimpleTypeReference && ref.invokedComponent().equals("src.lib.user.User")));
    }
}
