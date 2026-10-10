package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.internalTargets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A multiplatform project declares one name in several files: an {@code expect} declaration in
 * common code and an {@code actual} one per platform. On the JVM they are one type, so they are one
 * component holding what each declares, and the model does not depend on the order the files are
 * read in.
 */
public class KotlinMultiplatformTest {

    private static final List<ProjectFile> FILES = List.of(
            new ProjectFile("/src/commonMain/kotlin/kmp/Clock.kt", String.join("\n",
                    "package kmp",
                    "expect class Clock() { fun now(): Long }",
                    "expect fun platformName(): String")),
            new ProjectFile("/src/jvmMain/kotlin/kmp/Clock.jvm.kt", String.join("\n",
                    "package kmp",
                    "import java.time.Instant",
                    "actual class Clock actual constructor() { actual fun now(): Long = Instant.now().toEpochMilli() }",
                    "actual fun platformName(): String = \"jvm\"")),
            new ProjectFile("/src/jsMain/kotlin/kmp/Clock.js.kt", String.join("\n",
                    "package kmp",
                    "actual class Clock actual constructor() { actual fun now(): Long = 0L }",
                    "actual fun platformName(): String = \"js\"")),
            new ProjectFile("/src/commonMain/kotlin/app/Use.kt", String.join("\n",
                    "package app",
                    "import kmp.Clock",
                    "import kmp.platformName",
                    "class Use {",
                    "    fun time(clock: Clock): Long = clock.now()",
                    "    fun name(): String = platformName()",
                    "}")));

    private static OOPSourceCodeModel compile(final List<ProjectFile> files) throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        files.forEach(projectFiles::insertFile);
        final CompileResult result = new ClarpseProject(projectFiles, Lang.KOTLIN).result();
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        return result.model();
    }

    @Test
    public void anExpectClassAndItsActualsAreOneComponent() throws Exception {
        final OOPSourceCodeModel model = compile(FILES);
        assertEquals(1, model.components().filter(component -> component.uniqueName().equals("kmp.Clock")).count());
        final Component clock = component(model, "kmp.Clock");
        assertTrue(clock.children().contains("kmp.Clock.now()"));
        assertTrue(clock.references().stream().anyMatch(reference ->
                reference.invokedComponent().equals("java.time.Instant")));
    }

    @Test
    public void aReferenceToTheExpectedTypeResolvesInternally() throws Exception {
        assertTrue(internalTargets(component(compile(FILES), "app.Use.time(Clock)")).contains("kmp.Clock"));
    }

    @Test
    public void aCallToAnExpectedFunctionReferencesAFileClassDeclaringIt() throws Exception {
        final Set<String> declaring = Set.of("kmp.ClockKt", "kmp.Clock_jvmKt", "kmp.Clock_jsKt");
        final Set<String> called = internalTargets(component(compile(FILES), "app.Use.name()")).stream()
                .filter(target -> target.startsWith("kmp.")).collect(Collectors.toSet());
        assertEquals(called.toString(), 1, called.size());
        assertTrue(called.toString(), declaring.containsAll(called));
    }

    @Test
    public void theModelDoesNotDependOnTheOrderFilesAreRead() throws Exception {
        final List<ProjectFile> reversed = new ArrayList<>(FILES);
        Collections.reverse(reversed);
        assertEquals(describe(compile(FILES)), describe(compile(reversed)));
    }

    private static List<String> describe(final OOPSourceCodeModel model) {
        return model.components().sorted(Comparator.comparing(Component::uniqueName))
                .map(component -> component.uniqueName() + " " + component.componentType() + " "
                        + component.sourceFile() + " " + component.modifiers() + " " + component.children()
                        + " " + new TreeSet<>(component.references().stream()
                        .map(reference -> reference.invokedComponent()).collect(Collectors.toSet())))
                .collect(Collectors.toList());
    }
}
