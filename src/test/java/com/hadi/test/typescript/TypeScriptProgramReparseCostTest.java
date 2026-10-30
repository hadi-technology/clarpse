package com.hadi.test.typescript;

import com.hadi.clarpse.compiler.typescript.NodeRuntime;
import com.hadi.test.typescript.ParseCountedTypeScript.Observation;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/**
 * The work a one-level TypeScript compile does in the compiler, measured as parses and programs
 * rather than time. A repository of several packages with a {@code tsconfig.json} each should cost
 * about what the same files cost under one config: a file, or a library declaration file, read
 * once however many programs hold it.
 *
 * <p>A parse is one distinct {@code SourceFile} object per path across all programs the daemon
 * built (see {@link ParseCountedTypeScript}).
 */
public class TypeScriptProgramReparseCostTest {

    private static final int GROUPS = 8;
    private static final int PER_GROUP = 10;
    private static final double TOLERATED_RATIO = 1.25;

    @Before
    public void nodeIsAvailable() {
        Assume.assumeTrue(NodeRuntime.isNodeAvailable());
    }

    private static Observation analyseEverything(final SyntheticMonorepo repo) throws Exception {
        final Observation observed = ParseCountedTypeScript.observe(repo.files, repo.sourcePaths());
        assertTrue("fixture must compile cleanly: " + observed.result.failures(),
                observed.result.failures().isEmpty());
        return observed;
    }

    @Test
    public void everySourceFileIsParsedOnceAcrossAllProgramsOfACompile() throws Exception {
        final SyntheticMonorepo repo = new SyntheticMonorepo(GROUPS, PER_GROUP, true);
        final Observation observed = analyseEverything(repo);

        final int distinct = observed.parses.size();
        final int worst = observed.parses.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue("expected " + repo.sourcePaths().size() + " source files parsed once each (" + distinct
                        + " distinct files seen), but there were " + observed.sourceParses() + " parses across "
                        + observed.programs() + " programs; the most-parsed file was parsed " + worst + " times",
                observed.sourceParses() == distinct && worst == 1);
    }

    @Test
    public void libraryDeclarationFilesAreParsedOnceAcrossAllProgramsOfACompile() throws Exception {
        final SyntheticMonorepo repo = new SyntheticMonorepo(GROUPS, PER_GROUP, true);
        final Observation observed = analyseEverything(repo);

        final int distinct = observed.libParses.size();
        assertTrue("expected each of " + distinct + " library .d.ts files parsed once (" + distinct
                        + " parses in all), but there were " + observed.libFileParses() + " library parses across "
                        + observed.programs() + " programs",
                distinct > 0 && observed.libFileParses() == distinct);
    }

    @Test
    public void parseWorkForTheSameFilesDoesNotGrowWithTheNumberOfConfigs() throws Exception {
        final Observation oneConfig = analyseEverything(new SyntheticMonorepo(GROUPS, PER_GROUP, false));
        final Observation manyConfigs = analyseEverything(new SyntheticMonorepo(GROUPS, PER_GROUP, true));

        final int one = oneConfig.sourceParses() + oneConfig.libFileParses();
        final int many = manyConfigs.sourceParses() + manyConfigs.libFileParses();
        final double ratio = (double) many / one;
        assertTrue("the same " + GROUPS * PER_GROUP + " files cost " + one + " parses under 1 config ("
                        + oneConfig.programs() + " program) and " + many + " parses under " + GROUPS + " configs ("
                        + manyConfigs.programs() + " programs): ratio " + String.format("%.2f", ratio)
                        + ", expected at most " + TOLERATED_RATIO,
                ratio <= TOLERATED_RATIO);
    }

    @Test
    public void aConfigsProgramHoldsOnlyTheFilesItOwnsAndWhatTheyImport() throws Exception {
        final SyntheticMonorepo repo = new SyntheticMonorepo(GROUPS, PER_GROUP, true);
        // One analysed file per package; each imports its predecessor (same package, so level one)
        // and a file of the next package (analysed or level one of that package).
        final List<String> analysed = java.util.stream.IntStream.range(0, GROUPS)
                .mapToObj(g -> repo.path(g, 3)).collect(Collectors.toList());
        final Observation observed = ParseCountedTypeScript.observe(repo.files, analysed);
        assertTrue("fixture must compile cleanly: " + observed.result.failures(),
                observed.result.failures().isEmpty());

        // Per package: the analysed file and its same-package import are owned (2); what those two
        // import in the next package is the only other file the program may need (2).
        final int allowed = 4;
        final List<Integer> rootCounts = observed.programRoots.stream().map(List::size)
                .collect(Collectors.toList());
        final int planned = observed.programRoots.stream().mapToInt(List::size).max().orElse(0);
        assertTrue("expected each of the " + GROUPS + " programs to hold at most " + allowed
                        + " root files (2 owned + 2 imported), but root counts were " + rootCounts
                        + " and the largest program held " + planned + " roots and "
                        + observed.programFileCounts.stream().mapToInt(Integer::intValue).max().orElse(0)
                        + " source files in all",
                planned <= allowed);
    }

    @Test
    public void noProgramIsBuiltTwiceWhileAnalysedAndLevelOneFilesAreModelled() throws Exception {
        final SyntheticMonorepo repo = new SyntheticMonorepo(GROUPS, PER_GROUP, true);
        final List<String> analysed = java.util.stream.IntStream.range(0, GROUPS)
                .mapToObj(g -> repo.path(g, 3)).collect(Collectors.toList());
        final Observation observed = ParseCountedTypeScript.observe(repo.files, analysed);
        assertTrue("fixture must compile cleanly: " + observed.result.failures(),
                observed.result.failures().isEmpty());

        final long distinctConfigs = observed.programOutDirs.stream().distinct().count();
        assertTrue("expected " + GROUPS + " programs, one per config, but " + observed.programs()
                        + " were built for " + distinctConfigs + " configs in " + observed.sessions
                        + " daemon session(s): " + observed.programOutDirs,
                observed.programs() == distinctConfigs && distinctConfigs == GROUPS);
    }
}
