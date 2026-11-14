package com.hadi.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ConstructionReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.StandardLibraryStatus;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The construction reference kind: identity, JSON, selection by kind, and the standard-library mark. */
public class ConstructionReferenceTest {

    @Test
    public void identityIsTheClassAndTheTarget() {
        final Set<ComponentReference> set = new HashSet<>();
        set.add(new ConstructionReference("a.B"));
        set.add(new ConstructionReference("a.B"));
        set.add(new ConstructionReference("a.C"));
        set.add(new SimpleTypeReference("a.B"));
        assertEquals(3, set.size());
    }

    @Test
    public void theTargetIsErasedLikeAnyOtherReference() {
        assertEquals("a.Box", new ConstructionReference("a.Box<a.Item>").invokedComponent());
    }

    @Test
    public void itIsSelectedByItsOwnKind() {
        assertEquals(ConstructionReference.class, TypeReferences.CONSTRUCTION.getMatchingClass());
        final Component component = new Component();
        component.setComponentType(ComponentType.METHOD);
        component.insertCmpRef(new ConstructionReference("a.B"));
        component.insertCmpRef(new SimpleTypeReference("a.B"));
        assertEquals(1, component.references(TypeReferences.CONSTRUCTION).size());
        assertEquals(1, component.references(TypeReferences.SIMPLE).size());
    }

    @Test
    public void itRoundTripsThroughJson() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final ConstructionReference ref = new ConstructionReference("a.B");
        ref.setResolutionKind(ResolutionKind.EXACT);
        final String json = mapper.writeValueAsString(ref);
        assertTrue(json, json.contains("\"type\":\"construction\""));
        final ComponentReference read = mapper.readValue(json, ComponentReference.class);
        assertEquals(ref, read);
        assertEquals(ResolutionKind.EXACT, read.resolutionKind());
    }

    @Test
    public void aCloneKeepsTheResolution() throws Exception {
        final ConstructionReference ref = new ConstructionReference("a.B");
        ref.setResolutionKind(ResolutionKind.UNIQUE_SIMPLE_NAME);
        final ConstructionReference copy = (ConstructionReference) ref.clone();
        assertEquals(ref, copy);
        assertEquals(ResolutionKind.UNIQUE_SIMPLE_NAME, copy.resolutionKind());
    }

    @Test
    public void aTargetOfTheCodebaseIsNeverStandardAndAnUnmarkedExternalOneIsUnknown() {
        final ConstructionReference internal = new ConstructionReference("a.B");
        assertEquals(StandardLibraryStatus.NOT_STANDARD, internal.standardLibrary());
        final ConstructionReference external = new ConstructionReference("x.Y");
        external.setExternal(true);
        assertEquals(StandardLibraryStatus.UNKNOWN, external.standardLibrary());
        external.setStandardLibrary(StandardLibraryStatus.STANDARD);
        assertEquals(StandardLibraryStatus.STANDARD, external.standardLibrary());
    }

    @Test
    public void theMarkIsNotPartOfIdentityAndSurvivesAComponentCopy() {
        final ConstructionReference marked = new ConstructionReference("java.util.ArrayList");
        marked.setExternal(true);
        marked.setStandardLibrary(StandardLibraryStatus.STANDARD);
        final ConstructionReference unmarked = new ConstructionReference("java.util.ArrayList");
        unmarked.setExternal(true);
        assertEquals(marked, unmarked);
        final Component component = new Component();
        component.insertCmpRef(marked);
        final ComponentReference copied = new Component(component).references().iterator().next();
        assertEquals(StandardLibraryStatus.STANDARD, copied.standardLibrary());
    }

    @Test
    public void anUnmarkedReferenceAddsNothingToItsJson() throws Exception {
        final SimpleTypeReference ref = new SimpleTypeReference("x.Y");
        ref.setExternal(true);
        assertFalse(new ObjectMapper().writeValueAsString(ref).contains("standardLibrary"));
    }
}
