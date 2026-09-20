package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Emission of a component that names no source file. The set of emitted paths is sorted, so looking
 * a null path up in it would be rejected rather than answered; a component no file claims is left
 * out instead.
 */
public class CSharpModelAssemblerNullSourceFileTest {

    private static final String PATH = "/Core/A.cs";

    private static CSharpModel.CSharpFileModel fileModelWithAnUnattributedMember() {
        final ProjectFile file = new ProjectFile(PATH, "namespace N\n{\n    public class A { }\n}\n");
        final CSharpModel.CSharpFileModel fileModel =
                new CSharpModel.CSharpFileModel(file, file.content(), "A");
        final CSharpModel.CSharpTypeModel type = new CSharpModel.CSharpTypeModel();
        type.kind = "class";
        type.name = "A";
        type.namespaceName = "N";
        type.sourcePath = PATH;
        final CSharpModel.CSharpMemberModel attributed = new CSharpModel.CSharpMemberModel();
        attributed.kind = "field";
        attributed.name = "Known";
        attributed.sourcePath = PATH;
        final CSharpModel.CSharpMemberModel unattributed = new CSharpModel.CSharpMemberModel();
        unattributed.kind = "field";
        unattributed.name = "Unattributed";
        type.members.add(attributed);
        type.members.add(unattributed);
        fileModel.types.add(type);
        return fileModel;
    }

    @Test
    public void aComponentWithNoSourceFileIsLeftOutOfTheEmittedModel() {
        final Set<String> emitted = new TreeSet<>(Set.of(PATH));
        final OOPSourceCodeModel model = CSharpModelAssembler.buildModel(
                List.of(fileModelWithAnUnattributedMember()), List.of(), emitted);
        assertTrue(model.containsComponent("N.A"));
        assertTrue(model.containsComponent("N.A.Known"));
        assertFalse(model.containsComponent("N.A.Unattributed"));
    }
}
