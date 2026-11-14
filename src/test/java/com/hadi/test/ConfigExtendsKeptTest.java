package com.hadi.test;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A project config's relative {@code extends} keeps the files it names, in every way files arrive. */
public class ConfigExtendsKeptTest {

    private static final String TS_ENTRY = "export const a = 1;";

    private static Map<String, String> files(final String... pathAndContent) {
        final Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pathAndContent.length; i += 2) {
            map.put(pathAndContent[i], pathAndContent[i + 1]);
        }
        return map;
    }

    private static ProjectFiles fromZip(final Map<String, String> files) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (final Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry("repo/" + file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return new ProjectFiles(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static ProjectFiles inserted(final Map<String, String> files, final boolean reversed) {
        final ProjectFiles project = new ProjectFiles();
        final List<Map.Entry<String, String>> entries = new ArrayList<>(files.entrySet());
        if (reversed) {
            Collections.reverse(entries);
        }
        for (final Map.Entry<String, String> file : entries) {
            project.insertFile(new ProjectFile("/repo/" + file.getKey(), file.getValue()));
        }
        return project;
    }

    private static Set<String> configs(final ProjectFiles project) {
        final Set<String> paths = new TreeSet<>();
        project.configFiles().forEach(file -> paths.add(file.path().replaceFirst("^/", "")));
        return paths;
    }

    private static void inEveryWay(final Map<String, String> files, final Set<String> expected) throws Exception {
        assertEquals("zip", expected, configs(fromZip(files)));
        assertEquals("inserted in order", expected, configs(inserted(files, false)));
        assertEquals("inserted in reverse", expected, configs(inserted(files, true)));
    }

    @Test
    public void aPresetTheConfigNamesIsKeptWhateverItIsCalled() throws Exception {
        inEveryWay(files(
                "web/tsconfig.json", "{ \"extends\": \"../config/react-library.json\" }",
                "config/react-library.json", "{ \"compilerOptions\": { \"paths\": { \"@/*\": [\"./src/*\"] } } }",
                "config/unrelated.json", "{}",
                "web/src/a.ts", TS_ENTRY),
                Set.of("repo/web/tsconfig.json", "repo/config/react-library.json"));
    }

    @Test
    public void aTargetWrittenWithoutTheJsonSuffixIsFoundAndCommentsAreTolerated() throws Exception {
        inEveryWay(files(
                "tsconfig.json", "{ // base\n \"extends\": \"./presets/base\", }",
                "presets/base.json", "{}"),
                Set.of("repo/tsconfig.json", "repo/presets/base.json"));
    }

    @Test
    public void aChainIsFollowedThroughEveryLink() throws Exception {
        inEveryWay(files(
                "app/tsconfig.json", "{ \"extends\": \"../cfg/a.json\" }",
                "cfg/a.json", "{ \"extends\": \"./deep/b.json\" }",
                "cfg/deep/b.json", "{ \"extends\": \"../../root/c\" }",
                "root/c.json", "{}",
                "cfg/other.json", "{}"),
                Set.of("repo/app/tsconfig.json", "repo/cfg/a.json", "repo/cfg/deep/b.json", "repo/root/c.json"));
    }

    @Test
    public void everyEntryOfAnArrayIsKept() throws Exception {
        inEveryWay(files(
                "tsconfig.json", "{ \"extends\": [\"./one.json\", \"./two\", \"@scope/pkg/tsconfig\", 7] }",
                "one.json", "{}",
                "two.json", "{}"),
                Set.of("repo/tsconfig.json", "repo/one.json", "repo/two.json"));
    }

    @Test
    public void aPackageNameIsNotFollowed() throws Exception {
        inEveryWay(files(
                "tsconfig.json", "{ \"extends\": \"@acme/config/base.json\" }",
                "@acme/config/base.json", "{}",
                "node_modules/@acme/config/base.json", "{}"),
                Set.of("repo/tsconfig.json"));
    }

    @Test
    public void aMissingTargetIsNotAnError() throws Exception {
        inEveryWay(files(
                "tsconfig.json", "{ \"extends\": \"./absent.json\" }",
                "a.ts", TS_ENTRY),
                Set.of("repo/tsconfig.json"));
    }

    @Test
    public void aTargetThatClimbsOutOfTheRepositoryIsNotFollowed() throws Exception {
        inEveryWay(files("tsconfig.json", "{ \"extends\": \"../../outside.json\" }"),
                Set.of("repo/tsconfig.json"));
    }

    @Test
    public void aCycleEndsWhereItReturns() throws Exception {
        inEveryWay(files(
                "tsconfig.json", "{ \"extends\": \"./a.json\" }",
                "a.json", "{ \"extends\": \"./b.json\" }",
                "b.json", "{ \"extends\": \"./a.json\" }",
                "self.json", "{ \"extends\": \"./self.json\" }"),
                Set.of("repo/tsconfig.json", "repo/a.json", "repo/b.json"));
    }

    @Test
    public void aFileNoConfigNamesIsNotKeptAndNeitherIsATargetOfANonConfig() throws Exception {
        inEveryWay(files(
                "settings.json", "{ \"extends\": \"./hidden.json\" }",
                "hidden.json", "{}"),
                Set.of());
    }

    @Test
    public void aChainLongerThanTheBoundIsCutNotCrashed() throws Exception {
        final Map<String, String> chain = new LinkedHashMap<>();
        chain.put("tsconfig.json", "{ \"extends\": \"./c0.json\" }");
        for (int i = 0; i < 40; i++) {
            chain.put("c" + i + ".json", "{ \"extends\": \"./c" + (i + 1) + ".json\" }");
        }
        final Set<String> kept = configs(fromZip(chain));
        assertTrue(kept.contains("repo/c0.json"));
        assertFalse(kept.contains("repo/c39.json"));
        assertEquals(kept, configs(inserted(chain, false)));
    }
}
