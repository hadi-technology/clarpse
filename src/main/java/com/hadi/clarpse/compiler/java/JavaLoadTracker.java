package com.hadi.clarpse.compiler.java;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records the repository files the solvers of one compile load, and caps how many distinct files
 * they may load.
 *
 * <p>Shared by every thread's solver in a compile. A file already admitted stays admitted, so a
 * second solver loading the same file does not count against the cap.
 */
public final class JavaLoadTracker {

    private final Set<String> loaded = ConcurrentHashMap.newKeySet();
    private final int cap;

    /**
     * Creates a tracker.
     *
     * @param cap The most distinct files the compile's solvers may load.
     */
    public JavaLoadTracker(final int cap) {
        this.cap = cap;
    }

    /**
     * Admits a file for loading, recording it, unless the cap is reached.
     *
     * @param path The file's path.
     * @return {@code true} when the file may be loaded.
     */
    public synchronized boolean admit(final String path) {
        if (loaded.contains(path)) {
            return true;
        }
        if (loaded.size() >= cap) {
            return false;
        }
        loaded.add(path);
        return true;
    }

    /**
     * The files loaded so far.
     *
     * @return A sorted copy of the loaded paths.
     */
    public Set<String> loaded() {
        return new TreeSet<>(loaded);
    }
}
