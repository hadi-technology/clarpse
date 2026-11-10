package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import org.junit.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** A {@code var} local initialised with a very long literal or expression is read without error. */
public class CSharpLongLiteralInitializerTest {

    private static final int LENGTH = 200_000;

    private static Set<String> referencesOfS(final String initializer) throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(new ProjectFile("/U.cs",
                "namespace App { public class U { public void M() { var s = " + initializer
                        + "; } } }")).model();
        return model.components()
                .filter(c -> c.componentType() == ComponentType.LOCAL && c.name().equals("s"))
                .flatMap(c -> c.references().stream()).map(ComponentReference::invokedComponent)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    public void aLongRegularStringIsAString() throws Exception {
        assertEquals(Set.of("System.String"), referencesOfS("\"" + "a\\\"".repeat(LENGTH / 3) + "\""));
    }

    @Test
    public void aLongVerbatimStringWithDoubledQuotesIsAString() throws Exception {
        assertEquals(Set.of("System.String"), referencesOfS("@\"" + "a\"\"b".repeat(LENGTH / 4) + "\""));
    }

    @Test
    public void aLongInterpolatedStringIsAString() throws Exception {
        assertEquals(Set.of("System.String"), referencesOfS("$\"" + "ab ".repeat(LENGTH / 3) + "\""));
    }

    @Test(timeout = 20_000)
    public void aLongNonLiteralInitializerIsNotALiteral() throws Exception {
        assertTrue(referencesOfS("a" + "+a".repeat(LENGTH / 2)).isEmpty());
    }
}
