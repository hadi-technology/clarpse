package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * An import written inside a function binds its name in that function's scope, as Python does: the
 * references in that function resolve through it, and the references in every other function do not.
 */
public class PythonFunctionLocalImportTest {

    private static final String FIXTURE = "function-local-imports";
    private static final String SERVICE = "app.service.Service";
    private static final String ORDER = "app.models.Order";
    private static final String INVOICE = "app.models.Invoice";
    private static final String RECEIPT = "app.models.Receipt";
    private static final String LEDGER = "app.models.Ledger";
    private static final String REFUND = "app.models.Refund";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void aMethodLocalImportResolvesTheMethodsReferences() {
        assertInternal(method(SERVICE, "make_order"), ORDER);
    }

    @Test
    public void aModuleLevelFunctionLocalImportResolvesItsReferences() {
        assertInternal(method("app.service", "build_order"), ORDER);
    }

    /** The module-level import still resolves alongside. */
    @Test
    public void aModuleLevelImportStillResolves() {
        assertInternal(method(SERVICE, "make_invoice"), INVOICE);
    }

    /** Inside the function, its own import of a name hides the module-level import of that name. */
    @Test
    public void aFunctionLocalImportHidesTheModuleLevelBinding() {
        final Component method = method(SERVICE, "shadowing_import");
        assertInternal(method, "app.other.Invoice");
        assertNoReferenceTo(method, INVOICE);
    }

    @Test
    public void aFunctionLocalImportDoesNotReachAnotherFunction() {
        assertNoReferenceTo(method(SERVICE, "order_without_import"), ORDER);
    }

    @Test
    public void aRelativeFunctionLocalImportResolves() {
        assertInternal(method(SERVICE, "relative_import"), REFUND);
    }

    @Test
    public void anImportInsideIfAndTryBlocksResolves() {
        assertInternal(method(SERVICE, "inside_nested_blocks"), RECEIPT);
    }

    @Test
    public void aFunctionLocalModuleImportResolvesMembersThroughIt() {
        assertInternal(method(SERVICE, "module_import"), LEDGER);
    }

    @Test
    public void anAnnotatedLocalResolvesThroughAFunctionLocalImport() {
        final Component local = component(method(SERVICE, "annotated_local").uniqueName() + ".order");
        assertInternal(local, ORDER);
    }

    /** A name the function also assigns may hold something else wherever it is used. */
    @Test
    public void anImportedNameTheFunctionReassignsIsNotResolved() {
        assertNoReferenceTo(method(SERVICE, "rebound_import"), LEDGER);
    }

    /** A nested function's import is in the nested function's scope, not the enclosing one's. */
    @Test
    public void anImportInANestedFunctionDoesNotReachTheEnclosingFunction() {
        assertNoReferenceTo(method(SERVICE, "import_in_nested_function"), RECEIPT);
    }

    /** The class carries its file's module-level imports, not the imports its methods perform. */
    @Test
    public void functionLocalImportsAreNotTheFilesImports() {
        final Set<String> imports = new HashSet<>(component(SERVICE).imports());
        Assert.assertTrue(imports.toString(), imports.contains(INVOICE));
        Assert.assertFalse(imports.toString(), imports.contains(ORDER));
    }

    private static void assertInternal(final Component component, final String target) {
        final Set<String> internal = invoked(component.internalDependencies());
        Assert.assertTrue(component.uniqueName() + " should reference " + target + ", has " + internal,
                internal.contains(target));
    }

    private static void assertNoReferenceTo(final Component component, final String target) {
        final Set<String> all = invoked(component.references());
        Assert.assertFalse(component.uniqueName() + " should not reference " + target + ", has " + all,
                all.contains(target));
    }

    private static Component method(final String owner, final String name) {
        return model.components()
                .filter(cmp -> name.equals(cmp.name()) && cmp.uniqueName().startsWith(owner + "." + name + "("))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no function " + owner + "." + name));
    }

    private static Component component(final String uniqueName) {
        return model.copyOfComponent(uniqueName)
                .orElseThrow(() -> new AssertionError("no component " + uniqueName));
    }

    private static Set<String> invoked(final Iterable<ComponentReference> refs) {
        final Set<String> names = new HashSet<>();
        refs.forEach(ref -> names.add(ref.invokedComponent()));
        return names;
    }
}
