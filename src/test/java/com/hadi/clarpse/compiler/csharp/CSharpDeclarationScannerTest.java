package com.hadi.clarpse.compiler.csharp;

import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The lexical declaration scanner finds the types a C# file declares, with the unique names the
 * assembler gives them, and nothing inside comments, strings or bodies.
 */
public class CSharpDeclarationScannerTest {

    private static List<String> declared(final String text) {
        return CSharpDeclarationScanner.scan(text).stream()
                .map(declaration -> declaration.kind() + " " + declaration.uniqueName()
                        + (declaration.partial() ? " partial" : ""))
                .collect(Collectors.toList());
    }

    @Test
    public void blockAndNestedDeclarationsGetTheAssemblersUniqueNames() {
        assertEquals(List.of("class App.Core.Outer", "struct App.Core.Outer.Point", "interface App.Core.IShape",
                        "enum App.Core.Color", "class App.Core.Box partial"),
                declared("namespace App.Core\n{\n"
                        + "    public class Outer\n    {\n        private struct Point { int x; }\n    }\n"
                        + "    public interface IShape { }\n"
                        + "    internal enum Color { Red, Green }\n"
                        + "    public sealed partial class Box<T> where T : class { }\n"
                        + "}\n"));
    }

    @Test
    public void fileScopedNamespaceAndByteOrderMarkAndPreprocessorLines() {
        assertEquals(List.of("class Lib.Widget"),
                declared("\uFEFF#pragma warning disable CS1591\n#region Types\n"
                        + "namespace Lib;\n\npublic class Widget { }\n#endregion\n"));
    }

    @Test
    public void nothingIsDeclaredInsideCommentsStringsOrCharacters() {
        assertEquals(List.of("class N.Real"),
                declared("namespace N {\n"
                        + "// class LineComment { }\n"
                        + "/* class BlockComment { } */\n"
                        + "public class Real {\n"
                        + "  string a = \"class InString { }\";\n"
                        + "  string b = @\"class \"\"InVerbatim\"\" { }\";\n"
                        + "  string c = $\"{(true ? \"class InHole\" : \"x\")} class InInterpolated { }\";\n"
                        + "  string d = \"\"\"\n class InRaw { }\n \"\"\";\n"
                        + "  string e = $$\"\"\"\n {{Name}} class InRawInterpolated { }\n \"\"\";\n"
                        + "  string f = $@\"{Name} class InVerbatimInterpolated\";\n"
                        + "  char g = '{';\n  char h = '\\'';\n"
                        + "  string i = \"escaped \\\" class InEscaped { }\";\n"
                        + "  void M() { var x = new { A = 1 }; }\n"
                        + "}\n}\n"));
    }

    @Test
    public void recordsDelegatesAndRecordUsedAsAnIdentifier() {
        final List<String> declared = declared("namespace R {\n"
                + "public record Person(string Name);\n"
                + "public record struct Point(int X, int Y);\n"
                + "public record class Named { }\n"
                + "public delegate void Handler(object sender);\n"
                + "public delegate T Factory<T>();\n"
                + "public class Uses {\n"
                + "  object record = null;\n"
                + "  void M() { var r = record; Action a = delegate { }; Func<int> f = delegate () { return 1; }; }\n"
                + "}\n}\n");
        assertEquals(List.of("record R.Person", "recordStruct R.Point", "record R.Named", "delegate R.Handler",
                "delegate R.Factory", "class R.Uses"), declared);
    }

    @Test
    public void genericConstraintsAndBaseListsDeclareNothing() {
        final List<String> declared = declared("namespace G {\n"
                + "public class Repo<T> : IRepo<T>, IDisposable where T : class, new() { }\n"
                + "public interface IRepo<T> where T : class { }\n"
                + "}\n");
        assertEquals(List.of("class G.Repo", "interface G.IRepo"), declared);
    }

    @Test
    public void unterminatedTextEndsTheScanWithoutFailing() {
        assertTrue(declared("namespace U { class A { string s = \"never closed").contains("class U.A"));
        assertTrue(declared("namespace U { class B { /* never closed").contains("class U.B"));
        assertTrue(declared(null).isEmpty());
        assertTrue(declared("class").isEmpty());
    }
}
