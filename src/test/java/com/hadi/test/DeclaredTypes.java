package com.hadi.test;

import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Assert;

import java.util.List;
import java.util.stream.Collectors;

/** Reads the declared type of the components of a parsed fixture, for the per-language tests. */
public final class DeclaredTypes {

    private DeclaredTypes() {
    }

    /** The component of the owner with the simple name, as a method (any signature) or a field. */
    public static Component member(final OOPSourceCodeModel model, final String owner, final String name) {
        final List<Component> found = model.components()
                .filter(c -> c.componentType() != OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT
                        && c.componentType() != OOPSourceModelConstants.ComponentType.LOCAL)
                .filter(c -> c.uniqueName().matches(java.util.regex.Pattern.quote(owner + "." + name)
                        + "(\\([^)]*\\))?( : .*)?"))
                .collect(Collectors.toList());
        Assert.assertEquals(owner + "." + name + " in " + model.components().map(Component::uniqueName)
                .collect(Collectors.toList()), 1, found.size());
        return found.get(0);
    }

    /** The parameter of the callable whose unique name starts with the given prefix. */
    public static SimpleTypeReference parameter(final OOPSourceCodeModel model, final String callablePrefix,
                                                final String name) {
        final List<Component> found = model.components()
                .filter(c -> c.componentType() == OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT
                        && c.uniqueName().startsWith(callablePrefix) && name.equals(c.name()))
                .collect(Collectors.toList());
        Assert.assertEquals(callablePrefix + " " + name, 1, found.size());
        return found.get(0).declaredType();
    }

    /** The target of the declared type of the member, or null. */
    public static String of(final OOPSourceCodeModel model, final String owner, final String name) {
        final SimpleTypeReference declared = member(model, owner, name).declaredType();
        return declared == null ? null : declared.invokedComponent();
    }

    /** Every non-null declared type of the model is one of the component's own references. */
    public static void assertContainedInReferences(final OOPSourceCodeModel model) {
        model.components().forEach(component -> {
            final SimpleTypeReference declared = component.declaredType();
            if (declared != null) {
                Assert.assertTrue(component.uniqueName() + " declares " + declared,
                        component.references().stream().anyMatch(ref -> ref.equals(declared)));
                final ComponentReference same = component.references().stream()
                        .filter(ref -> ref.equals(declared)).findFirst().orElseThrow();
                Assert.assertEquals(same.resolutionKind(), declared.resolutionKind());
                Assert.assertEquals(same.isExternal(), declared.isExternal());
            }
        });
    }
}
