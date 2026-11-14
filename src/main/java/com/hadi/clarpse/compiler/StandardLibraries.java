package com.hadi.clarpse.compiler;

import com.hadi.clarpse.compiler.csharp.CSharpStandardLibrary;
import com.hadi.clarpse.compiler.java.JavaStandardLibrary;
import com.hadi.clarpse.compiler.kotlin.KotlinStandardLibrary;
import com.hadi.clarpse.compiler.python.PythonStandardLibrary;
import com.hadi.clarpse.compiler.typescript.TypeScriptStandardLibrary;
import com.hadi.clarpse.reference.StandardLibraryStatus;

import java.util.EnumMap;
import java.util.Map;

/**
 * The registry of each language's {@link LanguageStandardLibrary}. It holds no rule of its own: it
 * hands a name to the language's strategy, and answers
 * {@link StandardLibraryStatus#UNKNOWN} for a missing language or name.
 */
public final class StandardLibraries {

    private static final Map<Lang, LanguageStandardLibrary> BY_LANGUAGE = new EnumMap<>(Lang.class);

    static {
        BY_LANGUAGE.put(Lang.JAVA, new JavaStandardLibrary());
        BY_LANGUAGE.put(Lang.KOTLIN, new KotlinStandardLibrary());
        BY_LANGUAGE.put(Lang.CSHARP, new CSharpStandardLibrary());
        BY_LANGUAGE.put(Lang.TYPESCRIPT, new TypeScriptStandardLibrary());
        BY_LANGUAGE.put(Lang.PYTHON, new PythonStandardLibrary());
    }

    private StandardLibraries() {
    }

    /**
     * Classifies the target of a reference that the model holds no component for, as the language
     * writes it (see {@code docs/MODEL.md}, external names).
     *
     * @param lang   The language of the referencing file.
     * @param target The reference's {@code invokedComponent()}.
     * @return The status of the target.
     */
    public static StandardLibraryStatus ofExternalName(final Lang lang, final String target) {
        final LanguageStandardLibrary rule = ruleFor(lang, target);
        if (rule == null) {
            return StandardLibraryStatus.UNKNOWN;
        }
        return rule.ofExternalName(target);
    }

    /**
     * Classifies the target of an import directive.
     *
     * @param lang              The language of the importing file.
     * @param target            What the directive names, as written.
     * @param relative          Whether the target is written relative to the importing file.
     * @param resolvesToProject Whether the target resolved to a file of the project.
     * @return The status of the target.
     */
    public static StandardLibraryStatus ofImport(final Lang lang, final String target, final boolean relative,
                                                 final boolean resolvesToProject) {
        final LanguageStandardLibrary rule = ruleFor(lang, target);
        if (rule == null) {
            return StandardLibraryStatus.UNKNOWN;
        }
        return rule.ofImport(target, relative, resolvesToProject);
    }

    private static LanguageStandardLibrary ruleFor(final Lang lang, final String target) {
        if (lang == null || target == null || target.isEmpty()) {
            return null;
        }
        return BY_LANGUAGE.get(lang);
    }
}
