package com.hadi.clarpse.compiler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * Languages currently supported by Clarpse.
 */
public enum Lang {

    JAVA("java", new HashSet<>(List.of("java")), Collections.emptySet()),
    CSHARP("csharp", new HashSet<>(List.of("cs")), Collections.emptySet()),
    TYPESCRIPT("typescript", new HashSet<>(List.of("ts", "tsx")), Collections.emptySet()),
    PYTHON("python", new HashSet<>(List.of("py")), Collections.emptySet());

    private static final Map<String, Lang> NAMES_MAP = new LinkedHashMap<>();

    static {
        NAMES_MAP.put(JAVA.value, JAVA);
        NAMES_MAP.put(CSHARP.value, CSHARP);
        NAMES_MAP.put(TYPESCRIPT.value, TYPESCRIPT);
        NAMES_MAP.put(PYTHON.value, PYTHON);
    }

    private final String value;
    private final Set<String> sourceFileExtns;
    private final Set<String> nonSourceFileExtns;

    Lang(final String value, final Set<String> sourceFileExtns, Set<String> nonSourceFileExtns) {
        this.value = value;
        this.sourceFileExtns = Collections.unmodifiableSet(new LinkedHashSet<>(sourceFileExtns));
        this.nonSourceFileExtns = Collections.unmodifiableSet(new LinkedHashSet<>(nonSourceFileExtns));
    }

    public static Set<String> supportedSourceFileExtns() {
        Set<String> extns = new HashSet<>();
        Lang.supportedLanguages().forEach(lang -> extns.addAll(lang.sourceFileExtns()));
        return extns;
    }
    public static Set<String> supportedFileExtns() {
        Set<String> extns = new HashSet<>();
        Lang.supportedLanguages().forEach(lang -> extns.addAll((lang.fileExtns())));
        return extns;
    }

    /**
     * Returns the Language for the given file extension (e.g .java, .go, etc..) if it is
     * supported, otherwise null is returned.
     */
    public static Lang langFromExtn(String extension) {
        if (extension == null || extension.trim().isEmpty()) {
            return null;
        }
        final String normalizedExtension = extension.trim().toLowerCase(Locale.ROOT);
        return Lang.supportedLanguages().stream()
                .filter(lang -> lang.fileExtns().stream()
                        .anyMatch(extn -> extn.equalsIgnoreCase(normalizedExtension)))
                .findFirst()
                .orElse(null);
    }

    public static List<Lang> supportedLanguages() {
        return new ArrayList<>(NAMES_MAP.values());
    }

    @JsonCreator
    public static Lang forValue(final String value) {
        if (value == null) {
            return null;
        }
        return NAMES_MAP.get(value.toLowerCase(Locale.ROOT));
    }

    @JsonValue
    public String value() {
        return value;
    }

    public Set<String> sourceFileExtns() {
        return sourceFileExtns;
    }

    public Set<String> nonSourceFileExtns() {
        return this.nonSourceFileExtns;
    }

    public Set<String> fileExtns() {
        Set<String> tmpSet = new LinkedHashSet<>(this.sourceFileExtns);
        tmpSet.addAll(this.nonSourceFileExtns);
        return Collections.unmodifiableSet(tmpSet);
    }
}
