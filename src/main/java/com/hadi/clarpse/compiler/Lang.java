package com.hadi.clarpse.compiler;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Languages currently supported by Clarpse.
 */
public enum Lang {

    JAVA("java", new HashSet<>(List.of("java")), Collections.emptySet()),
    JAVASCRIPT("javascript", new HashSet<>(List.of("js")), Collections.emptySet()),
    GOLANG("golang", new HashSet<>(List.of("go")), new HashSet<>(List.of("mod")));

    private static final Map<String, Lang> NAMES_MAP = new HashMap<>();

    static {
        NAMES_MAP.put(JAVA.value, JAVA);
        NAMES_MAP.put(JAVASCRIPT.value, JAVASCRIPT);
        NAMES_MAP.put(GOLANG.value, GOLANG);
    }

    private final String value;
    private final Set<String> sourceFileExtns;
    private final Set<String> nonSourceFileExtns;

    Lang(final String value, final Set<String> sourceFileExtns, Set<String> nonSourceFileExtns) {
        this.value = value;
        this.sourceFileExtns = sourceFileExtns;
        this.nonSourceFileExtns = nonSourceFileExtns;
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
        return Lang.supportedLanguages().stream()
                .filter(lang -> lang.fileExtns().stream()
                        .filter(extension::equalsIgnoreCase)
                        .collect(Collectors.toSet()).size() > 0)
                .findFirst()
                .orElse(null);
    }

    public static List<Lang> supportedLanguages() {
        final List<Lang> langs = new ArrayList<>();
        for (final Map.Entry<String, Lang> entry : NAMES_MAP.entrySet()) {
            langs.add(entry.getValue());
        }
        return langs;
    }

    @JsonCreator
    public static Lang forValue(final String value) {
        return NAMES_MAP.get(value);
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
        Set<String> tmpSet = this.sourceFileExtns;
        tmpSet.addAll(this.nonSourceFileExtns);
        return tmpSet;
    }
}
