package com.hadi.test.kotlin;

import com.hadi.clarpse.compiler.AnalysisOptions;
import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import java.util.List;

import static com.hadi.test.kotlin.KotlinTestUtil.component;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@code Component.importedFiles()} for Kotlin: the repository files a file's import directives
 * resolve to, as project file paths (the form of {@code ProjectFile.path()} and
 * {@code Component.sourceFile()}).
 */
public class KotlinImportedFilesTest {

    private static final String SHOP = "/src/shop/";
    private static final String ORDER = SHOP + "Order.kt";
    private static final String CUSTOMER = SHOP + "Customer.kt";
    private static final String HELPERS = SHOP + "Helpers.kt";
    private static final String MORE_HELPERS = SHOP + "MoreHelpers.kt";
    private static final String USER = "/src/app/User.kt";

    private static ProjectFile[] repository(final String userImports, final String userBody) {
        return new ProjectFile[] {
            new ProjectFile(ORDER, String.join("\n",
                    "package shop",
                    "class Order {",
                    "    class Line",
                    "}",
                    "enum class Color { RED, GREEN }",
                    "object Registry {",
                    "    val DEFAULT = 1",
                    "    fun make() = 2",
                    "}",
                    "typealias Orders = List<Order>")),
            new ProjectFile(CUSTOMER, "package shop\nclass Customer\n"),
            new ProjectFile(HELPERS, "package shop\nfun formatPrice(cents: Int): String = \"\"\nval TAX = 20\n"),
            new ProjectFile(MORE_HELPERS, "package shop\nfun formatPrice(cents: Long): String = \"\"\n"),
            new ProjectFile("/src/shop/sub/Deep.kt", "package shop.sub\nclass Deep\n"),
            new ProjectFile("/src/other/Other.kt", "package other\nclass Other\n"),
            new ProjectFile(USER, "package app\n" + userImports + "\n" + userBody + "\n"),
        };
    }

    private static OOPSourceCodeModel compile(final String userImports) throws Exception {
        return compile(userImports, "class User");
    }

    private static OOPSourceCodeModel compile(final String userImports, final String userBody) throws Exception {
        final CompileResult result = KotlinTestUtil.compileInline(repository(userImports, userBody));
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        return result.model();
    }

    private static List<String> importedBy(final OOPSourceCodeModel model, final String name) {
        return List.copyOf(component(model, name).importedFiles());
    }

    @Test
    public void aTypeImportGivesTheFileDeclaringIt() throws Exception {
        assertEquals(List.of(CUSTOMER), importedBy(compile("import shop.Customer"), "app.User"));
    }

    @Test
    public void aNestedTypeImportGivesTheFileOfItsTopLevelType() throws Exception {
        assertEquals(List.of(ORDER), importedBy(compile("import shop.Order.Line"), "app.User"));
    }

    @Test
    public void anAliasedImportGivesTheFileDeclaringTheTarget() throws Exception {
        assertEquals(List.of(CUSTOMER), importedBy(compile("import shop.Customer as Buyer"), "app.User"));
    }

    @Test
    public void aStarImportOfAPackageWithProjectFilesAddsNothing() throws Exception {
        final OOPSourceCodeModel model = compile("import shop.*\nimport shop.sub.*");

        assertTrue(importedBy(model, "app.User").isEmpty());
        assertEquals(List.of("shop.*", "shop.sub.*"), List.copyOf(component(model, "app.User").imports()));
    }

    @Test
    public void aStarImportAddsNothingBesideTheNamedImportsOfTheSamePackage() throws Exception {
        assertEquals(List.of(CUSTOMER), importedBy(compile("import shop.*\nimport shop.Customer"), "app.User"));
    }

    @Test
    public void aStarImportOverATypeGivesTheFileDeclaringTheType() throws Exception {
        assertEquals(List.of(ORDER), importedBy(compile("import shop.Color.*\nimport shop.Registry.*"), "app.User"));
    }

    @Test
    public void aTopLevelFunctionImportGivesEveryFileDeclaringIt() throws Exception {
        assertEquals(List.of(HELPERS, MORE_HELPERS),
                importedBy(compile("import shop.formatPrice"), "app.User"));
    }

    @Test
    public void aTopLevelPropertyImportGivesTheFileDeclaringIt() throws Exception {
        assertEquals(List.of(HELPERS), importedBy(compile("import shop.TAX"), "app.User"));
    }

    @Test
    public void aTopLevelImportKeepsItsFileWhenAliased() throws Exception {
        assertEquals(List.of(HELPERS), importedBy(compile("import shop.TAX as Vat"), "app.User"));
    }

    @Test
    public void aTypeAliasImportGivesTheFileDeclaringTheAlias() throws Exception {
        assertEquals(List.of(ORDER), importedBy(compile("import shop.Orders"), "app.User"));
    }

    @Test
    public void anObjectMemberAndAnEnumEntryGiveTheFileDeclaringTheirType() throws Exception {
        assertEquals(List.of(ORDER), importedBy(compile(
                "import shop.Registry.make\nimport shop.Registry.DEFAULT\nimport shop.Color.RED"), "app.User"));
    }

    @Test
    public void libraryImportsAddNothing() throws Exception {
        final OOPSourceCodeModel model = compile(String.join("\n",
                "import kotlinx.coroutines.*",
                "import kotlinx.coroutines.launch",
                "import kotlin.collections.List",
                "import java.util.UUID",
                "import java.io.*",
                "import org.springframework.stereotype.Service"));

        assertTrue(importedBy(model, "app.User").isEmpty());
        assertEquals(6, component(model, "app.User").imports().size());
    }

    @Test
    public void anImportNamingNothingInTheProjectAddsNothing() throws Exception {
        assertTrue(importedBy(compile(String.join("\n",
                "import shop.Ghost",
                "import shop.ghost.Phantom",
                "import shop.Ghost as Spirit",
                "import ghost.*",
                "import shop.ghost")), "app.User").isEmpty());
    }

    @Test
    public void aTypeOfTheFilesOwnPackageNeedsNoImportAndAddsNothing() throws Exception {
        final CompileResult result = KotlinTestUtil.compileInline(
                new ProjectFile(USER, "package shop\nclass User(val c: Customer)\n"),
                new ProjectFile(CUSTOMER, "package shop\nclass Customer\n"));

        assertTrue(importedBy(result.model(), "shop.User").isEmpty());
    }

    @Test
    public void aFileDoesNotImportItself() throws Exception {
        final CompileResult result = KotlinTestUtil.compileInline(
                new ProjectFile(ORDER, "package shop\nimport shop.Customer\nimport shop.Order.Line\n"
                        + "class Order { class Line }\n"),
                new ProjectFile(CUSTOMER, "package shop\nclass Customer\n"));

        assertEquals(List.of(CUSTOMER), importedBy(result.model(), "shop.Order"));
    }

    @Test
    public void aJavaTypeImportedByKotlinGivesTheJavaFile() throws Exception {
        final ProjectFiles files = KotlinTestUtil.projectOf(repository(String.join("\n",
                "import legacy.Widget",
                "import legacy.Widget.Part",
                "import legacy.Widget.make",
                "import legacy.Ghost",
                "import shop.Customer"), "class User"));
        files.insertFile(new ProjectFile("/src/legacy/Widget.java",
                "package legacy;\npublic class Widget { public static class Part { } public static int make() { return 1; } }\n"));
        final CompileResult result = new ClarpseProject(files, Lang.KOTLIN).result();

        assertEquals(List.of("/src/legacy/Widget.java", CUSTOMER), importedBy(result.model(), "app.User"));
    }

    @Test
    public void aStarImportOverANestedTypeGivesTheFileOfTheTopLevelType() throws Exception {
        assertEquals(List.of(ORDER), importedBy(compile("import shop.Order.Line.*"), "app.User"));
        final ProjectFiles files = KotlinTestUtil.projectOf(repository("import legacy.Widget.Part.*", "class User"));
        files.insertFile(new ProjectFile("/src/legacy/Widget.java",
                "package legacy;\npublic class Widget { public static class Part { } }\n"));
        assertEquals(List.of("/src/legacy/Widget.java"),
                importedBy(new ClarpseProject(files, Lang.KOTLIN).result().model(), "app.User"));
    }

    @Test
    public void aStarImportOverAJavaPackageAddsNothing() throws Exception {
        final ProjectFiles files = KotlinTestUtil.projectOf(repository("import legacy.*", "class User"));
        files.insertFile(new ProjectFile("/src/legacy/Widget.java", "package legacy;\npublic class Widget { }\n"));

        assertTrue(importedBy(new ClarpseProject(files, Lang.KOTLIN).result().model(), "app.User").isEmpty());
    }

    @Test
    public void entriesFollowTheDirectivesThenPathOrderWithoutRepeats() throws Exception {
        assertEquals(List.of("/src/other/Other.kt", CUSTOMER, HELPERS, MORE_HELPERS),
                importedBy(compile(String.join("\n",
                        "import other.Other",
                        "import shop.*",
                        "import shop.Customer",
                        "import shop.formatPrice",
                        "import shop.Other.*")), "app.User"));
    }

    @Test
    public void everyEntryIsTheSourceFileOfAComponentInThatFile() throws Exception {
        final OOPSourceCodeModel model = compile("import shop.Order\nimport shop.Customer");

        assertTrue(importedBy(model, "app.User").contains(component(model, "shop.Order").sourceFile()));
        assertTrue(importedBy(model, "app.User").contains(component(model, "shop.Customer").sourceFile()));
    }

    @Test
    public void everyComponentCarryingImportsCarriesTheImportedFiles() throws Exception {
        final OOPSourceCodeModel model = compile("import shop.Customer", String.join("\n",
                "class User(val c: Customer) {",
                "    val field: Customer? = null",
                "    fun find(other: Customer): Customer = other",
                "    class Inner",
                "    enum class Mode { A }",
                "    interface Port",
                "}",
                "object Single",
                "interface Api",
                "fun helper(c: Customer) = c",
                "val limit = 1"));
        final List<String> expected = List.of(CUSTOMER);
        int carrying = 0;

        for (final Component component : (Iterable<Component>) model.components()::iterator) {
            if (!USER.equals(component.sourceFile())) {
                continue;
            }
            if (component.imports().isEmpty()) {
                assertTrue(component.uniqueName(), component.importedFiles().isEmpty());
            } else {
                carrying++;
                assertEquals(component.uniqueName(), expected, List.copyOf(component.importedFiles()));
            }
        }
        assertTrue("nested types, objects, interfaces and the file class are covered: " + carrying,
                carrying >= 7);
        assertTrue(model.containsComponent("app.UserKt"));
        assertEquals(expected, importedBy(model, "app.UserKt"));
    }

    @Test
    public void importsAreUnchanged() throws Exception {
        final OOPSourceCodeModel model = compile(String.join("\n",
                "import shop.Customer as Buyer",
                "import shop.*",
                "import shop.formatPrice",
                "import kotlinx.coroutines.*"));

        assertEquals(List.of("shop.Customer", "shop.*", "shop.formatPrice", "kotlinx.coroutines.*"),
                List.copyOf(component(model, "app.User").imports()));
    }

    @Test
    public void theSetIsUnmodifiableAndNeverNull() throws Exception {
        final OOPSourceCodeModel model = compile("import shop.Customer");

        assertThrows(UnsupportedOperationException.class,
                () -> component(model, "app.User").importedFiles().add("/x.kt"));
        assertTrue(component(model, "shop.Customer").importedFiles().isEmpty());
    }

    @Test
    public void copiesKeepTheField() throws Exception {
        final OOPSourceCodeModel model = compile("import shop.Customer");

        assertEquals(List.of(CUSTOMER), List.copyOf(component(model, "app.User").importedFiles()));
        assertEquals(List.of(CUSTOMER),
                List.copyOf(model.copy().component("app.User").orElseThrow().importedFiles()));
    }

    @Test
    public void aFileOutsideTheAnalysedSetStillResolvesAgainstTheWholeProject() throws Exception {
        final CompileResult result = new ClarpseProject(KotlinTestUtil.projectOf(repository("import shop.Order\nimport shop.Customer",
                "class User")), Lang.KOTLIN, List.of(USER)).result();

        assertEquals(List.of(ORDER, CUSTOMER), importedBy(result.model(), "app.User"));
        assertFalse(result.model().containsComponent("shop.Order"));
    }

    @Test
    public void aOneLevelAnalysisRecordsTheFieldOnAnalysedAndBoundaryFiles() throws Exception {
        final CompileResult result = new ClarpseProject(KotlinTestUtil.projectOf(
                new ProjectFile(USER, "package app\nimport shop.Customer\nclass User(val c: Customer)\n"),
                new ProjectFile(CUSTOMER, "package shop\nimport other.Other\nclass Customer(val o: Other)\n"),
                new ProjectFile("/src/other/Other.kt", "package other\nclass Other\n")), Lang.KOTLIN,
                List.of(USER), AnalysisOptions.oneLevel()).result();
        final Component customer = component(result.model(), "shop.Customer");

        assertEquals(List.of(CUSTOMER), importedBy(result.model(), "app.User"));
        assertTrue(customer.isBoundary());
        assertEquals(List.of("/src/other/Other.kt"), List.copyOf(customer.importedFiles()));
    }
}
