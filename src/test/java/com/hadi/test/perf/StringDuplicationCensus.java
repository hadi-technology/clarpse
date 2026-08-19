package com.hadi.test.perf;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Counts, per field of {@link Component}, how many string instances a parsed model holds, how many
 * distinct values those instances carry, and how many bytes the duplicates cost.
 *
 * <p>This exists because a string pool is only worth its own map entry where the values actually
 * repeat, and which fields repeat is not obvious from reading the parser. A fully-qualified
 * component name is unique by construction and pooling it can only lose; a package or a declared
 * type name recurs on nearly every component. The census settles it per field rather than by
 * argument.
 *
 * <p>Three columns are reported. <b>instances</b> counts distinct string <em>objects</em> by identity,
 * which is what occupies the heap. <b>values</b> counts distinct string <em>contents</em>, which is
 * what would survive pooling. <b>duplicate bytes</b> is the difference, charged at the JVM's
 * shallow cost for a latin1 String -- a 16-byte header plus the byte array's 16-byte header and
 * contents, rounded to 8. It undercounts, because it ignores the reference slots that stay either
 * way, so treat it as a floor.
 *
 * <p>Run it against a real source tree: {@code StringDuplicationCensus <dir> [<dir>...]}. With no
 * argument it censuses clarpse's own main sources.
 */
public final class StringDuplicationCensus {

    private StringDuplicationCensus() {
    }

    /**
     * Parses the given trees and prints the census.
     *
     * @param args Source directories to parse. Defaults to clarpse's own main sources.
     * @throws Exception If a tree cannot be read or parsed.
     */
    public static void main(final String[] args) throws Exception {
        List<String> roots = args.length == 0
                ? List.of("src/main/java")
                : List.of(args);
        ProjectFiles files = new ProjectFiles();
        int count = 0;
        for (String root : roots) {
            count += load(files, Path.of(root));
        }
        System.out.printf(Locale.ROOT, "parsed input: %d java files from %s%n", count, roots);

        OOPSourceCodeModel model = new ClarpseProject(files, Lang.JAVA).result().model();
        System.out.printf(Locale.ROOT, "model: %d components%n%n", model.size());
        census(model);
    }

    private static int load(final ProjectFiles files, final Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> javaFiles = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
            for (Path p : javaFiles) {
                files.insertFile(new ProjectFile("/" + root.relativize(p),
                        Files.readString(p, StandardCharsets.UTF_8)));
            }
            return javaFiles.size();
        }
    }

    private static void census(final OOPSourceCodeModel model) {
        Map<String, Function<Component, Stream<String>>> fields = new LinkedHashMap<>();
        fields.put("componentName", c -> Stream.of(c.componentName()));
        fields.put("name", c -> Stream.of(c.name()));
        fields.put("value", c -> Stream.of(c.value()));
        fields.put("sourceFile", c -> Stream.of(c.sourceFile()));
        fields.put("module", c -> Stream.of(c.module()));
        fields.put("comment", c -> Stream.of(c.comment()));
        fields.put("codeFragment", c -> Stream.of(c.codeFragment()));
        fields.put("pkg.name", c -> Stream.of(c.pkg() == null ? null : c.pkg().name()));
        fields.put("pkg.path", c -> Stream.of(c.pkg() == null ? null : c.pkg().path()));
        fields.put("pkg.ellipsis", c -> Stream.of(
                c.pkg() == null ? null : c.pkg().ellipsisSeparatedPkgPath()));
        fields.put("children (elements)", c -> c.children().stream());
        fields.put("imports (elements)", c -> c.imports().stream());
        fields.put("modifiers (elements)", c -> c.modifiers().stream());
        fields.put("refs.invokedComponent",
                c -> c.references().stream().map(r -> r.invokedComponent()));

        System.out.printf(Locale.ROOT, "%-24s %9s %10s %8s %9s %14s%n",
                "field", "mentions", "instances", "values", "dup ratio", "dup bytes");
        System.out.println("-".repeat(80));
        long totalDup = 0;
        for (Map.Entry<String, Function<Component, Stream<String>>> field : fields.entrySet()) {
            List<String> all = new ArrayList<>();
            model.components().forEach(c -> field.getValue().apply(c)
                    .filter(s -> s != null)
                    .forEach(all::add));
            Set<String> identities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            long distinctObjects = all.stream().filter(identities::add).count();
            long distinctValues = all.stream().distinct().count();
            long dupBytes = 0;
            Set<String> seenValues = new java.util.HashSet<>();
            Set<String> seenObjects = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            for (String s : all) {
                if (!seenObjects.add(s)) {
                    continue;
                }
                if (!seenValues.add(s)) {
                    dupBytes += shallowBytes(s);
                }
            }
            totalDup += dupBytes;
            System.out.printf(Locale.ROOT, "%-24s %9d %10d %8d %8.1fx %,14d%n",
                    field.getKey(), all.size(), distinctObjects, distinctValues,
                    distinctValues == 0 ? 0 : (double) distinctObjects / distinctValues, dupBytes);
        }
        System.out.println("-".repeat(80));
        System.out.printf(Locale.ROOT, "%-24s %49s%,14d%n", "recoverable total", "", totalDup);
        System.out.printf(Locale.ROOT, "model retained: %,d bytes (%.1f MiB)%n",
                retainedBytes(model), retainedBytes(model) / (1024.0 * 1024.0));
        System.out.printf(Locale.ROOT, "%nPackage objects: %d instances, %d distinct values%n",
                distinctPackageObjects(model), distinctPackageValues(model));
    }

    private static long retainedBytes(final OOPSourceCodeModel model) {
        java.lang.management.MemoryMXBean memory = java.lang.management.ManagementFactory.getMemoryMXBean();
        for (int i = 0; i < 6; i++) {
            System.gc();
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        long used = memory.getHeapMemoryUsage().getUsed();
        if (model.size() < 0) {
            throw new IllegalStateException("unreachable");
        }
        return used;
    }

    private static long shallowBytes(final String s) {
        // 16-byte String header (compressed oops) + byte[] header 16 + contents, each rounded to 8.
        long contents = ((s.length() + 7) / 8) * 8L;
        return 16 + 16 + contents;
    }

    private static long distinctPackageObjects(final OOPSourceCodeModel model) {
        Set<Object> identities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        model.components().map(Component::pkg).filter(p -> p != null).forEach(identities::add);
        return identities.size();
    }

    private static long distinctPackageValues(final OOPSourceCodeModel model) {
        return model.components().map(Component::pkg).filter(p -> p != null).distinct().count();
    }
}
