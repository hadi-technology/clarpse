package com.hadi.test;

import com.hadi.clarpse.compiler.DiscardedEntryObserver;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Covers observing the archive entries extraction discards: what an observer sees, what it must
 * never see, what it costs, and what happens when it throws.
 */
public class DiscardedEntryObserverTest {

    /**
     * The entry-count limit of src/main/resources/clarpse.properties; the archive built for the
     * limit test holds one entry more than this.
     */
    private static final int MAX_ZIP_ENTRIES = 100000;

    /** The per-entry uncompressed limit of src/main/resources/clarpse.properties. */
    private static final int MAX_ENTRY_UNCOMPRESSED_BYTES = 10 * 1024 * 1024;

    /** The total uncompressed limit of src/main/resources/clarpse.properties. */
    private static final long TOTAL_UNCOMPRESSED_LIMIT = 200L * 1024 * 1024;

    private static final Instant ENTRY_TIME = Instant.ofEpochSecond(1700000000L);

    private static final String JAVA_SOURCE = "package repo; public class Main { void run() { } }";
    private static final String TS_SOURCE = "export const greet = () => 'hi';";
    private static final String TSCONFIG = "{\"compilerOptions\":{\"allowJs\":true}}";
    private static final String README = "# Repo\n\nThe architecture lives here.\n";
    private static final String GUIDE = "Guide\n=====\n\nHow the pieces fit.\n";
    private static final byte[] LOGO = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    public void projectFilesAreIdenticalWithAndWithoutAnObserver() throws Exception {
        byte[] archive = fixtureArchive();

        List<String> withoutObserver = describe(new ProjectFiles(new ByteArrayInputStream(archive)));
        List<String> withObserver = describe(
                ProjectFiles.fromZip(new ByteArrayInputStream(archive), new RecordingObserver()));

        assertEquals(withoutObserver, withObserver);
        assertEquals(List.of(
                "/repo/src/Main.java=" + JAVA_SOURCE,
                "/repo/src/app.ts=" + TS_SOURCE,
                "/repo/tsconfig.json=" + TSCONFIG), withObserver);
    }

    @Test
    public void observerSeesExactlyTheEntriesExtractionDiscards() throws Exception {
        RecordingObserver observer = new RecordingObserver();

        ProjectFiles.fromZip(new ByteArrayInputStream(fixtureArchive()), observer);

        assertEquals(List.of("repo/README.md", "repo/docs/guide.rst", "repo/assets/logo.png"),
                observer.paths);
        assertEquals(README, observer.text("repo/README.md"));
        assertEquals(GUIDE, observer.text("repo/docs/guide.rst"));
        assertArrayEquals(LOGO, observer.contents.get("repo/assets/logo.png"));
        assertNotNull(observer.times.get("repo/README.md"));
        assertEquals(ENTRY_TIME.getEpochSecond(),
                observer.times.get("repo/README.md").getEpochSecond());
    }

    @Test
    public void aPathPredicateBoundsWhatIsObserved() throws Exception {
        byte[] archive = fixtureArchive();
        RecordingObserver observer = new RecordingObserver(path -> path.endsWith(".md"));

        ProjectFiles projectFiles = ProjectFiles.fromZip(new ByteArrayInputStream(archive), observer);

        assertEquals(List.of("repo/README.md"), observer.paths);
        assertEquals(List.of("repo/README.md", "repo/docs/guide.rst", "repo/assets/logo.png"),
                observer.asked);
        assertEquals(describe(new ProjectFiles(new ByteArrayInputStream(archive))), describe(projectFiles));
    }

    @Test
    public void zipSlipEntriesAreRefusedAndNeverObserved() throws Exception {
        byte[] archive = archiveOf(entry("../../escape.md", README), entry("repo/README.md", README),
                entry("repo/src/Main.java", JAVA_SOURCE));
        RecordingObserver observer = new RecordingObserver();

        ProjectFiles projectFiles = ProjectFiles.fromZip(new ByteArrayInputStream(archive), observer);

        assertEquals(List.of("repo/README.md"), observer.paths);
        assertEquals(List.of("repo/README.md"), observer.asked);
        assertFalse(observer.paths.stream().anyMatch(path -> path.contains("..")));
        assertEquals(describe(new ProjectFiles(new ByteArrayInputStream(archive))), describe(projectFiles));
    }

    @Test
    public void anEntryOverTheSizeLimitIsRefusedAndNeverObserved() throws Exception {
        byte[] archive = archiveOf(entry("repo/huge.md", new byte[MAX_ENTRY_UNCOMPRESSED_BYTES + 1]),
                entry("repo/README.md", README), entry("repo/src/Main.java", JAVA_SOURCE));

        RecordingObserver observing = new RecordingObserver();
        ProjectFiles observed = ProjectFiles.fromZip(new ByteArrayInputStream(archive), observing);
        assertEquals(List.of("repo/README.md"), observing.paths);

        RecordingObserver declining = new RecordingObserver(path -> false);
        ProjectFiles declined = ProjectFiles.fromZip(new ByteArrayInputStream(archive), declining);
        assertTrue(declining.paths.isEmpty());

        List<String> withoutObserver = describe(new ProjectFiles(new ByteArrayInputStream(archive)));
        assertEquals(withoutObserver, describe(observed));
        assertEquals(withoutObserver, describe(declined));
    }

    @Test
    public void theTotalSizeLimitCountsTheEntriesAnObserverDeclined() throws Exception {
        byte[] archive = archiveOverTheTotalSizeLimit();
        RecordingObserver declining = new RecordingObserver(path -> false);

        ProjectFiles declined = ProjectFiles.fromZip(new ByteArrayInputStream(archive), declining);

        assertTrue(declining.paths.isEmpty());
        assertTrue(describe(declined).isEmpty());
        assertEquals(describe(new ProjectFiles(new ByteArrayInputStream(archive))), describe(declined));
    }

    @Test
    public void tooManyEntriesIsRefusedWithAnObserverPresent() throws Exception {
        byte[] archive = archiveOfManyEmptyEntries(MAX_ZIP_ENTRIES + 1);
        RecordingObserver observer = new RecordingObserver(path -> false);

        assertEquals("Zip contains too many entries.",
                refusalOf(() -> new ProjectFiles(new ByteArrayInputStream(archive))));
        assertEquals("Zip contains too many entries.",
                refusalOf(() -> ProjectFiles.fromZip(new ByteArrayInputStream(archive), observer)));
        assertEquals(MAX_ZIP_ENTRIES, observer.asked.size());
        assertFalse(observer.asked.contains(entryName(MAX_ZIP_ENTRIES)));
    }

    @Test
    public void anObserverThatThrowsPropagatesWithTheArchiveClosed() throws Exception {
        IllegalStateException thrown = new IllegalStateException("no room for this one");
        ClosingRecordingStream stream = new ClosingRecordingStream(fixtureArchive());

        try {
            ProjectFiles.fromZip(stream, (path, content, lastModified) -> {
                throw thrown;
            });
            fail("Expected the observer's exception.");
        } catch (final IllegalStateException e) {
            assertSame(thrown, e);
        }
        assertTrue(stream.closed);
    }

    @Test
    public void aPathPredicateThatThrowsPropagatesWithTheArchiveClosed() throws Exception {
        IllegalArgumentException thrown = new IllegalArgumentException("unreadable path");
        ClosingRecordingStream stream = new ClosingRecordingStream(fixtureArchive());

        try {
            ProjectFiles.fromZip(stream, new DiscardedEntryObserver() {
                @Override
                public boolean observesPath(final String path) {
                    throw thrown;
                }

                @Override
                public void observe(final String path, final byte[] content, final Instant lastModified) {
                    fail("The predicate refused to answer, so nothing may be observed.");
                }
            });
            fail("Expected the predicate's exception.");
        } catch (final IllegalArgumentException e) {
            assertSame(thrown, e);
        }
        assertTrue(stream.closed);
    }

    private static String refusalOf(final ThrowingSupplier supplier) throws Exception {
        try {
            supplier.get();
        } catch (final IllegalArgumentException e) {
            return e.getMessage();
        }
        return "no refusal";
    }

    private static List<String> describe(final ProjectFiles projectFiles) {
        List<String> described = new ArrayList<>();
        for (ProjectFile file : projectFiles.files()) {
            described.add(file.path() + "=" + file.content());
        }
        described.sort(String::compareTo);
        return described;
    }

    private static byte[] fixtureArchive() throws IOException {
        return archiveOf(
                directory("repo/docs/"),
                entry("repo/src/Main.java", JAVA_SOURCE),
                entry("repo/src/app.ts", TS_SOURCE),
                entry("repo/tsconfig.json", TSCONFIG),
                entry("repo/README.md", README),
                entry("repo/docs/guide.rst", GUIDE),
                entry("repo/assets/logo.png", LOGO));
    }

    private static byte[] archiveOf(final ArchiveEntry... entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (ArchiveEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name);
                zipEntry.setLastModifiedTime(FileTime.from(ENTRY_TIME));
                zos.putNextEntry(zipEntry);
                zos.write(entry.content);
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /**
     * An archive whose discarded entries alone exceed the total uncompressed limit, followed by a
     * source file the limit therefore keeps out.
     */
    private static byte[] archiveOverTheTotalSizeLimit() throws IOException {
        final int entries = (int) (TOTAL_UNCOMPRESSED_LIMIT / MAX_ENTRY_UNCOMPRESSED_BYTES) + 1;
        final byte[] filler = new byte[MAX_ENTRY_UNCOMPRESSED_BYTES];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (int i = 0; i < entries; i++) {
                zos.putNextEntry(new ZipEntry("repo/docs/large" + i + ".md"));
                zos.write(filler);
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry("repo/src/Main.java"));
            zos.write(JAVA_SOURCE.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return out.toByteArray();
    }

    private static byte[] archiveOfManyEmptyEntries(final int count) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (int i = 0; i < count; i++) {
                zos.putNextEntry(new ZipEntry(entryName(i)));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static String entryName(final int index) {
        return "e/" + index + ".bin";
    }

    private static ArchiveEntry directory(final String name) {
        return new ArchiveEntry(name, new byte[0]);
    }

    private static ArchiveEntry entry(final String name, final String content) {
        return new ArchiveEntry(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static ArchiveEntry entry(final String name, final byte[] content) {
        return new ArchiveEntry(name, content);
    }

    private interface ThrowingSupplier {
        ProjectFiles get() throws Exception;
    }

    private static final class ArchiveEntry {

        private final String name;
        private final byte[] content;

        private ArchiveEntry(final String name, final byte[] content) {
            this.name = name;
            this.content = Arrays.copyOf(content, content.length);
        }
    }

    private static final class RecordingObserver implements DiscardedEntryObserver {

        private final Predicate<String> filter;
        private final List<String> asked = new ArrayList<>();
        private final List<String> paths = new ArrayList<>();
        private final Map<String, byte[]> contents = new LinkedHashMap<>();
        private final Map<String, Instant> times = new LinkedHashMap<>();

        private RecordingObserver() {
            this(path -> true);
        }

        private RecordingObserver(final Predicate<String> filter) {
            this.filter = filter;
        }

        @Override
        public boolean observesPath(final String path) {
            this.asked.add(path);
            return this.filter.test(path);
        }

        @Override
        public void observe(final String path, final byte[] content, final Instant lastModified) {
            this.paths.add(path);
            this.contents.put(path, content);
            this.times.put(path, lastModified);
        }

        private String text(final String path) {
            return new String(this.contents.get(path), StandardCharsets.UTF_8);
        }
    }

    private static final class ClosingRecordingStream extends FilterInputStream {

        private boolean closed;

        private ClosingRecordingStream(final byte[] content) {
            super(new ByteArrayInputStream(content));
        }

        @Override
        public void close() throws IOException {
            this.closed = true;
            super.close();
        }
    }
}
