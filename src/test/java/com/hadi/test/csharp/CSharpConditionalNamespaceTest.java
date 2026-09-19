package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * A file that chooses its namespace with {@code #if}/{@code #elif}/{@code #else} declares its types
 * under the namespace of the first alternative, and never under several alternatives joined into one.
 */
public class CSharpConditionalNamespaceTest {

    private static List<String> classNames(final String source) throws Exception {
        final OOPSourceCodeModel model = CSharpTestUtil.compileInline(
                new ProjectFile("/src/Shared/Queries.cs", source)).model();
        return model.components()
                .filter(component -> component.componentType() == OOPSourceModelConstants.ComponentType.CLASS)
                .map(Component::uniqueName)
                .sorted()
                .collect(Collectors.toList());
    }

    @Test
    public void firstAlternativeNamesTheNamespace() throws Exception {
        assertEquals(List.of("Demo.Clustering.Storage.Queries"), classNames("""
                #if CLUSTERING
                namespace Demo.Clustering.Storage
                #elif REMINDERS
                namespace Demo.Reminders.Storage
                #elif PERSISTENCE
                namespace Demo.Persistence.Storage
                #endif
                {
                    public class Queries
                    {
                    }
                }
                """));
    }

    @Test
    public void usingInLaterAlternativeDoesNotJoinNamespaces() throws Exception {
        assertEquals(List.of("Demo.Clustering.Storage.Queries"), classNames("""
                #if CLUSTERING
                namespace Demo.Clustering.Storage
                #elif REMINDERS
                using Demo.Reminders.Converters;
                namespace Demo.Reminders.Storage
                #endif
                {
                    public class Queries
                    {
                    }
                }
                """));
    }

    @Test
    public void usingInEveryAlternativeDoesNotJoinNamespaces() throws Exception {
        assertEquals(List.of("Demo.Clustering.Storage.Queries"), classNames("""
                #if CLUSTERING
                using Demo.Clustering.Converters;
                namespace Demo.Clustering.Storage
                #else
                using Demo.Reminders.Converters;
                namespace Demo.Reminders.Storage
                #endif
                {
                    public class Queries
                    {
                    }
                }
                """));
    }

    @Test
    public void elseAlternativeWithUsingDoesNotJoinNamespaces() throws Exception {
        assertEquals(List.of("Demo.Clustering.Storage.Queries"), classNames("""
                #if CLUSTERING
                namespace Demo.Clustering.Storage
                #else
                using Demo.Reminders.Converters;
                namespace Demo.Reminders.Storage
                #endif
                {
                    public class Queries
                    {
                    }
                }
                """));
    }

    @Test
    public void nestedConditionalTakesFirstAlternativeOfEachGroup() throws Exception {
        assertEquals(List.of("Demo.Clustering.Storage.Queries"), classNames("""
                #if CLUSTERING
                #if LEGACY
                namespace Demo.Clustering.Storage
                #else
                using Demo.Clustering.Converters;
                namespace Demo.Clustering.Modern
                #endif
                #else
                using Demo.Reminders.Converters;
                namespace Demo.Reminders.Storage
                #endif
                {
                    public class Queries
                    {
                    }
                }
                """));
    }

    @Test
    public void fileScopedAlternativesTakeTheFirst() throws Exception {
        assertEquals(List.of("Demo.Clustering.Storage.Queries"), classNames("""
                #if CLUSTERING
                namespace Demo.Clustering.Storage;
                #else
                namespace Demo.Reminders.Storage;
                #endif
                public class Queries
                {
                }
                """));
    }

    @Test
    public void typesInLaterAlternativesStayModelledUnderTheFirstNamespace() throws Exception {
        assertEquals(List.of("Demo.Clustering.Storage.ClusteringOnly", "Demo.Clustering.Storage.RemindersOnly"),
                classNames("""
                #if CLUSTERING
                namespace Demo.Clustering.Storage
                #else
                using Demo.Reminders.Converters;
                namespace Demo.Reminders.Storage
                #endif
                {
                #if CLUSTERING
                    public class ClusteringOnly { }
                #else
                    public class RemindersOnly { }
                #endif
                }
                """));
    }

    @Test
    public void blockNamespaceInsideFileScopedNamespaceIsNotCombined() throws Exception {
        assertEquals(List.of("Demo.Feature.Queries"), classNames("""
                namespace Demo.Feature;
                namespace Other.Place
                {
                    public class Queries { }
                }
                """));
    }

    @Test
    public void genuinelyNestedBlockNamespacesStillCombine() throws Exception {
        assertEquals(List.of("Demo.Feature.Inner.Queries", "Demo.Feature.Outer"), classNames("""
                namespace Demo
                {
                    namespace Feature
                    {
                        public class Outer { }
                        namespace Inner
                        {
                            public class Queries { }
                        }
                    }
                }
                """));
    }

    @Test
    public void fileScopedNamespaceStillNamesItsTypes() throws Exception {
        assertEquals(List.of("Demo.Feature.Queries"), classNames("""
                using Demo.Shared;
                namespace Demo.Feature;
                public class Queries { }
                """));
    }
}
