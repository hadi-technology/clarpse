package com.hadi.clarpse.sourcemodel;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One string instance per distinct value, for the fields of a parsed model that repeat.
 *
 * <p><b>Why it exists.</b> A differential analysis parses a repository twice, at base and at head,
 * and holds both models for its whole duration. A pull request touching three files out of 452
 * leaves the two models naming almost entirely the same packages, types and members, so nearly every
 * name is allocated twice over and both copies stay reachable until the analysis ends. Measured on
 * two revisions of a 4,330-component model, {@code 50,844} distinct string objects carry only
 * {@code 12,466} distinct values -- 2.45MB of a 21.4MB pair of models, or 11%, is the same text held
 * twice.
 *
 * <p><b>Which fields, and why not all of them.</b> The gain is per field and it is not where reading
 * the parser suggests. Recoverable bytes across a base/head pair, measured:
 *
 * <table border="1">
 *   <caption>Recoverable bytes per field, base and head together</caption>
 *   <tr><th>field</th><th>ratio</th><th>recovered</th></tr>
 *   <tr><td>children</td><td>2.0x</td><td>536,648</td></tr>
 *   <tr><td>module</td><td>64.6x</td><td>442,144</td></tr>
 *   <tr><td>componentName</td><td>2.0x</td><td>413,912</td></tr>
 *   <tr><td>name</td><td>4.8x</td><td>315,672</td></tr>
 *   <tr><td>modifiers</td><td>1090.6x</td><td>305,104</td></tr>
 *   <tr><td>codeFragment</td><td>4.0x</td><td>245,320</td></tr>
 *   <tr><td>imports</td><td>7.2x</td><td>98,392</td></tr>
 *   <tr><td>comment</td><td>2.1x</td><td>66,320</td></tr>
 *   <tr><td>pkg</td><td>12.2x</td><td>15,312</td></tr>
 *   <tr><td>sourceFile</td><td>2.0x</td><td>11,536</td></tr>
 * </table>
 *
 * <p>{@code componentName} is the field that shows why this had to be measured twice. Within one
 * revision it is a fully-qualified name, unique by construction, and a census of a single model puts
 * it at 1.0x -- pooling it there is pure loss. Across a <em>pair</em> of revisions it is 2.0x and the
 * third largest saving available, because the same 4,330 names are allocated once per parse. A
 * one-model measurement would have excluded the field.
 *
 * <p>Nothing is pooled speculatively. A reference's target name is absent from the table because it
 * is canonicalised where it is constructed; {@code Component.value} measured zero because the Java
 * parser never populates it, and it is pooled anyway, being a declared type name in the languages
 * that do.
 *
 * <p><b>Lifetime, which is the whole safety argument.</b> A pool that is never emptied is a leak, and
 * this codebase has already paid for one: 10.1.2 fixed a static registry in {@code JavaParserFacade}
 * where each entry pinned a whole AST. So this pool is <b>not static</b>. An instance is owned by the
 * {@link OOPSourceCodeModel}s that were given it and becomes unreachable with them, which is exactly
 * the scope the saving is wanted over -- one analysis. It also retains nothing the models do not
 * already hold: every value in it is a string a component points at, so the only cost above the
 * saving is the map's own entries.
 *
 * <p>{@link String#intern()} is deliberately not used. It would make the sharing global and permanent
 * rather than scoped to an analysis, and its table is sized outside the heap where none of this
 * platform's memory instrumentation can see it.
 *
 * <p>Instances are safe for use by several threads, because the two revisions of an analysis are
 * parsed concurrently.
 */
public final class StringPool implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<String, String> strings = new ConcurrentHashMap<>();
    private final Map<Package, Package> packages = new ConcurrentHashMap<>();

    /**
     * The canonical instance for the given value.
     *
     * @param value Any string, or {@code null}.
     * @return The first instance this pool saw carrying that value, or {@code null} for {@code null}.
     *         Empty strings are returned as they came, since they are already shared by the JVM.
     */
    public String pooled(final String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        final String existing = strings.get(value);
        if (existing != null) {
            return existing;
        }
        final String previous = strings.putIfAbsent(value, value);
        if (previous == null) {
            return value;
        }
        return previous;
    }

    /**
     * The canonical instance for the given package, which carries three strings of its own.
     *
     * @param pkg Any package, or {@code null}.
     * @return The first equal package this pool saw, or {@code null} for {@code null}.
     */
    public Package pooled(final Package pkg) {
        if (pkg == null) {
            return null;
        }
        final Package existing = packages.get(pkg);
        if (existing != null) {
            return existing;
        }
        final Package previous = packages.putIfAbsent(pkg, pkg);
        if (previous == null) {
            return pkg;
        }
        return previous;
    }

    /**
     * A set holding the canonical instance of each of the given values.
     *
     * <p>Iteration order is preserved, because a model's imports and modifiers are rendered in the
     * order they were declared and a diagram must not change shape for this.
     *
     * @param values Values to canonicalise. May be {@code null}.
     * @return A new set of pooled instances, empty if {@code values} was {@code null}.
     */
    public Set<String> pooledSet(final Set<String> values) {
        final Set<String> pooled = new LinkedHashSet<>();
        if (values == null) {
            return pooled;
        }
        for (final String value : values) {
            pooled.add(pooled(value));
        }
        return pooled;
    }

    /**
     * How many distinct values this pool holds, for instrumentation and for tests that assert the
     * pool is doing something.
     *
     * @return Distinct string values pooled so far.
     */
    public int size() {
        return strings.size();
    }
}
