package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * A using directive in any alternative of an {@code #if} group is a using, wherever the parser
 * places it: as a using statement, or inside the header of a namespace whose declaration the
 * alternatives split.
 *
 * <p>In the C# package so it can read the package-private file model.
 */
public class CSharpUsingInConditionalTest {

    @Test
    public void usingInElifBetweenNamespaceHeadersIsAUsing() {
        assertEquals(List.of("Other.Lib"), targets(parse("""
                #if A
                namespace Demo.App
                #elif B
                using Other.Lib;
                namespace Demo.App
                #endif
                {
                    public class Svc { private Helper h; }
                }
                """)));
    }

    @Test
    public void usingInElseBetweenNamespaceHeadersIsAUsing() {
        assertEquals(List.of("Other.Lib"), targets(parse("""
                #if A
                namespace Demo.App
                #else
                using Other.Lib;
                namespace Demo.App
                #endif
                {
                    public class Svc { private Helper h; }
                }
                """)));
    }

    @Test
    public void usingAfterSeveralAlternativeHeadersAndACommentIsAUsing() {
        assertEquals(List.of("Other.Lib"), targets(parse("""
                #if A
                namespace Demo.App
                #elif B
                namespace Demo.App
                #else
                // the fallback configuration
                using Other.Lib;
                namespace Demo.App
                #endif
                {
                    public class Svc { private Helper h; }
                }
                """)));
    }

    @Test
    public void everyUsingOfALaterAlternativeIsAUsing() {
        final CSharpModel.CSharpFileModel model = parse("""
                #if A
                namespace Demo.App
                #elif B
                using Other.Lib;
                using static Third.Lib.Helper;
                using H = Other.Lib.Helper;
                namespace Demo.App
                #endif
                {
                    public class Svc { private Helper h; }
                }
                """);
        assertEquals(List.of("Other.Lib", "Third.Lib.Helper", "Other.Lib.Helper"), targets(model));
    }

    @Test
    public void namespaceHeaderWithoutAUsingAddsNone() {
        assertEquals(List.of(), targets(parse("""
                #if A
                namespace Demo.App
                #else
                namespace Demo.Other
                #endif
                {
                    public class Svc { }
                }
                """)));
    }

    @Test
    public void typesKeepTheFirstAlternativesNamespace() {
        final CSharpModel.CSharpFileModel model = parse("""
                #if A
                namespace Demo.App
                #elif B
                using Other.Lib;
                namespace Demo.Other
                #endif
                {
                    public class Svc { }
                }
                """);
        assertEquals("Demo.App", model.types.get(0).namespaceName);
    }

    private static CSharpModel.CSharpFileModel parse(final String source) {
        final CSharpModel.ParseOutcome outcome =
                CSharpFileParser.parseFile(new ProjectFile("/Svc.cs", source), 0);
        assertEquals(null, outcome.failure());
        return outcome.fileModel();
    }

    private static List<String> targets(final CSharpModel.CSharpFileModel model) {
        return model.usings.stream().map(using -> using.target).toList();
    }
}
