package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A member whose signature and body are separated by a preprocessor directive is modelled like any
 * other member of its type.
 */
public class CSharpPreprocessorBodyTest {

    @Test
    public void interfaceMethodWithDirectiveBeforeItsBodyIsModelled() throws Exception {
        assertEquals(List.of("I", "I.M1()", "I.M5()", "I.M6()"), components("""
                interface I {
                  void M1();
                  void M5()
                #if NET
                  { }
                #else
                  ;
                #endif
                  void M6();
                }
                """));
    }

    @Test
    public void classMethodWithDirectiveBeforeItsBodyIsModelled() throws Exception {
        assertEquals(List.of("C", "C.M1()", "C.M5()", "C.M6()"), components("""
                class C {
                  void M1() { }
                  void M5()
                #if NET
                  { }
                #else
                  { }
                #endif
                  void M6() { }
                }
                """));
    }

    @Test
    public void directiveAroundAWholeMemberDoesNotHideIt() throws Exception {
        assertEquals(List.of("I", "I.M1()", "I.M5()", "I.M6()"), components("""
                interface I {
                  void M1();
                #if NET
                  void M5();
                #endif
                  void M6();
                }
                """));
    }

    @Test
    public void methodWithoutADirectiveIsModelled() throws Exception {
        assertEquals(List.of("I", "I.M1()", "I.M5()", "I.M6()"), components("""
                interface I {
                  void M1();
                  void M5();
                  void M6();
                }
                """));
    }

    @Test
    public void modifiedMethodSplitByDirectiveKeepsItsSignature() {
        final CSharpModel.CSharpMemberModel method = member("""
                class C {
                  [Obsolete]
                  public static async Task<int> M<T>(int a, string b) where T : Constraint, IThing
                #if NET
                  { return null; }
                #else
                  { return null; }
                #endif
                }
                class Constraint { }
                interface IThing { }
                """, "C", "M<T>");
        assertEquals("Task<int>", method.returnType);
        assertEquals(List.of("public", "static", "async"), method.modifiers);
        assertEquals(List.of("int", "string"),
                method.parameters.stream().map(parameter -> parameter.declaredType).toList());
        assertEquals(List.of("Constraint", "IThing"), method.constraintTypes);
        assertEquals(List.of("Obsolete"),
                method.annotations.stream().map(annotation -> annotation.name()).toList());
    }

    @Test
    public void methodWithReturnTypeParametersAndConstraintsSplitByDirectiveKeepsThem() {
        final CSharpModel.CSharpMemberModel method = member("""
                class C {
                  int M<T>(int a, string b) where T : Constraint, IThing
                #if NET
                  { return a; }
                #else
                  { return 0; }
                #endif
                }
                class Constraint { }
                interface IThing { }
                """, "C", "M<T>");
        assertEquals("int", method.returnType);
        assertEquals(List.of("int", "string"),
                method.parameters.stream().map(parameter -> parameter.declaredType).toList());
        assertEquals(List.of("Constraint", "IThing"), method.constraintTypes);
    }

    @Test
    public void constructorSplitByDirectiveStillParses() throws Exception {
        final CompileResult result = compile("""
                class C {
                  public C()
                #if NET
                  { }
                #else
                  { }
                #endif
                  void M() { }
                }
                """);
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        assertTrue(result.model().containsComponent("C.C()"));
        assertTrue(result.model().containsComponent("C.M()"));
    }

    @Test
    public void unrecognisedMemberSplitByDirectiveDoesNotFailTheFile() throws Exception {
        final CompileResult result = compile("""
                class C {
                  ~C()
                #if NET
                  { }
                #else
                  { }
                #endif
                  void M() { }
                }
                """);
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        assertTrue(result.model().containsComponent("C.M()"));
    }

    private static CompileResult compile(final String source) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/Source.cs", source));
        return new ClarpseProject(files, Lang.CSHARP).result();
    }

    private static List<String> components(final String source) throws Exception {
        final CompileResult result = compile(source);
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        return result.model().components()
                .map(component -> component.uniqueName())
                .sorted()
                .collect(Collectors.toList());
    }

    private static CSharpModel.CSharpMemberModel member(final String source, final String typeName,
                                                       final String memberName) {
        final CSharpModel.ParseOutcome outcome =
                CSharpFileParser.parseFile(new ProjectFile("/Source.cs", source), 0);
        assertNull(outcome.failure());
        final CSharpModel.CSharpTypeModel type = outcome.fileModel().types.stream()
                .filter(candidate -> candidate.name.equals(typeName))
                .findFirst()
                .orElseThrow();
        return type.members.stream()
                .filter(candidate -> candidate.name.equals(memberName))
                .findFirst()
                .orElseThrow();
    }
}
