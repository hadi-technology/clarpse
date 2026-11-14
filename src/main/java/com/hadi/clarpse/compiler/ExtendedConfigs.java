package com.hadi.clarpse.compiler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Decides which JSON files a project's configs keep because they extend them.
 *
 * <p>A kept {@code tsconfig.json}, {@code tsconfig.<name>.json} or {@code jsconfig.json} names other
 * files in a relative {@code extends}, and those name further files in turn. Such a file is kept in
 * the shared config map for as long as a project config reaches it, whatever the file is called and
 * in whatever order the input delivers it. The set is recomputed whenever a config or a kept file is
 * inserted, replaced or removed, so a changed preset replaces the old text, a removed one is waited
 * for again, and a file no config reaches any more is released.
 *
 * <p>JSON files that are not (yet) reached are held as candidates, within a size bound, so that an
 * archive or a sequence of inserts can deliver a preset before the config that names it. Candidates
 * live as long as the instance and are copied with it. Targets are only ever looked up among files
 * the input delivered; nothing is read by a target path here.
 */
final class ExtendedConfigs {

    /** The longest chain of relative {@code extends} followed from one project config. */
    static final int MAX_CHAIN = ClarpseProperties.getInt("clarpse.config.maxExtendsChain", 32);
    /** The largest JSON file held or read as an {@code extends} target. */
    static final int MAX_FILE_BYTES = ClarpseProperties.getInt("clarpse.config.maxCandidateJsonBytes", 1024 * 1024);
    private static final long MAX_HELD_BYTES =
            ClarpseProperties.getLong("clarpse.config.maxCandidateJsonTotalBytes", 16L * 1024 * 1024);
    private static final int MAX_NOT_HELD_REASONS = 10000;

    private static final Logger LOGGER = LogManager.getLogger(ExtendedConfigs.class);

    private final Map<String, String> configFiles;
    private final Map<String, String> candidates = new HashMap<>();
    private long candidateBytes = 0;
    private final Set<String> kept = new LinkedHashSet<>();
    private Set<String> missing = new LinkedHashSet<>();
    private final Map<String, String> notHeld = new LinkedHashMap<>();
    private final Map<String, List<String>> targetsCache = new HashMap<>();

    ExtendedConfigs(final Map<String, String> configFiles) {
        this.configFiles = configFiles;
    }

    /** A copy over another config map, which must already hold the same files. */
    ExtendedConfigs copyOver(final Map<String, String> otherConfigFiles) {
        final ExtendedConfigs copy = new ExtendedConfigs(otherConfigFiles);
        copy.candidates.putAll(candidates);
        copy.candidateBytes = candidateBytes;
        copy.kept.addAll(kept);
        copy.missing.addAll(missing);
        copy.notHeld.putAll(notHeld);
        return copy;
    }

    /**
     * A file was put into, or taken out of, the config map by name.
     *
     * @param path The file's normalised path.
     * @return The change in the number of config files held that this caused.
     */
    int configChanged(final String path) {
        targetsCache.remove(path);
        kept.remove(path);
        return reconcile();
    }

    /**
     * A config or kept file was removed from the config map.
     *
     * @param path The file's normalised path.
     * @return The change in the number of config files held that this caused.
     */
    int removed(final String path) {
        return configChanged(path);
    }

    /** Drops a held candidate. */
    void forgetCandidate(final String path) {
        final String dropped = candidates.remove(path);
        if (dropped != null) {
            candidateBytes -= dropped.length();
        }
        targetsCache.remove(path);
    }

    /**
     * Takes a JSON file that is not a project config.
     *
     * @param path    The file's normalised path.
     * @param content The file's text.
     * @return The change in the number of config files held that this caused.
     */
    int offer(final String path, final String content) {
        if (path == null || content == null) {
            return 0;
        }
        targetsCache.remove(path);
        if (configFiles.containsKey(path)) {
            configFiles.put(path, content);
            return reconcile();
        }
        notHeld.remove(path);
        forgetCandidate(path);
        final boolean wanted = missing.contains(path);
        if (content.length() > MAX_FILE_BYTES) {
            noteNotHeld(path, "it is over the size bound of " + MAX_FILE_BYTES + " bytes");
            return 0;
        }
        if (!wanted && candidateBytes + content.length() > MAX_HELD_BYTES) {
            noteNotHeld(path, "the bound on held JSON files was reached");
            return 0;
        }
        candidates.put(path, content);
        candidateBytes += content.length();
        if (wanted) {
            return reconcile();
        }
        return 0;
    }

    /** Remembers why a named file was not taken, so a report never calls it absent. */
    void noteNotHeld(final String path, final String reason) {
        if (notHeld.size() < MAX_NOT_HELD_REASONS) {
            notHeld.put(path, reason);
        }
    }

    /** The targets a kept config names that have not arrived. */
    Set<String> missingTargets() {
        return new LinkedHashSet<>(missing);
    }

    /** Re-keys every path, for the sub-directory shift; a path that cannot be shifted is dropped. */
    void shift(final UnaryOperator<String> shifter) {
        final Map<String, String> shiftedCandidates = new HashMap<>();
        candidates.forEach((path, content) -> {
            try {
                shiftedCandidates.put(shifter.apply(path), content);
            } catch (final IllegalArgumentException e) {
                candidateBytes -= content.length();
            }
        });
        candidates.clear();
        candidates.putAll(shiftedCandidates);
        final Set<String> shiftedKept = new LinkedHashSet<>();
        kept.forEach(path -> shiftedKept.add(shifter.apply(path)));
        kept.clear();
        kept.addAll(shiftedKept);
        notHeld.clear();
        targetsCache.clear();
        reconcile();
    }

    /** Logs every target that was named and never held, with the true reason. */
    void logUnresolved() {
        for (final String target : missing) {
            final String reason = notHeld.get(target);
            if (reason != null) {
                LOGGER.warn("A project config extends {}, which was not kept: {}.", target, reason);
            } else if (notHeld.size() >= MAX_NOT_HELD_REASONS) {
                LOGGER.warn("A project config extends {}, which was not among the files read or was skipped.",
                        target);
            } else {
                LOGGER.warn("A project config extends {}, which was not among the files read.", target);
            }
        }
    }

    /**
     * Recomputes which files the project configs reach: promotes the candidates they reach, releases
     * the kept files they no longer reach, and records the targets still missing.
     */
    private int reconcile() {
        final Map<String, Integer> reached = new HashMap<>();
        final Set<String> absent = new LinkedHashSet<>();
        for (final String path : new ArrayList<>(configFiles.keySet())) {
            if (ConfigExtends.isProjectConfig(path)) {
                walk(path, MAX_CHAIN, reached, absent);
            }
        }
        int delta = 0;
        for (final String target : reached.keySet()) {
            final String held = candidates.remove(target);
            if (held != null) {
                candidateBytes -= held.length();
                configFiles.put(target, held);
                kept.add(target);
                delta += 1;
                LOGGER.debug("Kept {} because a project config extends it.", target);
            }
        }
        for (final String path : new ArrayList<>(kept)) {
            if (!reached.containsKey(path)) {
                final String content = configFiles.remove(path);
                kept.remove(path);
                if (content != null) {
                    candidates.put(path, content);
                    candidateBytes += content.length();
                    delta -= 1;
                    LOGGER.debug("Released {}: no project config extends it any more.", path);
                }
            }
        }
        missing = absent;
        return delta;
    }

    private void walk(final String path, final int hops, final Map<String, Integer> reached,
                      final Set<String> absent) {
        String content = configFiles.get(path);
        if (content == null) {
            content = candidates.get(path);
        }
        if (content == null) {
            return;
        }
        final String text = content;
        for (final String target : targetsCache.computeIfAbsent(path, p -> ConfigExtends.targets(p, text))) {
            if (configFiles.containsKey(target) && !kept.contains(target)) {
                continue;
            }
            if (hops <= 0) {
                LOGGER.debug("The extends chain from {} is longer than {}; {} is not followed.",
                        path, MAX_CHAIN, target);
                continue;
            }
            final Integer before = reached.get(target);
            if (configFiles.containsKey(target) || candidates.containsKey(target)) {
                if (before == null || before < hops - 1) {
                    reached.put(target, hops - 1);
                    walk(target, hops - 1, reached, absent);
                }
            } else {
                absent.add(target);
            }
        }
    }
}
