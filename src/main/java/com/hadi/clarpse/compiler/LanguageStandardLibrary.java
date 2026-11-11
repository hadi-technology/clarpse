package com.hadi.clarpse.compiler;

import com.hadi.clarpse.reference.StandardLibraryStatus;

/**
 * One language's rule for what belongs to its standard library.
 *
 * <p>An implementation decides from the name alone, against a data list it ships, and answers
 * {@link StandardLibraryStatus#UNKNOWN} wherever the name does not settle the question. It is
 * stateless and thread-safe.
 */
public interface LanguageStandardLibrary {

    /**
     * Classifies the target of a reference the model holds no component for, as the language writes it.
     *
     * @param target The reference's {@code invokedComponent()}.
     * @return The status of the target.
     */
    StandardLibraryStatus ofExternalName(String target);

    /**
     * Classifies the target of an import directive. A language whose imports name types and
     * namespaces classifies the target as a name.
     *
     * @param target            What the directive names, as written.
     * @param relative          Whether the target is written relative to the importing file.
     * @param resolvesToProject Whether the target resolved to a file of the project, which then
     *                          shadows any library of that name.
     * @return The status of the target.
     */
    default StandardLibraryStatus ofImport(final String target, final boolean relative,
                                           final boolean resolvesToProject) {
        return ofExternalName(target);
    }
}
