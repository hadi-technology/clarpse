package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.ProjectFile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * The types a project's Kotlin files declare on the JVM, by fully qualified name: their classes,
 * interfaces, objects and nested types, and the file classes holding their top-level functions and
 * properties. It lets another JVM language's compiler tell a name that a Kotlin file declares from
 * one nothing in the repository declares. Files are read without their function bodies, and a file
 * that cannot be parsed contributes nothing.
 */
public final class KotlinDeclarations {

    private final KotlinDeclarationIndex index;

    private KotlinDeclarations(final KotlinDeclarationIndex index) {
        this.index = index;
    }

    /**
     * Reads the declarations of the given Kotlin files.
     *
     * @param kotlinFiles The project's Kotlin files.
     * @return Their declarations.
     * @throws CompileException When reading is interrupted or a parse task fails unexpectedly.
     */
    public static KotlinDeclarations of(final Collection<ProjectFile> kotlinFiles) throws CompileException {
        return new KotlinDeclarations(new KotlinDeclarationIndex(ClarpseKotlinCompiler.models(
                ClarpseKotlinCompiler.parseFiles(new ArrayList<>(kotlinFiles), false), new HashSet<>()), null));
    }

    /**
     * Whether a Kotlin file declares a type of exactly this name.
     *
     * @param uniqueName A fully qualified name.
     * @return Whether it names a Kotlin type or file class.
     */
    public boolean declaresType(final String uniqueName) {
        return index.isType(uniqueName);
    }

    /**
     * Whether a Kotlin file declares the type a reference names, or the type it is nested in.
     *
     * @param uniqueName A fully qualified name, possibly of a nested type.
     * @return Whether some Kotlin file declares it.
     */
    public boolean declares(final String uniqueName) {
        return !index.kotlinFilesDeclaring(uniqueName).isEmpty();
    }
}
