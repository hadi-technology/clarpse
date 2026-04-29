package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.ClarpseCompiler;
import com.hadi.clarpse.compiler.CompileException;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompileResult;
import com.hadi.clarpse.compiler.CompilerParallelismSupport;
import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.Lang;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.ProjectFiles;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * C# compiler backed by the JetBrains JVM parser and Clarpse-owned resolution.
 */
public final class ClarpseCSharpCompiler implements ClarpseCompiler {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseCSharpCompiler.class);

    @Override
    public CompileResult compile(final ProjectFiles projectFiles,
                                 final Collection<String> analyzedFilePaths) throws CompileException {
        final List<ProjectFile> csharpFiles = ClarpseCompiler.analyzedFiles(projectFiles, Lang.CSHARP, analyzedFilePaths);
        if (csharpFiles.isEmpty()) {
            return new CompileResult(new OOPSourceCodeModel(), Set.of());
        }
        final List<CSharpModel.ParseOutcome> parseOutcomes = parseFiles(csharpFiles);
        final List<CSharpModel.CSharpFileModel> successfulFiles = new ArrayList<>();
        final Set<CompileFailure> failures = new HashSet<>();
        for (final CSharpModel.ParseOutcome outcome : parseOutcomes) {
            if (outcome.failure() != null) {
                failures.add(outcome.failure());
            } else if (outcome.fileModel() != null) {
                successfulFiles.add(outcome.fileModel());
            }
        }
        final OOPSourceCodeModel model = CSharpModelAssembler.buildModel(successfulFiles);
        CompilerSupport.classifyClassCyclo(model, Set.of(
                com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.CLASS,
                com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.STRUCT,
                com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType.ENUM
        ));
        CompilerSupport.classifyReferences(model);
        return new CompileResult(model, failures);
    }

    private List<CSharpModel.ParseOutcome> parseFiles(final List<ProjectFile> files) throws CompileException {
        final int parallelism = CompilerParallelismSupport.resolveParallelism(files.size());
        if (parallelism > 1) {
            LOGGER.info("Parsing C# files in parallel using " + parallelism + " threads.");
        }
        final ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            final List<Future<CSharpModel.ParseOutcome>> futures = new ArrayList<>();
            for (int i = 0; i < files.size(); i += 1) {
                final ProjectFile file = files.get(i);
                final int index = i;
                futures.add(executor.submit(() -> CSharpFileParser.parseFile(file, index)));
            }
            final List<CSharpModel.ParseOutcome> outcomes = new ArrayList<>();
            for (final Future<CSharpModel.ParseOutcome> future : futures) {
                try {
                    outcomes.add(future.get());
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new CompileException("Interrupted while parsing C# files.", e);
                } catch (final ExecutionException e) {
                    throw new CompileException("Failed while parsing C# files.", e);
                }
            }
            outcomes.sort((left, right) -> Integer.compare(left.index(), right.index()));
            return outcomes;
        } finally {
            CompilerParallelismSupport.shutdownExecutor(executor);
        }
    }
}
