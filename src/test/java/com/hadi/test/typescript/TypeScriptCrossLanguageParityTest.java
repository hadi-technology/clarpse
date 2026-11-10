package com.hadi.test.typescript;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * References the TypeScript parser records beside those other languages record for the same shapes:
 * a type parameter, a namespace member and an inferred type's text are never recorded as types,
 * while decorator arguments, typed lambda parameters, {@code typeof} operands of a type alias and
 * type-parameter defaults are.
 */
public class TypeScriptCrossLanguageParityTest {

    private static final String TYPES = "export class Foo { x = 1; }\n"
            + "export class Bar { y = 1; }\n"
            + "export class Cfg { a = 1; }\n"
            + "export const TOKEN = 'tok';\n"
            + "export const config = { a: 1 };\n"
            + "export function factory(): number { return 1; }\n";

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static Set<String> targets(final String name, final String... sources) throws Exception {
        final String[] all = new String[sources.length + 2];
        all[0] = "/src/types.ts";
        all[1] = TYPES;
        System.arraycopy(sources, 0, all, 2, sources.length);
        final CompileResult result = TypeScriptReferenceGapsTest.compile(all);
        return TypeScriptReferenceGapsTest.targets(TypeScriptReferenceGapsTest.component(result, name));
    }

    @Test
    public void aTypeParameterIsNotRecordedAsAType() throws Exception {
        final Set<String> targets = targets("src.box.Box",
                "/src/box.ts", "export class Box<T> { v!: T; get(x: T): T { return x; } }\n");
        assertFalse(targets.toString(), targets.contains("src.box.T"));
        final Set<String> member = targets("src.box.Box.v",
                "/src/box.ts", "export class Box<T> { v!: T; }\n");
        assertFalse(member.toString(), member.contains("src.box.T"));
    }

    @Test
    public void aTypeParameterKeepsItsConstraint() throws Exception {
        final Set<String> targets = targets("src.use.run",
                "/src/use.ts", "import { Cfg } from './types';\n"
                        + "export function run<U extends Cfg>(u: U): number { return 1; }\n");
        assertTrue(targets.toString(), targets.contains("src.types.Cfg"));
        assertFalse(targets.toString(), targets.contains("src.use.U"));
    }
}
