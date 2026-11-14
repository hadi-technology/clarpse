package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A preset another config extends, whatever it is called, supplies the {@code paths} the program needs. */
public class TypeScriptExtendedPresetTest {

    private static Map<String, String> repository(final boolean withPreset) {
        final Map<String, String> files = new LinkedHashMap<>();
        files.put("repo/web/tsconfig.json", "{ \"extends\": \"../config/typescript/react-library.json\","
                + " \"include\": [\"src\"] }");
        if (withPreset) {
            files.put("repo/config/typescript/react-library.json",
                    "{ \"compilerOptions\": { \"baseUrl\": \".\", \"paths\": { \"@lib/*\": [\"../../web/src/lib/*\"] } } }");
        }
        files.put("repo/web/src/lib/User.ts", "export class User { name: string = ''; }\n");
        files.put("repo/web/src/app/Service.ts",
                "import { User } from '@lib/User';\nexport class Service { get(): User { return new User(); } }\n");
        return files;
    }

    private static boolean serviceReferencesUser(final Map<String, String> files) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (final Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        final CompileResult result = new ClarpseProject(
                new ProjectFiles(new ByteArrayInputStream(bytes.toByteArray())), Lang.TYPESCRIPT).result();
        final Component get = result.model().components()
                .filter(component -> component.uniqueName().endsWith("Service.get()")).findFirst().orElseThrow();
        return get.references().stream().map(ComponentReference::invokedComponent)
                .anyMatch("repo.web.src.lib.User.User"::equals);
    }

    @Test
    public void aPresetNamedByExtendsSuppliesPathsFromAnArchive() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        assertTrue(serviceReferencesUser(repository(true)));
    }

    @Test
    public void withoutThePresetThePathsAreLost() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        assertFalse(serviceReferencesUser(repository(false)));
    }
}
