package com.hadi.test.java;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/** A type a method only ever constructs is a type the method uses. */
public class ConstructorOnlyReferenceTest {

    @Test
    public void aTypeOnlyConstructedIsReferenced() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/com/Sender.java",
                "package com;\npublic class Sender {\n  public void send() {\n    new Envelope();\n"
                + "    Object held = new com.other.Stamp(1);\n  }\n}"));
        files.insertFile(new ProjectFile("/com/Envelope.java",
                "package com;\npublic class Envelope { }"));
        files.insertFile(new ProjectFile("/com/other/Stamp.java",
                "package com.other;\npublic class Stamp { public Stamp(int value) { } }"));

        final OOPSourceCodeModel model = new ClarpseProject(files, Lang.JAVA).result().model();

        final Set<String> used = model.copyOfComponent("com.Sender.send()").orElseThrow()
                .references().stream().map(ComponentReference::invokedComponent)
                .collect(Collectors.toSet());
        assertTrue(used.toString(), used.contains("com.Envelope"));
        assertTrue(used.toString(), used.contains("com.other.Stamp"));
    }
}
