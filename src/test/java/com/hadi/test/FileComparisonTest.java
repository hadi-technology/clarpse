package com.hadi.test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.ProjectFile;
import org.junit.BeforeClass;
import org.junit.Test;


public class FileComparisonTest {

    private static ProjectFile fileA;
    private static final String rawFileAName = "/fileA";
    private static final String rawFileAContent = "Ain't nobody got time for that";

    private static ProjectFile fileB;
    private static final String rawFileBName = "/fileB";
    private static final String rawFileBContent = "Ain't nobody got time for that";

    @BeforeClass
    public static void setup() {

        fileA = new ProjectFile(rawFileAName, rawFileAContent);
        fileB = new ProjectFile(rawFileBName, rawFileBContent);

    }

    @Test
    public void testRawFileAEqualsRawFileBIsFalse() {
        assertFalse(fileA.equals(fileB));
    }

    @Test
    public void testRawFileBEqualsRawFileAIsFalse() {
        assertFalse(fileB.equals(fileA));
    }

    @Test
    public void testRawFileAEqualsCopyIsTrue() {
        assertEquals(fileA, fileA.copy());
    }

    @Test
    public void testRawFileAHashCodeDoesNotEqualFileB() {
        assertTrue(fileA.hashCode() != fileB.hashCode());
    }

    @Test
    public void testRawFileAHashCodeEqualsCopy() {
        assertEquals(fileA.hashCode(), fileA.copy().hashCode());
    }

    @Test
    public void testRawFileBHashCodeDoesNotEqualFileA() {
        assertTrue(fileB.hashCode() != fileA.hashCode());
    }
}