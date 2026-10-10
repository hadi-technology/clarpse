package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Set;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static com.hadi.test.kotlin.KotlinTestUtil.targets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A Kotlin type holds the references its members make, as a Java type does: those of its
 * properties, functions, parameters, locals and nested types, and a function holds those of its
 * parameters and locals. A nested type's supertypes are its own, and never the enclosing type's.
 */
public class KotlinReferenceInheritanceTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void compile() throws Exception {
        model = KotlinTestUtil.compileInline(
                new ProjectFile("/a/Types.kt", String.join("\n",
                        "package a",
                        "",
                        "open class Base",
                        "interface Marker",
                        "class FieldType",
                        "class ReturnType",
                        "class ParamType",
                        "class LocalType",
                        "class NestedType",
                        "class InterfaceType")),
                new ProjectFile("/a/Holders.kt", String.join("\n",
                        "package a",
                        "",
                        "class WithField { val field: FieldType? = null }",
                        "class WithMethod { fun m(): ReturnType? = null }",
                        "class WithParam { fun m(p: ParamType) {} }",
                        "class WithLocal { fun m() { val local: LocalType? = null } }",
                        "class WithNested { class Nested { fun m(p: NestedType) {} } }",
                        "class WithNestedHeritage { class Nested : Base(), Marker }",
                        "interface WithInterfaceMembers {",
                        "    val property: InterfaceType",
                        "    fun m(p: ParamType): ReturnType",
                        "}",
                        "object WithObjectMember { fun m(p: ParamType) {} }"))
        ).model();
    }

    @Test
    public void aClassHoldsItsPropertiesReferences() {
        assertTrue(targets(component(model, "a.WithField")).contains("a.FieldType"));
    }

    @Test
    public void aClassHoldsItsFunctionsReferences() {
        assertTrue(targets(component(model, "a.WithMethod")).contains("a.ReturnType"));
    }

    @Test
    public void aClassAndItsFunctionHoldTheParametersReferences() {
        assertTrue(targets(component(model, "a.WithParam.m(ParamType)")).contains("a.ParamType"));
        assertTrue(targets(component(model, "a.WithParam")).contains("a.ParamType"));
    }

    @Test
    public void aClassAndItsFunctionHoldTheLocalsReferences() {
        assertTrue(targets(component(model, "a.WithLocal.m()")).contains("a.LocalType"));
        assertTrue(targets(component(model, "a.WithLocal")).contains("a.LocalType"));
    }

    @Test
    public void aClassHoldsItsNestedTypesReferences() {
        assertTrue(targets(component(model, "a.WithNested.Nested")).contains("a.NestedType"));
        assertTrue(targets(component(model, "a.WithNested")).contains("a.NestedType"));
    }

    @Test
    public void aClassDoesNotTakeOnANestedTypesSupertypes() {
        assertTrue(targets(component(model, "a.WithNestedHeritage")).isEmpty());
        assertEquals(1, component(model, "a.WithNestedHeritage.Nested").references(TypeReferences.EXTENSION).size());
        assertEquals(1, component(model, "a.WithNestedHeritage.Nested")
                .references(TypeReferences.IMPLEMENTATION).size());
    }

    @Test
    public void anInterfaceHoldsItsMembersReferences() {
        assertTrue(targets(component(model, "a.WithInterfaceMembers")).containsAll(
                Set.of("a.InterfaceType", "a.ParamType", "a.ReturnType")));
    }

    @Test
    public void anObjectHoldsItsMembersReferences() {
        assertTrue(targets(component(model, "a.WithObjectMember")).contains("a.ParamType"));
    }
}
