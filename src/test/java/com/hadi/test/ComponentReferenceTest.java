package com.hadi.test;

import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import org.junit.Test;

import java.util.HashSet;

import static org.junit.Assert.assertTrue;


public class ComponentReferenceTest {

    @Test
    public void testEqualReferences() {
        ComponentReference refA = new SimpleTypeReference("test");
        ComponentReference refB = new SimpleTypeReference("test");
        HashSet<Object> set = new HashSet<>();
        set.add(refA);
        set.add(refB);
        assert(set.size() == 1);
    }

    @Test
    public void testNonEqualByReferencesByType() {
        ComponentReference refA = new TypeImplementationReference("test");
        ComponentReference refB = new SimpleTypeReference("test");
        HashSet set = new HashSet<>();
        set.add(refA);
        set.add(refB);
        assert(set.size() == 2);
    }

    @Test
    public void testNonEqualByReferencesByTypeAndValue() {
        ComponentReference refA = new TypeImplementationReference("test");
        ComponentReference refB = new SimpleTypeReference("testA");
        HashSet set = new HashSet<>();
        set.add(refA);
        set.add(refB);
        assert(set.size() == 2);
    }
}