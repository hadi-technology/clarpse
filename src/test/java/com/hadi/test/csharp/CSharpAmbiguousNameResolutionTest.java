package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which type a plain name refers to, when several types share it.
 *
 * <p>Resolution used to end in a repository-wide guess that returned whichever same-named type was
 * registered first, and that guess was consulted <em>before</em> the file's own {@code using}
 * directives. Measured across 28 repositories, 222 of 1,490 decidable C# references onto an
 * ambiguous short name bound to the wrong type — 57% on ardalis/CleanArchitecture and 34% on
 * kgrzybek/modular-monolith-with-ddd, the codebases that duplicate a type per module, which is
 * exactly the shape a module-boundary rule is written about.
 *
 * <p>These are not cosmetic: a fabricated edge became the cited evidence under a "this change
 * breaks a rule your own documentation states" verdict shown to a maintainer.
 */
public class CSharpAmbiguousNameResolutionTest {

    private static Set<String> refs(final OOPSourceCodeModel model, final String component) {
        final Component cmp = model.copyOfComponent(component).orElseThrow(
                () -> new AssertionError("no component " + component + " in "
                        + model.components().map(Component::uniqueName).collect(Collectors.toList())));
        return cmp.references().stream().map(r -> r.invokedComponent()).collect(Collectors.toSet());
    }

    /**
     * Every module declaring its own base type is the modular-monolith pattern, not an accident.
     * The importing file names one of them and must get that one.
     */
    @Test
    public void aUsingDirectiveOutranksASameNamedTypeElsewhere() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Admin/Contracts/CommandBase.cs",
                        "namespace App.Modules.Admin.Contracts;\npublic class CommandBase { }\n"),
                new ProjectFile("/Meetings/Contracts/CommandBase.cs",
                        "namespace App.Modules.Meetings.Contracts;\npublic class CommandBase { }\n"),
                new ProjectFile("/Meetings/Commands/RemoveComment.cs",
                        "using App.Modules.Meetings.Contracts;\n"
                        + "namespace App.Modules.Meetings.Commands;\n"
                        + "public class RemoveComment : CommandBase { }\n")).model();

        final Set<String> references = refs(model, "App.Modules.Meetings.Commands.RemoveComment");
        assertTrue("should bind the CommandBase named by the using directive",
                references.contains("App.Modules.Meetings.Contracts.CommandBase"));
        assertFalse("must not bind another module's identically named type",
                references.contains("App.Modules.Admin.Contracts.CommandBase"));
    }

    /**
     * A repository class may share a name with a framework generic. {@code List<T>} in a Domain
     * entity bound to a user endpoint class called {@code List} in another project, and those two
     * fabricated edges were the witnesses of a false layering violation on
     * ardalis/CleanArchitecture.
     */
    @Test
    public void aFrameworkGenericIsNotCapturedByASameNamedUserClass() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Web/Contributors/List.cs",
                        "namespace App.Web.Contributors;\npublic class List { }\n"),
                new ProjectFile("/Domain/Order.cs",
                        "namespace App.Domain;\n"
                        + "public class Order {\n"
                        + "  private readonly List<OrderItem> items = new();\n"
                        + "}\n"),
                new ProjectFile("/Domain/OrderItem.cs",
                        "namespace App.Domain;\npublic class OrderItem { }\n")).model();

        assertFalse("the BCL generic must not bind to a user class of the same name",
                refs(model, "App.Domain.Order").contains("App.Web.Contributors.List"));
    }

    /**
     * The guess is refused, not replaced by a different guess: a name carried by exactly one type
     * still resolves, so ordinary code loses nothing.
     */
    @Test
    public void anUnambiguousNameStillResolvesWithoutAUsing() throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/Domain/Repo.cs",
                        "namespace App.Domain;\npublic class UniqueRepo { }\n"),
                new ProjectFile("/Web/Handler.cs",
                        "namespace App.Web;\npublic class Handler : UniqueRepo { }\n")).model();

        assertTrue(refs(model, "App.Web.Handler").contains("App.Domain.UniqueRepo"));
    }
}
