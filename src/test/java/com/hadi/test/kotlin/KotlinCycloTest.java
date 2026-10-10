package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static org.junit.Assert.assertEquals;

/**
 * The cyclomatic complexity of a Kotlin function: one, plus one for every {@code if}, loop,
 * {@code catch}, {@code &&}, {@code ||} and elvis, and one for every {@code when} entry other than
 * {@code else}. A class's is the mean of its functions', as it is for Java.
 */
public class KotlinCycloTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(new ProjectFile("/c/Cy.kt", String.join("\n",
                "package c",
                "",
                "class Cy {",
                "    fun plain(): Int = 1",
                "    fun oneIf(a: Int): Int { if (a > 0) return 1; return 0 }",
                "    fun elseIf(a: Int): Int { if (a > 0) return 1 else if (a < 0) return 2 else return 0 }",
                "    fun ifExpression(a: Int): Int = if (a > 0) 1 else 0",
                "    fun forLoop(xs: List<Int>) { for (x in xs) { } }",
                "    fun whileLoop(a: Int) { var b = a; while (b > 0) { b-- } }",
                "    fun doWhile(a: Int) { var b = a; do { b-- } while (b > 0) }",
                "    fun andOr(a: Boolean, b: Boolean, c: Boolean): Boolean = a && b || c",
                "    fun elvis(a: Int?): Int = a ?: 0",
                "    fun safeCall(a: String?): Int? = a?.length",
                "    fun catches() {",
                "        try { } catch (e: IllegalStateException) { } catch (e: Exception) { } finally { }",
                "    }",
                "    fun whenWithElse(a: Int): Int = when (a) { 1 -> 1; 2, 3 -> 2; else -> 0 }",
                "    fun whenWithoutSubject(a: Int): Int = when { a > 0 -> 1; a < 0 -> 2; else -> 0 }",
                "    fun inALambda(xs: List<Int>): List<Int> = xs.filter { if (it > 0) true else false }",
                "}",
                "",
                "class Even { fun a(x: Int): Int { if (x > 0) return 1; return 0 }; fun b(): Int = 1 }"))).model();
    }

    private static int cyclo(final String method) {
        return component(model, "c.Cy." + method).cyclo();
    }

    @Test
    public void aFunctionWithoutBranchesHasOne() {
        assertEquals(1, cyclo("plain()"));
    }

    @Test
    public void eachIfAddsOne() {
        assertEquals(2, cyclo("oneIf(Int)"));
        assertEquals(3, cyclo("elseIf(Int)"));
        assertEquals(2, cyclo("ifExpression(Int)"));
    }

    @Test
    public void eachLoopAddsOne() {
        assertEquals(2, cyclo("forLoop(List<Int>)"));
        assertEquals(2, cyclo("whileLoop(Int)"));
        assertEquals(2, cyclo("doWhile(Int)"));
    }

    @Test
    public void eachShortCircuitOperatorAddsOne() {
        assertEquals(3, cyclo("andOr(Boolean, Boolean, Boolean)"));
        assertEquals(2, cyclo("elvis(Int?)"));
    }

    @Test
    public void aSafeCallIsNotABranch() {
        assertEquals(1, cyclo("safeCall(String?)"));
    }

    @Test
    public void eachCatchAddsOneAndFinallyNone() {
        assertEquals(3, cyclo("catches()"));
    }

    @Test
    public void eachWhenEntryOtherThanElseAddsOne() {
        assertEquals(3, cyclo("whenWithElse(Int)"));
        assertEquals(3, cyclo("whenWithoutSubject(Int)"));
    }

    @Test
    public void branchesInALambdaCountForTheFunctionHoldingIt() {
        assertEquals(2, cyclo("inALambda(List<Int>)"));
    }

    @Test
    public void aClassesComplexityIsTheMeanOfItsFunctions() {
        assertEquals(1, component(model, "c.Even").cyclo());
    }
}
