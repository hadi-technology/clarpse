package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CSharpPartialAndFailureTest {

    @Test
    public void partialClassesMergeMembersAcrossFiles() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/User.Part1.cs", """
                        namespace Demo;
                        public partial class User {
                          public string Name { get; set; }
                        }
                        """),
                new ProjectFile("/User.Part2.cs", """
                        namespace Demo;
                        public partial class User {
                          public void Save() {}
                        }
                        """)
        ).model();

        assertTrue(model.containsComponent("Demo.User.Name"));
        assertTrue(model.containsComponent("Demo.User.Save()"));
    }

    @Test
    public void parseFailureUsesLanguageAgnosticCode() throws Exception {
        final ProjectFiles projectFiles = new ProjectFiles();
        projectFiles.insertFile(new ProjectFile("/Broken.cs", "namespace Demo; public class User {"));

        final CompileResult result = new ClarpseProject(projectFiles, Lang.CSHARP).result();
        assertEquals(1, result.failures().size());
        assertEquals(Integer.valueOf(FailureCode.PARSE_FAILED), result.failures().iterator().next().errorCode());
    }

    @Test
    public void interpolatedStringsDoNotTriggerFalseParseFailure() throws Exception {
        final CompileResult result = CSharpTestUtil.compileInline(
                new ProjectFile("/Interpolated.cs", """
                        namespace Demo;
                        public class User {
                          public string FileFor(string id) {
                            return $"{id}.json";
                          }
                        }
                        """)
        );

        assertTrue(result.failures().isEmpty());
        assertTrue(result.model().containsComponent("Demo.User"));
        assertTrue(result.model().containsComponent("Demo.User.FileFor(string)"));
    }

    @Test
    public void verbatimStringsAndCommentsDoNotTriggerFalseParseFailure() throws Exception {
        final CompileResult result = CSharpTestUtil.compileInline(
                new ProjectFile("/Verbatim.cs",
                        "namespace Demo;\n"
                                + "public class User {\n"
                                + "  public string Query() {\n"
                                + "    // braces here should not matter: {}\n"
                                + "    var sql = @\"select * from Users where Name = \"\"{demo}\"\"\";\n"
                                + "    return sql;\n"
                                + "  }\n"
                                + "}\n")
        );

        assertTrue(result.failures().isEmpty());
        assertTrue(result.model().containsComponent("Demo.User.Query()"));
    }

    @Test
    public void stringsEndingWithEscapedBackslashDoNotTriggerFalseParseFailure() throws Exception {
        final CompileResult result = CSharpTestUtil.compileInline(
                new ProjectFile("/Paths.cs",
                        "namespace Demo;\n"
                                + "public class User {\n"
                                + "  public string Build(string company) {\n"
                                + "    var part = $\"{company}\\\\\";\n"
                                + "    return part;\n"
                                + "  }\n"
                                + "}\n")
        );

        assertTrue(result.failures().isEmpty());
        assertTrue(result.model().containsComponent("Demo.User.Build(string)"));
    }
}
