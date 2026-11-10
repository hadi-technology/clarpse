package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.StringPool;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@code Component.importedFiles()}: the repository files a TypeScript file's imports resolve to,
 * as project file paths (the form of {@code ProjectFile.path()} and {@code Component.sourceFile()}).
 */
public class TypeScriptImportedFilesTest {

    private static final String CONFIG = "{ \"compilerOptions\": { \"target\": \"es2020\","
            + " \"module\": \"commonjs\", \"strict\": true, \"jsx\": \"preserve\","
            + " \"baseUrl\": \".\", \"paths\": { \"@alias/fit\": [\"packages/fit/src/index.ts\"],"
            + " \"@ws/*\": [\"packages/*/src/index.ts\"] } },"
            + " \"include\": [\"packages/**/*.ts\", \"packages/**/*.tsx\"] }";

    private static final String USE = "packages/core/src/application/use.ts";
    private static final String USE_PREFIX = "packages.core.src.application.use.";

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static ProjectFiles project(final String... pathsAndContents) {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/tsconfig.json", CONFIG));
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            files.insertFile(new ProjectFile("/" + pathsAndContents[i], pathsAndContents[i + 1]));
        }
        return files;
    }

    private static OOPSourceCodeModel compile(final ProjectFiles files) throws Exception {
        final CompileResult result = new ClarpseProject(files, Lang.TYPESCRIPT).result();
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        return result.model();
    }

    private static Set<String> importedBy(final OOPSourceCodeModel model, final String name) {
        return model.component(name).orElseThrow(() -> new AssertionError("no component " + name))
                .importedFiles();
    }

    private static String use(final String imports, final String body) {
        return imports + "\nexport class Use { " + body + " }\n";
    }

    @Test
    public void aRelativeImportOfAFileDeclaringATypeIsRecordedAndImportsAreUnchanged() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, "import { Fit } from '../../../fit/src/index'; export class Use { f: Fit | null = null; }",
                "packages/fit/src/index.ts", "export class Fit {}"));
        final Component use = model.component(USE_PREFIX + "Use").orElseThrow();

        assertEquals(Set.of("/packages/fit/src/index.ts"), use.importedFiles());
        assertEquals(use.sourceFile().replace("core/src/application/use.ts", "fit/src/index.ts"),
                use.importedFiles().iterator().next());
        assertEquals(List.of("packages.fit.src.index.Fit"), List.copyOf(use.imports()));
    }

    @Test
    public void aTargetThatDeclaresNoTypeIsRecordedToo() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import './side-effects'; import * as consts from './consts';",
                        "n = consts.LIMIT;"),
                "packages/core/src/application/side-effects.ts", "console.log('loaded');",
                "packages/core/src/application/consts.ts", "export const LIMIT = 3;"));

        assertEquals(List.of("/packages/core/src/application/side-effects.ts",
                "/packages/core/src/application/consts.ts"),
                List.copyOf(importedBy(model, USE_PREFIX + "Use")));
    }

    @Test
    public void aFolderNameContainingADotComesOutExactly() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import { Fit } from '../../../fit.core/src/index';", "f: Fit | null = null;"),
                "packages/fit.core/src/index.ts", "export class Fit {}"));

        assertEquals(List.of("/packages/fit.core/src/index.ts"),
                List.copyOf(importedBy(model, USE_PREFIX + "Use")));
    }

    @Test
    public void aDirectoryImportResolvesToItsIndexFile() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import { Dir } from './dir';", "d: Dir | null = null;"),
                "packages/core/src/application/dir/index.ts", "export class Dir {}"));

        assertEquals(Set.of("/packages/core/src/application/dir/index.ts"),
                importedBy(model, USE_PREFIX + "Use"));
    }

    @Test
    public void aTsxTargetIsRecorded() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import { View } from './view';", "v: typeof View | null = null;"),
                "packages/core/src/application/view.tsx", "export function View() { return null; }"));

        assertEquals(Set.of("/packages/core/src/application/view.tsx"),
                importedBy(model, USE_PREFIX + "Use"));
    }

    @Test
    public void aDeclarationFileTargetIsRecorded() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import type { Shape } from './shapes';", "s: Shape | null = null;"),
                "packages/core/src/application/shapes.d.ts", "export interface Shape { sides: number }"));

        assertEquals(Set.of("/packages/core/src/application/shapes.d.ts"),
                importedBy(model, USE_PREFIX + "Use"));
    }

    @Test
    public void aPathAliasIsRecorded() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import { Fit } from '@alias/fit';", "f: Fit | null = null;"),
                "packages/fit/src/index.ts", "export class Fit {}"));
        final Component use = model.component(USE_PREFIX + "Use").orElseThrow();

        assertEquals(Set.of("/packages/fit/src/index.ts"), use.importedFiles());
        assertEquals(Set.of("packages.fit.src.index.Fit"), use.imports());
    }

    @Test
    public void aWildcardPathAliasForAWorkspacePackageIsRecorded() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import { Fit } from '@ws/fit';", "f: Fit | null = null;"),
                "packages/fit/src/index.ts", "export class Fit {}"));

        assertEquals(Set.of("/packages/fit/src/index.ts"), importedBy(model, USE_PREFIX + "Use"));
    }

    @Test
    public void aPackageResolvedIntoNodeModulesIsNotRecorded() throws Exception {
        // The installed package is itself a project file, which the compiler reports as outside
        // every program; only the use's own model matters here.
        final OOPSourceCodeModel model = new ClarpseProject(project(
                USE, use("import { Dep } from 'dep';", "d: Dep | null = null;"),
                "packages/node_modules/dep/index.d.ts", "export interface Dep { id: number }"),
                Lang.TYPESCRIPT).result().model();

        assertTrue(importedBy(model, USE_PREFIX + "Use").isEmpty());
    }

    @Test
    public void anUnresolvedNpmPackageIsNotRecorded() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import React from 'react'; import { Fit } from '../../../fit/src/index';",
                        "f: Fit | null = null;"),
                "packages/fit/src/index.ts", "export class Fit {}"));
        final Component use = model.component(USE_PREFIX + "Use").orElseThrow();

        assertEquals(Set.of("/packages/fit/src/index.ts"), use.importedFiles());
        assertTrue("the specifier still appears in imports()", use.imports().contains("react"));
    }

    @Test
    public void aFileImportingOnlyAnUnresolvedPackageHasNone() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import React from 'react';", "n = 1;")));

        assertTrue(importedBy(model, USE_PREFIX + "Use").isEmpty());
    }

    @Test
    public void exportFromDynamicImportAndRequireAreRecorded() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, "export { Y } from './y';\n"
                        + "declare const require: (id: string) => any;\n"
                        + "export class Use {\n"
                        + "  load() { return import('./z'); }\n"
                        + "  legacy() { return require('./w'); }\n"
                        + "}\n",
                "packages/core/src/application/y.ts", "export class Y {}",
                "packages/core/src/application/z.ts", "export const z = 1;",
                "packages/core/src/application/w.ts", "export const w = 1;"));

        assertEquals(List.of("/packages/core/src/application/y.ts",
                "/packages/core/src/application/z.ts", "/packages/core/src/application/w.ts"),
                List.copyOf(importedBy(model, USE_PREFIX + "Use")));
    }

    @Test
    public void everyModuleLevelComponentOfTheFileCarriesTheFilesImportedFiles() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, "import { Fit } from '../../../fit/src/index';\n"
                        + "export class Use { f: Fit | null = null; }\n"
                        + "export class Other { f: Fit | null = null; }\n"
                        + "export function helper(): Fit | null { return null; }\n",
                "packages/fit/src/index.ts", "export class Fit {}"));
        final Set<String> expected = Set.of("/packages/fit/src/index.ts");

        assertEquals(expected, importedBy(model, USE_PREFIX + "Use"));
        assertEquals(expected, importedBy(model, USE_PREFIX + "Other"));
        assertEquals(expected, model.components()
                .filter(c -> c.uniqueName().startsWith(USE_PREFIX + "helper")).findFirst().orElseThrow()
                .importedFiles());
        assertEquals("a member reports the same as it does in imports(): none",
                model.component(USE_PREFIX + "Use.f").orElseThrow().imports(),
                model.component(USE_PREFIX + "Use.f").orElseThrow().importedFiles());
    }

    @Test
    public void everyEntryIsTheSourceFileFormOfAComponentInThatFile() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import { Fit } from '../../../fit/src/index';", "f: Fit | null = null;"),
                "packages/fit/src/index.ts", "export class Fit {}"));
        final String fitFile = model.component("packages.fit.src.index.Fit").orElseThrow().sourceFile();

        assertTrue(importedBy(model, USE_PREFIX + "Use").contains(fitFile));
    }

    @Test
    public void theSetIsUnmodifiableAndNeverNull() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import { Fit } from '../../../fit/src/index';", "f: Fit | null = null;"),
                "packages/fit/src/index.ts", "export class Fit {}"));

        assertThrows(UnsupportedOperationException.class,
                () -> importedBy(model, USE_PREFIX + "Use").add("/x.ts"));
        assertNotNull(new Component().importedFiles());
        assertTrue(new Component().importedFiles().isEmpty());
    }

    @Test
    public void aBoundaryFileReportsWhatModuleResolutionGivesForIt() throws Exception {
        final ProjectFiles files = project(
                USE, use("import { Mid } from './mid';", "m: Mid | null = null;"),
                "packages/core/src/application/mid.ts",
                "import { Fit } from '../../../fit/src/index'; export class Mid { f: Fit | null = null; }",
                "packages/fit/src/index.ts", "export class Fit {}");
        final CompileResult result = new ClarpseProject(files, Lang.TYPESCRIPT, List.of("/" + USE),
                com.hadi.clarpse.compiler.AnalysisOptions.oneLevel()).result();
        final Component mid = result.model().component("packages.core.src.application.mid.Mid").orElseThrow();

        assertTrue(mid.isBoundary());
        assertEquals(Set.of("/packages/fit/src/index.ts"), mid.importedFiles());
        assertEquals(Set.of("/packages/core/src/application/mid.ts"),
                result.model().component(USE_PREFIX + "Use").orElseThrow().importedFiles());
    }

    @Test
    public void copiesAndPoolingKeepTheField() throws Exception {
        final OOPSourceCodeModel model = compile(project(
                USE, use("import { Fit } from '../../../fit/src/index';", "f: Fit | null = null;"),
                "packages/fit/src/index.ts", "export class Fit {}"));
        final List<String> expected = List.of("/packages/fit/src/index.ts");

        assertEquals(expected, List.copyOf(
                model.copyOfComponent(USE_PREFIX + "Use").orElseThrow().importedFiles()));
        assertEquals(expected, List.copyOf(
                model.copy().component(USE_PREFIX + "Use").orElseThrow().importedFiles()));
    }

    @Test
    public void insertingAComponentPoolsItsImportedFilesInOrderWithoutSharingThem() {
        final Set<String> files = new LinkedHashSet<>(List.of("/b.ts", "/a.ts"));
        final Component source = new Component();
        source.setName("Holder");
        source.setComponentName("Holder");
        source.setImportedFiles(files);

        final Component copy = new Component(source, new StringPool());
        files.add("/c.ts");
        source.setImportedFiles(Set.of());

        assertEquals(List.of("/b.ts", "/a.ts"), List.copyOf(copy.importedFiles()));
        assertEquals(List.of("/b.ts", "/a.ts"), List.copyOf(new Component(copy).importedFiles()));
        assertFalse(source.importedFiles().contains("/b.ts"));
    }

    @Test
    public void aJavaComponentHasNone() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/src/a/A.java", "package a; import b.B; public class A { B b; }"));
        files.insertFile(new ProjectFile("/src/b/B.java", "package b; public class B {}"));
        final OOPSourceCodeModel model = new ClarpseProject(files, Lang.JAVA).result().model();
        final Component a = model.component("a.A").orElseThrow();

        assertFalse(a.imports().isEmpty());
        assertTrue(a.importedFiles().isEmpty());
    }

    @Test
    public void aPythonComponentHasNone() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/src/a.py", "from src.b import B\n\nclass A:\n    b: B\n"));
        files.insertFile(new ProjectFile("/src/b.py", "class B:\n    pass\n"));
        final OOPSourceCodeModel model = new ClarpseProject(files, Lang.PYTHON).result().model();
        final Component a = model.components()
                .filter(c -> c.uniqueName().endsWith("a.A")).findFirst().orElseThrow();

        assertTrue(a.importedFiles().isEmpty());
    }
}
