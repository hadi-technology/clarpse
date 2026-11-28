package com.hadi.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.StringPool;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The declared type is carried by every way a component or a model is copied, merged and written. */
public class DeclaredTypeModelTest {

    private static OOPSourceCodeModel model;

    private static OOPSourceCodeModel parse(final String path, final String code) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile(path, code));
        return new ClarpseProject(files, Lang.JAVA).result().model();
    }

    @BeforeClass
    public static void setup() throws Exception {
        model = parse("/a/Repo.java", "package a;\npublic class Repo {\n  public Order find() { return null; }\n"
                + "  public void clear() {}\n}\nclass Order {}\n");
    }

    @Test
    public void aCopyOfTheComponentAndOfTheModelKeepIt() {
        assertEquals("a.Order", model.copyOfComponent("a.Repo.find()").orElseThrow().declaredType().invokedComponent());
        assertEquals("a.Order", new Component(model.copyOfComponent("a.Repo.find()").orElseThrow())
                .declaredType().invokedComponent());
        assertEquals("a.Order", model.copy().copyOfComponent("a.Repo.find()").orElseThrow()
                .declaredType().invokedComponent());
    }

    @Test
    public void aCopyThroughTheStringPoolKeepsItAndSharesTheTarget() {
        final StringPool pool = new StringPool();
        final Component copy = new Component(model.copyOfComponent("a.Repo.find()").orElseThrow(), pool);
        assertEquals("a.Order", copy.declaredType().invokedComponent());
        assertEquals(pool.pooled("a.Order"), copy.declaredType().invokedComponent());
    }

    @Test
    public void mergingModelsKeepsIt() throws Exception {
        final OOPSourceCodeModel merged = parse("/b/Other.java", "package b;\npublic class Other {}\n");
        merged.merge(model);
        assertEquals("a.Order", merged.copyOfComponent("a.Repo.find()").orElseThrow().declaredType()
                .invokedComponent());
    }

    @Test
    public void aComponentWithNoDeclaredTypeHasNone() {
        assertNull(model.copyOfComponent("a.Repo.clear()").orElseThrow().declaredType());
        assertNull(new Component(model.copyOfComponent("a.Repo.clear()").orElseThrow()).declaredType());
    }

    @Test
    public void theJsonGainsTheFieldOnlyWhenItIsNotNull() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final Component find = model.copyOfComponent("a.Repo.find()").orElseThrow();
        final JsonNode withType = mapper.readTree(mapper.writeValueAsString(find));
        assertTrue(withType.has("declaredType"));
        assertEquals("a.Order", withType.get("declaredType").asText());
        final JsonNode without = mapper.readTree(mapper.writeValueAsString(
                model.copyOfComponent("a.Repo.clear()").orElseThrow()));
        assertFalse(without.has("declaredType"));
    }
}
