package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * References that follow the rules the other languages already keep: a name binds to a type only
 * where the language's scoping lets it, a namespace is never a type, and what a construct names is
 * recorded once it is provable.
 */
public class CSharpCrossLanguageParityTest {

    private static OOPSourceCodeModel model(final ProjectFile... files) throws Exception {
        return CSharpTestUtil.compileInline(files).model();
    }

    private static Set<ComponentReference> references(final OOPSourceCodeModel model, final String component) {
        final Component found = model.copyOfComponent(component).orElseThrow(
                () -> new AssertionError("no component " + component + " in "
                        + model.components().map(Component::uniqueName).collect(Collectors.toList())));
        return found.references();
    }

    private static boolean references(final OOPSourceCodeModel model, final String from, final String to) {
        return references(model, from).stream().anyMatch(r -> to.equals(r.invokedComponent()));
    }

    private static ResolutionKind kindOf(final OOPSourceCodeModel model, final String from, final String to) {
        return references(model, from).stream().filter(r -> to.equals(r.invokedComponent()))
                .map(ComponentReference::resolutionKind).findFirst()
                .orElseThrow(() -> new AssertionError(from + " has no reference to " + to));
    }

    // ---- an inherited member is not a type ----

    private static final ProjectFile USER_TYPE = new ProjectFile("/User.cs",
            "namespace App { public class User { public static object FindFirst(string s) { return null; } } }");

    /**
     * {@code User.FindFirst()} in a controller is the inherited {@code ControllerBase.User} property.
     * Nothing in the parse shows whether the unparsed base declares it, so the bare name binds to no type.
     */
    @Test
    public void aBareReceiverInAClassWithAnUnparsedBaseIsNotReadAsAType() throws Exception {
        final OOPSourceCodeModel model = model(USER_TYPE, new ProjectFile("/C.cs", """
                using Microsoft.AspNetCore.Mvc;
                namespace App {
                  public class UsersController : ControllerBase {
                    public object Get() { return User.FindFirst("sub"); }
                  }
                }
                """));
        assertFalse(references(model, "App.UsersController.Get()", "App.User"));
        assertFalse(references(model, "App.UsersController", "App.User"));
    }

    /** The unparsed base may be several steps up: the guard reads the whole ancestry. */
    @Test
    public void anUnparsedBaseHigherUpTheAncestryAlsoGuards() throws Exception {
        final OOPSourceCodeModel model = model(USER_TYPE,
                new ProjectFile("/B.cs", "namespace App { public class Middle : Framework.Base { } }"),
                new ProjectFile("/C.cs", """
                        namespace App {
                          public class Leaf : Middle {
                            public object Get() { return User.FindFirst("sub"); }
                          }
                        }
                        """));
        assertFalse(references(model, "App.Leaf.Get()", "App.User"));
    }

    /** A type nested in the class hides an inherited member of its name, so the guard has nothing to protect. */
    @Test
    public void aNestedTypeOfTheClassIsStillRead() throws Exception {
        final OOPSourceCodeModel model = model(new ProjectFile("/C.cs", """
                namespace App {
                  public class Holder : Framework.Base {
                    public class Helper { public static void Go() { } }
                    public void Run() { Helper.Go(); }
                  }
                }
                """));
        assertTrue(references(model, "App.Holder.Run()", "App.Holder.Helper"));
    }

    /** A receiver written with its namespace, or through an alias, names a type whatever the base declares. */
    @Test
    public void aQualifiedOrAliasedReceiverIsStillRead() throws Exception {
        final OOPSourceCodeModel model = model(USER_TYPE, new ProjectFile("/C.cs", """
                using Account = App.User;
                namespace Web {
                  public class UsersController : Microsoft.AspNetCore.Mvc.ControllerBase {
                    public object Qualified() { return App.User.FindFirst("sub"); }
                    public object Aliased() { return Account.FindFirst("sub"); }
                  }
                }
                """));
        assertTrue(references(model, "Web.UsersController.Qualified()", "App.User"));
        assertTrue(references(model, "Web.UsersController.Aliased()", "App.User"));
    }

    /** With every ancestor parsed, the members a class can have are known, and a static call resolves. */
    @Test
    public void aClassWithAFullyParsedAncestryStillResolvesStaticCalls() throws Exception {
        final OOPSourceCodeModel model = model(USER_TYPE,
                new ProjectFile("/B.cs", "namespace App { public class Base { } public interface IThing { } }"),
                new ProjectFile("/C.cs", """
                        namespace App {
                          public class Service : Base, IThing {
                            public object Get() { return User.FindFirst("sub"); }
                          }
                        }
                        """));
        assertEquals(ResolutionKind.EXACT, kindOf(model, "App.Service.Get()", "App.User"));
    }
}
