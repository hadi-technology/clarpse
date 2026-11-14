package com.hadi.test;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/** Presets a project config extends follow the config and the preset through reads, updates and removals. */
public class ConfigExtendsUpdatesTest {

    private static final String LOGGER = "com.hadi.clarpse.compiler.ExtendedConfigs";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static Map<String, String> contents(final ProjectFiles project) {
        final Map<String, String> map = new TreeMap<>();
        project.configFiles().forEach(file -> map.put(file.path().replaceFirst("^/", ""), file.content()));
        return map;
    }

    private static Set<String> paths(final ProjectFiles project) {
        return new TreeSet<>(contents(project).keySet());
    }

    private static ProjectFiles zip(final Map<String, String> files) throws Exception {
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

    private static Map<String, String> repository() {
        final Map<String, String> files = new TreeMap<>();
        files.put("web/tsconfig.json", "{ \"extends\": \"../config/base.json\" }");
        files.put("config/base.json", "{ \"v\": 1, \"extends\": \"./root\" }");
        files.put("config/root.json", "{ \"r\": 1 }");
        return files;
    }

    private void write(final String relative, final String text) throws Exception {
        final Path path = folder.getRoot().toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, text);
    }

    @Test
    public void aDirectoryKeepsTheNamedPresetsAndReadsNoOtherJson() throws Exception {
        repository().forEach((path, text) -> {
            try {
                write("project/" + path, text);
            } catch (final Exception e) {
                throw new IllegalStateException(e);
            }
        });
        write("project/config/unrelated.json", "{}");
        write("project/node_modules/lib/data.json", "{}");
        write("project/node_modules/lib/package.json", "{}");
        final ProjectFiles project = new ProjectFiles(folder.getRoot().toPath().resolve("project").toString());

        final Set<String> kept = paths(project);
        assertTrue(kept.stream().anyMatch(p -> p.endsWith("project/config/base.json")));
        assertTrue(kept.stream().anyMatch(p -> p.endsWith("project/config/root.json")));
        assertFalse(kept.stream().anyMatch(p -> p.endsWith("unrelated.json")));
        assertFalse(kept.stream().anyMatch(p -> p.endsWith("node_modules/lib/data.json")));
    }

    @Test
    public void aDirectoryFollowsAChainAndEndsAtACycleAndLeavesAMissingTargetAlone() throws Exception {
        write("project/tsconfig.json", "{ \"extends\": [\"./a.json\", \"./absent\"] }");
        write("project/a.json", "{ \"extends\": \"./b.json\" }");
        write("project/b.json", "{ \"extends\": \"./a.json\" }");
        final ProjectFiles project = new ProjectFiles(folder.getRoot().toPath().resolve("project").toString());

        assertEquals(3, paths(project).size());
    }

    @Test
    public void aSymlinkedPresetPointingOutsideTheProjectIsNotReadAndTheLogSaysWhy() throws Exception {
        write("outside/secret.json", "{ \"secret\": true }");
        write("project/tsconfig.json", "{ \"extends\": \"./linked.json\" }");
        final Path link = folder.getRoot().toPath().resolve("project/linked.json");
        try {
            Files.createSymbolicLink(link, folder.getRoot().toPath().resolve("outside/secret.json"));
        } catch (final UnsupportedOperationException | java.io.IOException e) {
            assumeTrue("symlinks unavailable", false);
        }
        try (CapturedLog log = new CapturedLog(LOGGER)) {
            final ProjectFiles project = new ProjectFiles(folder.getRoot().toPath().resolve("project").toString());

            assertFalse(paths(project).stream().anyMatch(p -> p.endsWith("linked.json")));
            assertFalse(log.eventsMentioning("outside the project directory").isEmpty());
            assertTrue(log.eventsMentioning("not among the files read").isEmpty());
        }
    }

    @Test
    public void aTargetOverTheSizeBoundIsLoggedAsSkippedNeverAsAbsent() throws Exception {
        final Map<String, String> files = new TreeMap<>(repository());
        files.put("config/root.json", "{ \"pad\": \"" + "x".repeat(1_100_000) + "\" }");
        try (CapturedLog log = new CapturedLog(LOGGER)) {
            final ProjectFiles project = zip(files);

            assertFalse(paths(project).stream().anyMatch(p -> p.endsWith("config/root.json")));
            assertFalse(log.eventsMentioning("size bound").isEmpty());
            assertTrue(log.eventsMentioning("not among the files read").isEmpty());
        }
        try (CapturedLog log = new CapturedLog(LOGGER)) {
            final Map<String, String> missing = new TreeMap<>(repository());
            missing.remove("config/root.json");
            zip(missing);

            assertFalse(log.eventsMentioning("not among the files read").isEmpty());
        }
    }

    @Test
    public void aChangedPresetReplacesTheOldTextAndItsChain() throws Exception {
        final ProjectFiles project = zip(repository());
        assertTrue(contents(project).get("repo/config/base.json").contains("\"v\": 1"));

        project.insertFile(new ProjectFile("/repo/config/base.json", "{ \"v\": 2, \"extends\": \"./other\" }"));
        project.insertFile(new ProjectFile("/repo/config/other.json", "{}"));

        final Map<String, String> after = contents(project);
        assertTrue(after.get("repo/config/base.json").contains("\"v\": 2"));
        assertTrue(after.containsKey("repo/config/other.json"));
        assertFalse("root is no longer extended", after.containsKey("repo/config/root.json"));
    }

    @Test
    public void aRemovedPresetIsWaitedForAndReinsertingItRestoresTheChain() throws Exception {
        final ProjectFiles project = zip(repository());
        final int before = project.size();

        assertTrue(project.removeFile("/repo/config/base.json"));
        assertFalse(paths(project).contains("repo/config/base.json"));
        assertFalse("the chain below goes with it", paths(project).contains("repo/config/root.json"));

        project.insertFile(new ProjectFile("/repo/config/base.json", "{ \"v\": 3, \"extends\": \"./root\" }"));
        project.insertFile(new ProjectFile("/repo/config/root.json", "{ \"r\": 2 }"));

        final Map<String, String> after = contents(project);
        assertTrue(after.get("repo/config/base.json").contains("\"v\": 3"));
        assertTrue(after.get("repo/config/root.json").contains("\"r\": 2"));
        assertEquals(before, project.size());
    }

    @Test
    public void removingTheConfigReleasesItsPresetsAndAddingItBackKeepsThemAgain() throws Exception {
        final ProjectFiles project = zip(repository());
        final int before = project.size();

        assertTrue(project.removeFile("/repo/web/tsconfig.json"));
        assertEquals(Set.of(), paths(project));
        assertEquals(before - 3, project.size());

        project.insertFile(new ProjectFile("/repo/web/tsconfig.json", "{ \"extends\": \"../config/base.json\" }"));
        assertEquals(3, paths(project).size());
        assertEquals(before, project.size());
    }

    @Test
    public void changingTheConfigsExtendsMovesWhichPresetIsKept() throws Exception {
        final ProjectFiles project = zip(repository());
        project.insertFile(new ProjectFile("/repo/config/alt.json", "{}"));

        project.insertFile(new ProjectFile("/repo/web/tsconfig.json", "{ \"extends\": \"../config/alt.json\" }"));

        assertEquals(Set.of("repo/web/tsconfig.json", "repo/config/alt.json"), paths(project));
    }

    @Test
    public void aCopyKeepsFollowingUpdates() throws Exception {
        final ProjectFiles copy = new ProjectFiles(zip(repository()));

        copy.insertFile(new ProjectFile("/repo/config/base.json", "{ \"v\": 9 }"));

        assertTrue(contents(copy).get("repo/config/base.json").contains("\"v\": 9"));
        assertFalse(paths(copy).contains("repo/config/root.json"));
    }
}
