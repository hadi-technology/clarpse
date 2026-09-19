package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A file saved as UTF-8 with a byte-order mark, and a file that opens with a preprocessor
 * directive, must yield the same types as the plain file.
 */
public class CSharpByteOrderMarkAndDirectivesTest {

    private static final String BOM = "\uFEFF";

    private static final String WIDGET_BODY = """
            namespace Demo
            {
                public class Widget
                {
                }
            }
            """;

    private static final String SHOP = """
            namespace Demo
            {
                public class Shop
                {
                    public Widget Item;
                }
            }
            """;

    /** The bytes EF BB BF read from disk, followed by `#pragma`, as an editor saves them. */
    @Test
    public void aFileReadFromDiskWithABomAndPragmaYieldsItsTypes() throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        bytes.write(("#pragma warning disable CS1591\n" + WIDGET_BODY).getBytes(StandardCharsets.UTF_8));
        final File widget = File.createTempFile("Widget", ".cs");
        widget.deleteOnExit();
        Files.write(widget.toPath(), bytes.toByteArray());
        final ProjectFile widgetFile = new ProjectFile(widget);
        assertTrue(widgetFile.content().startsWith(BOM + "#pragma"));

        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Widget.cs", widgetFile.content()),
                new ProjectFile("/Shop.cs", SHOP)
        ).model();

        assertTrue(model.containsComponent("Demo.Widget"));
        assertResolvesToWidget(model.copyOfComponent("Demo.Shop.Item").orElseThrow());
    }

    @Test
    public void bomFollowedByNullableYieldsItsTypes() throws Exception {
        assertWidgetExtracted(BOM + "#nullable enable\n" + WIDGET_BODY);
    }

    @Test
    public void nullableWithoutBomYieldsItsTypes() throws Exception {
        assertWidgetExtracted("#nullable enable\n" + WIDGET_BODY);
    }

    @Test
    public void bomFollowedByRegionYieldsItsTypes() throws Exception {
        assertWidgetExtracted(BOM + "#region Header\n" + WIDGET_BODY + "#endregion\n");
    }

    @Test
    public void regionWithoutBomYieldsItsTypes() throws Exception {
        assertWidgetExtracted("#region Header\n" + WIDGET_BODY + "#endregion\n");
    }

    @Test
    public void bomFollowedByDefineYieldsItsTypes() throws Exception {
        assertWidgetExtracted(BOM + "#define TRACE_WIDGETS\n" + WIDGET_BODY);
    }

    @Test
    public void bomWithoutDirectiveYieldsItsTypes() throws Exception {
        assertWidgetExtracted(BOM + WIDGET_BODY);
    }

    /**
     * A directive that does not start its line is not lexed as one, and the parser wraps the
     * namespace after it in a statement node; the types inside are still declared.
     */
    @Test
    public void namespaceWrappedAfterAnUnrecognisedDirectiveYieldsItsTypes() throws Exception {
        assertWidgetExtracted("/* header */ #pragma warning disable CS1591\n" + WIDGET_BODY);
    }

    /** A leading directive must not swallow the file's usings either. */
    @Test
    public void bomFollowedByPragmaKeepsUsingsOfAFileScopedNamespace() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Widget.cs", BOM + """
                        #pragma warning disable CS1591
                        using System.Text;
                        namespace Demo;
                        public class Widget
                        {
                            public StringBuilder Buffer;
                        }
                        """)
        ).model();
        final Component widget = model.copyOfComponent("Demo.Widget").orElseThrow();
        assertTrue(widget.imports().contains("System.Text"));
        assertTrue(model.containsComponent("Demo.Widget.Buffer"));
    }

    /** A using directive after a leading directive is still a using directive. */
    @Test
    public void usingsAfterALeadingDirectiveAreImports() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Widget.cs", """
                        #region License
                        #endregion
                        #nullable enable
                        using System.Text;
                        using static System.Math;
                        namespace Demo
                        {
                            public class Widget
                            {
                                public StringBuilder Buffer;
                            }
                        }
                        """)
        ).model();
        final Component widget = model.copyOfComponent("Demo.Widget").orElseThrow();
        assertTrue(widget.imports().contains("System.Text"));
        assertTrue(widget.imports().contains("System.Math"));
    }

    @Test
    public void fileWithoutContentYieldsNoTypesAndNoFailure() throws Exception {
        final ProjectFile empty = new ProjectFile("/Empty.cs", "");
        empty.content(null);
        final CompileResult result = CSharpTestUtil.compileInline(
                empty,
                new ProjectFile("/Widget.cs", BOM + WIDGET_BODY)
        );
        assertTrue(result.model().containsComponent("Demo.Widget"));
        assertTrue(result.failures().isEmpty());
    }

    /** Offsets are taken on the text the parser saw, so a member's code fragment carries no BOM. */
    @Test
    public void bomDoesNotLeakIntoCodeFragments() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Widget.cs", BOM + "#pragma warning disable CS1591\n" + WIDGET_BODY),
                new ProjectFile("/Shop.cs", SHOP)
        ).model();
        model.components().forEach(component -> {
            final String fragment = component.codeFragment();
            assertFalse(fragment != null && fragment.contains(BOM));
        });
    }

    private static void assertWidgetExtracted(final String widgetSource) throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Widget.cs", widgetSource),
                new ProjectFile("/Shop.cs", SHOP)
        ).model();
        assertTrue(model.containsComponent("Demo.Widget"));
        assertEquals("/Widget.cs", model.copyOfComponent("Demo.Widget").orElseThrow().sourceFile());
        assertResolvesToWidget(model.copyOfComponent("Demo.Shop.Item").orElseThrow());
    }

    private static void assertResolvesToWidget(final Component field) {
        boolean resolved = false;
        for (final ComponentReference reference : field.references()) {
            if ("Demo.Widget".equals(reference.invokedComponent()) && !reference.isExternal()) {
                resolved = true;
            }
        }
        assertTrue("expected " + field.uniqueName() + " to resolve Widget to Demo.Widget", resolved);
    }
}
