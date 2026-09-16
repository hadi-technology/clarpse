package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A component's comment is the text the author wrote, not the syntax that introduced it.
 */
public class CSharpCommentMarkersTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Comments.cs", """
                        namespace Demo;
                        /// <summary>
                        /// Wraps a subscription and delivers events in order.
                        /// </summary>
                        public class StreamListener {
                          // Plain comment, see https://example.com/docs
                          public void Go() {}
                        }
                        """)
        ).model();
    }

    @Test
    public void docCommentLosesItsMarkers() {
        assertEquals("<summary>\nWraps a subscription and delivers events in order.\n</summary>\n",
                model.copyOfComponent("Demo.StreamListener").get().comment());
    }

    @Test
    public void plainLineCommentLosesItsMarker() {
        assertEquals("Plain comment, see https://example.com/docs\n",
                model.copyOfComponent("Demo.StreamListener.Go()").get().comment());
    }

    @Test
    public void aUrlInsideACommentSurvives() {
        assertTrue("only the leading marker is removed, not every // in the line",
                model.copyOfComponent("Demo.StreamListener.Go()").get().comment()
                        .contains("https://example.com/docs"));
    }
}
