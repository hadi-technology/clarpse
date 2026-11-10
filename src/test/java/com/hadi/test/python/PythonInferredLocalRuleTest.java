package com.hadi.test.python;

import org.junit.Assert;
import org.junit.Test;

import static com.hadi.test.python.PythonInlineProject.lines;

/** A Python local without an annotation records what the annotation of its inferred class would. */
public class PythonInferredLocalRuleTest {

    private static PythonInlineProject project() throws Exception {
        return PythonInlineProject.compile("/pkg/rule.py", lines(
                "def inferred():",
                "    number = 1",
                "    text = 'x'",
                "    words = []",
                "    flag = True",
                "    nothing = None",
                "    return number",
                "def written():",
                "    number: int = 1",
                "    text: str = 'x'",
                "    words: list = []",
                "    flag: bool = True",
                "    return number",
                ""));
    }

    @Test
    public void aBuiltinClassIsRecordedAsItsAnnotationIs() throws Exception {
        final PythonInlineProject project = project();
        for (final String local : new String[] {"number", "text", "words", "flag"}) {
            Assert.assertFalse(local, project.localReferences("pkg.rule.written", local).isEmpty());
            Assert.assertEquals(local, project.localReferences("pkg.rule.written", local),
                    project.localReferences("pkg.rule.inferred", local));
        }
    }

    @Test
    public void noneIsNotAClassTheLocalDependsOn() throws Exception {
        Assert.assertTrue(project().localReferences("pkg.rule.inferred", "nothing").isEmpty());
    }
}
