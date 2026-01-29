package com.hadi.clarpse.compiler;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseStart;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StringProvider;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.hadi.clarpse.listener.JavaTreeListener;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Set;

/**
 * JavaParser based compiler to process source code.
 */
public class ClarpseJavaCompiler implements ClarpseCompiler {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseJavaCompiler.class);

    @Override
    public CompileResult compile(final ProjectFiles projectFiles) throws CompileException {
        final OOPSourceCodeModel srcModel = new OOPSourceCodeModel();
        final Set<ProjectFile> compileFailures = new HashSet<>();
        if (projectFiles.size() > 0) {
            String persistDir = null;
            try {
                persistDir = projectFiles.projectDir();
                final CombinedTypeSolver typeSolver = setupTypeSolver(persistDir);
                final ParserConfiguration parserConfiguration = setupParserConfig(typeSolver);
                for (final ProjectFile file : projectFiles.files(Lang.JAVA)) {
                    try {
                        final CompilationUnit cu = new JavaParser(parserConfiguration)
                                .parse(ParseStart.COMPILATION_UNIT,
                                        new StringProvider(file.content())).getResult().get();
                        if (cu.getParsed() == Node.Parsedness.UNPARSABLE || file.content().isEmpty()) {
                            LOGGER.warn("Compilation unit (" + file.path() + ") is unparseable!");
                            compileFailures.add(file);
                        }
                        new JavaTreeListener(srcModel, file, typeSolver).visit(cu, null);
                    } catch (final Exception e) {
                        LOGGER.error("Failed to parse file " + file.path() + ".", e);
                        compileFailures.add(file);
                    }
                }
            } catch (Exception e) {
                throw new CompileException("An error occurred while parsing!", e);
            } finally {
                if (persistDir != null && !persistDir.isEmpty() && projectFiles.isTempProjectDir()) {
                    FileUtils.deleteQuietly(new File(persistDir));
                }
            }
            // Classify component references as internal/external
            classifyRefs(srcModel);
        }
        return new CompileResult(srcModel, compileFailures);
    }

    private void classifyRefs(OOPSourceCodeModel srcModel) {
        srcModel.components().forEach(component -> {
            final Set<ComponentReference> internalReferences = new LinkedHashSet<>();
            final Set<ComponentReference> externalReferences = new LinkedHashSet<>();
            component.references().forEach(componentReference -> {
                final boolean isInternal = srcModel.containsComponent(componentReference.invokedComponent());
                componentReference.setExternal(!isInternal);
                if (isInternal) {
                    internalReferences.add(componentReference);
                } else {
                    externalReferences.add(componentReference);
                }
            });
            component.setReferenceClassification(internalReferences, externalReferences);
        });
        LOGGER.debug("Classified component references as internal/external.");
    }

    private CombinedTypeSolver setupTypeSolver(String persistDir) {
        final CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(persistDir));
        return typeSolver;
    }

    private ParserConfiguration setupParserConfig(CombinedTypeSolver typeSolver) {
        final ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        parserConfiguration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
        parserConfiguration.setIgnoreAnnotationsWhenAttributingComments(true);
        return parserConfiguration;
    }
}
