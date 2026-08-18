package com.hadi.test.perf;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Measures how much of a two-revision analysis's string footprint is the same string held twice,
 * once per revision.
 *
 * <p>This is the question a shared pool answers and a per-model pool cannot. A differential analysis
 * parses the whole repository at base and at head and holds both models for its whole duration, and
 * a pull request touching three files out of 452 leaves the two models naming almost entirely the
 * same packages, types and members. Whether that is worth a map is a matter of measurement, so this
 * reports the recoverable bytes both within one revision and across the pair.
 *
 * <p>Both revisions are the same source tree parsed twice, which is the honest worst case for a pool
 * -- a real pull request differs slightly more -- and it is deterministic, which a checked-out pair
 * of real revisions would not be.
 *
 * <p>Retained readings are used-heap after repeated collection, so they are approximate; the string
 * counts beside them are exact.
 */
public final class CrossRevisionPoolCensus {

    private static final double MEBIBYTE = 1024.0 * 1024.0;

    private CrossRevisionPoolCensus() {
    }

    /**
     * Parses the given trees twice and prints the census.
     *
     * @param args Source directories. Defaults to clarpse's own main sources.
     * @throws Exception If a tree cannot be read or parsed.
     */
    public static void main(final String[] args) throws Exception {
        List<String> roots = args.length == 0 ? List.of("src/main/java") : List.of(args);
        OOPSourceCodeModel base = parse(roots);
        OOPSourceCodeModel head = parse(roots);
        System.out.printf(Locale.ROOT, "base=%d components, head=%d components%n",
                base.size(), head.size());
        System.out.printf(Locale.ROOT, "retained (both models reachable): %.1f MiB%n",
                retainedMib(base, head));

        List<String> baseStrings = strings(base);
        List<String> headStrings = strings(head);
        System.out.printf(Locale.ROOT, "%nstring mentions: base=%d head=%d%n",
                baseStrings.size(), headStrings.size());

        System.out.printf(Locale.ROOT, "within base:  %d objects -> %d values, recoverable %s%n",
                objects(baseStrings), values(baseStrings),
                mib(recoverable(baseStrings)));

        List<String> both = new ArrayList<>(baseStrings);
        both.addAll(headStrings);
        long objectsBoth = objects(both);
        long valuesBoth = values(both);
        System.out.printf(Locale.ROOT, "base+head:    %d objects -> %d values, recoverable %s%n",
                objectsBoth, valuesBoth, mib(recoverable(both)));

        perField(base, head);

        long withinEach = recoverable(baseStrings) + recoverable(headStrings);
        long crossOnly = recoverable(both) - withinEach;
        System.out.printf(Locale.ROOT,
                "%nof which within a single revision: %s%nattributable to holding two revisions: %s%n",
                mib(withinEach), mib(crossOnly));
    }

    /** Reports, per field, what a pool spanning both revisions would recover. */
    private static void perField(final OOPSourceCodeModel base, final OOPSourceCodeModel head) {
        java.util.Map<String, java.util.function.Function<Component, Stream<String>>> fields =
                new java.util.LinkedHashMap<>();
        fields.put("componentName", c -> Stream.of(c.componentName()));
        fields.put("name", c -> Stream.of(c.name()));
        fields.put("value", c -> Stream.of(c.value()));
        fields.put("sourceFile", c -> Stream.of(c.sourceFile()));
        fields.put("module", c -> Stream.of(c.module()));
        fields.put("comment", c -> Stream.of(c.comment()));
        fields.put("codeFragment", c -> Stream.of(c.codeFragment()));
        fields.put("pkg (3 strings)", c -> c.pkg() == null ? Stream.of()
                : Stream.of(c.pkg().name(), c.pkg().path(), c.pkg().ellipsisSeparatedPkgPath()));
        fields.put("children", c -> c.children().stream());
        fields.put("imports", c -> c.imports().stream());
        fields.put("modifiers", c -> c.modifiers().stream());
        fields.put("refs.invokedComponent", c -> c.references().stream()
                .map(r -> r.invokedComponent()));

        System.out.printf(Locale.ROOT, "%n%-24s %9s %9s %9s %9s %16s%n", "field",
                "mentions", "objects", "values", "ratio", "recoverable");
        System.out.println("-".repeat(82));
        long total = 0;
        for (var field : fields.entrySet()) {
            List<String> all = new ArrayList<>();
            Stream.concat(base.components(), head.components())
                    .forEach(c -> field.getValue().apply(c).filter(x -> x != null).forEach(all::add));
            long objects = objects(all);
            long values = values(all);
            long bytes = recoverable(all);
            total += bytes;
            System.out.printf(Locale.ROOT, "%-24s %9d %9d %9d %8.1fx %,10d bytes%n",
                    field.getKey(), all.size(), objects, values,
                    values == 0 ? 0 : (double) objects / values, bytes);
        }
        System.out.println("-".repeat(82));
        System.out.printf(Locale.ROOT, "%-24s %49s %,10d bytes%n", "total", "", total);
    }

    private static List<String> strings(final OOPSourceCodeModel model) {
        List<String> all = new ArrayList<>();
        model.components().forEach(c -> {
            add(all, c.componentName());
            add(all, c.name());
            add(all, c.value());
            add(all, c.sourceFile());
            add(all, c.module());
            add(all, c.comment());
            add(all, c.codeFragment());
            if (c.pkg() != null) {
                add(all, c.pkg().name());
                add(all, c.pkg().path());
                add(all, c.pkg().ellipsisSeparatedPkgPath());
            }
            c.children().forEach(s -> add(all, s));
            c.imports().forEach(s -> add(all, s));
            c.modifiers().forEach(s -> add(all, s));
            c.references().forEach(r -> add(all, r.invokedComponent()));
        });
        return all;
    }

    private static void add(final List<String> all, final String s) {
        if (s != null) {
            all.add(s);
        }
    }

    private static long objects(final List<String> all) {
        Set<String> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        return all.stream().filter(identities::add).count();
    }

    private static long values(final List<String> all) {
        return all.stream().distinct().count();
    }

    /** Bytes that would be freed by collapsing distinct objects onto distinct values. */
    private static long recoverable(final List<String> all) {
        Set<String> seenObjects = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> seenValues = new HashSet<>();
        long bytes = 0;
        for (String s : all) {
            if (!seenObjects.add(s)) {
                continue;
            }
            if (!seenValues.add(s)) {
                bytes += 32 + ((s.length() + 7) / 8) * 8L;
            }
        }
        return bytes;
    }

    private static String mib(final long bytes) {
        return String.format(Locale.ROOT, "%,d bytes (%.2f MiB)", bytes, bytes / MEBIBYTE);
    }

    private static OOPSourceCodeModel parse(final List<String> roots) throws Exception {
        ProjectFiles files = new ProjectFiles();
        for (String root : roots) {
            load(files, Path.of(root));
        }
        return new ClarpseProject(files, Lang.JAVA).result().model();
    }

    private static void load(final ProjectFiles files, final Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                files.insertFile(new ProjectFile("/" + root.relativize(p),
                        Files.readString(p, StandardCharsets.UTF_8)));
            }
        }
    }

    private static double retainedMib(final Object... keep) {
        for (int i = 0; i < 6; i++) {
            System.gc();
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        long used = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
        if (keep.length == 0) {
            throw new IllegalStateException("unreachable");
        }
        return used / MEBIBYTE;
    }
}
