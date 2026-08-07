package com.hadi.test;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@code references()} should contain types, and only types.
 *
 * <p>Consumers treat a reference as a dependency on another type — that is what makes a dependency
 * graph a dependency graph. Anything else in there is not merely noise: downstream it becomes an
 * edge, and an edge to something that is not a type is a claim about the codebase that is simply
 * false. striff builds architectural rules on these edges, and a rule naming
 * {@code app.MAX_NOTES} is a rule about a constant that no model can resolve.
 *
 * <p>The observed shape is that an <b>unresolved token is recorded with the current package
 * prefixed, whether or not it is a type</b>. A class reading its own {@code static final} field
 * yields a reference to {@code <package>.<FIELD_NAME>}; the same path turns an unresolved generic
 * expression into {@code <package>.Set<String>}. Seen on a real repository as
 * {@code com.hadi.striff.ai.spi.NOTE_STEREOTYPE} and {@code com.hadi.striff.ai.spi.MAX_DIAGRAM_NOTES}.
 */
public class CrossLanguageReferenceHygieneTest {

    /**
     * Java: reading a class's own constant must not become a reference to a type that does not
     * exist. {@code K} is a field of this very class, not a type in package {@code app}.
     */
    @Test
    public void java_readingItsOwnConstantIsNotATypeReference() throws Exception {
        List<String> refs = classReferences(Lang.JAVA, "/app/Gen.java", String.join("\n",
                "package app;",
                "public class Gen {",
                "  public static final String K = \"k\";",
                "  private static final int MAX_NOTES = 5;",
                "  public String use() { return K; }",
                "  public int cap() { return MAX_NOTES; }",
                "}"));

        assertTrue("a constant is not a type, but references() holds " + refs,
                refs.stream().noneMatch(r -> r.endsWith(".K") || r.endsWith(".MAX_NOTES")));
    }

    /**
     * Java: an unresolvable token must be omitted rather than invented into the current package.
     * Prefixing turns "I could not resolve this" into "this type exists here", which is worse than
     * silence — silence is visible as a coverage gap, invention is not.
     */
    @Test
    public void java_anUnresolvedTokenIsNotInventedIntoTheCurrentPackage() throws Exception {
        List<String> refs = classReferences(Lang.JAVA, "/app/Gen.java", String.join("\n",
                "package app;",
                "public class Gen {",
                "  private static final String NOTE_STEREOTYPE = \"s\";",
                "  public String s() { return NOTE_STEREOTYPE; }",
                "}"));

        assertEquals("nothing named app.NOTE_STEREOTYPE exists; references() claims it does",
                0, refs.stream().filter(r -> r.startsWith("app.")).count());
    }

    /**
     * TypeScript records primitives as references, and that is deliberate here.
     *
     * <p>Sixteen existing tests assert it — {@code references()} reports what the source declares,
     * primitives included, and it is the consumer's business which of those count as architectural
     * dependencies. The asymmetry with the other languages is real but not a defect: Java's
     * {@code java.lang.String} and C#'s {@code System.String} are classes with declarations,
     * whereas TypeScript's {@code string} is a primitive with none. This test pins the contract so
     * a future reader does not mistake it for the invention bug above and "fix" it here.
     */
    @Test
    public void typescript_recordsPrimitivesDeliberately() throws Exception {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/tsconfig.json",
                "{\"compilerOptions\":{\"target\":\"ES2020\"}}"));
        files.insertFile(new ProjectFile("/package.json", "{\"name\":\"t\",\"version\":\"1.0.0\"}"));
        files.insertFile(new ProjectFile("/src/svc.ts", String.join("\n",
                "export class Svc {",
                "  label(): string { return ''; }",
                "}")));

        assertTrue("the TypeScript reference contract changed: " + classReferences(files, Lang.TYPESCRIPT),
                classReferences(files, Lang.TYPESCRIPT).contains("string"));
    }

    /**
     * Python: a typed field reference must be recorded, or Python has no dependency graph at all.
     *
     * <p>This is the opposite failure from the others — under-reporting rather than invention — and
     * it is the more damaging one for a rule engine, because a missing edge reads as a rule upheld.
     */
    @Test
    public void python_recordsATypedFieldReference() throws Exception {
        // Python parses through a daemon like TypeScript does, so it needs the same guard: without
        // the runtime the compile yields nothing and the assertion would fail on the environment
        // rather than on the behaviour.
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
        ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile("/app/dep.py", "class Dep:\n    pass\n"));
        files.insertFile(new ProjectFile("/app/svc.py", String.join("\n",
                "from app.dep import Dep",
                "class Svc:",
                "    def __init__(self):",
                "        self.dep: Dep = Dep()")));
        List<String> refs = classReferences(files, Lang.PYTHON);

        assertTrue("Python recorded no reference to Dep at all: " + refs,
                refs.stream().anyMatch(r -> r.endsWith("Dep")));
    }

    /** C# is the control: it already keeps constants and primitives out of references(). */
    @Test
    public void csharp_keepsConstantsOutOfReferences() throws Exception {
        List<String> refs = classReferences(Lang.CSHARP, "/app/Svc.cs", String.join("\n",
                "namespace App {",
                "  public class Svc {",
                "    private const string NOTE = \"x\";",
                "    public string Label() { return NOTE; }",
                "  }",
                "}"));

        assertTrue("C# regressed: " + refs, refs.stream().noneMatch(r -> r.endsWith(".NOTE")));
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> classReferences(Lang lang, String path, String source)
            throws Exception {
        ProjectFiles files = new ProjectFiles();
        files.insertFile(new ProjectFile(path, source));
        return classReferences(files, lang);
    }

    private static List<String> classReferences(ProjectFiles files, Lang lang) throws Exception {
        OOPSourceCodeModel model = new ClarpseProject(files, lang).result().model();
        List<String> refs = new ArrayList<>();
        model.components().filter(c -> c.componentType().isBaseComponent())
                .forEach(component -> collect(component, refs));
        return refs;
    }

    private static void collect(Component component, List<String> into) {
        if (component.references() == null) {
            return;
        }
        for (ComponentReference reference : component.references()) {
            into.add(reference.invokedComponent());
        }
    }
}
