package com.hadi.test.onelevel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.LevelOneReport;
import com.hadi.clarpse.compiler.LevelOneSelection;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import com.hadi.clarpse.sourcemodel.Package;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.hadi.test.onelevel.OneLevelTestSupport.files;
import static com.hadi.test.onelevel.OneLevelTestSupport.project;
import static com.hadi.test.onelevel.OneLevelTestSupport.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

/**
 * The model surface of one-level analysis: options, selection, the report, boundary components and
 * the not-loaded reference state.
 */
public class OneLevelModelTest {

    @Test
    public void optionsDefaultToFullAndOneLevelNeedsNamedFiles() {
        assertEquals(0, AnalysisOptions.full().depth());
        assertEquals(1, AnalysisOptions.oneLevel().depth());
        assertEquals(1, AnalysisOptions.full().withDepth(1).depth());
        assertThrows(IllegalArgumentException.class, () -> AnalysisOptions.full().withDepth(0));
        assertThrows(IllegalArgumentException.class, () -> AnalysisOptions.full().withDepth(2));
        assertFalse(AnalysisOptions.full().modelsBoundary(List.of("/a.java")));
        assertTrue(AnalysisOptions.oneLevel().modelsBoundary(List.of("/a.java")));
        assertFalse(AnalysisOptions.oneLevel().modelsBoundary(null));
        assertEquals(AnalysisOptions.DEFAULT_LEVEL_ONE_BUDGET, AnalysisOptions.oneLevel().levelOneBudget());
        assertThrows(IllegalArgumentException.class, () -> AnalysisOptions.oneLevel().withLevelOneBudget(-1));
        assertEquals(Set.of("/b.java"),
                AnalysisOptions.oneLevel().withLevelOnePaths(Arrays.asList("/b.java", null, "")).levelOnePaths());
        assertTrue(AnalysisOptions.oneLevel().withLevelOnePaths(null).levelOnePaths().isEmpty());
    }

    @Test
    public void selectionSortsDropsAnalysedAndUnknownPathsAndAppliesTheBudget() {
        final List<ProjectFile> all = List.of(new ProjectFile("/a.java", ""), new ProjectFile("/b.java", ""),
                new ProjectFile("/c.java", ""), new ProjectFile("/d.java", ""));
        final LevelOneSelection selection = LevelOneSelection.select(List.of("/d.java", "/a.java", "/b.java"),
                AnalysisOptions.oneLevel().withLevelOneBudget(2).withLevelOnePaths(List.of("c.java", "/zz.java")),
                List.of(all.get(0)), all);
        assertEquals(List.of("/b.java", "/c.java"), selection.modelledPaths());
        assertEquals(List.of("/d.java"), selection.heldByBudget());
    }

    @Test
    public void reportIsSortedAndNoneIsEmpty() {
        final LevelOneReport report = new LevelOneReport(List.of("/b", "/a"), List.of("/d", "/c"), null, 3);
        assertEquals(List.of("/a", "/b"), report.levelOneFiles());
        assertEquals(List.of("/c", "/d"), report.heldByBudget());
        assertTrue(report.loadedBeyondLevelOne().isEmpty());
        assertEquals(3, report.notLoadedReferences());
        assertTrue(report.budgetHit());
        assertFalse(LevelOneReport.none().budgetHit());
        assertSame(LevelOneReport.none(), new com.hadi.clarpse.compiler.CompileResult(new OOPSourceCodeModel())
                .withLevelOne(null).levelOne());
    }

    @Test
    public void aReferenceIsInAtMostOneOfExternalAndNotLoaded() {
        final SimpleTypeReference reference = new SimpleTypeReference("a.B");
        reference.setNotLoaded(true);
        assertTrue(reference.isNotLoaded());
        assertFalse(reference.isExternal());
        reference.setExternal(true);
        assertTrue(reference.isExternal());
        assertFalse(reference.isNotLoaded());
        reference.setNotLoaded(true);
        assertFalse(reference.isExternal());
    }

    private static Component boundaryComponentWithEveryState() {
        final OOPSourceCodeModel model = new OOPSourceCodeModel();
        final Component component = new Component();
        component.setPkg(new Package("a", "a"));
        component.setComponentName("A");
        component.setName("A");
        component.setComponentType(ComponentType.CLASS);
        component.setSourceFilePath("/a/A.java");
        component.insertCmpRef(new SimpleTypeReference("a.A"));
        component.insertCmpRef(new SimpleTypeReference("a.Missing"));
        component.insertCmpRef(new SimpleTypeReference("java.util.List"));
        model.insertComponent(component);
        CompilerSupport.classifyReferences(model, reference -> reference.invokedComponent().startsWith("a."));
        CompilerSupport.markBoundary(model, List.of("a/A.java"));
        return model.component("a.A").orElseThrow();
    }

    @Test
    public void classificationSortsReferencesIntoThreeStatesAndMarkBoundaryMarksByFile() {
        final Component component = boundaryComponentWithEveryState();
        assertTrue(component.isBoundary());
        assertEquals(Set.of("a.A"), targets(component.internalDependencies()));
        assertEquals(Set.of("a.Missing"), targets(component.notLoadedDependencies()));
        assertEquals(Set.of("java.util.List"), targets(component.externalDependencies()));
    }

    @Test
    public void boundaryAndNotLoadedSurviveCopiesJsonAndJavaSerialisation() throws Exception {
        final Component original = boundaryComponentWithEveryState();
        final Component copy = new Component(original);
        assertTrue(copy.isBoundary());
        assertEquals(Set.of("a.Missing"), targets(copy.notLoadedDependencies()));

        final ObjectMapper mapper = new ObjectMapper();
        final String json = mapper.writeValueAsString(original);
        assertTrue(json, json.contains("\"boundary\":true"));
        assertTrue(json, json.contains("\"notLoaded\":true"));
        final ComponentReference notLoaded = original.notLoadedDependencies().iterator().next();
        final ComponentReference fromJson =
                mapper.readValue(mapper.writeValueAsString(notLoaded), ComponentReference.class);
        assertTrue(fromJson.isNotLoaded());
        assertFalse(fromJson.isExternal());

        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            final Component deserialised = (Component) in.readObject();
            assertTrue(deserialised.isBoundary());
            assertEquals(Set.of("a.Missing"), targets(deserialised.notLoadedDependencies()));
        }
    }

    @Test
    public void onlyAOneLevelProjectCanBePrepared() {
        final ClarpseProject project = new ClarpseProject(project(files("/A.java", "class A { }")), Lang.JAVA,
                List.of("/A.java"));
        assertThrows(IllegalStateException.class, project::prepare);
    }
}
