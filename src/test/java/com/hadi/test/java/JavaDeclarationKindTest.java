package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A record is told from a final class by the {@code record} modifier it carries. */
public class JavaDeclarationKindTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/src/p/Point.java", "package p;\npublic record Point(int x, int y) { }\n"));
        files.insertFile(new ProjectFile("/src/p/Sealed.java", "package p;\npublic final class Sealed { record Inner(int a) { } }\n"));
        files.insertFile(new ProjectFile("/src/p/Plain.java", "package p;\npublic class Plain { }\n"));
        model = new ClarpseProject(files, Lang.JAVA).result().model();
    }

    @Test
    public void topLevelRecordCarriesTheModifier() {
        assertTrue(model.copyOfComponent("p.Point").orElseThrow().modifiers().contains("record"));
    }

    @Test
    public void nestedRecordCarriesTheModifier() {
        assertTrue(model.copyOfComponent("p.Sealed.Inner").orElseThrow().modifiers().contains("record"));
    }

    @Test
    public void finalClassAndPlainClassDoNot() {
        assertFalse(model.copyOfComponent("p.Sealed").orElseThrow().modifiers().contains("record"));
        assertFalse(model.copyOfComponent("p.Plain").orElseThrow().modifiers().contains("record"));
    }
}
