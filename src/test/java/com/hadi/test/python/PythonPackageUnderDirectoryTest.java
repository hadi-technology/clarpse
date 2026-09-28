package com.hadi.test.python;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * A repository that keeps its package under a directory of its own imports it by the package's
 * name, as the code does when it runs with that directory on the import path. The project names
 * the directory in no config, and the package itself shows where imports are written from.
 */
public class PythonPackageUnderDirectoryTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = PythonTestUtil.compileFixture("package-under-directory");
        model = result.model();
        Assert.assertTrue(result.failures().toString(), result.failures().isEmpty());
    }

    @Test
    public void anImportWrittenFromThePackagesOwnRootResolves() {
        Component store = model.copyOfComponent(
                PythonTestUtil.uniqueName("backend/app/store", "s3", "S3Store")).orElseThrow();

        Assert.assertTrue(targets(store).toString(), targets(store).contains(
                PythonTestUtil.uniqueName("backend/app/store", "base", "FileStore")));
    }

    @Test
    public void aModuleImportedByItsDottedNameResolvesFromOutsideThePackage() {
        Set<String> used = model.components()
                .filter(component -> component.uniqueName().startsWith(
                        PythonTestUtil.uniqueName("tools", "export", "Exporter")))
                .flatMap(component -> component.references().stream())
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());

        Assert.assertTrue(used.toString(), used.contains(
                PythonTestUtil.uniqueName("backend/app/store", "base", "FileStore")));
    }

    private static Set<String> targets(final Component component) {
        return component.references().stream()
                .map(ComponentReference::invokedComponent).collect(Collectors.toSet());
    }
}
