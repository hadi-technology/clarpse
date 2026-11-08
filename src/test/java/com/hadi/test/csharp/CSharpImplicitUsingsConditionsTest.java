package com.hadi.test.csharp;

import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Implicit usings apply only where the project file always enables them. */
public class CSharpImplicitUsingsConditionsTest {

    private static OOPSourceCodeModel model(final String csproj) throws Exception {
        return CSharpTestUtil.compileInline(
                new ProjectFile("/App/App.csproj", csproj),
                new ProjectFile("/App/C.cs", "namespace App; public class C { }")).model();
    }

    private static boolean imports(final OOPSourceCodeModel model, final String namespace) {
        return model.copyOfComponent("App.C").orElseThrow().imports().contains(namespace);
    }

    @Test
    public void aSettingInAnXmlCommentIsIgnored() throws Exception {
        assertFalse(imports(model("<Project Sdk=\"Microsoft.NET.Sdk\"><!-- <ImplicitUsings>enable</ImplicitUsings> --></Project>"),
                "System.Linq"));
    }

    @Test
    public void aSettingInAConditionedGroupIsUnknownAndNotApplied() throws Exception {
        assertFalse(imports(model("<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup Condition=\"'$(Configuration)'=='Debug'\">"
                + "<ImplicitUsings>enable</ImplicitUsings></PropertyGroup></Project>"), "System.Linq"));
    }

    @Test
    public void aConditionedElementIsNotApplied() throws Exception {
        assertFalse(imports(model("<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup>"
                + "<ImplicitUsings Condition=\"'$(X)'=='1'\">enable</ImplicitUsings></PropertyGroup></Project>"), "System.Linq"));
    }

    @Test
    public void anUnconditionedSettingNextToAConditionedOneStillApplies() throws Exception {
        assertTrue(imports(model("<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup><ImplicitUsings>enable</ImplicitUsings></PropertyGroup>"
                + "<PropertyGroup Condition=\"'$(X)'=='1'\"><ImplicitUsings>disable</ImplicitUsings></PropertyGroup></Project>"),
                "System.Linq"));
    }

    @Test
    public void usingRemoveTakesANamespaceOut() throws Exception {
        final OOPSourceCodeModel model = model("<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup><ImplicitUsings>enable</ImplicitUsings></PropertyGroup>"
                + "<ItemGroup><Using Remove=\"System.Net.Http\" /></ItemGroup></Project>");
        assertFalse(imports(model, "System.Net.Http"));
        assertTrue(imports(model, "System.Linq"));
    }

    @Test
    public void usingIncludeAddsANamespace() throws Exception {
        assertTrue(imports(model("<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup><ImplicitUsings>enable</ImplicitUsings></PropertyGroup>"
                + "<ItemGroup><Using Include=\"Acme.Common\" /></ItemGroup></Project>"), "Acme.Common"));
    }

    @Test
    public void aConditionedUsingRemoveIsNotApplied() throws Exception {
        assertTrue(imports(model("<Project Sdk=\"Microsoft.NET.Sdk\"><PropertyGroup><ImplicitUsings>enable</ImplicitUsings></PropertyGroup>"
                + "<ItemGroup Condition=\"'$(X)'=='1'\"><Using Remove=\"System.Net.Http\" /></ItemGroup></Project>"), "System.Net.Http"));
    }
}
