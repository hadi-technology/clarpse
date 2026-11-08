package com.hadi.test.typescript;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import java.util.Set;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/** A constraint written on the type parameter of an arrow function bound to a constant is a reference. */
public class TypeScriptArrowConstraintTest {

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    @Test
    public void constraintOfAnArrowFunctionIsReferenced() throws Exception {
        final CompileResult result = TypeScriptReferenceGapsTest.compile("/src/a.ts",
                "export interface Cfg { name: string }\n"
                        + "export interface Other { id: number }\n"
                        + "export const parse = <T extends Cfg>(x: T): number => 1;\n"
                        + "export const build = function <U extends Other>(x: U) { return x; };\n"
                        + "export const plain = (x: number) => x;\n");
        final Set<String> parse = TypeScriptReferenceGapsTest.targets(TypeScriptReferenceGapsTest.component(result, "src.a.parse"));
        assertTrue(parse.toString(), parse.contains("src.a.Cfg"));
        assertFalse(parse.toString(), parse.contains("src.a.Other"));
        final Set<String> build = TypeScriptReferenceGapsTest.targets(TypeScriptReferenceGapsTest.component(result, "src.a.build"));
        assertTrue(build.toString(), build.contains("src.a.Other"));
        final Set<String> plain = TypeScriptReferenceGapsTest.targets(TypeScriptReferenceGapsTest.component(result, "src.a.plain"));
        assertFalse(plain.toString(), plain.contains("src.a.Cfg"));
    }
}
