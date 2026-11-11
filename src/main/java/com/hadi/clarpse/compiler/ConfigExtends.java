package com.hadi.clarpse.compiler;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads which files a TypeScript or JavaScript project config names in a relative {@code extends}.
 *
 * <p>A config's {@code extends} is a string or an array of strings. Only a relative path (one that
 * starts with {@code ./} or {@code ../}) names a file of the project; a package name names a module
 * under {@code node_modules} and is not followed. A target written without the {@code .json} suffix
 * is the file of that name with the suffix, as the compiler reads it.
 */
final class ConfigExtends {

    private static final Logger LOGGER = LogManager.getLogger(ConfigExtends.class);

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(JsonParser.Feature.ALLOW_COMMENTS)
            .enable(JsonParser.Feature.ALLOW_TRAILING_COMMA)
            .build();

    private ConfigExtends() {
    }

    /**
     * Whether a normalised path is a project config whose {@code extends} is read: a
     * {@code tsconfig.json}, a {@code tsconfig.<name>.json} or a {@code jsconfig.json}.
     *
     * @param normalizedPath The path with forward slashes and no leading slash.
     * @return True for a config whose extended files are kept.
     */
    static boolean isProjectConfig(final String normalizedPath) {
        final String lower = normalizedPath.toLowerCase(Locale.ROOT);
        final String name = lower.substring(lower.lastIndexOf('/') + 1);
        return name.equals("jsconfig.json") || name.matches("tsconfig(\\..+)?\\.json");
    }

    /**
     * The normalised paths of the {@code .json} files a config names in a relative {@code extends}.
     *
     * @param configPath The config's normalised path.
     * @param content    The config's text.
     * @return The targets in the order written; empty when there are none or the text is not JSON.
     */
    static List<String> targets(final String configPath, final String content) {
        final List<String> targets = new ArrayList<>();
        final JsonNode root;
        try {
            root = JSON.readTree(content);
        } catch (final Exception e) {
            LOGGER.debug("Cannot read the extends of {}: {}", configPath, e.getMessage());
            return targets;
        }
        final JsonNode named;
        if (root == null) {
            return targets;
        }
        named = root.get("extends");
        if (named == null) {
            return targets;
        }
        final List<String> bases = new ArrayList<>();
        if (named.isArray()) {
            named.forEach(node -> addText(bases, node));
        } else {
            addText(bases, named);
        }
        String directory = "";
        if (configPath.contains("/")) {
            directory = configPath.substring(0, configPath.lastIndexOf('/'));
        }
        for (final String base : bases) {
            if (!(base.startsWith("./") || base.startsWith("../"))) {
                continue;
            }
            final String resolved = resolve(directory, base);
            if (resolved == null) {
                continue;
            }
            if (resolved.toLowerCase(Locale.ROOT).endsWith(".json")) {
                targets.add(resolved);
            } else {
                targets.add(resolved + ".json");
            }
        }
        return targets;
    }

    private static void addText(final List<String> texts, final JsonNode node) {
        if (node.isTextual()) {
            texts.add(node.asText());
        }
    }

    /** Joins a relative path onto a directory, or returns null when it climbs out of the root. */
    private static String resolve(final String directory, final String relative) {
        final List<String> segments = new ArrayList<>();
        if (!directory.isEmpty()) {
            for (final String segment : directory.split("/")) {
                segments.add(segment);
            }
        }
        for (final String segment : relative.replace('\\', '/').split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    return null;
                }
                segments.remove(segments.size() - 1);
            } else {
                segments.add(segment);
            }
        }
        return String.join("/", segments);
    }
}
