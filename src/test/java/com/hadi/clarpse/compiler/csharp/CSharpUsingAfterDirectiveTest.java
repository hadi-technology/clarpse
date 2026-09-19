package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * After a leading preprocessor directive the parser produces using directives as using statements.
 * Those with a directive shape are read as usings; a using declaration is not.
 *
 * <p>In the C# package so it can read the package-private file model.
 */
public class CSharpUsingAfterDirectiveTest {

    @Test
    public void directiveShapedUsingsAfterADirectiveAreUsings() {
        final CSharpModel.CSharpFileModel model = parse("""
                #nullable enable
                using System.Text;
                using static System.Math;
                using Io = System.IO;
                global using System.Linq;
                namespace Demo { public class Widget { } }
                """);
        assertEquals(List.of("System.Text", "System.Math", "System.IO", "System.Linq"), targets(model));
        final CSharpModel.CSharpUsingModel alias = model.usings.get(2);
        assertTrue(alias.aliasImport);
        assertEquals("Io", alias.alias);
        assertTrue(model.usings.get(1).staticImport);
        assertTrue(model.usings.get(3).globalImport);
    }

    @Test
    public void usingDeclarationInATopLevelProgramIsNotAUsing() {
        final CSharpModel.CSharpFileModel model = parse("""
                #nullable enable
                using System.IO;
                using var stream = File.OpenRead("widgets.txt");
                using var copy = stream;
                """);
        assertEquals(List.of("System.IO"), targets(model));
    }

    private static CSharpModel.CSharpFileModel parse(final String source) {
        final CSharpModel.ParseOutcome outcome =
                CSharpFileParser.parseFile(new ProjectFile("/Program.cs", source), 0);
        assertEquals(null, outcome.failure());
        return outcome.fileModel();
    }

    private static List<String> targets(final CSharpModel.CSharpFileModel model) {
        return model.usings.stream().map(using -> using.target).toList();
    }
}
