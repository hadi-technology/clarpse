package com.hadi.test.typescript;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Runs a one-level TypeScript compile with the daemon observed by {@code parse-counter.js}, and
 * reads back how many programs the compile built and how often each file was parsed.
 *
 * <p>A parse is one distinct {@code SourceFile} object for a path across every program the daemon
 * built, so a file served from a cache shared by programs counts once and a file each program reads
 * for itself counts once per program.
 */
final class ParseCountedTypeScript {

    private static final String NODE_PATH_PROP = "clarpse.node.path";
    private static final String HOOK = "src/test/resources/typescript-hooks/parse-counter.js";

    private ParseCountedTypeScript() {
    }

    /** What the observed daemon built. */
    static final class Observation {
        /** Root file names of every createProgram call, in call order. */
        final List<List<String>> programRoots = new ArrayList<>();
        /** The {@code outDir} option of every createProgram call, which names the config built. */
        final List<String> programOutDirs = new ArrayList<>();
        /** Number of daemon processes that built at least one program. */
        int sessions;
        /** Source files held by every createProgram call, in call order. */
        final List<Integer> programFileCounts = new ArrayList<>();
        /** Parses of each repository or dependency source file, by path. */
        final Map<String, Integer> parses = new TreeMap<>();
        /** Parses of each default-library declaration file, by path. */
        final Map<String, Integer> libParses = new TreeMap<>();
        /** Milliseconds spent inside createProgram. */
        double programMs;
        /** The compile's result. */
        CompileResult result;

        int programs() {
            return programRoots.size();
        }

        int sourceParses() {
            return parses.values().stream().mapToInt(Integer::intValue).sum();
        }

        int libFileParses() {
            return libParses.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /**
     * Compiles the repository one-level with the given files analysed.
     *
     * @param files    Repository path to content.
     * @param analysed Paths of the analysed files, in order.
     * @return What the daemon built.
     */
    static Observation observe(final Map<String, String> files, final List<String> analysed) throws Exception {
        final Path dir = Files.createTempDirectory("clarpse-parse-count");
        final Path wrapper = dir.resolve("node-observed.sh");
        final Path statsDir = Files.createDirectory(dir.resolve("stats"));
        Files.writeString(wrapper, "#!/bin/sh\nexport CLARPSE_PARSE_STATS=\"" + statsDir + "/stats-$$.json\"\n"
                + "exec node --require \"" + new File(HOOK).getAbsolutePath() + "\" \"$@\"\n");
        if (!wrapper.toFile().setExecutable(true)) {
            throw new IOException("cannot make " + wrapper + " executable");
        }
        final ProjectFiles projectFiles = new ProjectFiles();
        files.forEach((path, content) -> projectFiles.insertFile(new ProjectFile(path, content)));
        final Observation observation = new Observation();
        System.setProperty(NODE_PATH_PROP, wrapper.toString());
        try {
            observation.result = new ClarpseProject(projectFiles, Lang.TYPESCRIPT, analysed,
                    AnalysisOptions.oneLevel()).result();
        } finally {
            System.clearProperty(NODE_PATH_PROP);
        }
        read(statsDir, observation);
        deleteTree(dir);
        return observation;
    }

    private static void read(final Path statsDir, final Observation observation) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final File[] written = statsDir.toFile().listFiles();
        if (written == null) {
            return;
        }
        for (final File file : written) {
            final JsonNode stats = mapper.readTree(file);
            if (stats.path("programs").size() > 0) {
                observation.sessions++;
            }
            observation.programMs += stats.path("programMs").asDouble();
            for (final JsonNode program : stats.path("programs")) {
                final List<String> roots = new ArrayList<>();
                observation.programOutDirs.add(program.path("outDir").asText());
                program.path("rootNames").forEach(root -> roots.add(root.asText()));
                observation.programRoots.add(roots);
                observation.programFileCounts.add(program.path("fileCount").asInt());
            }
            stats.path("parses").fields().forEachRemaining(
                    e -> observation.parses.merge(e.getKey(), e.getValue().asInt(), Integer::sum));
            stats.path("libParses").fields().forEachRemaining(
                    e -> observation.libParses.merge(e.getKey(), e.getValue().asInt(), Integer::sum));
        }
    }

    private static void deleteTree(final Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }
}
