package com.hadi.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.MemberKind;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import com.hadi.clarpse.sourcemodel.StringPool;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The member reference kind: identity, JSON, Java serialisation, copying and classification. */
public class MemberReferenceTest {

    private static MemberReference external() {
        final MemberReference ref = new MemberReference("pkg:Client", "send", MemberKind.CALL, "pkg");
        ref.setExternal(true);
        ref.setResolutionKind(ResolutionKind.EXACT);
        return ref;
    }

    @Test
    public void theOwnerIsTheInvokedComponent() {
        final MemberReference ref = external();
        assertEquals("pkg:Client", ref.ownerType());
        assertEquals(ref.ownerType(), ref.invokedComponent());
        assertEquals("send", ref.member());
        assertEquals(MemberKind.CALL, ref.memberKind());
        assertEquals("pkg", ref.externalUnit());
    }

    @Test
    public void identityIsOwnerMemberAndKind() {
        final Set<ComponentReference> set = new HashSet<>();
        set.add(new MemberReference("a.B", "m", MemberKind.CALL));
        set.add(new MemberReference("a.B", "m", MemberKind.CALL, "unit"));
        set.add(new MemberReference("a.B", "n", MemberKind.CALL));
        set.add(new MemberReference("a.B", "m", MemberKind.FIELD_READ));
        set.add(new MemberReference("a.C", "m", MemberKind.CALL));
        set.add(new SimpleTypeReference("a.B"));
        assertEquals(5, set.size());
    }

    @Test
    public void aProjectOwnerHasNoUnit() {
        final MemberReference ref = new MemberReference("a.B", "m", MemberKind.CALL, "stale");
        assertNull(ref.externalUnit());
    }

    @Test
    public void itMatchesTheMemberTypeReferencesEntry() {
        assertEquals(MemberReference.class, TypeReferences.MEMBER.getMatchingClass());
        final Component component = new Component();
        component.setComponentType(ComponentType.METHOD);
        component.insertCmpRef(external());
        component.insertCmpRef(new SimpleTypeReference("pkg:Client"));
        assertEquals(1, component.references(TypeReferences.MEMBER).size());
        assertEquals(1, component.references(TypeReferences.SIMPLE).size());
    }

    @Test
    public void itRoundTripsThroughJson() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final String json = mapper.writeValueAsString(external());
        assertTrue(json, json.contains("\"type\":\"member\""));
        final ComponentReference read = mapper.readValue(json, ComponentReference.class);
        assertTrue(read instanceof MemberReference);
        final MemberReference ref = (MemberReference) read;
        assertEquals(external(), ref);
        assertEquals("pkg:Client", ref.invokedComponent());
        assertEquals("send", ref.member());
        assertEquals(MemberKind.CALL, ref.memberKind());
        assertEquals("pkg", ref.externalUnit());
    }

    @Test
    public void itRoundTripsThroughJavaSerialisation() throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(external());
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            final MemberReference ref = (MemberReference) in.readObject();
            assertEquals(external(), ref);
            assertEquals("pkg", ref.externalUnit());
            assertEquals("send", ref.member());
        }
    }

    @Test
    public void aCopyOfAComponentKeepsEveryFieldOfTheReference() {
        final Component component = new Component();
        component.setComponentType(ComponentType.METHOD);
        component.setComponentName("a.M");
        component.insertCmpRef(external());
        for (final Component copy : new Component[] {new Component(component),
                new Component(component, new StringPool())}) {
            final MemberReference copied = copy.references().stream()
                    .filter(MemberReference.class::isInstance).map(MemberReference.class::cast)
                    .findFirst().orElseThrow();
            assertEquals("pkg:Client", copied.ownerType());
            assertEquals("send", copied.member());
            assertEquals("pkg", copied.externalUnit());
            assertEquals(ResolutionKind.EXACT, copied.resolutionKind());
            assertTrue(copied.isExternal());
            assertEquals(1, copy.externalDependencies().size());
        }
    }
}
