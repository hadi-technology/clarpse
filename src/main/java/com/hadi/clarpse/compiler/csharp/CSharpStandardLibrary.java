package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.LanguageStandardLibrary;
import com.hadi.clarpse.compiler.StandardLibraryData;
import com.hadi.clarpse.reference.StandardLibraryStatus;

import java.util.Set;

/**
 * The C# standard library: the namespaces and public types of the .NET base runtime
 * (Microsoft.NETCore.App.Ref 8.0), as two shipped lists. A name is standard when it is a listed
 * namespace, a listed type, or a nested type of one. A name under {@code System}, {@code Microsoft} or
 * {@code Windows} that the lists do not hold is unknown, since NuGet packages and other frameworks
 * use the same roots ({@code System.CommandLine}). Any other qualified name is not standard; a name
 * with no namespace is unknown.
 */
public final class CSharpStandardLibrary implements LanguageStandardLibrary {

    private static final Set<String> NAMESPACES = StandardLibraryData.lines("/stdlib/dotnet-namespaces.txt");
    private static final Set<String> TYPES = StandardLibraryData.lines("/stdlib/dotnet-types.txt");
    private static final Set<String> SHARED_ROOTS = Set.of("System", "Microsoft", "Windows");

    @Override
    public StandardLibraryStatus ofExternalName(final String name) {
        if (NAMESPACES.contains(name) || TYPES.contains(name)) {
            return StandardLibraryStatus.STANDARD;
        }
        String candidate = name;
        while (candidate.indexOf('.') >= 0) {
            candidate = candidate.substring(0, candidate.lastIndexOf('.'));
            if (TYPES.contains(candidate)) {
                return StandardLibraryStatus.STANDARD;
            }
        }
        final int dot = name.indexOf('.');
        if (dot < 0) {
            return StandardLibraryStatus.UNKNOWN;
        }
        if (SHARED_ROOTS.contains(name.substring(0, dot))) {
            return StandardLibraryStatus.UNKNOWN;
        }
        return StandardLibraryStatus.NOT_STANDARD;
    }
}
