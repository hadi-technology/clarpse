package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@code Component.importedFiles()} for C#: the repository files a file's own using directives
 * resolve to, as project file paths (the form of {@code ProjectFile.path()} and
 * {@code Component.sourceFile()}).
 */
public class CSharpImportedFilesTest {

    private static final String ORDER = "/src/model/Order.cs";
    private static final String CUSTOMER = "/src/model/Customer.cs";
    private static final String ACCOUNT = "/src/model/Account.cs";
    private static final String ACCOUNT_BILLING = "/src/model/Account.Billing.cs";
    private static final String BOX = "/src/model/Box.cs";
    private static final String DEEP = "/src/model/nested/Deep.cs";
    private static final String STRINGS = "/src/util/Strings.cs";
    private static final String USER = "/src/app/User.cs";

    private static List<ProjectFile> repository() {
        final List<ProjectFile> files = new ArrayList<>();
        files.add(new ProjectFile(ORDER, """
                namespace Acme.Model;
                public class Order {
                    public class Line { }
                    public static int Count() { return 0; }
                }
                """));
        files.add(new ProjectFile(CUSTOMER, "namespace Acme.Model;\npublic class Customer { }\n"));
        files.add(new ProjectFile(ACCOUNT, "namespace Acme.Model;\npublic partial class Account { int a; }\n"));
        files.add(new ProjectFile(ACCOUNT_BILLING,
                "namespace Acme.Model;\npublic partial class Account { int b; }\n"));
        files.add(new ProjectFile(BOX, "namespace Acme.Model;\npublic class Box<T> { }\n"));
        files.add(new ProjectFile(DEEP, "namespace Acme.Model.Nested;\npublic class Deep { }\n"));
        files.add(new ProjectFile(STRINGS, """
                namespace Acme.Util;
                public static class Strings { public static string Up(this string s) { return s; } }
                """));
        return files;
    }

    private static ProjectFiles project(final String userSource) {
        final ProjectFiles files = new ProjectFiles();
        repository().forEach(files::insertFile);
        files.insertFile(new ProjectFile(USER, userSource));
        return files;
    }

    /** A file declaring {@code Acme.App.User} under the given using directives. */
    private static String user(final String usings) {
        return usings + "\nnamespace Acme.App;\npublic class User { }\n";
    }

    private static OOPSourceCodeModel compile(final ProjectFiles files) throws Exception {
        final CompileResult result = new ClarpseProject(files, Lang.CSHARP).result();
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        return result.model();
    }

    private static List<String> importedBy(final String usings) throws Exception {
        return importedBy(compile(project(user(usings))), "Acme.App.User");
    }

    private static List<String> importedBy(final OOPSourceCodeModel model, final String name) {
        return List.copyOf(model.component(name).orElseThrow(() -> new AssertionError("no component " + name))
                .importedFiles());
    }

    @Test
    public void aNamespaceUsingOfANamespaceWithProjectFilesAddsNothing() throws Exception {
        assertTrue(importedBy("using Acme.Model;").isEmpty());
        assertTrue(importedBy("using Acme.Model.Nested;").isEmpty());
        assertTrue(importedBy("using Acme.Util;").isEmpty());
    }

    @Test
    public void aNamespaceUsingAddsNothingBesideTheNamedTypesOfTheSameNamespace() throws Exception {
        assertEquals(List.of(CUSTOMER), importedBy("using Acme.Model;\nusing static Acme.Model.Customer;"));
    }

    @Test
    public void aUsingStaticGivesTheFileDeclaringTheType() throws Exception {
        assertEquals(List.of(ORDER), importedBy("using static Acme.Model.Order;"));
        assertEquals(List.of(STRINGS), importedBy("using static Acme.Util.Strings;"));
    }

    @Test
    public void aUsingStaticOfANestedTypeGivesTheFileOfItsOuterType() throws Exception {
        assertEquals(List.of(ORDER), importedBy("using static Acme.Model.Order.Line;"));
    }

    @Test
    public void aUsingStaticOfAPartialTypeGivesEveryFileDeclaringAPart() throws Exception {
        assertEquals(List.of(ACCOUNT_BILLING, ACCOUNT), importedBy("using static Acme.Model.Account;"));
    }

    @Test
    public void aUsingStaticNeverReadsANamespace() throws Exception {
        assertTrue(importedBy("using static Acme.Model;").isEmpty());
    }

    @Test
    public void anAliasOfATypeGivesTheFilesDeclaringIt() throws Exception {
        assertEquals(List.of(CUSTOMER), importedBy("using Buyer = Acme.Model.Customer;"));
        assertEquals(List.of(ACCOUNT_BILLING, ACCOUNT), importedBy("using Acc = Acme.Model.Account;"));
        assertEquals(List.of(ORDER), importedBy("using Row = Acme.Model.Order.Line;"));
    }

    @Test
    public void anAliasOfAClosedGenericTypeGivesTheFileDeclaringIt() throws Exception {
        assertEquals(List.of(BOX), importedBy("using IntBox = Acme.Model.Box<int>;"));
    }

    @Test
    public void anAliasOfANamespaceAddsNothing() throws Exception {
        assertTrue(importedBy("using M = Acme.Model;").isEmpty());
        assertTrue(importedBy("using N = Acme.Model.Nested;").isEmpty());
    }

    @Test
    public void aGlobalQualifiedNameIsReadFromTheRoot() throws Exception {
        assertEquals(List.of(CUSTOMER), importedBy("using Buyer = global::Acme.Model.Customer;"));
        assertEquals(List.of(STRINGS), importedBy("using static global::Acme.Util.Strings;"));
    }

    @Test
    public void libraryAndFrameworkUsingsAddNothing() throws Exception {
        final OOPSourceCodeModel model = compile(project(user(String.join("\n",
                "using System;",
                "using System.Linq;",
                "using System.Collections.Generic;",
                "using static System.Math;",
                "using Text = System.Text.StringBuilder;",
                "using Microsoft.Extensions.Logging;"))));

        assertTrue(importedBy(model, "Acme.App.User").isEmpty());
        assertEquals(6, model.component("Acme.App.User").orElseThrow().imports().size());
    }

    @Test
    public void aUsingNamingNothingInTheProjectAddsNothing() throws Exception {
        assertTrue(importedBy(String.join("\n",
                "using Acme.Ghost;",
                "using static Acme.Model.Ghost;",
                "using Spirit = Acme.Model.Ghost;",
                "using Acme.Model.Ghost.Deeper;")).isEmpty());
    }

    @Test
    public void aNamespaceWithNoDeclaredTypeAddsNothing() throws Exception {
        assertTrue(importedBy("using Acme;").isEmpty());
    }

    @Test
    public void aTypeOfTheFilesOwnNamespaceNeedsNoUsingAndAddsNothing() throws Exception {
        final ProjectFiles files = project("namespace Acme.Model;\npublic class User { Customer c; }\n");

        assertTrue(importedBy(compile(files), "Acme.Model.User").isEmpty());
    }

    @Test
    public void aFileDoesNotImportItself() throws Exception {
        final ProjectFiles self = new ProjectFiles();
        self.insertFile(new ProjectFile(ORDER, "using static Acme.Model.Order;\nusing static Acme.Model.Customer;\n"
                + "namespace Acme.Model;\npublic class Order { }\n"));
        self.insertFile(new ProjectFile(CUSTOMER, "namespace Acme.Model;\npublic class Customer { }\n"));
        assertEquals(List.of(CUSTOMER), importedBy(compile(self), "Acme.Model.Order"));
    }

    @Test
    public void aUsingInsideANamespaceIsLookedUpFromTheInnermostNamespaceOutwards() throws Exception {
        final OOPSourceCodeModel block = compile(project(
                "namespace Acme.App\n{\n    using static Model.Order;\n    using Row = Model.Order.Line;\n"
                        + "    using Cust = Acme.Model.Customer;\n    using static Model.Ghost;\n"
                        + "    public class User { }\n}\n"));
        final OOPSourceCodeModel scoped = compile(project(
                "namespace Acme.App;\nusing static Util.Strings;\nusing static Model.Order;\npublic class User { }\n"));

        assertEquals(List.of(ORDER, CUSTOMER), importedBy(block, "Acme.App.User"));
        assertEquals(List.of(STRINGS, ORDER), importedBy(scoped, "Acme.App.User"));
    }

    @Test
    public void aGlobalUsingBelongsToTheFileThatWritesIt() throws Exception {
        final ProjectFiles files = project(user("using static Acme.Util.Strings;"));
        files.insertFile(new ProjectFile("/src/Globals.cs",
                "global using Acme.Model;\nglobal using static Acme.Util.Strings;\nglobal using Cust = Acme.Model.Customer;\n"
                        + "namespace Acme.App;\npublic class Globals { }\n"));
        final OOPSourceCodeModel model = compile(files);

        assertEquals(List.of(STRINGS, CUSTOMER), importedBy(model, "Acme.App.Globals"));
        assertEquals("the global usings do not reach the other files", List.of(STRINGS),
                importedBy(model, "Acme.App.User"));
        assertTrue("imports() still carries them", model.component("Acme.App.User").orElseThrow()
                .imports().contains("Acme.Model"));
    }

    @Test
    public void namespacesAProjectImportsImplicitlyAreNoDirective() throws Exception {
        final ProjectFiles files = project(user("using static Acme.Util.Strings;"));
        files.insertFile(new ProjectFile("/Directory.Build.props",
                "<Project><PropertyGroup><ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>"));
        files.insertFile(new ProjectFile("/src/App.csproj", "<Project Sdk=\"Microsoft.NET.Sdk\">"
                + "<ItemGroup><Using Include=\"Acme.Model\" /></ItemGroup></Project>"));
        final OOPSourceCodeModel model = compile(files);

        assertTrue(model.component("Acme.App.User").orElseThrow().imports().contains("Acme.Model"));
        assertEquals(List.of(STRINGS), importedBy(model, "Acme.App.User"));
    }

    @Test
    public void aPartialTypeCarriesTheDirectivesOfEveryPart() throws Exception {
        final ProjectFiles files = new ProjectFiles();
        repository().forEach(files::insertFile);
        files.insertFile(new ProjectFile(ACCOUNT, "using static Acme.Util.Strings;\nnamespace Acme.Model;\n"
                + "public partial class Account { int a; }\n"));
        files.insertFile(new ProjectFile(ACCOUNT_BILLING, "using static Acme.Model.Order;\n"
                + "namespace Acme.Model;\npublic partial class Account { int b; }\n"));
        final OOPSourceCodeModel model = compile(files);

        assertEquals(Set.of(STRINGS, ORDER), model.component("Acme.Model.Account").orElseThrow().importedFiles());
    }

    @Test
    public void entriesFollowTheDirectivesThenPathOrderWithoutRepeats() throws Exception {
        assertEquals(List.of(STRINGS, ORDER, CUSTOMER),
                importedBy(String.join("\n",
                        "using static Acme.Util.Strings;",
                        "using Acme.Model;",
                        "using static Acme.Model.Order;",
                        "using Buyer = Acme.Model.Customer;")));
    }

    @Test
    public void everyEntryIsTheSourceFileOfAComponentInThatFile() throws Exception {
        final OOPSourceCodeModel model = compile(project(user("using static Acme.Model.Order;\nusing static Acme.Util.Strings;")));

        assertTrue(importedBy(model, "Acme.App.User").contains(
                model.component("Acme.Model.Order").orElseThrow().sourceFile()));
        assertTrue(importedBy(model, "Acme.App.User").contains(
                model.component("Acme.Util.Strings").orElseThrow().sourceFile()));
    }

    @Test
    public void everyComponentCarryingImportsCarriesTheImportedFiles() throws Exception {
        final OOPSourceCodeModel model = compile(project("""
                using static Acme.Util.Strings;
                namespace Acme.App;
                public class User {
                    int field;
                    public void Run(int p) { int local = p; }
                    public class Inner { }
                    public enum Mode { A }
                    public interface IPort { }
                }
                public struct Point { }
                public enum Level { Low }
                public interface IApi { }
                public record Pair(int A);
                """));
        int types = 0;

        for (final Component component : (Iterable<Component>) model.components()::iterator) {
            if (!USER.equals(component.sourceFile())) {
                continue;
            }
            if (component.imports().isEmpty()) {
                assertTrue(component.uniqueName(), component.importedFiles().isEmpty());
            } else {
                types++;
                assertEquals(component.uniqueName(), List.of(STRINGS), List.copyOf(component.importedFiles()));
            }
        }
        assertTrue("nested types, structs, enums, interfaces and records are all covered: " + types, types >= 8);
    }

    @Test
    public void importsAreUnchanged() throws Exception {
        final OOPSourceCodeModel model = compile(project(user(String.join("\n",
                "using Acme.Model;",
                "using static Acme.Model.Order;",
                "using Buyer = Acme.Model.Customer;",
                "using System.Linq;"))));

        assertEquals(List.of("Acme.Model", "Acme.Model.Order", "Acme.Model.Customer", "System.Linq"),
                List.copyOf(model.component("Acme.App.User").orElseThrow().imports()));
    }

    @Test
    public void theSetIsUnmodifiableAndNeverNull() throws Exception {
        final OOPSourceCodeModel model = compile(project(user("using static Acme.Util.Strings;")));

        assertThrows(UnsupportedOperationException.class,
                () -> model.component("Acme.App.User").orElseThrow().importedFiles().add("/x.cs"));
        assertTrue(model.component("Acme.Util.Strings").orElseThrow().importedFiles().isEmpty());
    }

    @Test
    public void copiesKeepTheField() throws Exception {
        final OOPSourceCodeModel model = compile(project(user("using static Acme.Util.Strings;")));

        assertEquals(List.of(STRINGS),
                List.copyOf(model.copyOfComponent("Acme.App.User").orElseThrow().importedFiles()));
        assertEquals(List.of(STRINGS),
                List.copyOf(model.copy().component("Acme.App.User").orElseThrow().importedFiles()));
    }

    @Test
    public void aFileOutsideTheAnalysedSetStillResolvesAgainstTheWholeProject() throws Exception {
        final CompileResult result = new ClarpseProject(project(user("using static Acme.Model.Order;")), Lang.CSHARP,
                List.of(USER)).result();

        assertEquals(List.of(ORDER), importedBy(result.model(), "Acme.App.User"));
        assertFalse(result.model().containsComponent("Acme.Model.Order"));
    }

    @Test
    public void aOneLevelAnalysisRecordsTheFieldOnAnalysedAndBoundaryFiles() throws Exception {
        final ProjectFiles files = project("using Acme.Model;\nusing static Acme.Model.Order;\nnamespace Acme.App;\n"
                + "public class User { Customer c; }\n");
        files.insertFile(new ProjectFile(CUSTOMER, "using static Acme.Util.Strings;\nnamespace Acme.Model;\n"
                + "public class Customer { }\n"));
        final CompileResult result = new ClarpseProject(files, Lang.CSHARP, List.of(USER),
                AnalysisOptions.oneLevel()).result();
        final Component customer = result.model().component("Acme.Model.Customer").orElseThrow();

        assertEquals(List.of(ORDER), importedBy(result.model(), "Acme.App.User"));
        assertTrue(customer.isBoundary());
        assertEquals(List.of(STRINGS), List.copyOf(customer.importedFiles()));
    }
}
