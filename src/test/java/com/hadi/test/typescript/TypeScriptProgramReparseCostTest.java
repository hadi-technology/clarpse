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
        final SyntheticMonorepo repo = new SyntheticMonorepo(GROUPS, PER_GROUP, true, true);
        final List<String> analysed = java.util.stream.IntStream.range(0, GROUPS)
                .mapToObj(g -> repo.path(g, 3)).collect(Collectors.toList());
        final Observation observed = ParseCountedTypeScript.observe(repo.files, analysed);
        assertTrue("fixture must compile cleanly: " + observed.result.failures(),
                observed.result.failures().isEmpty());

        // Per package g: the analysed file f3 and its same-package import f2 are owned. A package
        // of even index also imports f3 and f2 of the next package, so its program may hold those
        // two as well; an odd package imports nothing from another package.
        final List<String> built = observed.programOutDirs;
        final StringBuilder over = new StringBuilder();
        for (int p = 0; p < observed.programs(); p++) {
            final int group = Integer.parseInt(built.get(p).substring(built.get(p).lastIndexOf('g') + 1));
            final int allowed = group % 2 == 0 ? 4 : 2;
            final int roots = observed.programRoots.get(p).size();
            if (roots > allowed) {
                over.append(String.format("g%02d: %d roots (at most %d); ", group, roots, allowed));
            }
        }
        assertTrue("expected each program to hold only the files its config owns and what they import, but "
                        + over + "root counts were " + observed.programRoots.stream().map(List::size)
                        .collect(Collectors.toList()) + " and the largest program held "
                        + observed.programFileCounts.stream().mapToInt(Integer::intValue).max().orElse(0)
                        + " source files in all",
                observed.programs() > 0 && over.length() == 0);
    }

    @Test
    public void noProgramIsBuiltTwiceWhileAnalysedAndLevelOneFilesAreModelled() throws Exception {
        final SyntheticMonorepo repo = new SyntheticMonorepo(GROUPS, PER_GROUP, true, true);
        final List<String> analysed = java.util.stream.IntStream.range(0, GROUPS)
                .mapToObj(g -> repo.path(g, 3)).collect(Collectors.toList());
        final Observation observed = ParseCountedTypeScript.observe(repo.files, analysed);
        assertTrue("fixture must compile cleanly: " + observed.result.failures(),
                observed.result.failures().isEmpty());

        final long distinctConfigs = observed.programOutDirs.stream().distinct().count();
        assertTrue("expected " + GROUPS + " programs, one per config, but " + observed.programs()
                        + " were built for " + distinctConfigs + " configs in " + observed.sessions
                        + " daemon session(s): "
                        + observed.programOutDirs.stream().map(d -> d.substring(d.lastIndexOf('/') + 1))
                        .collect(Collectors.toList()),
                observed.programs() == distinctConfigs && distinctConfigs == GROUPS);
    }
}
