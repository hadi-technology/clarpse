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
import static org.junit.Assert.assertTrue;

/**
 * A method whose signature and body are separated by a preprocessor directive is modelled like any
 * other method of its type.
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

    private static List<String> components(final String source) throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/Source.cs", source));
        final CompileResult result = new ClarpseProject(files, Lang.CSHARP).result();
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        return result.model().components()
                .map(component -> component.uniqueName())
                .sorted()
                .collect(Collectors.toList());
    }
}
