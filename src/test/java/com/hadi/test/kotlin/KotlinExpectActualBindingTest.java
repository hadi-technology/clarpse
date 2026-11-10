package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.internalTargets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A call to a function declared in several files of a multiplatform project binds to the
 * {@code expect} declaration, the one common code resolves to, whatever the paths sort like. With no
 * {@code expect} in the project no declaration is the one meant, and the call binds to none.
 */
public class KotlinExpectActualBindingTest {

    private static OOPSourceCodeModel compile(final ProjectFile... files) throws Exception {
        return KotlinTestUtil.compileInline(files).model();
    }

    private static Set<String> fileClasses(final OOPSourceCodeModel model, final String caller) {
        return internalTargets(component(model, caller)).stream().filter(t -> t.startsWith("kmp."))
                .collect(Collectors.toSet());
    }

    private static final List<ProjectFile> WITH_EXPECT = List.of(
            // "androidMain" sorts before "commonMain": the first file by path is an actual.
            new ProjectFile("/src/androidMain/kotlin/kmp/Name.android.kt", String.join("\n",
                    "package kmp",
                    "actual fun platformName(): String = \"android\"")),
            new ProjectFile("/src/commonMain/kotlin/kmp/Name.kt", String.join("\n",
                    "package kmp",
                    "expect fun platformName(): String")),
            new ProjectFile("/src/jvmMain/kotlin/kmp/Name.jvm.kt", String.join("\n",
                    "package kmp",
                    "actual fun platformName(): String = \"jvm\"")),
            new ProjectFile("/src/commonMain/kotlin/app/Use.kt", String.join("\n",
                    "package app",
                    "import kmp.platformName",
                    "class Use { fun name(): String = platformName() }")));

    @Test
    public void aCallBindsToTheExpectDeclarationEvenWhenAnActualSortsFirst() throws Exception {
        assertEquals(Set.of("kmp.NameKt"),
                fileClasses(compile(WITH_EXPECT.toArray(new ProjectFile[0])), "app.Use.name()"));
    }

    @Test
    public void aCallToFunctionsThatAreAllActualBindsToNone() throws Exception {
        final OOPSourceCodeModel model = compile(WITH_EXPECT.get(0), WITH_EXPECT.get(2), WITH_EXPECT.get(3));
        assertTrue(fileClasses(model, "app.Use.name()").toString(), fileClasses(model, "app.Use.name()").isEmpty());
    }
}
