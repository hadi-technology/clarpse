package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TypeScriptWrittenTypeAnnotationTest {

    private static final String FIXTURE = "written-type-annotations";
    private static final String PACKAGE_PATH = "src";
    private static final String MODULE = "Tokens";
    private static final String EXPANDED = "(\"alpha\" | \"beta\" | \"gamma\")";
    private static OOPSourceCodeModel model;

    @BeforeClass
    public static void setup() throws Exception {
        CompileResult result = TypeScriptTestUtil.compileFixture(FIXTURE);
        model = result.model();
    }

    @Test
    public void returnTypeUsesWrittenAlias() {
        Assert.assertEquals("tokensFromFlags(Partial<Flags>) : Token[]",
                fragment("tokensFromFlags"));
    }

    @Test
    public void moduleVariableUsesWrittenAnnotation() {
        Assert.assertEquals("RUNTIME : Record<Id, readonly Token[]>", fragment("RUNTIME"));
    }

    @Test
    public void classAndInterfacePropertiesUseWrittenAnnotation() {
        Assert.assertEquals("items : Token[]", fragment("Holder.items"));
        Assert.assertEquals("tokens : Token[]", fragment("Shape.tokens"));
        Assert.assertEquals("byId : Record<Id, readonly Token[]>", fragment("Shape.byId"));
    }

    @Test
    public void multiLineAnnotationIsCollapsedToOneLine() {
        Assert.assertEquals("multiLine : Record< Id, readonly Token[] >", fragment("Holder.multiLine"));
        Assert.assertEquals("multiLineReturn() : Promise< Record<Id, Token[]> >", fragment("multiLineReturn"));
    }

    @Test
    public void literalUnionReturnAnnotationStaysNormalized() {
        Assert.assertEquals("literalReturn() : string", fragment("literalReturn"));
    }

    @Test
    public void unannotatedDeclarationsKeepCheckerText() {
        Assert.assertEquals("inferredTokens(Flags) : " + EXPANDED + "[]",
                fragment("inferredTokens"));
        Assert.assertEquals("inferredConst : readonly [\"alpha\", \"beta\", \"gamma\"]", fragment("inferredConst"));
        Assert.assertEquals("inferredField : readonly [\"alpha\", \"beta\", \"gamma\"]",
                fragment("Holder.inferredField"));
    }

    @Test
    public void parameterTypesAndMethodUniqueNamesAreUnchanged() {
        Assert.assertTrue(model.copyOfComponent(name("tokensFromFlags.flags")).isPresent());
        Assert.assertEquals("Partial<Flags>", fragment("tokensFromFlags.flags"));
        Assert.assertTrue(model.copyOfComponent(name("Holder.describe(Flags)")).isPresent());
        Assert.assertTrue(model.copyOfComponent(name("Holder.describe(Flags).flags")).isPresent());
    }

    @Test
    public void referencesComeFromTheResolvedType() {
        final String literals = "SimpleTypeReference:external:\"alpha\", SimpleTypeReference:external:\"beta\", "
                + "SimpleTypeReference:external:\"gamma\", ";
        Assert.assertEquals("[" + literals
                + "SimpleTypeReference:external:(\"alpha\" | \"beta\" | \"gamma\")[], "
                + "SimpleTypeReference:external:Partial, SimpleTypeReference:internal:src.Tokens.Flags]",
                references("tokensFromFlags"));
        Assert.assertEquals("[" + literals + "SimpleTypeReference:external:\"one\", "
                + "SimpleTypeReference:external:\"two\", SimpleTypeReference:external:Record, "
                + "SimpleTypeReference:external:readonly (\"alpha\" | \"beta\" | \"gamma\")[]]",
                references("RUNTIME"));
    }

    private static String references(final String symbolPath) {
        return model.copyOfComponent(name(symbolPath)).orElseThrow().references().stream()
                .map(ref -> ref.getClass().getSimpleName() + ":" + (ref.isExternal() ? "external" : "internal")
                        + ":" + ref.invokedComponent())
                .sorted().toList().toString();
    }

    private static String fragment(final String symbolPath) {
        return model.copyOfComponent(name(symbolPath)).orElseThrow().codeFragment();
    }

    private static String name(final String symbolPath) {
        return TypeScriptTestUtil.uniqueName(PACKAGE_PATH, MODULE, symbolPath);
    }
}
