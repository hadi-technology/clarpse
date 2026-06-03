package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class PythonMethodBodyRefsTest {

    private static final String FIXTURE = "method-body-refs";
    private static final String PACKAGE_PATH = "src";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture(FIXTURE);
        model = result.model();
        Assert.assertTrue(result.failures().isEmpty());
    }

    @Test
    public void get_user_callsUserDao() {
        Component method = getMethod("UserService.get_user(user_id: int) : User");
        Assert.assertTrue("get_user should reference UserDao",
                containsInvokedName(method.internalDependencies(),
                        PythonTestUtil.uniqueName(PACKAGE_PATH, "dao", "UserDao")));
    }

    @Test
    public void get_user_callsAddress() {
        Component method = getMethod("UserService.get_user(user_id: int) : User");
        Assert.assertTrue("get_user should reference Address",
                containsInvokedName(method.internalDependencies(),
                        PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "Address")));
    }

    @Test
    public void get_user_callsBuildName() {
        Component method = getMethod("UserService.get_user(user_id: int) : User");
        Assert.assertTrue("get_user should reference build_name",
                containsInvokedName(method.externalDependencies(),
                        "src.types.build_name"));
    }

    @Test
    public void get_user_ignoresLocalVariableCalls() {
        Component method = getMethod("UserService.get_user(user_id: int) : User");
        // dao = UserDao(); dao.find_by_id(...) — 'dao' is a local variable, not an import
        Assert.assertFalse("dao.find_by_id should NOT resolve as src.dao.find_by_id",
                containsInvokedName(method.externalDependencies(), "src.dao.find_by_id"));
    }

    @Test
    public void process_hasNoBodyRefs_beyondBuiltin() {
        Component method = getMethod("UserService.process(data: str) : str");
        Assert.assertEquals("process should have no internal body refs", 0,
                countRefsContaining(method.internalDependencies(), "src."));
    }

    private static Component getMethod(String symbolPath) {
        return model.getComponent(PythonTestUtil.uniqueName(PACKAGE_PATH, "service", symbolPath))
                .orElseThrow();
    }

    private static boolean containsInvokedName(final Iterable<ComponentReference> refs,
                                               final String invokedName) {
        for (ComponentReference ref : refs) {
            if (invokedName.equals(ref.invokedComponent())) {
                return true;
            }
        }
        return false;
    }

    private static long countRefsContaining(final Iterable<ComponentReference> refs, final String substring) {
        long count = 0;
        for (ComponentReference ref : refs) {
            if (ref.invokedComponent().contains(substring)) {
                count++;
            }
        }
        return count;
    }
}
