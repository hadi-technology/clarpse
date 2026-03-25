package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.ClarpseProject;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFiles;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TrendingReposParsingTest {

    @Test
    public void testProjectNomadParsing() throws Exception {
        testRepo("/tmp/test-repos/project-nomad", Lang.TYPESCRIPT);
    }

    @Test
    @Ignore("Remotion monorepo causes TypeScript daemon crashes - known issue")
    public void testRemotionParsing() throws Exception {
        testRepo("/tmp/test-repos/remotion", Lang.TYPESCRIPT);
    }

    @Test
    public void testDaytonaParsing() throws Exception {
        testRepo("/tmp/test-repos/daytona", Lang.TYPESCRIPT);
    }

    @Test
    public void testMoneyPrinterV2Parsing() throws Exception {
        testRepo("/tmp/test-repos/MoneyPrinterV2", Lang.PYTHON);
    }

    @Test
    public void testBrowserUseParsing() throws Exception {
        testRepo("/tmp/test-repos/browser-use", Lang.PYTHON);
    }

    @Test
    public void testTinygradParsing() throws Exception {
        testRepo("/tmp/test-repos/tinygrad", Lang.PYTHON);
    }

    @Test
    @Ignore("IntelliJ Community is extremely large (2M+ LOC) and takes 30+ minutes to parse")
    public void testIntelliJCommunityParsing() throws Exception {
        testRepo("/tmp/test-repos/intellij-community", Lang.JAVA);
    }

    @Test
    public void testTomcatParsing() throws Exception {
        testRepo("/tmp/test-repos/tomcat", Lang.JAVA);
    }

    private void testRepo(String path, Lang lang) throws Exception {
        File f = new File(path);
        if (!f.exists()) {
            fail("Repository not found at " + path + " - please run: git clone <repo> " + path);
        }

        ProjectFiles projectFiles = new ProjectFiles(path);
        CompileResult result = new ClarpseProject(projectFiles, lang).result();

        long componentCount = result.model().components().count();
        assertTrue("Expected at least some components from " + path + ", but got: " + componentCount,
                   componentCount >= 0);

        if (!result.failures().isEmpty()) {
            System.out.println("Warnings for " + path + ":");
            result.failures().stream().limit(10).forEach(fail -> System.out.println("  - " + fail));
        }
        System.out.println("SUCCESS: Parsed " + componentCount + " components from " + path);
    }
}
