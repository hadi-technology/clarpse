package com.hadi.test;

import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.StandardLibraries;
import com.hadi.clarpse.reference.StandardLibraryStatus;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.hadi.clarpse.reference.StandardLibraryStatus.NOT_STANDARD;
import static com.hadi.clarpse.reference.StandardLibraryStatus.STANDARD;
import static com.hadi.clarpse.reference.StandardLibraryStatus.UNKNOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** What each language's rule says about a name, and that the shipped module lists match their sources. */
public class StandardLibrariesTest {

    private static void name(final Lang lang, final String target, final StandardLibraryStatus expected) {
        assertEquals(lang + " " + target, expected, StandardLibraries.ofExternalName(lang, target));
    }

    @Test
    public void javaRuntimePackagesAreStandardAndOthersAreNot() {
        name(Lang.JAVA, "java.util.Map.Entry", STANDARD);
        name(Lang.JAVA, "javax.swing.JFrame", STANDARD);
        name(Lang.JAVA, "org.w3c.dom.Node", STANDARD);
        name(Lang.JAVA, "java.sql.Connection", STANDARD);
        name(Lang.JAVA, "javax.inject.Inject", NOT_STANDARD);
        name(Lang.JAVA, "com.acme.Widget", NOT_STANDARD);
        name(Lang.JAVA, "Widget", UNKNOWN);
    }

    @Test
    public void kotlinAddsItsOwnLibraryToTheJavaPlatform() {
        name(Lang.KOTLIN, "kotlin.collections.List", STANDARD);
        name(Lang.KOTLIN, "kotlin", STANDARD);
        name(Lang.KOTLIN, "java.io.File", STANDARD);
        name(Lang.KOTLIN, "kotlinx.coroutines.Job", NOT_STANDARD);
        name(Lang.KOTLIN, "kotlin.reflect.full.KClasses", UNKNOWN);
        name(Lang.KOTLIN, "kotlin.test.assertEquals", UNKNOWN);
        name(Lang.KOTLIN, "kotlin.reflect.KClass", STANDARD);
        name(Lang.KOTLIN, "io.ktor.Application", NOT_STANDARD);
    }

    @Test
    public void csharpStandardIsWhatTheBaseRuntimeShipsAndNothingUnderTheSameRoot() {
        name(Lang.CSHARP, "System", STANDARD);
        name(Lang.CSHARP, "System.Collections.Generic", STANDARD);
        name(Lang.CSHARP, "System.Collections.Generic.List", STANDARD);
        name(Lang.CSHARP, "System.Collections.Generic.Dictionary.Enumerator", STANDARD);
        name(Lang.CSHARP, "System.Text.Json.JsonSerializer", STANDARD);
        name(Lang.CSHARP, "Microsoft.Win32", STANDARD);
        name(Lang.CSHARP, "System.CommandLine", UNKNOWN);
        name(Lang.CSHARP, "System.CommandLine.Command", UNKNOWN);
        name(Lang.CSHARP, "System.Reactive.Linq", UNKNOWN);
        name(Lang.CSHARP, "System.Linq.AsyncEnumerable", UNKNOWN);
        name(Lang.CSHARP, "System.Data.SqlClient", UNKNOWN);
        name(Lang.CSHARP, "Microsoft.Extensions.Logging", UNKNOWN);
        name(Lang.CSHARP, "Newtonsoft.Json.JsonConvert", NOT_STANDARD);
        name(Lang.CSHARP, "List", UNKNOWN);
    }

    @Test
    public void typescriptNodeBuiltinsAreStandard() {
        name(Lang.TYPESCRIPT, "node:fs:readFile", STANDARD);
        name(Lang.TYPESCRIPT, "fs:readFile", STANDARD);
        name(Lang.TYPESCRIPT, "fs/promises:readFile", STANDARD);
        name(Lang.TYPESCRIPT, "react:Component", NOT_STANDARD);
        name(Lang.TYPESCRIPT, "@scope/pkg:Thing", NOT_STANDARD);
        name(Lang.TYPESCRIPT, "Error", UNKNOWN);
    }

    @Test
    public void pythonTypeshedModulesAreStandardByTheirTopLevelPackage() {
        name(Lang.PYTHON, "os.path.join", STANDARD);
        name(Lang.PYTHON, "collections.OrderedDict", STANDARD);
        name(Lang.PYTHON, "requests.Session", NOT_STANDARD);
        name(Lang.PYTHON, "Session", UNKNOWN);
    }

    @Test
    public void anImportOfAProjectFileOrARelativeModuleIsNeverStandard() {
        assertEquals(NOT_STANDARD, StandardLibraries.ofImport(Lang.PYTHON, "json", false, true));
        assertEquals(NOT_STANDARD, StandardLibraries.ofImport(Lang.PYTHON, ".json", true, false));
        assertEquals(NOT_STANDARD, StandardLibraries.ofImport(Lang.TYPESCRIPT, "fs", false, true));
        assertEquals(NOT_STANDARD, StandardLibraries.ofImport(Lang.TYPESCRIPT, "./fs", true, false));
        assertEquals(STANDARD, StandardLibraries.ofImport(Lang.TYPESCRIPT, "node:test", false, false));
        assertEquals(STANDARD, StandardLibraries.ofImport(Lang.PYTHON, "json", false, false));
    }

    @Test
    public void nothingIsClassifiedWithoutALanguageOrAName() {
        assertEquals(UNKNOWN, StandardLibraries.ofExternalName(null, "java.util.List"));
        assertEquals(UNKNOWN, StandardLibraries.ofExternalName(Lang.JAVA, ""));
        assertEquals(UNKNOWN, StandardLibraries.ofImport(Lang.JAVA, null, false, false));
    }

    @Test
    public void thePythonModuleListIsTheTopLevelModulesOfTheBundledTypeshed() throws Exception {
        final Set<String> expected = new TreeSet<>();
        try (InputStream in = getClass().getResourceAsStream("/python/pyright-bundle.zip");
             ZipInputStream zip = new ZipInputStream(in)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().endsWith("typeshed-fallback/stdlib/VERSIONS")) {
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
                    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                        if (line.startsWith("#") || !line.contains(":")) {
                            continue;
                        }
                        final String module = line.substring(0, line.indexOf(':'));
                        expected.add(module.split("\\.")[0]);
                    }
                }
            }
        }
        assertTrue("the bundle holds a VERSIONS file", expected.size() > 100);
        assertEquals(expected, resourceLines("/stdlib/python-modules.txt"));
    }

    @Test
    public void theNodeModuleListMatchesNodesPublicBuiltins() throws Exception {
        final Set<String> listed = resourceLines("/stdlib/node-modules.txt");
        for (final String required : new String[] {"fs", "path", "fs/promises", "child_process", "crypto", "http"}) {
            assertTrue(required, listed.contains(required));
        }
        for (final String listedModule : listed) {
            assertTrue(listedModule + " is a public name", !listedModule.startsWith("_"));
        }
    }

    @Test
    public void everyListNamesItsSourceAndVersion() throws Exception {
        final String[][] expected = {
            {"/stdlib/java-packages.txt", "OpenJDK 17.0.21"},
            {"/stdlib/dotnet-namespaces.txt", "Microsoft.NETCore.App.Ref 8.0.34"},
            {"/stdlib/dotnet-types.txt", "Microsoft.NETCore.App.Ref 8.0.34"},
            {"/stdlib/node-modules.txt", "Node.js 22"},
            {"/stdlib/python-modules.txt", "typeshed"}};
        for (final String[] list : expected) {
            try (InputStream in = getClass().getResourceAsStream(list[0]);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                final String header = reader.readLine();
                assertTrue(list[0] + ": " + header, header.startsWith("#") && header.contains(list[1]));
                if (list[0].contains("dotnet")) {
                    assertTrue(list[0] + " names its package hash", header.matches(".*SHA-512 [0-9a-f]{128}.*"));
                }
            }
        }
    }

    @Test
    public void javaAnswersDoNotDependOnTheRunningJdk() throws Exception {
        final Set<String> listed = resourceLines("/stdlib/java-packages.txt");
        for (final String required : new String[] {"javax.swing", "org.w3c.dom", "javax.xml.parsers", "java.sql"}) {
            assertTrue(required, listed.contains(required));
        }
        assertTrue("a Java EE package is not in the platform", !listed.contains("javax.inject"));
    }

    private Set<String> resourceLines(final String path) throws Exception {
        final Set<String> lines = new TreeSet<>();
        try (InputStream in = getClass().getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    lines.add(line.trim());
                }
            }
        }
        return lines;
    }
}
