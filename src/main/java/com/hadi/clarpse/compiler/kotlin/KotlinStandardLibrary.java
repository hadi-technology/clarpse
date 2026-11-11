package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.LanguageStandardLibrary;
import com.hadi.clarpse.compiler.java.JavaStandardLibrary;
import com.hadi.clarpse.reference.StandardLibraryStatus;

import java.util.List;

/**
 * The Kotlin standard library: {@code kotlin} and {@code kotlin.*}, and the Java platform rule for
 * Kotlin/JVM. {@code kotlin.reflect.full}, {@code kotlin.reflect.jvm} and {@code kotlin.test} ship as
 * separate artifacts and are unknown; {@code kotlinx.*} and every other package is not standard.
 */
public final class KotlinStandardLibrary implements LanguageStandardLibrary {

    private static final List<String> SEPARATE_ARTIFACTS = List.of(
            "kotlin.reflect.full", "kotlin.reflect.jvm", "kotlin.test");

    private final JavaStandardLibrary platform = new JavaStandardLibrary();

    @Override
    public StandardLibraryStatus ofExternalName(final String name) {
        if (name.equals("kotlin") || name.startsWith("kotlin.")) {
            for (final String separate : SEPARATE_ARTIFACTS) {
                if (name.equals(separate) || name.startsWith(separate + ".")) {
                    return StandardLibraryStatus.UNKNOWN;
                }
            }
            return StandardLibraryStatus.STANDARD;
        }
        return platform.ofExternalName(name);
    }
}
