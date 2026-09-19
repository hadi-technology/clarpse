package com.hadi.clarpse.compiler.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseStart;
import com.github.javaparser.StringProvider;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.listener.JavaTreeListener;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class for parsing individual Java files.
 */
public final class FileParser {

    private static final Logger LOGGER = LogManager.getLogger(FileParser.class);

    private FileParser() {
        // Utility class
    }

    /**
     * Parse a single Java file.
     *
     * @param parser     the JavaParser instance
     * @param typeSolver the type solver for symbol resolution
     * @param file       the file to parse
     * @param index      the index of the file (for ordering in parallel processing)
     * @return the parse outcome
     */
    public static ParseOutcome parseFile(
            final JavaParser parser,
            final CombinedTypeSolver typeSolver,
            final ProjectFile file,
            final int index) {
        return parseFile(parser, typeSolver, file, index, false);
    }

    /**
     * Parse a single Java file, optionally attributing method calls from written names only.
     *
     * @param parser     the JavaParser instance
     * @param typeSolver the type solver for symbol resolution
     * @param file       the file to parse
     * @param index      the index of the file (for ordering in parallel processing)
     * @param shallow    whether to attribute method calls without resolving them
     * @return the parse outcome
     */
    public static ParseOutcome parseFile(
            final JavaParser parser,
            final CombinedTypeSolver typeSolver,
            final ProjectFile file,
            final int index,
            final boolean shallow) {
        return walk(file, index, () -> parser.parse(ParseStart.COMPILATION_UNIT,
                new StringProvider(file.content())).getResult(), typeSolver, shallow, null);
    }

    /**
     * Walk a single Java file in the given context: taking its unit from the context's shared cache
     * when it has one, and parsing it otherwise.
     *
     * @param context the calling thread's parser context
     * @param file    the file to walk
     * @param index   the index of the file (for ordering in parallel processing)
     * @param shallow whether to attribute method calls without resolving them
     * @return the parse outcome
     */
    public static ParseOutcome parseFile(final ParserContext context, final ProjectFile file, final int index,
                                         final boolean shallow) {
        if (context.units() == null) {
            return parseFile(context.parser(), context.typeSolver(), file, index, shallow);
        }
        return walk(file, index, () -> context.units().walked(file.path()), context.typeSolver(), shallow,
                context);
    }

    private static ParseOutcome walk(final ProjectFile file, final int index,
                                     final java.util.function.Supplier<java.util.Optional<CompilationUnit>> unit,
                                     final CombinedTypeSolver typeSolver, final boolean shallow,
                                     final ParserContext context) {
        final OOPSourceCodeModel localModel = new OOPSourceCodeModel();
        CompileFailure failure = null;
        try {
            final java.util.Optional<CompilationUnit> parseResult = unit.get();
            if (parseResult.isEmpty()) {
                LOGGER.warn("Compilation unit (" + file.path() + ") is unparseable!");
                failure = new CompileFailure(file, "PARSE_FAILED", FailureCode.PARSE_FAILED);
            } else {
                final CompilationUnit cu = parseResult.get();
                if (cu.getParsed() == Node.Parsedness.UNPARSABLE || file.content().isEmpty()) {
                    LOGGER.warn("Compilation unit (" + file.path() + ") is unparseable!");
                    failure = new CompileFailure(file, "PARSE_FAILED", FailureCode.PARSE_FAILED);
                } else {
                    if (context != null) {
                        context.attach(cu);
                    }
                    new JavaTreeListener(localModel, file, typeSolver, shallow).visit(cu, null);
                }
            }
        } catch (final Throwable e) {
            LOGGER.error("Failed to parse file " + file.path() + ".", e);
            String message = e.getMessage();
            if (message == null || message.isEmpty()) {
                message = e.getClass().getSimpleName();
            }
            failure = new CompileFailure(file, message, FailureCode.PARSE_FAILED);
        }
        return new ParseOutcome(index, localModel, failure);
    }
}
