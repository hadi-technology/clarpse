package com.hadi.clarpse.listener;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.CompactConstructorDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.types.ResolvedType;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import com.hadi.clarpse.sourcemodel.Package;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * As the parse tree is developed by JavaParser, we add listener methods to
 * procedurally capture important information during this process and populate
 * our Source Code Model.
 */
public class JavaTreeListener extends VoidVisitorAdapter<Object> {

    private static final Logger LOGGER = LogManager.getLogger(JavaTreeListener.class);
    private final Stack<Component> componentStack = new Stack<>();
    private final Set<String> currentImports = new HashSet<>();
    private final TypeSolver typeSolver;
    private final OOPSourceCodeModel srcModel;
    private final Map<String, String> currentImportsMap = new HashMap<>();

    /** Packages brought into scope by an on-demand import (`import a.*;`), searched by name. */
    private final Set<String> currentWildcardImports = new HashSet<>();
    private final ProjectFile file;
    private Package currentPkg;
    private int currCyclomaticComplexity = 0;

    /**
     * @param srcModel Source model to populate from the parsing of the given code
     *                 base.
     * @param file     The path of the source file being parsed.
     */
    public JavaTreeListener(final OOPSourceCodeModel srcModel, final ProjectFile file,
            final TypeSolver typeSolver) {
        this.srcModel = srcModel;
        this.file = file;
        this.typeSolver = typeSolver;
    }

    private void completeComponent() {
        if (!componentStack.isEmpty()) {
            final Component completedCmp = componentStack.pop();
            // update cyclomatic complexity if component is a method or class
            if (completedCmp.componentType().isMethodComponent()
                    && !ParseUtil.componentStackContainsInterface(componentStack)) {
                completedCmp.setCyclo(currCyclomaticComplexity);
            } else if (completedCmp.componentType() == ComponentType.CLASS
                    || completedCmp.componentType() == ComponentType.ENUM) {
                completedCmp.setCyclo(ParseUtil.calculateClassCyclo(completedCmp, srcModel));
            }
            ParseUtil.copyRefsToParents(completedCmp, componentStack);
            srcModel.insertComponent(completedCmp);
        }
    }

    /**
     * Creates a new component based on the given ParseRuleContext.
     */
    private Component createComponent(final Node node, final ComponentType componentType) {
        final Component newCmp = new Component();
        newCmp.setPkg(currentPkg);
        newCmp.setModule(moduleNameForFile(file.path()));
        newCmp.setComponentType(componentType);
        if (node.getComment().isPresent()) {
            newCmp.setComment(node.getComment().get().toString());
        }
        newCmp.setCodeHash(normalizedCode(node).hashCode());
        newCmp.setSourceFilePath(file.path());
        return newCmp;
    }

    /**
     * The given node's source text with its comment and all whitespace stripped, so that a
     * reformatting or a comment edit leaves the derived code hash untouched.
     */
    private static String normalizedCode(final Node node) {
        final StringBuilder codeBuffer = new StringBuilder();
        final Node nodeNoComment = node.removeComment();
        nodeNoComment.getTokenRange().ifPresent(tokenRange -> tokenRange.iterator().forEachRemaining(
                javaToken -> codeBuffer.append(javaToken.asString().replaceAll("\\s+", ""))));
        if (codeBuffer.length() == 0) {
            codeBuffer.append(nodeNoComment.toString().replaceAll("\\s+", ""));
        }
        return codeBuffer.toString();
    }

    private static String moduleNameForFile(final String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        try {
            String fileName = java.nio.file.Paths.get(filePath).getFileName().toString();
            int extIndex = fileName.lastIndexOf('.');
            if (extIndex > 0) {
                fileName = fileName.substring(0, extIndex);
            }
            return fileName;
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public final void visit(final PackageDeclaration ctx, final Object arg) {
        String pkgPath = ctx.getNameAsString();
        currentPkg = new Package(pkgPath, pkgPath);
        if (!componentStack.isEmpty()) {
            LOGGER.error(
                    "New package declaration found while component stack not empty! component "
                            + "stack size is: " + componentStack.size());
        }
        super.visit(ctx, arg);
    }

    @Override
    public final void visit(final ImportDeclaration ctx, final Object arg) {
        final String fullImportName = ctx.getNameAsString().trim().replaceAll(";", "");
        final String shortImportName = ctx.getName().getId().trim().replaceAll(";", "");
        currentImports.add(fullImportName);
        // An on-demand import names a package, not a type, so it cannot go in the short-name map:
        // JavaParser reports `import a.*;` as name "a", which would record the useless entry
        // a -> a and leave every type it brings into scope unresolvable. `resolveType` searches
        // these prefixes instead. Without this, a class implementing an interface reached through
        // a wildcard import had no edge at all -- reproduced in three files, where `import a.Base;`
        // yields the edge and `import a.*;` yields nothing.
        if (ctx.isAsterisk()) {
            currentWildcardImports.add(fullImportName);
        } else {
            currentImportsMap.put(shortImportName, fullImportName);
        }
        super.visit(ctx, arg);
    }

    @Override
    public final void visit(final ClassExpr ctx, final Object arg) {
        if (ctx.toString().endsWith(".class")) {
            for (final Node node : ctx.getChildNodes()) {
                if (node.toString().equals(ctx.toString().substring(0, ctx.toString().indexOf(
                        ".class")))) {
                    ctx.remove(node);
                }
            }

        }
    }

    @Override
    public final void visit(final ClassOrInterfaceDeclaration ctx, final Object arg) {
        if (!ParseUtil.componentStackContainsMethod(componentStack)) {
            final Component cmp;
            if (ctx.isInterface()) {
                cmp = createComponent(ctx, ComponentType.INTERFACE);
            } else {
                cmp = createComponent(ctx, ComponentType.CLASS);
            }
            if (ctx.getTypeParameters().isNonEmpty()) {
                cmp.setCodeFragment(typeParametersCodeFragment(ctx.getTypeParameters()));
            }

            cmp.setAccessModifiers(resolveJavaParserModifiers(ctx.getModifiers()));
            cmp.setComponentName(ParseUtil.generateComponentName(ctx.getNameAsString(),
                    componentStack));
            cmp.setName(ctx.getNameAsString());
            cmp.setImports(currentImports);
            if (ctx.getComment().isPresent()) {
                cmp.setComment(ctx.getComment().get().toString());
            }
            ParseUtil.pointParentsToGivenChild(cmp, componentStack);

            if (ctx.getExtendedTypes() != null) {
                for (final ClassOrInterfaceType outerType : ctx.getExtendedTypes()) {
                    final String resolvedType = resolveType(outerType.asString());
                    if (resolvedType != null) {
                        ParseUtil.insertCmpRef(cmp, new TypeExtensionReference(resolvedType),
                                this.componentStack);
                    }
                }
            }

            if (ctx.getImplementedTypes() != null) {
                for (final ClassOrInterfaceType outerType : ctx.getImplementedTypes()) {
                    final String resolvedOuterType = resolveType(outerType.asString());
                    if (resolvedOuterType != null) {
                        ParseUtil.insertCmpRef(cmp, new TypeImplementationReference(resolvedOuterType),
                                this.componentStack);
                    }
                }
            }

            componentStack.push(cmp);
            visitTypeBody(ctx, arg);
            completeComponent();
        }
    }

    /**
     * Visits the member declarations of the given type, leaving alone the nodes that make up its own
     * declaration (its modifiers, type parameters and supertypes).
     */
    private void visitTypeBody(final Node ctx, final Object arg) {
        for (final Node node : ctx.getChildNodes()) {
            if (node instanceof FieldDeclaration || node instanceof Statement || node instanceof Expression
                    || node instanceof MethodDeclaration || node instanceof ConstructorDeclaration
                    || node instanceof ClassOrInterfaceDeclaration || node instanceof EnumDeclaration
                    || node instanceof AnnotationDeclaration || node instanceof RecordDeclaration) {
                node.accept(this, arg);
            }
        }
    }

    private static String typeParametersCodeFragment(final NodeList<? extends Type> typeParameters) {
        final StringBuilder fragment = new StringBuilder("<");
        for (final Type typeParam : typeParameters) {
            fragment.append(typeParam.asString()).append(", ");
        }
        while (fragment.toString().endsWith(", ") || fragment.toString().endsWith(",")) {
            fragment.setLength(fragment.length() - 1);
        }
        return fragment + ">";
    }

    /**
     * Records are modelled as {@link ComponentType#CLASS} components; their record components become
     * {@code FIELD} children, and the canonical constructor is modelled whether it is declared
     * explicitly, declared compactly, or left implicit.
     */
    @Override
    public final void visit(final RecordDeclaration ctx, final Object arg) {
        if (!ParseUtil.componentStackContainsMethod(componentStack)) {
            final Component cmp = createComponent(ctx, ComponentType.CLASS);
            if (ctx.getTypeParameters().isNonEmpty()) {
                cmp.setCodeFragment(typeParametersCodeFragment(ctx.getTypeParameters()));
            }
            cmp.setAccessModifiers(resolveJavaParserModifiers(ctx.getModifiers()));
            // A record is implicitly final, which its modifier list does not spell out.
            cmp.insertAccessModifier("final");
            cmp.setComponentName(ParseUtil.generateComponentName(ctx.getNameAsString(), componentStack));
            cmp.setName(ctx.getNameAsString());
            cmp.setImports(currentImports);
            if (ctx.getComment().isPresent()) {
                cmp.setComment(ctx.getComment().get().toString());
            }
            ParseUtil.pointParentsToGivenChild(cmp, componentStack);

            for (final ClassOrInterfaceType implementedType : ctx.getImplementedTypes()) {
                final String resolvedType = resolveType(implementedType.asString());
                if (resolvedType != null) {
                    ParseUtil.insertCmpRef(cmp, new TypeImplementationReference(resolvedType),
                            this.componentStack);
                }
            }

            componentStack.push(cmp);
            insertRecordComponentFields(ctx, arg);
            insertRecordCanonicalConstructor(ctx, arg);
            visitTypeBody(ctx, arg);
            completeComponent();
        }
    }

    /**
     * Each record component is an implicitly private final field of the record.
     */
    private void insertRecordComponentFields(final RecordDeclaration ctx, final Object arg) {
        for (final Parameter recordComponent : ctx.getParameters()) {
            final Component fieldCmp = createComponent(recordComponent, ComponentType.FIELD);
            fieldCmp.setName(recordComponent.getNameAsString());
            fieldCmp.setCodeFragment(recordComponent.getNameAsString() + " : "
                    + recordComponent.getType().asString());
            fieldCmp.setComponentName(ParseUtil.generateComponentName(recordComponent.getNameAsString(),
                    componentStack));
            fieldCmp.setAccessModifiers(Arrays.asList("private", "final"));
            ParseUtil.pointParentsToGivenChild(fieldCmp, componentStack);
            componentStack.push(fieldCmp);
            // Walk the declared type the same way a field declaration's type is walked, so that a record
            // component contributes the same dependency edges an equivalent field would.
            recordComponent.getType().accept(this, arg);
            completeComponent();
        }
    }

    /**
     * Models the record's canonical constructor. An explicitly declared one is left to
     * {@link #visit(ConstructorDeclaration, Object)}; a compact declaration or no declaration at all is
     * synthesized here from the record components, so that the constructor and its parameters are
     * present either way.
     */
    private void insertRecordCanonicalConstructor(final RecordDeclaration ctx, final Object arg) {
        if (declaresCanonicalConstructor(ctx)) {
            return;
        }
        final List<CompactConstructorDeclaration> compactCtors = ctx.getCompactConstructors();
        CompactConstructorDeclaration compactCtor = null;
        if (!compactCtors.isEmpty()) {
            compactCtor = compactCtors.get(0);
        }
        Node declarationNode = ctx;
        if (compactCtor != null) {
            declarationNode = compactCtor;
        }
        final Component ctorCmp = createComponent(declarationNode, ComponentType.CONSTRUCTOR);
        ctorCmp.setName(ctx.getNameAsString());
        final String signature = ctx.getNameAsString() + "(" + getFormalParameterTypesList(ctx.getParameters()) + ")";
        String compactBody = "";
        if (compactCtor != null) {
            compactBody = normalizedCode(compactCtor);
        }
        // Hash the canonical signature rather than the whole record, so that an unrelated member edit
        // does not read as a change to this constructor.
        ctorCmp.setCodeHash((signature + compactBody).hashCode());
        if (compactCtor != null) {
            ctorCmp.setAccessModifiers(resolveJavaParserModifiers(compactCtor.getModifiers()));
            for (final ReferenceType thrown : compactCtor.getThrownExceptions()) {
                final String resolvedType = resolveType(thrown.asString());
                if (resolvedType != null) {
                    ParseUtil.insertCmpRef(ctorCmp, new SimpleTypeReference(resolvedType), this.componentStack);
                }
            }
        } else {
            // An implicit canonical constructor is as visible as the record itself.
            ctorCmp.setAccessModifiers(visibilityModifiers(ctx.getModifiers()));
        }
        ctorCmp.setCodeFragment(signature);
        ctorCmp.setComponentName(ParseUtil.generateComponentName(signature, componentStack));
        ParseUtil.pointParentsToGivenChild(ctorCmp, componentStack);
        componentStack.push(ctorCmp);
        for (final Parameter recordComponent : ctx.getParameters()) {
            final Component ctorParamCmp = createComponent(recordComponent,
                    ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT);
            ctorParamCmp.setName(recordComponent.getNameAsString());
            ctorParamCmp.setCodeFragment(recordComponent.getType().asString());
            ctorParamCmp.setComponentName(ParseUtil.generateComponentName(recordComponent.getNameAsString(),
                    componentStack));
            ctorParamCmp.setAccessModifiers(resolveJavaParserModifiers(recordComponent.getModifiers()));
            final String resolvedType = resolveType(recordComponent.getType().asString());
            if (resolvedType != null) {
                ParseUtil.insertCmpRef(ctorParamCmp, new SimpleTypeReference(resolvedType), this.componentStack);
            }
            ParseUtil.pointParentsToGivenChild(ctorParamCmp, componentStack);
            componentStack.push(ctorParamCmp);
            completeComponent();
        }
        currCyclomaticComplexity = 1;
        if (compactCtor != null) {
            currCyclomaticComplexity += countLogicalBinaryOperators(compactCtor);
            compactCtor.getBody().accept(this, arg);
        }
        completeComponent();
    }

    /**
     * True if the record body declares the canonical constructor in full, in which case the regular
     * constructor visitor models it.
     */
    private static boolean declaresCanonicalConstructor(final RecordDeclaration ctx) {
        final List<String> recordComponentTypes = new ArrayList<>();
        for (final Parameter recordComponent : ctx.getParameters()) {
            recordComponentTypes.add(recordComponent.getType().asString());
        }
        for (final Node member : ctx.getMembers()) {
            if (!(member instanceof ConstructorDeclaration)) {
                continue;
            }
            final List<String> ctorParamTypes = new ArrayList<>();
            for (final Parameter param : ((ConstructorDeclaration) member).getParameters()) {
                ctorParamTypes.add(param.getType().asString());
            }
            if (ctorParamTypes.equals(recordComponentTypes)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> visibilityModifiers(final NodeList<Modifier> modifiers) {
        final List<String> visibility = new ArrayList<>();
        for (final Modifier modifier : modifiers) {
            final String keyword = modifier.toString().toLowerCase(Locale.ROOT).trim();
            if (keyword.equals("public") || keyword.equals("protected") || keyword.equals("private")) {
                visibility.add(keyword);
            }
        }
        return visibility;
    }

    @Override
    public final void visit(final EnumDeclaration ctx, final Object arg) {
        if (!ParseUtil.componentStackContainsMethod(componentStack)) {
            final Component enumCmp = createComponent(ctx, ComponentType.ENUM);
            enumCmp.setComponentName(ParseUtil.generateComponentName(ctx.getNameAsString(),
                    componentStack));
            enumCmp.setImports(currentImports);
            enumCmp.setName(ctx.getNameAsString());
            enumCmp.setAccessModifiers(resolveJavaParserModifiers(ctx.getModifiers()));
            ParseUtil.pointParentsToGivenChild(enumCmp, componentStack);
            if (ctx.getComment().isPresent()) {
                enumCmp.setComment(ctx.getComment().get().toString());
            }
            componentStack.push(enumCmp);
            for (final Node node : ctx.getChildNodes()) {
                node.accept(this, arg);
            }
            completeComponent();
        }
    }

    private int countLogicalBinaryOperators(final Node n) {
        int logicalBinaryOperators = 0;
        StringBuilder codeBuffer = new StringBuilder();
        final Node nodeNoComment = n.removeComment();
        nodeNoComment.getTokenRange().ifPresent(tokenRange -> tokenRange.iterator().forEachRemaining(
                javaToken -> codeBuffer.append(javaToken.asString()).append(" ")));
        if (codeBuffer.length() == 0) {
            codeBuffer.append(nodeNoComment.toString()).append(" ");
        }
        logicalBinaryOperators += StringUtils.countMatches(codeBuffer, " && ");
        logicalBinaryOperators += StringUtils.countMatches(codeBuffer, " || ");
        logicalBinaryOperators += StringUtils.countMatches(codeBuffer, " ? ");
        return logicalBinaryOperators;
    }

    @Override
    public final void visit(final EnumConstantDeclaration ctx, final Object arg) {
        final Component enumConstCmp = createComponent(ctx, ComponentType.ENUM_CONSTANT);
        enumConstCmp.setName(ctx.getNameAsString());
        enumConstCmp.setComponentName(ParseUtil.generateComponentName(ctx.getNameAsString(),
                componentStack));
        ParseUtil.pointParentsToGivenChild(enumConstCmp, componentStack);
        if (ctx.getComment().isPresent()) {
            enumConstCmp.setComment(ctx.getComment().get().toString());
        }
        componentStack.push(enumConstCmp);
        super.visit(ctx, arg);
        completeComponent();
    }

    @Override
    public final void visit(final MethodCallExpr ctx, final Object arg) {
        if (!componentStack.isEmpty()) {
            final Component currCmp = componentStack.peek();
            final String resolvedClassType = resolveMethodCallType(ctx);
            if (resolvedClassType != null) {
                ParseUtil.insertCmpRef(currCmp, new SimpleTypeReference(resolvedClassType),
                        this.componentStack);
            }
        }
        super.visit(ctx, arg);
    }

    private String resolveMethodCallType(final MethodCallExpr ctx) {
        try {
            final ResolvedMethodDeclaration resolvedMethod = ctx.resolve();
            final String declaringType = resolvedMethod.declaringType().getQualifiedName();
            return extractClassName(declaringType);
        } catch (final Exception ignored) {
        }
        if (ctx.getScope().isPresent()) {
            final Expression scope = ctx.getScope().get();
            try {
                final ResolvedType resolvedType = scope.calculateResolvedType();
                if (resolvedType.isReferenceType()) {
                    return extractClassName(resolvedType.asReferenceType().getQualifiedName());
                }
            } catch (final Exception ignored) {
            }
            if (scope.isNameExpr()) {
                return resolveType(scope.asNameExpr().getNameAsString());
            }
            if (scope.isFieldAccessExpr()) {
                return resolveType(scope.asFieldAccessExpr().getNameAsString());
            }
        }
        return resolveType(ctx.getNameAsString());
    }

    @Override
    public final void visit(final MethodDeclaration ctx, final Object arg) {
        if (!ParseUtil.componentStackContainsMethod(componentStack)) {
            final Component currMethodCmp = createComponent(ctx, ComponentType.METHOD);
            currMethodCmp.setName(ctx.getNameAsString());
            currMethodCmp.setCodeFragment(ctx.getType().asString());
            currMethodCmp.setAccessModifiers(resolveJavaParserModifiers(ctx.getModifiers()));
            String formalParametersString = "(";
            if (ctx.getParameters() != null) {
                formalParametersString += getFormalParameterTypesList(ctx.getParameters());
            }
            formalParametersString += ")";
            if (ctx.getComment().isPresent()) {
                currMethodCmp.setComment(ctx.getComment().get().toString());
            }
            for (final ReferenceType stmt : ctx.getThrownExceptions()) {
                final String resolvedType = resolveType(stmt.asString());
                if (resolvedType != null) {
                    ParseUtil.insertCmpRef(currMethodCmp,
                            new SimpleTypeReference(resolvedType),
                            this.componentStack);
                }
            }
            final String methodSignature = currMethodCmp.name() + formalParametersString;
            String codeFragment = currMethodCmp.name() + formalParametersString;
            if (ctx.getType().toString() != null && !ctx.getType().toString().equals("void")) {
                codeFragment += " : " + ctx.getType().toString();
            }
            currMethodCmp.setCodeFragment(codeFragment);
            currMethodCmp.setComponentName(ParseUtil.generateComponentName(methodSignature,
                    componentStack));
            ParseUtil.pointParentsToGivenChild(currMethodCmp, componentStack);
            componentStack.push(currMethodCmp);
            if (ctx.getParameters() != null) {
                for (final Parameter param : ctx.getParameters()) {
                    final Component methodParamCmp = createComponent(param,
                            ComponentType.METHOD_PARAMETER_COMPONENT);
                    methodParamCmp.setName(param.getNameAsString());
                    methodParamCmp.setCodeFragment(param.getType().asString());
                    methodParamCmp.setComponentName(ParseUtil.generateComponentName(
                            param.getNameAsString(), componentStack));
                    methodParamCmp.setAccessModifiers(resolveJavaParserModifiers(param.getModifiers()));
                    final String resolvedType = resolveType(param.getType().asString());
                    if (resolvedType != null) {
                        ParseUtil.insertCmpRef(methodParamCmp, new SimpleTypeReference(resolvedType),
                                this.componentStack);
                    }
                    ParseUtil.pointParentsToGivenChild(methodParamCmp, componentStack);
                    componentStack.push(methodParamCmp);
                    completeComponent();
                }
            }
            currCyclomaticComplexity = 1 + countLogicalBinaryOperators(ctx);
            super.visit(ctx, arg);
            completeComponent();
        }
    }

    private String getFormalParameterTypesList(final List<Parameter> formalParameterList) {
        StringBuilder typesList = new StringBuilder();
        for (final Parameter fpContext : formalParameterList) {
            typesList.append(fpContext.getType().toString().trim()).append(", ");
        }
        typesList = new StringBuilder(typesList.toString().trim());
        while (typesList.toString().trim().endsWith(",")) {
            typesList = new StringBuilder(typesList.substring(0, typesList.length() - 1).trim());
        }
        return typesList.toString();
    }

    @Override
    public final void visit(final ConstructorDeclaration ctx, final Object arg) {
        if (!ParseUtil.componentStackContainsMethod(componentStack)) {
            final Component currMethodCmp = createComponent(ctx, ComponentType.CONSTRUCTOR);
            final String methodName = ctx.getNameAsString();
            currMethodCmp.setName(methodName);
            currMethodCmp.setAccessModifiers(resolveJavaParserModifiers(ctx.getModifiers()));
            if (ctx.getComment().isPresent()) {
                currMethodCmp.setComment(ctx.getComment().get().toString());
            }
            currMethodCmp.setCodeFragment("void");
            String formalParametersString = "(";
            if (ctx.getParameters() != null) {
                formalParametersString += getFormalParameterTypesList(ctx.getParameters());
            }
            formalParametersString += ")";

            for (final ReferenceType stmt : ctx.getThrownExceptions()) {
                final String resolvedType = resolveType(stmt.asString());
                if (resolvedType != null) {
                    ParseUtil.insertCmpRef(currMethodCmp,
                            new SimpleTypeReference(resolvedType),
                            this.componentStack);
                }
            }

            final String methodSignature = currMethodCmp.name() + formalParametersString;
            final String codeFragment = currMethodCmp.name() + formalParametersString;
            currMethodCmp.setCodeFragment(codeFragment);
            currMethodCmp.setComponentName(ParseUtil.generateComponentName(methodSignature,
                    componentStack));
            ParseUtil.pointParentsToGivenChild(currMethodCmp, componentStack);
            componentStack.push(currMethodCmp);
            if (ctx.getParameters() != null) {
                for (final Parameter param : ctx.getParameters()) {
                    final Component methodParamCmp = createComponent(param,
                            ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT);
                    methodParamCmp.setCodeFragment(param.getType().asString());
                    methodParamCmp.setName(param.getNameAsString());
                    methodParamCmp.setComponentName(ParseUtil.generateComponentName(param.getNameAsString(),
                            componentStack));
                    methodParamCmp.setAccessModifiers(resolveJavaParserModifiers(param.getModifiers()));
                    final String resolvedType = resolveType(param.getType().asString());
                    if (resolvedType != null) {
                        ParseUtil.insertCmpRef(methodParamCmp, new SimpleTypeReference(
                                resolvedType),
                                this.componentStack);
                    }
                    ParseUtil.pointParentsToGivenChild(methodParamCmp, componentStack);
                    componentStack.push(methodParamCmp);
                    completeComponent();
                }
            }
            currCyclomaticComplexity = 1 + countLogicalBinaryOperators(ctx);
            super.visit(ctx, arg);
            completeComponent();
        }
    }

    private List<String> resolveJavaParserModifiers(final NodeList<Modifier> modifiers) {
        final List<String> modifierList = new ArrayList<>();
        for (final Modifier modifier : modifiers) {
            modifierList.add(modifier.toString().toLowerCase(Locale.ROOT).trim());
        }
        return modifierList;
    }

    @Override
    public final void visit(final IfStmt ctx, final Object arg) {
        currCyclomaticComplexity += 1;
        super.visit(ctx, arg);
    }

    @Override
    public final void visit(final CatchClause ctx, final Object arg) {
        currCyclomaticComplexity += 1;
        super.visit(ctx, arg);
    }

    @Override
    public final void visit(final ForEachStmt ctx, final Object arg) {
        currCyclomaticComplexity += 1;
        super.visit(ctx, arg);
    }

    @Override
    public final void visit(final ForStmt ctx, final Object arg) {
        currCyclomaticComplexity += 1;
        super.visit(ctx, arg);
    }

    @Override
    public final void visit(final WhileStmt ctx, final Object arg) {
        currCyclomaticComplexity += 1;
        super.visit(ctx, arg);
    }

    @Override
    public final void visit(final ThrowStmt ctx, final Object arg) {
        currCyclomaticComplexity += 1;
        super.visit(ctx, arg);
    }

    @Override
    public final void visit(final SwitchStmt ctx, final Object arg) {
        for (final SwitchEntry sEStmt : ctx.getEntries()) {
            if (sEStmt.getStatements().size() > 0 && !sEStmt.toString().trim().startsWith(
                    "default:")) {
                currCyclomaticComplexity += 1;
            }
        }
        super.visit(ctx, arg);
    }

    @Override
    public final void visit(final VariableDeclarationExpr ctx, final Object arg) {
        try {
            final Component cmp = createComponent(ctx, ComponentType.LOCAL);
            cmp.setAccessModifiers(resolveJavaParserModifiers(ctx.getModifiers()));
            for (final VariableDeclarator copy : ctx.getVariables()) {
                final Component tmp = new Component(cmp);
                tmp.setName(copy.getNameAsString());
                tmp.setComponentName(ParseUtil.generateComponentName(
                        copy.getNameAsString(), componentStack));
                ParseUtil.pointParentsToGivenChild(tmp, componentStack);
                componentStack.push(tmp);
                ctx.getAnnotations().forEach(annotation -> annotation.accept(this, arg));
                copy.accept(this, arg);
                completeComponent();
            }
        } catch (final Exception e) {
            LOGGER.error("Failed to process variable declaration!", e);
        }
    }

    @Override
    public final void visit(final FieldDeclaration ctx, final Object arg) {
        if (!componentStack.isEmpty()) {
            try {
                final Component currCmp = componentStack.peek();
                final Component cmp;
                if (currCmp.componentType() == ComponentType.INTERFACE) {
                    cmp = createComponent(ctx, ComponentType.INTERFACE_CONSTANT);
                } else {
                    cmp = createComponent(ctx, ComponentType.FIELD);
                }
                if (ctx.getComment().isPresent()) {
                    cmp.setComment(ctx.getComment().get().toString());
                }
                cmp.setAccessModifiers(resolveJavaParserModifiers(ctx.getModifiers()));
                for (final VariableDeclarator copy : ctx.getVariables()) {
                    final Component tmp = new Component(cmp);
                    tmp.setName(copy.getNameAsString());
                    tmp.setCodeFragment(tmp.name() + " : " + copy.getType().toString());
                    tmp.setComponentName(ParseUtil.generateComponentName(copy.getNameAsString(),
                            componentStack));
                    ParseUtil.pointParentsToGivenChild(tmp, componentStack);
                    componentStack.push(tmp);
                    ctx.getAnnotations().forEach(annotation -> annotation.accept(this, arg));
                    copy.accept(this, arg);
                    completeComponent();
                }
            } catch (final Exception e) {
                LOGGER.error("Failed to process field declaration!", e);
            }
        }
    }

    @Override
    public final void visit(final VariableDeclarator ctx, final Object arg) {
        ctx.getType().accept(this, arg);
        ctx.getInitializer().ifPresent(init -> init.accept(this, arg));
    }

    @Override
    public final void visit(final ClassOrInterfaceType ctx, final Object arg) {
        if (Character.isUpperCase(ctx.asString().codePointAt(0)) && ctx.getChildNodes().isEmpty()) {
            if (!componentStack.isEmpty()) {
                final Component currCmp = componentStack.peek();
                final String resolvedType = resolveType(ctx.asString());
                if (resolvedType != null) {
                    ParseUtil.insertCmpRef(currCmp, new SimpleTypeReference(resolvedType),
                            this.componentStack);
                }
            }

        }
        super.visit(ctx, arg);
    }

    @Override
    public final void visit(final SimpleName ctx, final Object arg) {
        if (!componentStack.isEmpty()) {
            final Component currCmp = componentStack.peek();
            // Every identifier in the file reaches here -- constants, locals, method names as
            // well as types -- so whether an unresolved token may be assumed to name a type in
            // this package depends on where it sits. `B` in `private B b;` is the name of a
            // ClassOrInterfaceType and can be nothing else; `CAP` in `return CAP;` is a bare
            // expression and is far more likely a constant. Assuming in both places is what
            // produced references to types like `<package>.MAX_NOTES` that do not exist.
            final boolean typePosition = ctx.getParentNode()
                    .filter(parent -> parent instanceof ClassOrInterfaceType)
                    .isPresent();
            final String resolvedType = resolveType(ctx.asString(), typePosition);
            if (resolvedType != null) {
                ParseUtil.insertCmpRef(currCmp, new SimpleTypeReference(resolvedType),
                        this.componentStack);
            }
        }
        super.visit(ctx, arg);
    }

    private String resolveType(final String type) {
        return resolveType(type, true);
    }

    /**
     * Resolves a token to a qualified type name.
     *
     * @param assumeCurrentPackage whether an unresolved token may be assumed to name a type in the
     *     current package. True in a <em>type position</em> -- {@code B b;} really does name a
     *     type, and if it is a sibling in the same package with no import there is nothing else it
     *     could be, so the assumption recovers a real edge. False for a bare identifier, where the
     *     token is as likely to be a constant, a variable or a method as a type: assuming there
     *     turned every {@code return MAX_NOTES;} into a reference to a type
     *     {@code <package>.MAX_NOTES} that does not exist. Measured on one repository, 528 of 1368
     *     external symbols were constants invented this way and 249 were generic expressions.
     *     Inventing is worse than omitting, because a missing edge reads as a coverage gap while
     *     an invented one reads as a fact.
     */
    private String resolveType(final String type, final boolean assumeCurrentPackage) {
        String resolvedType = "";
        final SymbolReference<ResolvedReferenceTypeDeclaration> symbol = typeSolver.tryToSolveType(type);
        if (currentImportsMap.containsKey(type)) {
            resolvedType = currentImportsMap.get(type);
        } else if (OOPSourceModelConstants.getJavaDefaultClasses().containsKey(type)) {
            resolvedType = OOPSourceModelConstants.getJavaDefaultClasses().get(type);
        } else if (symbol.isSolved()) {
            resolvedType = symbol.getCorrespondingDeclaration().getQualifiedName();
        } else {
            // On-demand imports, tried only after every exact form has failed and accepted only
            // when the type solver confirms the type exists in that package. A prefix that does not
            // resolve is skipped rather than assumed, so this recovers real edges without inventing
            // any -- the same rule the `assumeCurrentPackage` note below is about.
            for (final String wildcardPackage : currentWildcardImports) {
                final String candidate = wildcardPackage + "." + type;
                if (typeSolver.tryToSolveType(candidate).isSolved()) {
                    resolvedType = candidate;
                    break;
                }
            }
        }
        if (resolvedType.isEmpty()) {
            if (!assumeCurrentPackage) {
                return null;
            }
            // A sibling nested type, named plainly from inside the type that declares it:
            // `class Field { interface Validator {} static class RangeValidator implements Validator {} }`.
            // The solver cannot see it under a bare name and the current-package assumption below
            // turns it into `a.Validator`, which does not exist and is dropped as external -- so the
            // relation vanished rather than being wrong, which is why it read as a coverage gap.
            //
            // Confined to a type position and to candidates that are themselves types. Run over
            // bare identifiers it matched fields: `return MAX_NOTES;` inside a class found the
            // field `<Type>.MAX_NOTES` and invented a type reference to it, which is the failure
            // the hygiene tests exist to prevent.
            for (int i = componentStack.size() - 1; i >= 0; i--) {
                final Component enclosing = componentStack.get(i);
                if (!enclosing.componentType().isBaseComponent()) {
                    continue;
                }
                final String nested = enclosing.uniqueName() + "." + type;
                final boolean isType = srcModel.component(nested)
                        .map(found -> found.componentType().isBaseComponent()).orElse(false);
                if (isType || typeSolver.tryToSolveType(nested).isSolved()) {
                    return nested;
                }
            }
            if (currentPkg != null) {
                resolvedType = currentPkg.path() + "." + type;
            } else {
                resolvedType = type;
            }
        }
        final String resolvedClassType = extractClassName(resolvedType);
        if (!resolvedClassType.isEmpty()) {
            return resolvedClassType;
        } else {
            return null;
        }
    }

    private String extractClassName(final String symbolQualifiedName) {
        final LinkedList<String> parts = new LinkedList<>(Arrays.asList(symbolQualifiedName.split(
                "\\.")));
        String result = "";
        while (parts.size() > 0) {
            final int partsLen = parts.size();
            final String lastPart = parts.get(partsLen - 1);
            if (!lastPart.isEmpty()) {
                if (Character.isUpperCase(parts.get(partsLen - 1).charAt(0))) {
                    result = String.join(".", parts);
                    break;
                } else {
                    parts.remove(parts.get(partsLen - 1));
                }
            }
        }
        return result;
    }
}
