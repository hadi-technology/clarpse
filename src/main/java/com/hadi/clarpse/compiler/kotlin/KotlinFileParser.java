package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinFileModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinImport;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinMemberModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinParameterModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinSuperType;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinTypeAlias;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinTypeModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.MemberKind;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.TypeKind;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.TypeUsage;
import com.hadi.clarpse.compiler.kotlin.KotlinSyntaxTree.Token;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the declarations of one Kotlin file out of its syntax tree: the package, the imports, the
 * types with their members, the top-level functions and properties, and every type name each
 * declaration writes, in a type position or in an expression. Nothing is resolved here.
 */
final class KotlinFileParser {

    private static final String BYTE_ORDER_MARK = "﻿";
    private static final String IDENTIFIER = "IDENTIFIER";
    private static final String MODIFIER_LIST = "MODIFIER_LIST";
    private static final String TYPE_REFERENCE = "TYPE_REFERENCE";
    private static final String USER_TYPE = "USER_TYPE";
    private static final String REFERENCE_EXPRESSION = "REFERENCE_EXPRESSION";
    private static final String CALL_EXPRESSION = "CALL_EXPRESSION";
    private static final String VALUE_PARAMETER_LIST = "VALUE_PARAMETER_LIST";
    private static final String CLASS = "CLASS";
    private static final String OBJECT_DECLARATION = "OBJECT_DECLARATION";
    private static final String FUN = "FUN";
    private static final String PROPERTY = "PROPERTY";

    /** Nodes whose name is the selector of a qualified expression rather than a name in scope. */
    private static final Set<String> QUALIFIED_EXPRESSIONS = Set.of("DOT_QUALIFIED_EXPRESSION",
            "SAFE_ACCESS_EXPRESSION");

    /**
     * Nodes a simple name can stand in as a value on its own: an argument, a statement, an
     * initializer, a returned or compared value, a branch. There a name that resolves to a type can
     * only be an object or a companion object.
     */
    private static final Set<String> VALUE_POSITIONS = Set.of("VALUE_ARGUMENT", "BLOCK", PROPERTY, FUN,
            "RETURN", "WHEN_CONDITION_EXPRESSION", "BINARY_EXPRESSION", "PARENTHESIZED", "THEN", "ELSE");

    /** Declarations nested in a body that are modelled, or skipped, on their own. */
    private static final Set<String> NESTED_DECLARATIONS = Set.of(CLASS, OBJECT_DECLARATION, FUN);

    /** Nodes that each add a path through a function. */
    private static final Set<String> BRANCHES = Set.of("IF", "FOR", "WHILE", "DO_WHILE", "CATCH");

    private KotlinFileParser() {
    }

    /**
     * Parses one file, bodies included.
     *
     * @param file  The file.
     * @param index Its position, for deterministic ordering.
     * @return Its model, or the failure that took its place.
     */
    static KotlinModel.ParseOutcome parseFile(final ProjectFile file, final int index) {
        return parseFile(file, index, true);
    }

    /**
     * Parses one file.
     *
     * @param file       The file.
     * @param index      Its position, for deterministic ordering.
     * @param withBodies Whether function bodies and initializers are read; without them a model
     *                   carries every declaration but only the names its signatures write.
     * @return Its model, or the failure that took its place.
     */
    static KotlinModel.ParseOutcome parseFile(final ProjectFile file, final int index, final boolean withBodies) {
        try {
            return new KotlinModel.ParseOutcome(index, parse(file, withBodies), null);
        } catch (final RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            return new KotlinModel.ParseOutcome(index, null,
                    new CompileFailure(file, String.valueOf(e.getMessage()), FailureCode.PARSE_FAILED));
        } catch (final StackOverflowError e) {
            // An expression nested deeper than the parser's recursion can follow fails its file only.
            return new KotlinModel.ParseOutcome(index, null,
                    new CompileFailure(file, "Kotlin source nested too deeply to parse.", FailureCode.PARSE_FAILED));
        }
    }

    private static KotlinFileModel parse(final ProjectFile file, final boolean withBodies) {
        String source = file.content();
        if (source == null) {
            source = "";
        } else if (source.startsWith(BYTE_ORDER_MARK)) {
            // Offsets are taken on the text without the mark, which the lexer does not skip.
            source = source.substring(BYTE_ORDER_MARK.length());
        }
        final KotlinSyntaxTree tree = KotlinSyntaxTree.parse(source, withBodies);
        final KotlinFileModel model = new KotlinFileModel(file, CompilerSupport.moduleNameForFile(file.path()));
        new KotlinFileParser.Reader(tree).readFile(model);
        return model;
    }

    /** Reads one tree. */
    private static final class Reader {

        private final KotlinSyntaxTree tree;

        Reader(final KotlinSyntaxTree tree) {
            this.tree = tree;
        }

        void readFile(final KotlinFileModel model) {
            for (final KotlinSyntaxNode node : tree.root().children) {
                switch (node.type) {
                    case "FILE_ANNOTATION_LIST":
                        readFileAnnotations(node, model);
                        break;
                    case "PACKAGE_DIRECTIVE":
                        model.packageName = qualifiedNameIn(node);
                        break;
                    case "IMPORT_LIST":
                        for (final KotlinSyntaxNode directive : node.children("IMPORT_DIRECTIVE")) {
                            final KotlinImport kotlinImport = readImport(directive);
                            if (kotlinImport != null) {
                                model.imports.add(kotlinImport);
                            }
                        }
                        break;
                    case CLASS:
                    case OBJECT_DECLARATION:
                        model.types.add(readType(node));
                        break;
                    case FUN:
                        model.topLevelMembers.add(readFunction(node));
                        break;
                    case PROPERTY:
                        model.topLevelMembers.add(readProperty(node));
                        break;
                    case "TYPEALIAS":
                        readTypeAlias(node, model);
                        break;
                    default:
                        break;
                }
            }
        }

        private void readFileAnnotations(final KotlinSyntaxNode list, final KotlinFileModel model) {
            for (final KotlinSyntaxNode entry : list.descendants()) {
                if (!entry.is("ANNOTATION_ENTRY")) {
                    continue;
                }
                final String name = annotationName(entry);
                if ("JvmName".equals(name) || "kotlin.jvm.JvmName".equals(name)) {
                    final String literal = firstStringLiteral(entry);
                    if (literal != null && !literal.isBlank()) {
                        model.jvmName = literal.trim();
                    }
                } else if ("JvmMultifileClass".equals(name) || "kotlin.jvm.JvmMultifileClass".equals(name)) {
                    model.multifileClass = true;
                }
            }
        }

        private String firstStringLiteral(final KotlinSyntaxNode node) {
            for (final KotlinSyntaxNode descendant : node.descendants()) {
                if (descendant.is("STRING_TEMPLATE")) {
                    final StringBuilder text = new StringBuilder();
                    for (final KotlinSyntaxNode part : descendant.children) {
                        if (!part.is("LITERAL_STRING_TEMPLATE_ENTRY")) {
                            return null;
                        }
                        text.append(tree.text(part));
                    }
                    return text.toString();
                }
            }
            return null;
        }

        private KotlinImport readImport(final KotlinSyntaxNode directive) {
            String target = null;
            String alias = null;
            boolean star = false;
            for (final KotlinSyntaxNode child : directive.children) {
                if (child.is(REFERENCE_EXPRESSION) || child.is("DOT_QUALIFIED_EXPRESSION")) {
                    target = chainText(child);
                } else if (child.is("IMPORT_ALIAS")) {
                    alias = identifier(child);
                }
            }
            for (final Token token : tree.directTokens(directive)) {
                if ("MUL".equals(token.type())) {
                    star = true;
                }
            }
            if (target == null || target.isEmpty()) {
                return null;
            }
            return new KotlinImport(target, alias, star);
        }

        private void readTypeAlias(final KotlinSyntaxNode node, final KotlinFileModel model) {
            final String name = identifier(node);
            final KotlinSyntaxNode typeReference = node.child(TYPE_REFERENCE);
            if (name == null || typeReference == null) {
                return;
            }
            final List<String> target = new ArrayList<>();
            for (final TypeUsage usage : typeUsagesIn(typeReference, null)) {
                target.add(usage.name());
            }
            model.typeAliases.add(new KotlinTypeAlias(name, target));
        }

        private KotlinTypeModel readType(final KotlinSyntaxNode node) {
            final KotlinTypeModel type = new KotlinTypeModel();
            final List<String> modifiers = modifierKeywords(node);
            type.annotations.addAll(annotationNames(node));
            final List<Token> tokens = tree.directTokens(node);
            boolean isInterface = false;
            for (final Token token : tokens) {
                if ("interface".equals(token.type())) {
                    isInterface = true;
                }
            }
            if (node.is(OBJECT_DECLARATION)) {
                type.kind = TypeKind.OBJECT;
            } else if (isInterface) {
                type.kind = TypeKind.INTERFACE;
            } else if (modifiers.contains("enum")) {
                type.kind = TypeKind.ENUM;
            } else if (modifiers.contains("annotation")) {
                type.kind = TypeKind.ANNOTATION;
            } else {
                type.kind = TypeKind.CLASS;
            }
            type.companion = modifiers.contains("companion");
            type.name = identifier(node);
            if (type.name == null && type.companion) {
                type.name = "Companion";
            }
            if (type.name == null) {
                type.name = "";
            }
            type.modifiers.addAll(supportedModifiers(modifiers));
            type.comment = tree.leadingComment(node.startOffset);
            type.startOffset = node.startOffset;
            type.implementationHash = implementationHash(tree.text(node));
            type.codeFragment = typeHeader(node);
            readTypeParameters(node, type.typeParameters, type.ownUsages);
            final KotlinSyntaxNode primary = node.child("PRIMARY_CONSTRUCTOR");
            if (primary != null) {
                type.members.add(readPrimaryConstructor(primary, type));
            }
            final KotlinSyntaxNode superTypes = node.child("SUPER_TYPE_LIST");
            if (superTypes != null) {
                readSuperTypes(superTypes, type);
            }
            final KotlinSyntaxNode body = node.child("CLASS_BODY");
            if (body != null) {
                readBody(body, type);
            }
            return type;
        }

        private void readTypeParameters(final KotlinSyntaxNode declaration, final Set<String> names,
                                        final List<TypeUsage> usages) {
            final KotlinSyntaxNode list = declaration.child("TYPE_PARAMETER_LIST");
            if (list != null) {
                for (final KotlinSyntaxNode parameter : list.children("TYPE_PARAMETER")) {
                    final String name = identifier(parameter);
                    if (name != null) {
                        names.add(name);
                    }
                }
                usages.addAll(typeUsagesIn(list, null));
            }
            final KotlinSyntaxNode constraints = declaration.child("TYPE_CONSTRAINT_LIST");
            if (constraints != null) {
                usages.addAll(typeUsagesIn(constraints, null));
            }
        }

        private void readSuperTypes(final KotlinSyntaxNode list, final KotlinTypeModel type) {
            for (final KotlinSyntaxNode entry : list.children) {
                final boolean constructorCall = entry.is("SUPER_TYPE_CALL_ENTRY");
                if (!constructorCall && !entry.is("SUPER_TYPE_ENTRY") && !entry.is("DELEGATED_SUPER_TYPE_ENTRY")) {
                    continue;
                }
                KotlinSyntaxNode typeReference = entry.child(TYPE_REFERENCE);
                final KotlinSyntaxNode callee = entry.child("CONSTRUCTOR_CALLEE");
                if (typeReference == null && callee != null) {
                    typeReference = callee.child(TYPE_REFERENCE);
                }
                final KotlinSyntaxNode userType = outermostUserType(typeReference);
                if (userType == null) {
                    continue;
                }
                final String name = userTypeName(userType);
                if (name.isEmpty()) {
                    continue;
                }
                final List<String> typeArguments = new ArrayList<>();
                final KotlinSyntaxNode arguments = userType.child("TYPE_ARGUMENT_LIST");
                if (arguments != null) {
                    for (final TypeUsage usage : typeUsagesIn(arguments, null)) {
                        typeArguments.add(usage.name());
                    }
                }
                type.superTypes.add(new KotlinSuperType(name, typeArguments, constructorCall));
                for (final KotlinSyntaxNode child : entry.children) {
                    if (!child.is(TYPE_REFERENCE) && !child.is("CONSTRUCTOR_CALLEE")) {
                        type.ownUsages.addAll(typeUsagesIn(child, null));
                    }
                }
            }
        }

        private void readBody(final KotlinSyntaxNode body, final KotlinTypeModel type) {
            for (final KotlinSyntaxNode child : body.children) {
                switch (child.type) {
                    case PROPERTY:
                        type.members.add(readProperty(child));
                        break;
                    case FUN:
                        type.members.add(readFunction(child));
                        break;
                    case "SECONDARY_CONSTRUCTOR":
                        type.members.add(readSecondaryConstructor(child, type));
                        break;
                    case CLASS:
                    case OBJECT_DECLARATION:
                        type.nestedTypes.add(readType(child));
                        break;
                    case "ENUM_ENTRY":
                        type.members.add(readEnumEntry(child));
                        break;
                    case "CLASS_INITIALIZER":
                        type.ownUsages.addAll(typeUsagesIn(child, null));
                        break;
                    default:
                        break;
                }
            }
        }

        private KotlinMemberModel readEnumEntry(final KotlinSyntaxNode node) {
            final KotlinMemberModel member = newMember(node, MemberKind.ENUM_ENTRY);
            member.name = identifier(node);
            member.codeFragment = member.name;
            member.typeUsages.addAll(typeUsagesIn(node, member));
            return member;
        }

        private KotlinMemberModel readPrimaryConstructor(final KotlinSyntaxNode node, final KotlinTypeModel type) {
            final KotlinMemberModel member = newMember(node, MemberKind.CONSTRUCTOR);
            member.name = type.name;
            readParameters(node.child(VALUE_PARAMETER_LIST), member);
            member.typeUsages.addAll(typeUsagesIn(node, member));
            member.codeFragment = member.name + "(" + parameterTypes(member) + ")";
            return member;
        }

        private KotlinMemberModel readSecondaryConstructor(final KotlinSyntaxNode node, final KotlinTypeModel type) {
            final KotlinMemberModel member = newMember(node, MemberKind.CONSTRUCTOR);
            member.name = type.name;
            readParameters(node.child(VALUE_PARAMETER_LIST), member);
            member.typeUsages.addAll(typeUsagesIn(node, member));
            member.codeFragment = member.name + "(" + parameterTypes(member) + ")";
            readBodyDetails(node, member);
            return member;
        }

        private KotlinMemberModel readFunction(final KotlinSyntaxNode node) {
            final KotlinMemberModel member = newMember(node, MemberKind.FUNCTION);
            final Token name = nameToken(node, "fun");
            if (name != null) {
                member.name = stripBackticks(name.text());
            }
            readTypeParameters(node, member.typeParameters, member.typeUsages);
            final KotlinSyntaxNode parameters = node.child(VALUE_PARAMETER_LIST);
            for (final KotlinSyntaxNode child : node.children) {
                if (!child.is(TYPE_REFERENCE)) {
                    continue;
                }
                if (name != null && child.endOffset <= name.start()) {
                    member.receiverType = typeText(child);
                } else if (parameters != null && child.startOffset >= parameters.endOffset
                        && member.declaredType == null) {
                    member.declaredType = typeText(child);
                }
            }
            readParameters(parameters, member);
            member.typeUsages.addAll(typeUsagesIn(node, member));
            String fragment = member.name + "(" + parameterTypes(member) + ")";
            if (member.receiverType != null) {
                fragment = member.receiverType + "." + fragment;
            }
            if (member.declaredType != null) {
                fragment += " : " + member.declaredType;
            }
            member.codeFragment = fragment;
            readBodyDetails(node, member);
            return member;
        }

        private KotlinMemberModel readProperty(final KotlinSyntaxNode node) {
            final KotlinMemberModel member = newMember(node, MemberKind.PROPERTY);
            final Token name = nameToken(node, "val", "var");
            if (name != null) {
                member.name = stripBackticks(name.text());
            }
            for (final Token token : tree.directTokens(node)) {
                if ("val".equals(token.type())) {
                    member.modifiers.add("final");
                    break;
                }
            }
            readTypeParameters(node, member.typeParameters, member.typeUsages);
            for (final KotlinSyntaxNode child : node.children) {
                if (!child.is(TYPE_REFERENCE)) {
                    continue;
                }
                if (name != null && child.endOffset <= name.start()) {
                    member.receiverType = typeText(child);
                } else if (member.declaredType == null) {
                    member.declaredType = typeText(child);
                }
            }
            member.typeUsages.addAll(typeUsagesIn(node, member));
            if (member.declaredType == null) {
                member.codeFragment = member.name;
            } else {
                member.codeFragment = member.name + " : " + member.declaredType;
            }
            return member;
        }

        private KotlinMemberModel newMember(final KotlinSyntaxNode node, final MemberKind kind) {
            final KotlinMemberModel member = new KotlinMemberModel();
            member.kind = kind;
            member.name = "";
            member.startOffset = node.startOffset;
            member.comment = tree.leadingComment(node.startOffset);
            member.implementationHash = implementationHash(tree.text(node));
            member.modifiers.addAll(supportedModifiers(modifierKeywords(node)));
            member.annotations.addAll(annotationNames(node));
            return member;
        }

        private void readParameters(final KotlinSyntaxNode list, final KotlinMemberModel member) {
            if (list == null) {
                return;
            }
            for (final KotlinSyntaxNode node : list.children("VALUE_PARAMETER")) {
                final KotlinParameterModel parameter = new KotlinParameterModel();
                parameter.name = identifier(node);
                if (parameter.name == null) {
                    continue;
                }
                for (final Token token : tree.directTokens(node)) {
                    if ("val".equals(token.type())) {
                        parameter.property = true;
                    } else if ("var".equals(token.type())) {
                        parameter.property = true;
                        parameter.mutable = true;
                    }
                }
                final KotlinSyntaxNode typeReference = node.child(TYPE_REFERENCE);
                if (typeReference != null) {
                    parameter.declaredType = typeText(typeReference);
                    parameter.typeUsages.addAll(typeUsagesIn(typeReference, null));
                }
                parameter.modifiers.addAll(supportedModifiers(modifierKeywords(node)));
                parameter.implementationHash = implementationHash(tree.text(node));
                member.parameters.add(parameter);
            }
        }

        /** Reads a function or constructor body's locals and complexity. */
        private void readBodyDetails(final KotlinSyntaxNode declaration, final KotlinMemberModel member) {
            int cyclo = 1;
            for (final KotlinSyntaxNode node : descendantsOutsideNestedDeclarations(declaration)) {
                if (BRANCHES.contains(node.type)) {
                    cyclo += 1;
                } else if (node.is("WHEN_ENTRY") && !isElseEntry(node)) {
                    cyclo += 1;
                } else if (node.is("OPERATION_REFERENCE")) {
                    final String operator = tree.text(node);
                    if ("&&".equals(operator) || "||".equals(operator) || "?:".equals(operator)) {
                        cyclo += 1;
                    }
                } else if (node.is(PROPERTY) && node != declaration) {
                    member.locals.add(readLocal(node));
                }
            }
            member.cyclo = cyclo;
        }

        private KotlinMemberModel readLocal(final KotlinSyntaxNode node) {
            final KotlinMemberModel local = newMember(node, MemberKind.PROPERTY);
            local.comment = "";
            local.modifiers.clear();
            final Token name = nameToken(node, "val", "var");
            if (name != null) {
                local.name = stripBackticks(name.text());
            }
            final KotlinSyntaxNode typeReference = node.child(TYPE_REFERENCE);
            if (typeReference != null) {
                local.declaredType = typeText(typeReference);
            }
            local.typeUsages.addAll(typeUsagesIn(node, null));
            if (local.declaredType == null) {
                local.codeFragment = local.name;
            } else {
                local.codeFragment = local.name + " : " + local.declaredType;
            }
            return local;
        }

        private boolean isElseEntry(final KotlinSyntaxNode entry) {
            for (final Token token : tree.directTokens(entry)) {
                if ("else".equals(token.type())) {
                    return true;
                }
            }
            return false;
        }

        /** The nodes under a declaration, leaving out the declarations nested in its body. */
        private List<KotlinSyntaxNode> descendantsOutsideNestedDeclarations(final KotlinSyntaxNode declaration) {
            final List<KotlinSyntaxNode> result = new ArrayList<>();
            collectOutsideNested(declaration, result);
            return result;
        }

        private void collectOutsideNested(final KotlinSyntaxNode node, final List<KotlinSyntaxNode> into) {
            final Deque<KotlinSyntaxNode> pending = new ArrayDeque<>();
            for (int i = node.children.size() - 1; i >= 0; i -= 1) {
                pending.push(node.children.get(i));
            }
            while (!pending.isEmpty()) {
                final KotlinSyntaxNode child = pending.pop();
                into.add(child);
                if (!NESTED_DECLARATIONS.contains(child.type) && !child.is("OBJECT_LITERAL")) {
                    for (int i = child.children.size() - 1; i >= 0; i -= 1) {
                        pending.push(child.children.get(i));
                    }
                }
            }
        }

        /**
         * Every type name written under {@code node}: the types in type positions, and the names an
         * expression uses as a type -- the callee of a call, the receiver of a qualified expression,
         * the subject of a class literal or callable reference, and a name used as a value on its
         * own. Expression names are candidates; the assembler keeps only those that resolve to a type,
         * and of the names used as values only those that resolve to a repository type.
         */
        private List<TypeUsage> typeUsagesIn(final KotlinSyntaxNode node, final KotlinMemberModel member) {
            final List<TypeUsage> usages = new ArrayList<>();
            final List<KotlinSyntaxNode> nodes = new ArrayList<>();
            nodes.add(node);
            nodes.addAll(node.descendants());
            for (final KotlinSyntaxNode current : nodes) {
                if (current.is(USER_TYPE)) {
                    if (current.parent == null || !current.parent.is(USER_TYPE)) {
                        final String name = userTypeName(current);
                        if (!name.isEmpty()) {
                            usages.add(new TypeUsage(name, false));
                        }
                    }
                } else if (QUALIFIED_EXPRESSIONS.contains(current.type)) {
                    addQualifiedExpressionUsages(current, usages);
                } else if (current.is(CALL_EXPRESSION)) {
                    addCallUsage(current, usages, member);
                } else if (current.is("CLASS_LITERAL_EXPRESSION") || current.is("CALLABLE_REFERENCE_EXPRESSION")) {
                    addReferenceUsage(current, usages, member);
                } else if (current.is(REFERENCE_EXPRESSION) && current.parent != null
                        && VALUE_POSITIONS.contains(current.parent.type)) {
                    final String name = referenceName(current);
                    if (!name.isEmpty()) {
                        usages.add(new TypeUsage(name, true, true));
                    }
                }
            }
            return withoutTypeParameters(usages, nodes);
        }

        /**
         * The usages that do not name a type parameter declared under the node, such as one of a
         * function of an object expression, which is in scope nowhere else.
         */
        private List<TypeUsage> withoutTypeParameters(final List<TypeUsage> usages,
                                                      final List<KotlinSyntaxNode> nodes) {
            final Set<String> typeParameters = new HashSet<>();
            for (final KotlinSyntaxNode node : nodes) {
                if (node.is("TYPE_PARAMETER")) {
                    final String name = identifier(node);
                    if (name != null) {
                        typeParameters.add(name);
                    }
                }
            }
            if (typeParameters.isEmpty()) {
                return usages;
            }
            final List<TypeUsage> kept = new ArrayList<>();
            for (final TypeUsage usage : usages) {
                final int dot = usage.name().indexOf('.');
                final String head;
                if (dot < 0) {
                    head = usage.name();
                } else {
                    head = usage.name().substring(0, dot);
                }
                if (!typeParameters.contains(head)) {
                    kept.add(usage);
                }
            }
            return kept;
        }

        private void addQualifiedExpressionUsages(final KotlinSyntaxNode expression, final List<TypeUsage> usages) {
            if (expression.children.isEmpty()) {
                return;
            }
            final String receiver = chainText(expression.children.get(0));
            if (receiver == null) {
                return;
            }
            usages.add(new TypeUsage(receiver, true));
            if (expression.children.size() > 1) {
                final KotlinSyntaxNode selector = expression.children.get(1);
                if (selector.is(CALL_EXPRESSION) && !selector.children.isEmpty()
                        && selector.children.get(0).is(REFERENCE_EXPRESSION)) {
                    usages.add(new TypeUsage(receiver + "." + referenceName(selector.children.get(0)), true));
                }
            }
        }

        private void addCallUsage(final KotlinSyntaxNode call, final List<TypeUsage> usages,
                                  final KotlinMemberModel member) {
            if (call.children.isEmpty() || !call.children.get(0).is(REFERENCE_EXPRESSION)) {
                return;
            }
            final KotlinSyntaxNode parent = call.parent;
            if (parent != null && QUALIFIED_EXPRESSIONS.contains(parent.type)
                    && parent.children.indexOf(call) > 0) {
                return;
            }
            final String name = referenceName(call.children.get(0));
            if (name.isEmpty()) {
                return;
            }
            usages.add(new TypeUsage(name, true));
            recordCall(call, name, member);
        }

        private void addReferenceUsage(final KotlinSyntaxNode expression, final List<TypeUsage> usages,
                                       final KotlinMemberModel member) {
            final List<KotlinSyntaxNode> parts = new ArrayList<>();
            for (final KotlinSyntaxNode child : expression.children) {
                if (child.is(REFERENCE_EXPRESSION) || QUALIFIED_EXPRESSIONS.contains(child.type)) {
                    parts.add(child);
                }
            }
            if (parts.isEmpty()) {
                return;
            }
            final boolean unboundCallable = expression.is("CALLABLE_REFERENCE_EXPRESSION") && parts.size() == 1
                    && expression.children.size() == 1;
            final String name = chainText(parts.get(0));
            if (name == null) {
                return;
            }
            usages.add(new TypeUsage(name, true));
            if (unboundCallable) {
                recordCall(expression, name, member);
            }
        }

        /** Records a called name, noting whether the call is written inside a lambda. */
        private void recordCall(final KotlinSyntaxNode call, final String name, final KotlinMemberModel member) {
            if (member == null) {
                return;
            }
            final boolean firstCall = member.calledNames.add(name);
            final boolean inLambda = insideLambda(call);
            if (inLambda && firstCall) {
                member.calledInLambdas.add(name);
            } else if (!inLambda) {
                member.calledInLambdas.remove(name);
            }
        }

        private boolean insideLambda(final KotlinSyntaxNode node) {
            for (KotlinSyntaxNode parent = node.parent; parent != null; parent = parent.parent) {
                if (parent.is("LAMBDA_EXPRESSION")) {
                    return true;
                }
            }
            return false;
        }

        /**
         * The dotted name a chain of simple references spells, such as {@code a.b.C}, or null when
         * any part of it is not a simple reference.
         */
        private String chainText(final KotlinSyntaxNode node) {
            if (node.is(REFERENCE_EXPRESSION)) {
                final String name = referenceName(node);
                if (name.isEmpty()) {
                    return null;
                }
                return name;
            }
            if (node.is("DOT_QUALIFIED_EXPRESSION") && node.children.size() == 2) {
                final String left = chainText(node.children.get(0));
                final String right = chainText(node.children.get(1));
                if (left == null || right == null) {
                    return null;
                }
                return left + "." + right;
            }
            return null;
        }

        private String referenceName(final KotlinSyntaxNode reference) {
            return stripBackticks(tree.text(reference).trim());
        }

        /** The name a user type writes, its qualifier included and its type arguments left out. */
        private String userTypeName(final KotlinSyntaxNode userType) {
            final StringBuilder name = new StringBuilder();
            for (final KotlinSyntaxNode child : userType.children) {
                if (child.is(USER_TYPE)) {
                    name.append(userTypeName(child));
                } else if (child.is(REFERENCE_EXPRESSION)) {
                    final String part = referenceName(child);
                    if (part.isEmpty()) {
                        return "";
                    }
                    if (name.length() > 0) {
                        name.append('.');
                    }
                    name.append(part);
                }
            }
            return name.toString();
        }

        private KotlinSyntaxNode outermostUserType(final KotlinSyntaxNode typeReference) {
            if (typeReference == null) {
                return null;
            }
            for (final KotlinSyntaxNode node : typeReference.descendants()) {
                if (node.is(USER_TYPE)) {
                    return node;
                }
            }
            return null;
        }

        private String typeText(final KotlinSyntaxNode typeReference) {
            return normalizeTypeText(tree.text(typeReference));
        }

        private String qualifiedNameIn(final KotlinSyntaxNode node) {
            for (final KotlinSyntaxNode child : node.children) {
                final String name = chainText(child);
                if (name != null) {
                    return name;
                }
            }
            return "";
        }

        private String identifier(final KotlinSyntaxNode node) {
            for (final Token token : tree.directTokens(node)) {
                if (IDENTIFIER.equals(token.type())) {
                    return stripBackticks(token.text());
                }
            }
            return null;
        }

        /** The identifier a declaration names itself by: the first one after its keyword. */
        private Token nameToken(final KotlinSyntaxNode node, final String... keywords) {
            boolean afterKeyword = false;
            for (final Token token : tree.directTokens(node)) {
                for (final String keyword : keywords) {
                    if (keyword.equals(token.type())) {
                        afterKeyword = true;
                    }
                }
                if (afterKeyword && IDENTIFIER.equals(token.type())) {
                    return token;
                }
            }
            return null;
        }

        private List<String> modifierKeywords(final KotlinSyntaxNode declaration) {
            final List<String> keywords = new ArrayList<>();
            final KotlinSyntaxNode list = declaration.child(MODIFIER_LIST);
            if (list == null) {
                return keywords;
            }
            for (final KotlinSyntaxNode child : list.children) {
                if (child.type.chars().allMatch(Character::isLowerCase)) {
                    keywords.add(child.type);
                }
            }
            return keywords;
        }

        private List<String> annotationNames(final KotlinSyntaxNode declaration) {
            final List<String> names = new ArrayList<>();
            final KotlinSyntaxNode list = declaration.child(MODIFIER_LIST);
            if (list == null) {
                return names;
            }
            for (final KotlinSyntaxNode node : list.descendants()) {
                if (node.is("ANNOTATION_ENTRY")) {
                    final String name = annotationName(node);
                    if (name != null && !name.isEmpty()) {
                        names.add(name);
                    }
                }
            }
            return names;
        }

        private String annotationName(final KotlinSyntaxNode entry) {
            final KotlinSyntaxNode callee = entry.child("CONSTRUCTOR_CALLEE");
            if (callee == null) {
                return null;
            }
            final KotlinSyntaxNode userType = outermostUserType(callee.child(TYPE_REFERENCE));
            if (userType == null) {
                return null;
            }
            return userTypeName(userType);
        }

        /** The declaration up to its body, as written, on one line. */
        private String typeHeader(final KotlinSyntaxNode node) {
            final KotlinSyntaxNode body = node.child("CLASS_BODY");
            int end = node.endOffset;
            if (body != null) {
                end = body.startOffset;
            }
            final int start = headerStart(node);
            final String text = tree.source().substring(Math.min(start, end), Math.min(end, tree.source().length()));
            return normalizeWhitespace(text);
        }

        /** Where a declaration's header starts, after its annotations. */
        private int headerStart(final KotlinSyntaxNode node) {
            final KotlinSyntaxNode list = node.child(MODIFIER_LIST);
            if (list == null) {
                return node.startOffset;
            }
            int start = node.startOffset;
            for (final KotlinSyntaxNode child : list.children) {
                if (child.is("ANNOTATION_ENTRY") || child.is("ANNOTATION")) {
                    start = child.endOffset;
                }
            }
            return start;
        }

        private String parameterTypes(final KotlinMemberModel member) {
            final List<String> types = new ArrayList<>();
            for (final KotlinParameterModel parameter : member.parameters) {
                if (parameter.declaredType == null) {
                    types.add("Any");
                } else {
                    types.add(parameter.declaredType);
                }
            }
            return String.join(", ", types);
        }
    }

    /**
     * The modifiers the model's vocabulary holds. A Kotlin modifier the vocabulary has no token for,
     * such as {@code enum} or {@code annotation}, is carried by the component type instead, and
     * variance or parameter-passing modifiers say nothing about a declaration's shape.
     */
    private static List<String> supportedModifiers(final List<String> keywords) {
        final List<String> supported = new ArrayList<>();
        for (final String keyword : keywords) {
            if (OOPSourceModelConstants.accessModifier(keyword) != null) {
                supported.add(keyword);
            }
        }
        return supported;
    }

    static String stripBackticks(final String name) {
        if (name != null && name.length() > 1 && name.startsWith("`") && name.endsWith("`")) {
            return name.substring(1, name.length() - 1);
        }
        return name;
    }

    static int implementationHash(final String text) {
        if (text == null) {
            return 0;
        }
        final String stripped = text.replaceAll("\\s+", "");
        if (stripped.isEmpty()) {
            return 0;
        }
        final int hash = stripped.hashCode();
        if (hash == 0) {
            return 1;
        }
        return hash;
    }

    static String normalizeWhitespace(final String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    /** A type as written, on one line, without the spaces a type's punctuation does not need. */
    static String normalizeTypeText(final String text) {
        return normalizeWhitespace(text).replaceAll("\\s*([<?,.]|(?<!-)>)\\s*", "$1").replace(",", ", ");
    }
}
