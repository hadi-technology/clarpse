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
        String methodName = PythonTestUtil.uniqueName(PACKAGE_PATH, "service",
                "UserService.get_user(user_id: int) : User");
        Component method = model.getComponent(methodName).orElseThrow();
        Assert.assertTrue("get_user should reference UserDao",
                containsInvokedName(method.internalDependencies(),
                        PythonTestUtil.uniqueName(PACKAGE_PATH, "dao", "UserDao")));
    }

    @Test
    public void get_user_callsAddress() {
        String methodName = PythonTestUtil.uniqueName(PACKAGE_PATH, "service",
                "UserService.get_user(user_id: int) : User");
        Component method = model.getComponent(methodName).orElseThrow();
        Assert.assertTrue("get_user should reference Address",
                containsInvokedName(method.internalDependencies(),
                        PythonTestUtil.uniqueName(PACKAGE_PATH, "types", "Address")));
    }

    @Test
    public void get_user_callsBuildName() {
        String methodName = PythonTestUtil.uniqueName(PACKAGE_PATH, "service",
                "UserService.get_user(user_id: int) : User");
        Component method = model.getComponent(methodName).orElseThrow();
        Assert.assertTrue("get_user should reference build_name",
                containsInvokedName(method.externalDependencies(),
                        "src.types.build_name"));
    }

    @Test
    public void process_hasNoBodyRefs_beyondBuiltin() {
        String methodName = PythonTestUtil.uniqueName(PACKAGE_PATH, "service",
                "UserService.process(data: str) : str");
        Component method = model.getComponent(methodName).orElseThrow();
        Assert.assertEquals("process should have no internal body refs", 0,
                countRefsContaining(method.internalDependencies(), "src."));
    }

    @Test
    public void userDaoCallsFindById_bodyRef() {
        String methodName = PythonTestUtil.uniqueName(PACKAGE_PATH, "dao",
                "UserDao.find_by_id(user_id: int) : str");
        Component method = model.getComponent(methodName).orElseThrow();
        // find_by_id has pass — no body refs
        Assert.assertEquals("find_by_id should have no internal refs", 0,
                countRefsContaining(method.internalDependencies(), "src."));
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
