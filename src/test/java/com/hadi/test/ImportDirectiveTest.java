package com.hadi.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.reference.StandardLibraryStatus;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.ImportDirective;
import com.hadi.clarpse.sourcemodel.ImportKind;
import com.hadi.clarpse.sourcemodel.StringPool;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The import directive value: its accessors, equality, JSON and copying with a component. */
public class ImportDirectiveTest {

    private static ImportDirective named() {
        return new ImportDirective("./shapes", ImportKind.SINGLE, true, List.of("Circle", "Square"),
                List.of("Circle", "Sq"), null, StandardLibraryStatus.NOT_STANDARD);
    }

    @Test
    public void itHoldsWhatItWasGiven() {
        final ImportDirective directive = named();
        assertEquals("./shapes", directive.target());
        assertEquals(ImportKind.SINGLE, directive.kind());
        assertTrue(directive.isRelative());
        assertEquals(List.of("Circle", "Square"), directive.names());
        assertEquals(List.of("Circle", "Sq"), directive.binds());
        assertNull(directive.alias());
        assertFalse(directive.importsModule());
        assertFalse(directive.isDynamic());
        assertEquals(StandardLibraryStatus.NOT_STANDARD, directive.standardLibrary());
    }

    @Test
    public void aDirectiveCannotBeChangedThroughItsLists() {
        final ImportDirective directive = named();
        try {
            directive.names().add("Other");
        } catch (final UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("names() must be unmodifiable");
    }

    @Test
    public void equalityCoversEveryField() {
        assertEquals(named(), named());
        assertEquals(named().hashCode(), named().hashCode());
        final ImportDirective dynamic = new ImportDirective("./shapes", ImportKind.SINGLE, true,
                List.of("Circle", "Square"), List.of("Circle", "Sq"), null,
                StandardLibraryStatus.NOT_STANDARD).loading(false, true);
        assertNotEquals(named(), dynamic);
        final ImportDirective otherStatus = new ImportDirective("./shapes", ImportKind.SINGLE, true,
                List.of("Circle", "Square"), List.of("Circle", "Sq"), null,
                StandardLibraryStatus.UNKNOWN);
        assertNotEquals(named(), otherStatus);
    }

    @Test
    public void itRoundTripsThroughJson() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final ImportDirective module = new ImportDirective("numpy", ImportKind.SINGLE, false, List.of(), List.of(),
                "np", StandardLibraryStatus.NOT_STANDARD).loading(true, true);
        for (final ImportDirective directive : List.of(named(), module)) {
            final ImportDirective read = mapper.readValue(mapper.writeValueAsString(directive), ImportDirective.class);
            assertEquals(directive, read);
        }
    }

    @Test
    public void emptyAndDefaultFieldsAreLeftOutOfJson() throws Exception {
        final ImportDirective plain = new ImportDirective("java.util", ImportKind.ON_DEMAND, false, null, null, null,
                StandardLibraryStatus.STANDARD);
        final String json = new ObjectMapper().writeValueAsString(plain);
        assertFalse(json, json.contains("names"));
        assertFalse(json, json.contains("binds"));
        assertFalse(json, json.contains("alias"));
        assertFalse(json, json.contains("relative"));
        assertFalse(json, json.contains("dynamic"));
        assertFalse(json, json.contains("importsModule"));
    }

    @Test
    public void aComponentCopyKeepsItsDirectives() {
        final Component component = new Component();
        component.setImportDirectives(List.of(named()));
        assertEquals(List.of(named()), new Component(component).importDirectives());
        assertEquals(List.of(named()), new Component(component, new StringPool()).importDirectives());
    }

    @Test
    public void aComponentWithoutDirectivesHoldsAnEmptyList() throws Exception {
        assertTrue(new Component().importDirectives().isEmpty());
        final Component component = new Component();
        component.setImportDirectives(List.of(named()));
        component.setImportDirectives(null);
        assertTrue(component.importDirectives().isEmpty());
        final ObjectMapper mapper = new ObjectMapper();
        final String json = mapper.writeValueAsString(new Component());
        assertFalse(json, json.contains("importDirectives"));
        assertTrue(mapper.readValue(json, Component.class).importDirectives().isEmpty());
    }

    @Test
    public void directivesTravelWithAComponentThroughJson() throws Exception {
        final Component component = new Component();
        component.setImportDirectives(List.of(named()));
        final ObjectMapper mapper = new ObjectMapper();
        final Component read = mapper.readValue(mapper.writeValueAsString(component), Component.class);
        assertEquals(List.of(named()), read.importDirectives());
    }
}
