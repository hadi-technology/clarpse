package com.hadi.clarpse.compiler.java;

import com.hadi.clarpse.compiler.LanguageStandardLibrary;
import com.hadi.clarpse.compiler.StandardLibraryData;
import com.hadi.clarpse.reference.StandardLibraryStatus;

import java.util.Set;

/**
 * The Java standard library: {@code java.*}, which the platform reserves, and every package in the
 * shipped list of packages that the Java SE 17 platform exports ({@code javax.swing},
 * {@code org.w3c.dom}, ...). The list is fixed, so the answer does not depend on the runtime
 * clarpse runs on. A package outside it, including {@code javax.*} packages the platform does not
 * export, is not standard; a name with no package is unknown.
 */
public final class JavaStandardLibrary implements LanguageStandardLibrary {

    private static final Set<String> PACKAGES = StandardLibraryData.lines("/stdlib/java-packages.txt");

    @Override
    public StandardLibraryStatus ofExternalName(final String name) {
        if (name.startsWith("java.")) {
            return StandardLibraryStatus.STANDARD;
        }
        String candidate = name;
        while (candidate.indexOf('.') >= 0) {
            if (PACKAGES.contains(candidate)) {
                return StandardLibraryStatus.STANDARD;
            }
            candidate = candidate.substring(0, candidate.lastIndexOf('.'));
        }
        if (name.indexOf('.') < 0) {
            return StandardLibraryStatus.UNKNOWN;
        }
        return StandardLibraryStatus.NOT_STANDARD;
    }
}
