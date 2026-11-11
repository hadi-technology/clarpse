package com.hadi.clarpse.compiler.typescript;

import com.hadi.clarpse.compiler.LanguageStandardLibrary;
import com.hadi.clarpse.compiler.StandardLibraryData;
import com.hadi.clarpse.reference.StandardLibraryStatus;

import java.util.Set;

/**
 * The TypeScript standard library as Node provides it: a {@code node:} specifier, and the specifier
 * of a built-in module in the shipped list. Every other specifier or package is not standard, as is a
 * relative specifier or one that resolves to a file of the project. A bare global such as
 * {@code Error} is unknown.
 */
public final class TypeScriptStandardLibrary implements LanguageStandardLibrary {

    private static final Set<String> NODE_MODULES = StandardLibraryData.lines("/stdlib/node-modules.txt");

    @Override
    public StandardLibraryStatus ofExternalName(final String name) {
        final int colon = name.indexOf(':');
        if (colon < 0) {
            return StandardLibraryStatus.UNKNOWN;
        }
        if (name.startsWith("node:")) {
            return StandardLibraryStatus.STANDARD;
        }
        return specifier(name.substring(0, colon));
    }

    @Override
    public StandardLibraryStatus ofImport(final String target, final boolean relative,
                                          final boolean resolvesToProject) {
        if (relative || resolvesToProject) {
            return StandardLibraryStatus.NOT_STANDARD;
        }
        return specifier(target);
    }

    private static StandardLibraryStatus specifier(final String specifier) {
        if (specifier.startsWith("node:") || NODE_MODULES.contains(specifier)) {
            return StandardLibraryStatus.STANDARD;
        }
        return StandardLibraryStatus.NOT_STANDARD;
    }
}
