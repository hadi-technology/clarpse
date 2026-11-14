package com.hadi.clarpse.compiler.python;

import com.hadi.clarpse.compiler.LanguageStandardLibrary;
import com.hadi.clarpse.compiler.StandardLibraryData;
import com.hadi.clarpse.reference.StandardLibraryStatus;

import java.util.Set;

/**
 * The Python standard library: a module whose top-level package is in the shipped list, taken from
 * the typeshed bundled with the type checker. The list is the union over the Python versions
 * typeshed covers, so a module removed in a later version ({@code distutils}) is still listed. Any
 * other module, a relative import, and a module that resolves to a file of the project are not
 * standard; a bare name is unknown.
 */
public final class PythonStandardLibrary implements LanguageStandardLibrary {

    private static final Set<String> MODULES = StandardLibraryData.lines("/stdlib/python-modules.txt");

    @Override
    public StandardLibraryStatus ofExternalName(final String name) {
        if (name.indexOf('.') < 0) {
            return StandardLibraryStatus.UNKNOWN;
        }
        return module(name);
    }

    @Override
    public StandardLibraryStatus ofImport(final String target, final boolean relative,
                                          final boolean resolvesToProject) {
        if (relative || resolvesToProject) {
            return StandardLibraryStatus.NOT_STANDARD;
        }
        return module(target);
    }

    private static StandardLibraryStatus module(final String module) {
        final int dot = module.indexOf('.');
        String top = module;
        if (dot >= 0) {
            top = module.substring(0, dot);
        }
        if (MODULES.contains(top)) {
            return StandardLibraryStatus.STANDARD;
        }
        return StandardLibraryStatus.NOT_STANDARD;
    }
}
