package com.hadi.clarpse.compiler.java;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.resolution.Navigator;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves repository types by looking up the files a {@link JavaDeclarationIndex} names and
 * parsing only those, never scanning a directory.
 *
 * <p>This is the only type solver for repository sources in a one-level Java compile. A name is
 * looked up by its longest indexed prefix, and the type is found inside the declaring file with
 * {@link Navigator#findType}, so nested types resolve through their top-level type. Files come
 * from the compile's {@link JavaUnitCache}, which parses each once for every solver of the compile.
 *
 * <p>A file not yet parsed is loaded only while the cache's {@link JavaLoadTracker} admits it. Past
 * the cap a lookup that would load a new file answers unsolved, and the caller falls back to the
 * names the source writes.
 *
 * <p>One instance serves one thread: its cache of solved names is not synchronised.
 */
public final class IndexedTypeSolver implements TypeSolver {

    private final JavaDeclarationIndex index;
    private final JavaUnitCache units;
    private final Map<String, SymbolReference<ResolvedReferenceTypeDeclaration>> solved = new HashMap<>();
    private TypeSolver parent;

    /**
     * Creates a solver.
     *
     * @param index The repository's declaration index.
     * @param units The compile's shared units.
     */
    public IndexedTypeSolver(final JavaDeclarationIndex index, final JavaUnitCache units) {
        this.index = index;
        this.units = units;
    }

    @Override
    public TypeSolver getParent() {
        return parent;
    }

    @Override
    public void setParent(final TypeSolver parent) {
        this.parent = parent;
    }

    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveType(final String name) {
        final SymbolReference<ResolvedReferenceTypeDeclaration> cached = solved.get(name);
        if (cached != null) {
            return cached;
        }
        final SymbolReference<ResolvedReferenceTypeDeclaration> result = solve(name);
        solved.put(name, result);
        return result;
    }

    /** Java modules are not modelled; a type is never looked up by module. */
    @Override
    public SymbolReference<ResolvedReferenceTypeDeclaration> tryToSolveTypeInModule(final String moduleName,
                                                                                   final String name) {
        return SymbolReference.unsolved();
    }

    private SymbolReference<ResolvedReferenceTypeDeclaration> solve(final String name) {
        final String topLevel = index.topLevelName(name);
        if (topLevel == null) {
            return SymbolReference.unsolved();
        }
        final String packageName = packageOf(topLevel);
        String typeName = name;
        if (!packageName.isEmpty()) {
            typeName = name.substring(packageName.length() + 1);
        }
        final List<String> paths = index.filesDeclaringExactly(topLevel);
        for (final String path : paths) {
            final Optional<CompilationUnit> unit = unit(path);
            if (unit.isEmpty()) {
                continue;
            }
            final Optional<TypeDeclaration<?>> type = Navigator.findType(unit.get(), typeName);
            if (type.isPresent()) {
                return SymbolReference.solved(JavaParserFacade.get(this).getTypeDeclaration(type.get()));
            }
        }
        return SymbolReference.unsolved();
    }

    private static String packageOf(final String topLevelName) {
        final int dot = topLevelName.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return topLevelName.substring(0, dot);
    }

    private Optional<CompilationUnit> unit(final String path) {
        return units.resolved(path);
    }
}
