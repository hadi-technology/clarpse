package com.hadi.clarpse.listener;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
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
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.resolution.types.ResolvedType;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.TypeNames;
import com.hadi.clarpse.reference.AnnotationReference;
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
        this(srcModel, file, typeSolver, false);
    }

    /**
     * Whether method calls are attributed from the names the source writes alone, without resolving
     * the call or the type of its receiver. Resolving them reaches into the declaring types'
     * ancestors and the call results' types, which a boundary file of a one-level analysis must not
     * pull in.
     */
    private final boolean shallow;

    /**
     * A listener that may attribute method calls without resolving them.
     *
     * @param srcModel   Source model to populate.
     * @param file       The source file being parsed.
     * @param typeSolver Resolves type names.
     * @param shallow    Whether to attribute method calls from written names only.
     */
    public JavaTreeListener(final OOPSourceCodeModel srcModel, final ProjectFile file,
            final TypeSolver typeSolver, final boolean shallow) {
        this.srcModel = srcModel;
        this.file = file;
        this.typeSolver = typeSolver;
        this.shallow = shallow;
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

    /**
     * A class literal -- {@code Foo.class}, {@code a.b.Foo.class}, {@code Foo[].class} -- writes its
     * type in a type position, and is a dependency on it exactly as {@code Foo x;} is:
     * {@code store.save(record, Foo.class)} can be the only place a class mentions {@code Foo}. The
     * type is resolved the way every other written type is. {@code int.class} and {@code void.class}
     * name no class and contribute nothing.
     */
    @Override
    public final void visit(final ClassExpr ctx, final Object arg) {
        ctx.getType().accept(this, arg);
    }

    /**
     * The values of the annotations on a type declaration -- {@code @Uses(Foo.class)},
     * {@code @Path(Routes.BASE)} -- are dependencies of that type, as they already are on methods,
     * constructors, fields and enums. The annotation type itself is recorded separately, as an
     * {@link AnnotationReference}, by {@link #recordAnnotations}.
     */
    private void visitAnnotationValues(final NodeList<AnnotationExpr> annotations, final Object arg) {
        annotations.forEach(annotation -> annotation.accept(this, arg));
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
            recordAnnotations(ctx.getAnnotations(), cmp);
            cmp.setComponentName(ParseUtil.generateComponentName(ctx.getNameAsString(),
                    componentStack));
            cmp.setName(ctx.getNameAsString());
            cmp.setImports(currentImports);
            if (ctx.getComment().isPresent()) {
                cmp.setComment(ctx.getComment().get().toString());
            }
            ParseUtil.pointParentsToGivenChild(cmp, componentStack);

            final Set<String> typeParameters = typeParameterNamesInScope(ctx);
            if (ctx.getExtendedTypes() != null) {
                for (final ClassOrInterfaceType outerType : ctx.getExtendedTypes()) {
                    final String resolvedType = resolveType(outerType.asString());
                    if (resolvedType != null) {
                        ParseUtil.insertCmpRef(cmp, new TypeExtensionReference(resolvedType),
                                this.componentStack);
                    }
                    insertTypeArgumentRefs(cmp, outerType, typeParameters);
                }
            }

            if (ctx.getImplementedTypes() != null) {
                for (final ClassOrInterfaceType outerType : ctx.getImplementedTypes()) {
                    final String resolvedOuterType = resolveType(outerType.asString());
                    if (resolvedOuterType != null) {
                        ParseUtil.insertCmpRef(cmp, new TypeImplementationReference(resolvedOuterType),
                                this.componentStack);
                    }
                    insertTypeArgumentRefs(cmp, outerType, typeParameters);
                }
            }

            componentStack.push(cmp);
            visitAnnotationValues(ctx.getAnnotations(), arg);
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
                    || node instanceof AnnotationDeclaration || node instanceof RecordDeclaration
                    || node instanceof AnnotationMemberDeclaration) {
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
            recordAnnotations(ctx.getAnnotations(), cmp);
            // A record is implicitly final, which its modifier list does not spell out.
            cmp.insertAccessModifier("final");
            cmp.setComponentName(ParseUtil.generateComponentName(ctx.getNameAsString(), componentStack));
            cmp.setName(ctx.getNameAsString());
            cmp.setImports(currentImports);
            if (ctx.getComment().isPresent()) {
                cmp.setComment(ctx.getComment().get().toString());
            }
            ParseUtil.pointParentsToGivenChild(cmp, componentStack);

            final Set<String> typeParameters = typeParameterNamesInScope(ctx);
            for (final ClassOrInterfaceType implementedType : ctx.getImplementedTypes()) {
                final String resolvedType = resolveType(implementedType.asString());
                if (resolvedType != null) {
                    ParseUtil.insertCmpRef(cmp, new TypeImplementationReference(resolvedType),
                            this.componentStack);
                }
                insertTypeArgumentRefs(cmp, implementedType, typeParameters);
            }

            componentStack.push(cmp);
            visitAnnotationValues(ctx.getAnnotations(), arg);
            insertRecordComponentFields(ctx, arg);
            insertRecordAccessors(ctx, arg);
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
     * Models the accessor the compiler generates for each record component: a public method named for
     * the component, taking nothing and returning its type.
     *
     * <p>Only the implicit ones. A record may declare an accessor itself, to validate or to copy, and
     * that declaration is modelled by {@link #visit(MethodDeclaration, Object)} like any other method --
     * synthesizing a second one would give the record two members of the same name.
     */
    private void insertRecordAccessors(final RecordDeclaration ctx, final Object arg) {
        for (final Parameter recordComponent : ctx.getParameters()) {
            final String accessorName = recordComponent.getNameAsString();
            if (declaresAccessor(ctx, accessorName)) {
                continue;
            }
            final Component accessorCmp = createComponent(recordComponent, ComponentType.METHOD);
            accessorCmp.setName(accessorName);
            final String signature = accessorName + "()";
            final String returnType = recordComponent.getType().asString();
            accessorCmp.setCodeFragment(signature + " : " + returnType);
            accessorCmp.setComponentName(ParseUtil.generateComponentName(signature, componentStack));
            // An implicit accessor is as public as the record's own surface.
            accessorCmp.setAccessModifiers(Arrays.asList("public"));
            // Hashed on the accessor's own signature, so an edit elsewhere in the record does not read
            // as a change to it.
            accessorCmp.setCodeHash((signature + returnType).hashCode());
            ParseUtil.pointParentsToGivenChild(accessorCmp, componentStack);
            componentStack.push(accessorCmp);
            recordComponent.getType().accept(this, arg);
            currCyclomaticComplexity = 1;
            completeComponent();
        }
    }

    /**
     * True if the record body declares the accessor for the named component itself.
     */
    private static boolean declaresAccessor(final RecordDeclaration ctx, final String accessorName) {
        for (final Node member : ctx.getMembers()) {
            if (!(member instanceof MethodDeclaration)) {
                continue;
            }
            final MethodDeclaration method = (MethodDeclaration) member;
            if (method.getParameters().isEmpty() && method.getNameAsString().equals(accessorName)) {
                return true;
            }
        }
        return false;
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

    /**
     * An annotation type is a type. {@code @interface Retry {}} declares one, and the listener had no
     * visitor for it, so it produced no component at all -- neither the type nor its elements existed
     * in the model, and {@link ComponentType#ANNOTATION} had no producer in any language.
     */
    @Override
    public final void visit(final AnnotationDeclaration ctx, final Object arg) {
        if (!ParseUtil.componentStackContainsMethod(componentStack)) {
            final Component cmp = createComponent(ctx, ComponentType.ANNOTATION);
            cmp.setComponentName(ParseUtil.generateComponentName(ctx.getNameAsString(), componentStack));
            cmp.setName(ctx.getNameAsString());
            cmp.setImports(currentImports);
            cmp.setAccessModifiers(resolveJavaParserModifiers(ctx.getModifiers()));
            recordAnnotations(ctx.getAnnotations(), cmp);
            if (ctx.getComment().isPresent()) {
                cmp.setComment(ctx.getComment().get().toString());
            }
            ParseUtil.pointParentsToGivenChild(cmp, componentStack);
            componentStack.push(cmp);
            visitAnnotationValues(ctx.getAnnotations(), arg);
            visitTypeBody(ctx, arg);
            completeComponent();
        }
    }

    /**
     * An annotation element -- {@code int attempts() default 3;} -- is a method of the annotation type,
     * which is how the compiler models it. Its declared type is a dependency of the annotation, exactly
     * as a method's return type is.
     */
    @Override
    public final void visit(final AnnotationMemberDeclaration ctx, final Object arg) {
        if (componentStack.isEmpty()) {
            return;
        }
        final Component cmp = createComponent(ctx, ComponentType.METHOD);
        cmp.setName(ctx.getNameAsString());
        final String signature = ctx.getNameAsString() + "()";
        cmp.setCodeFragment(signature + " : " + ctx.getType().asString());
        cmp.setComponentName(ParseUtil.generateComponentName(signature, componentStack));
        cmp.setAccessModifiers(resolveJavaParserModifiers(ctx.getModifiers()));
        recordAnnotations(ctx.getAnnotations(), cmp);
        if (ctx.getComment().isPresent()) {
            cmp.setComment(ctx.getComment().get().toString());
        }
        ParseUtil.pointParentsToGivenChild(cmp, componentStack);
        componentStack.push(cmp);
        ctx.getType().accept(this, arg);
        ctx.getDefaultValue().ifPresent(defaultValue -> defaultValue.accept(this, arg));
        // An annotation element has no body, so it carries the complexity of one straight-line method.
        currCyclomaticComplexity = 1;
        completeComponent();
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
            recordAnnotations(ctx.getAnnotations(), enumCmp);
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

    /**
     * A type named by its qualified name in an expression -- {@code a.b.C.CONSTANT} -- is a
     * dependency on {@code a.b.C} exactly as {@code C.CONSTANT} with {@code a.b.C} imported is. The
     * unqualified form reaches the import map through its first identifier; the qualified form has no
     * single identifier that names the type, so it is recognised here, as a whole. Its qualifier is
     * part of that one name -- a package, or the type it is nested in -- and is not visited on its
     * own, just as the qualifier of a qualified type is not.
     */
    @Override
    public final void visit(final FieldAccessExpr ctx, final Object arg) {
        if (!componentStack.isEmpty()) {
            final String qualifiedType = typeNamedByQualifiedName(ctx);
            if (qualifiedType != null) {
                ParseUtil.insertCmpRef(componentStack.peek(), new SimpleTypeReference(qualifiedType),
                        this.componentStack);
                return;
            }
        }
        super.visit(ctx, arg);
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
        if (shallow) {
            return resolveMethodCallTypeShallow(ctx);
        }
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
                final String qualifiedType = typeNamedByQualifiedName(scope.asFieldAccessExpr());
                if (qualifiedType != null) {
                    return qualifiedType;
                }
                // A field access names a member of its scope, or a type in the package its scope
                // names -- never a top-level type of the current package, which is all the
                // current-package assumption could supply. `a.b.C.m()` with `a.b.C` outside the
                // parse path would otherwise become a call on `<current package>.C`.
                return resolveType(scope.asFieldAccessExpr().getNameAsString(), false);
            }
        }
        return resolveType(ctx.getNameAsString());
    }

    /**
     * The type a method call is attributed to, read from the receiver as written: a named variable
     * or type, or a qualified name. An unqualified call is attributed by its own name, as the
     * resolving path does when resolution fails.
     */
    private String resolveMethodCallTypeShallow(final MethodCallExpr ctx) {
        if (ctx.getScope().isPresent()) {
            final Expression scope = ctx.getScope().get();
            if (scope.isNameExpr()) {
                return resolveType(scope.asNameExpr().getNameAsString());
            }
            if (scope.isFieldAccessExpr()) {
                final String qualifiedType = typeNamedByQualifiedName(scope.asFieldAccessExpr());
                if (qualifiedType != null) {
                    return qualifiedType;
                }
                return resolveType(scope.asFieldAccessExpr().getNameAsString(), false);
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
            recordAnnotations(ctx.getAnnotations(), currMethodCmp);
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
            recordAnnotations(ctx.getAnnotations(), currMethodCmp);
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

    /**
     * Records each annotation applied to a declaration as an {@link AnnotationReference} on the
     * component that declaration became -- the same mechanism {@code extends} and {@code implements}
     * use, so that an applied annotation is a distinct kind of reference and not a separate field.
     *
     * @param annotations The declaration's applied annotations.
     * @param cmp         The component to record them on.
     */
    private void recordAnnotations(final NodeList<AnnotationExpr> annotations, final Component cmp) {
        for (final AnnotationExpr annotation : annotations) {
            ParseUtil.insertCmpRef(cmp, new AnnotationReference(resolveAnnotationName(annotation)),
                    this.componentStack);
        }
    }

    /**
     * Resolves an applied annotation to a type name the same way the listener resolves any other type
     * name: to a fully qualified name where an import or the symbol solver can supply one, and to the
     * simple name written in the source otherwise. Resolution never assumes the current package -- an
     * annotation whose type is not on the parse path is far more useful reported as {@code Service}
     * than invented as {@code <package>.Service}, which is the failure mode {@code resolveType}'s
     * {@code assumeCurrentPackage=false} path exists to avoid.
     */
    private String resolveAnnotationName(final AnnotationExpr annotation) {
        final String writtenName = annotation.getNameAsString();
        try {
            final String resolved = resolveType(writtenName, false);
            if (resolved != null && !resolved.isEmpty()) {
                return resolved;
            }
        } catch (final Exception ignored) {
            // Fall back to the name as written; an unresolvable annotation is still a real fact.
        }
        return writtenName;
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
                recordAnnotations(ctx.getAnnotations(), cmp);
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

    /**
     * A type written with a qualifier -- {@code a.b.C}, {@code Outer.Inner} -- names one type, and is
     * resolved as one name. Visiting its segments one at a time, the way an unqualified type is
     * handled, resolves the last segment as if it had been written alone: the qualifier is lost, and
     * {@code a.b.C} used from package {@code x} becomes a reference to {@code x.C}, a type that need
     * not exist -- or, where it does, the wrong one. Only the type arguments written anywhere along
     * the name are references in their own right, so only they are visited further.
     */
    @Override
    public final void visit(final ClassOrInterfaceType ctx, final Object arg) {
        if (ctx.getScope().isPresent()) {
            if (!componentStack.isEmpty()) {
                final String resolvedType = resolveType(ctx.getNameWithScope());
                if (resolvedType != null) {
                    ParseUtil.insertCmpRef(componentStack.peek(), new SimpleTypeReference(resolvedType),
                            this.componentStack);
                }
            }
            ClassOrInterfaceType segment = ctx;
            while (segment != null) {
                segment.getTypeArguments().ifPresent(typeArguments -> typeArguments.forEach(
                        typeArgument -> typeArgument.accept(this, arg)));
                segment.getAnnotations().forEach(annotation -> annotation.accept(this, arg));
                segment = segment.getScope().orElse(null);
            }
            return;
        }
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

    /**
     * Records the type arguments of a supertype as dependencies of their own.
     *
     * <p>A supertype reference names one type, so {@code implements Repository<Order>} says
     * {@code Repository} and stops. But the class does depend on {@code Order}: it is written in
     * the source, it is part of what the class is, and it was recorded nowhere -- the supertype
     * reference had swallowed it into a name, and the visitors that pick type names out of a class
     * body deliberately leave the declaration's own supertypes alone. Every other type position in
     * this listener records its arguments; heritage clauses were the one that did not, and
     * TypeScript and C# already record them, so Java was the odd language out.
     *
     * <p>Recorded as a plain dependency and not with the flavour of the clause it was written in:
     * a class implementing {@code Repository<Order>} does not implement {@code Order}.
     */
    private void insertTypeArgumentRefs(final Component cmp, final ClassOrInterfaceType supertype,
                                        final Set<String> typeParameters) {
        if (supertype.getTypeArguments().isEmpty()) {
            return;
        }
        for (final Type argument : supertype.getTypeArguments().get()) {
            insertTypeArgumentRef(cmp, argument, typeParameters);
        }
    }

    private void insertTypeArgumentRef(final Component cmp, final Type argument,
                                       final Set<String> typeParameters) {
        if (argument instanceof WildcardType) {
            // `?` names no type. Its bound does -- `? extends Bar` depends on Bar.
            final WildcardType wildcard = (WildcardType) argument;
            wildcard.getExtendedType().ifPresent(bound -> insertTypeArgumentRef(cmp, bound, typeParameters));
            wildcard.getSuperType().ifPresent(bound -> insertTypeArgumentRef(cmp, bound, typeParameters));
            return;
        }
        if (argument instanceof ArrayType) {
            insertTypeArgumentRef(cmp, ((ArrayType) argument).getComponentType(), typeParameters);
            return;
        }
        if (!(argument instanceof ClassOrInterfaceType)) {
            return;
        }
        final ClassOrInterfaceType classType = (ClassOrInterfaceType) argument;
        // A type variable is not a type. `class Box<T> implements Holder<T>` would otherwise
        // resolve `T` through the current-package fallback and invent a component `<package>.T`
        // that does not exist -- the failure mode that unresolved names are supposed to avoid by
        // being omitted rather than guessed.
        if (!typeParameters.contains(classType.getNameAsString())) {
            final String resolvedType = resolveType(classType.asString());
            if (resolvedType != null) {
                ParseUtil.insertCmpRef(cmp, new SimpleTypeReference(resolvedType), this.componentStack);
            }
        }
        insertTypeArgumentRefs(cmp, classType, typeParameters);
    }

    /**
     * The type variables a node may legally mention: its own, and those of every type that
     * encloses it.
     */
    private static Set<String> typeParameterNamesInScope(final Node node) {
        final Set<String> names = new HashSet<>();
        Node current = node;
        while (current != null) {
            if (current instanceof NodeWithTypeParameters) {
                for (final TypeParameter typeParameter : ((NodeWithTypeParameters<?>) current).getTypeParameters()) {
                    names.add(typeParameter.getNameAsString());
                }
            }
            current = current.getParentNode().orElse(null);
        }
        return names;
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
    private String resolveType(final String writtenType, final boolean assumeCurrentPackage) {
        // Resolution is by name, and every lookup below -- the import map, the default classes, the
        // type solver, the on-demand imports -- is keyed on the name of a type. A written type
        // expression is not that: `DTO<HttpRequest>` is absent from an import map that holds `DTO`,
        // and so is `Bar[]` from one that holds `Bar`. Every lookup then misses and the fallback
        // invents a current-package name out of the expression -- `com.DTO<HttpRequest>` for a
        // `DTO` that lives in another package entirely. So the type arguments have to come off
        // before the lookups, not after: erasing only the recorded name would keep the invented
        // package and merely make the invention look plausible.
        final String type = TypeNames.erasure(writtenType);
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
        if (resolvedType.isEmpty() && type.indexOf('.') > 0) {
            return resolveQualifiedName(type, assumeCurrentPackage);
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

    /**
     * Resolves a qualified type name that no lookup recognised as a whole -- a type outside the parse
     * path, or one nested in a type named by its simple name.
     *
     * <p>The qualifier says where the type lives, so the current-package assumption, which exists for
     * unqualified names, can never apply to the name as a whole: {@code a.b.C} written in package
     * {@code x} is never {@code x.C}, nor {@code x.a.b.C}. What the qualifier can be is a type, in
     * which case the name is a type nested in it and is resolved relative to wherever that type
     * resolves -- {@code Outer.Inner} with {@code Outer} imported from {@code p} is
     * {@code p.Outer.Inner}. Otherwise the qualifier is a package and the name is already fully
     * qualified, and is kept exactly as written, which is what an import of it would have recorded.
     */
    private String resolveQualifiedName(final String type, final boolean assumeCurrentPackage) {
        final int lastDot = type.lastIndexOf('.');
        final String enclosingType = resolveType(type.substring(0, lastDot), assumeCurrentPackage);
        if (enclosingType != null) {
            return enclosingType + type.substring(lastDot);
        }
        if (!assumeCurrentPackage) {
            return null;
        }
        final String writtenName = extractClassName(type);
        if (writtenName.isEmpty()) {
            return null;
        }
        return writtenName;
    }

    /**
     * The type a qualified name in expression position refers to, when it refers to one --
     * {@code a.b.C} in {@code a.b.C.CONSTANT} or {@code a.b.C.create()}.
     *
     * <p>In an expression a dotted name is ambiguous in a way it is not in a type position: it may be
     * a package-qualified type, or a chain of field accesses on a variable. Two things must hold for
     * it to be read as a type. The type solver must find a type of exactly that name, so that nothing
     * outside what it can see is guessed at; and the first segment must not name a variable, because
     * a variable in scope takes precedence over a package of the same name, so {@code a.b.C} where
     * {@code a} is a local is a field access however the packages are laid out.
     */
    private String typeNamedByQualifiedName(final FieldAccessExpr access) {
        if (!Character.isUpperCase(access.getNameAsString().codePointAt(0))) {
            return null;
        }
        final String dottedName = dottedName(access);
        if (dottedName == null) {
            return null;
        }
        final SymbolReference<ResolvedReferenceTypeDeclaration> symbol = typeSolver.tryToSolveType(dottedName);
        if (!symbol.isSolved() || namesAValue(firstSegment(access))) {
            return null;
        }
        final String resolvedType = extractClassName(symbol.getCorrespondingDeclaration().getQualifiedName());
        if (resolvedType.isEmpty()) {
            return null;
        }
        return resolvedType;
    }

    /**
     * The name an expression spells when it is nothing but identifiers joined by dots, or null.
     */
    private static String dottedName(final Expression expression) {
        if (expression.isNameExpr()) {
            return expression.asNameExpr().getNameAsString();
        }
        if (expression.isFieldAccessExpr()) {
            final String scope = dottedName(expression.asFieldAccessExpr().getScope());
            if (scope != null) {
                return scope + "." + expression.asFieldAccessExpr().getNameAsString();
            }
        }
        return null;
    }

    private static NameExpr firstSegment(final FieldAccessExpr access) {
        Expression current = access;
        while (current.isFieldAccessExpr()) {
            current = current.asFieldAccessExpr().getScope();
        }
        return current.asNameExpr();
    }

    private static boolean namesAValue(final NameExpr name) {
        try {
            name.resolve();
            return true;
        } catch (final Exception notAValue) {
            return false;
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
