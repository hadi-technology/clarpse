package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.test.DeclaredTypes;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** The declared type of a TypeScript method, function, field and parameter is the one type its annotation names. */
public class TypeScriptDeclaredTypeTest {

    private static final String CONFIG = "{\"compilerOptions\":{\"target\":\"es2020\",\"strict\":true},"
            + "\"include\":[\".\"]}";
    private static OOPSourceCodeModel model;

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    @BeforeClass
    public static void setup() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        final CompileResult result = TypeScriptReferenceGapsTest.compileWithConfig(CONFIG,
                "/repo.ts", """
                        import { Shown } from "./shown";
                        export class Order {}
                        export class Repo<T> {
                          held: Order | null = null;
                          many: Order[] = [];
                          either: Order | Shown = new Order();
                          orders(): Order[] { return []; }
                          find(id: string): Promise<Order> { return null as any; }
                          clear(): void {}
                          take(o: Order): void {}
                          inferred() { return new Order(); }
                          remote(): Shown { return new Shown(); }
                          element(): T { return null as any; }
                          callback(): () => Order { return () => new Order(); }
                        }
                        export function make(): Order { return new Order(); }
                        """,
                "/shown.ts", "export class Shown {}\n");
        model = result.model();
    }

    @Test
    public void aFunctionAndAFieldAreDeclaredWithTheirAnnotation() {
        assertEquals("repo.Order", DeclaredTypes.of(model, "repo", "make"));
        assertEquals("repo.Order", DeclaredTypes.of(model, "repo.Repo", "held"));
        assertEquals("shown.Shown", DeclaredTypes.of(model, "repo.Repo", "remote"));
    }

    @Test
    public void aGenericTypeIsErased() {
        assertEquals("Promise", DeclaredTypes.of(model, "repo.Repo", "find"));
    }

    @Test
    public void anArrayAUnionAnInferredTypeAFunctionTypeAndATypeParameterAreNull() {
        assertNull(DeclaredTypes.of(model, "repo.Repo", "orders"));
        assertNull(DeclaredTypes.of(model, "repo.Repo", "many"));
        assertNull(DeclaredTypes.of(model, "repo.Repo", "either"));
        assertNull(DeclaredTypes.of(model, "repo.Repo", "inferred"));
        assertNull(DeclaredTypes.of(model, "repo.Repo", "clear"));
        assertNull(DeclaredTypes.of(model, "repo.Repo", "callback"));
        assertNull(DeclaredTypes.of(model, "repo.Repo", "element"));
    }

    @Test
    public void aParameterIsDeclaredWithItsAnnotation() {
        assertEquals("repo.Order", DeclaredTypes.parameter(model, "repo.Repo.take(", "o").invokedComponent());
        assertNull(DeclaredTypes.parameter(model, "repo.Repo.find(", "id"));
    }

    @Test
    public void theDeclaredTypeIsOneOfTheReferences() {
        DeclaredTypes.assertContainedInReferences(model);
    }
}
