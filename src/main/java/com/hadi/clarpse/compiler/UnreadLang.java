package com.hadi.clarpse.compiler;

import org.apache.commons.io.FilenameUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Programming languages Clarpse recognises by file extension but has no parser for. An extension
 * several languages share names the one it most often belongs to.
 */
public enum UnreadLang {

    KOTLIN("kotlin", "kt", "kts"),
    SCALA("scala", "scala", "sc"),
    GO("go", "go"),
    RUST("rust", "rs"),
    SWIFT("swift", "swift"),
    RUBY("ruby", "rb"),
    PHP("php", "php"),
    C("c", "c", "h"),
    CPP("cpp", "cpp", "cc", "cxx", "hpp", "hh", "hxx"),
    OBJECTIVE_C("objective-c", "m", "mm"),
    JAVASCRIPT("javascript", "js", "jsx", "mjs", "cjs"),
    GROOVY("groovy", "groovy"),
    CLOJURE("clojure", "clj", "cljs", "cljc"),
    FSHARP("fsharp", "fs", "fsi", "fsx"),
    VISUALBASIC("visualbasic", "vb");

    private static final Map<String, UnreadLang> BY_SOURCE_FILE_EXTN = bySourceFileExtn();

    private final String value;
    private final Set<String> sourceFileExtns;

    UnreadLang(final String value, final String... sourceFileExtns) {
        this.value = value;
        this.sourceFileExtns = Collections.unmodifiableSet(
                new LinkedHashSet<>(Arrays.asList(sourceFileExtns)));
    }

    private static Map<String, UnreadLang> bySourceFileExtn() {
        final Map<String, UnreadLang> byExtn = new LinkedHashMap<>();
        for (final UnreadLang lang : values()) {
            for (final String extn : lang.sourceFileExtns) {
                byExtn.put(extn, lang);
            }
        }
        return Collections.unmodifiableMap(byExtn);
    }

    /**
     * Returns the language a path's extension names, if it is one Clarpse recognises but does not
     * parse, otherwise null is returned. An extension this library parses is never one of these.
     *
     * @param path Any path or file name; neither normalisation nor existence is required.
     * @return The language, or null.
     */
    public static UnreadLang langFromPath(final String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        final String extn = FilenameUtils.getExtension(path).toLowerCase(Locale.ROOT);
        if (extn.isEmpty() || Lang.langFromExtn(extn) != null) {
            return null;
        }
        return BY_SOURCE_FILE_EXTN.get(extn);
    }

    public String value() {
        return value;
    }

    public Set<String> sourceFileExtns() {
        return sourceFileExtns;
    }
}
