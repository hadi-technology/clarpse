package com.hadi.test.typescript;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A generated TypeScript repository of {@code groups} groups of {@code perGroup} files each. File
 * {@code i} of a group imports file {@code i - 1} of its own group and file {@code i} of the next
 * group, so imports cross group boundaries.
 *
 * <p>With {@code separateConfigs} every group is a package with a {@code tsconfig.json} of its own
 * under {@code /packages/gNN/}; without, the same files live under {@code /src/gNN/} and a single
 * root {@code tsconfig.json} owns all of them. Both layouts have the same files, classes and
 * import graph.
 */
final class SyntheticMonorepo {

    final int groups;
    final int perGroup;
    final boolean separateConfigs;
    final Map<String, String> files = new LinkedHashMap<>();

    SyntheticMonorepo(final int groups, final int perGroup, final boolean separateConfigs) {
        this.groups = groups;
        this.perGroup = perGroup;
        this.separateConfigs = separateConfigs;
        final String options = "\"compilerOptions\": { \"target\": \"es2020\", \"module\": \"commonjs\","
                + " \"strict\": true }";
        if (separateConfigs) {
            for (int g = 0; g < groups; g++) {
                files.put("/packages/" + group(g) + "/tsconfig.json",
                        "{ " + options.replace("\"strict\": true", "\"strict\": true, \"outDir\": \"dist/"
                                + group(g) + "\"") + ", \"include\": [\"src/**/*.ts\"] }");
            }
        } else {
            files.put("/tsconfig.json", "{ " + options + ", \"include\": [\"src/**/*.ts\"] }");
        }
        for (int g = 0; g < groups; g++) {
            for (int i = 0; i < perGroup; i++) {
                files.put(path(g, i), source(g, i));
            }
        }
    }

    static String group(final int g) {
        return String.format("g%02d", g);
    }

    String path(final int g, final int i) {
        final String dir = separateConfigs ? "/packages/" + group(g) + "/src" : "/src/" + group(g);
        return dir + "/f" + i + ".ts";
    }

    private String source(final int g, final int i) {
        final StringBuilder src = new StringBuilder();
        final StringBuilder body = new StringBuilder();
        if (i > 0) {
            src.append("import { ").append(cls(g, i - 1)).append(" } from \"./f").append(i - 1).append("\";\n");
            body.append("  before: ").append(cls(g, i - 1)).append(" | undefined;\n");
        }
        if (groups > 1) {
            final int next = (g + 1) % groups;
            src.append("import { ").append(cls(next, i)).append(" } from \"")
                    .append(relative(g, next, i)).append("\";\n");
            body.append("  across: ").append(cls(next, i)).append(" | undefined;\n");
        }
        src.append("export class ").append(cls(g, i)).append(" {\n").append(body)
                .append("  size(): number { return ").append(i).append("; }\n}\n");
        return src.toString();
    }

    private String relative(final int from, final int to, final int i) {
        if (separateConfigs) {
            return "../../" + group(to) + "/src/f" + i;
        }
        return "../" + group(to) + "/f" + i;
    }

    static String cls(final int g, final int i) {
        return "C" + group(g) + "x" + i;
    }

    /** Every source file path, in group order. */
    List<String> sourcePaths() {
        final List<String> paths = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            for (int i = 0; i < perGroup; i++) {
                paths.add(path(g, i));
            }
        }
        return paths;
    }
}
