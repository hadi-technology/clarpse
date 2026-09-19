package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * A comment beside a declaration's identifier is trivia, not part of the declared name:
 * line and block comments after namespace, type, method, property, field, enum-member and
 * type-parameter names are left out of component names, and references to those types
 * still resolve.
 */
public class CSharpCommentsInDeclaredNamesTest {

    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        model = CSharpTestUtil.compileInline(
                new ProjectFile("/Runtime.cs", """
                        namespace Demo.Core // runtime types
                        {
                            public interface IPubSub // Compare with: IRendezvous
                            {
                                void Publish /* topic first */ (string topic); // fire and forget
                            }
                            public interface IStore /* persistent */
                            {
                            }
                            public enum Color { Red /* warm */, Green // cool
                            }
                        }
                        """),
                new ProjectFile("/Broker.cs", """
                        namespace Demo.Core
                        {
                            public class Broker<TMessage /* payload */> : IPubSub // the default bus
                            {
                                public IPubSub Bus; // injected
                                public IStore Store /* optional */;
                                public int Count /* published */ { get; set; }
                                public void Publish(string topic) { }
                                public TMessage Take<TKey /* lookup */>(TKey key) { return default; }
                            }
                        }
                        """)
        ).model();
    }

    private static void assertComponent(final String uniqueName) {
        assertTrue("expected component " + uniqueName + " in "
                        + model.components().map(c -> c.uniqueName()).sorted().toList(),
                model.containsComponent(uniqueName));
    }

    private static void assertReferences(final String component, final String target) {
        assertTrue(component + " should reference " + target,
                model.copyOfComponent(component).get().references().stream()
                        .map(ComponentReference::invokedComponent)
                        .anyMatch(target::equals));
    }

    @Test
    public void lineCommentAfterTypeNameIsNotPartOfTheName() {
        assertComponent("Demo.Core.IPubSub");
    }

    @Test
    public void blockCommentAfterTypeNameIsNotPartOfTheName() {
        assertComponent("Demo.Core.IStore");
    }

    @Test
    public void commentAfterNamespaceNameIsNotPartOfTheNamespace() {
        assertComponent("Demo.Core.Color");
    }

    @Test
    public void commentAfterMethodNameIsNotPartOfTheName() {
        assertComponent("Demo.Core.IPubSub.Publish(string)");
    }

    @Test
    public void commentAfterPropertyAndFieldNamesIsNotPartOfTheName() {
        assertComponent("Demo.Core.Broker.Count");
        assertComponent("Demo.Core.Broker.Bus");
        assertComponent("Demo.Core.Broker.Store");
    }

    @Test
    public void commentAfterEnumMemberNameIsNotPartOfTheName() {
        assertComponent("Demo.Core.Color.Red");
        assertComponent("Demo.Core.Color.Green");
    }

    @Test
    public void commentAfterTypeParameterIsNotPartOfTheName() {
        assertComponent("Demo.Core.Broker.Take<TKey>(TKey)");
    }

    @Test
    public void referencesToACommentedTypeResolve() {
        assertReferences("Demo.Core.Broker", "Demo.Core.IPubSub");
        assertReferences("Demo.Core.Broker.Bus", "Demo.Core.IPubSub");
        assertReferences("Demo.Core.Broker.Store", "Demo.Core.IStore");
    }
}
