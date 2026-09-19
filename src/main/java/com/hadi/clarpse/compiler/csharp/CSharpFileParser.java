package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.compiler.CompileFailure;
import com.hadi.clarpse.compiler.CompilerSupport;
import com.hadi.clarpse.compiler.FailureCode;
import com.hadi.clarpse.compiler.ProjectFile;
import fleet.com.intellij.lang.PsiBuilder;
import fleet.com.intellij.lang.SyntaxTreeBuilder.Production;
import fleet.com.intellij.lexer.Lexer;
import fleet.com.intellij.psi.ArrayTokenSequence;
import fleet.com.intellij.psi.FleetPsiParser;
import fleet.com.intellij.psi.tree.IElementType;
import fleet.com.intellij.psi.tree.TokenSet;
import fleet.com.jetbrains.csharp.CSharpFleetParser;
import fleet.com.jetbrains.lang.parsing.builder.MarkerPsiBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Syntax-first C# file parser that converts JetBrains PSI productions into
 * lightweight Clarpse intermediate models. This stage is responsible for
 * declaration extraction, local/body token harvesting, and cheap structural
 * validation before the model assembler performs repo-local resolution.
 */
final class CSharpFileParser {

    private static final Set<String> TYPE_DECLARATIONS = Set.of(
            "cs:class-declaration",
            "cs:interface-declaration",
            "cs:struct-declaration",
            "cs:record-declaration",
            "cs:struct-record-declaration",
            "cs:enum-declaration",
            "cs:delegate-declaration"
    );

    private static final Set<String> MEMBER_DECLARATIONS = Set.of(
            "cs:property-declaration",
            "cs:event-declaration",
            "cs:field-declaration",
            // A `const` field is its own production, and was in no member set, so a type's constants
            // were absent from the model entirely.
            "cs:const-declaration",
            "cs:multiple-fields-declaration",
            "cs:ctor-declaration",
            "cs:method-declaration",
            "cs:inplace-record-field-declaration",
            "cs:enum-member-declaration"
    );

    /**
     * Statement-shaped productions the parser wraps around top-level declarations it could not
     * place, such as those following an unrecognised directive. They declare nothing themselves.
     */
    private static final Set<String> TOP_LEVEL_WRAPPERS = Set.of("cs:kops-statement");

    private static final String BYTE_ORDER_MARK = "\uFEFF";

    /**
     * Statement productions the parser emits for a using directive written after a preprocessor
     * directive: a using statement, or, for {@code global using}, a line statement around one.
     */
    private static final Set<String> MISPLACED_USING_STATEMENTS = Set.of(
            "cs:using-scoped-statement",
            "cs:line-statement"
    );

    /**
     * The using directive shapes: a namespace, a static type, or an alias, optionally global. A
     * declaration such as {@code using var x = ...;} does not match.
     */
    private static final Pattern USING_DIRECTIVE_PATTERN = Pattern.compile(
            "(?:global\\s+)?using\\s+(?:static\\s+)?(?:@?[A-Za-z_]\\w*\\s*=\\s*)?"
                    + "@?[A-Za-z_][\\w.@:<>,\\s]*;");

    /**
     * A using directive that ends a namespace header. When {@code #if} alternatives each open a block
     * namespace, the parser ends the first header at the next {@code ;}, which can be that of a using
     * directive in a later alternative.
     */
    private static final Pattern HEADER_TRAILING_USING_PATTERN = Pattern.compile(
            "(?:^|\\s)(" + USING_DIRECTIVE_PATTERN.pattern() + ")\\s*$");

    /** A preprocessor directive line. */
    private static final Pattern DIRECTIVE_LINE_PATTERN = Pattern.compile("(?m)^[ \\t]*#.*$");

    /** A line or block comment; namespace headers hold no string literals that could contain one. */
    private static final Pattern COMMENT_PATTERN = Pattern.compile("//[^\\n]*|/\\*(?s:.*?)\\*/");

    /** Nodes whose text is read as a name: declared identifiers, type usages and member usages. */
    private static final Set<String> NAME_NODES = Set.of(
            "cs:id-role",
            "cs:type-usage-role",
            "cs:field-usage-role"
    );

    private static final Pattern TYPE_TOKEN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_\\.]*");
    private static final Pattern THIS_MEMBER_ASSIGN_PATTERN =
            Pattern.compile("\\bthis\\.(\\w+)\\s*=\\s*(\\w+)\\b");
    private static final Pattern STATIC_RECEIVER_PATTERN =
            Pattern.compile("\\b([A-Z][A-Za-z0-9_\\.]*)\\s*\\.");

    private CSharpFileParser() {
    }

    static CSharpModel.ParseOutcome parseFile(final ProjectFile file, final int index) {
        try {
            final String sourceText = withoutByteOrderMark(file.content());
            validateBalancedDelimiters(sourceText);
            final CSharpModel.CSharpFileModel fileModel = new CSharpModel.CSharpFileModel(
                    file,
                    sourceText,
                    CompilerSupport.moduleNameForFile(file.path())
            );
            final SyntaxTree tree = parseSyntaxTree(sourceText);
            for (final SyntaxNode child : tree.root().children) {
                parseTopLevelNode(child, fileModel, "", tree.laterAlternatives(), false);
            }
            return new CSharpModel.ParseOutcome(index, fileModel, null);
        } catch (final Exception e) {
            return new CSharpModel.ParseOutcome(
                    index,
                    null,
                    new CompileFailure(file, e.getMessage(), FailureCode.PARSE_FAILED)
            );
        }
    }

    /**
     * The lexer does not treat a leading byte-order mark as whitespace, so a directive on the first
     * line stops starting a line and the parser folds everything after it into one statement node.
     * All offsets are taken on the returned text.
     */
    private static String withoutByteOrderMark(final String content) {
        if (content == null) {
            return "";
        }
        if (content.startsWith(BYTE_ORDER_MARK)) {
            return content.substring(BYTE_ORDER_MARK.length());
        }
        return content;
    }

    private static SyntaxTree parseSyntaxTree(final String sourceText) {
        final FleetPsiParser parser = new CSharpFleetParser();
        final Lexer lexer = parser.getLexer();
        final ArrayTokenSequence tokens = new ArrayTokenSequence.Builder(sourceText, lexer).performLexing();
        final PsiBuilder builder = new MarkerPsiBuilder(
                sourceText,
                tokens,
                parser.getWhitespaces(),
                parser.getComments(),
                0,
                tokens.getLexemeCount()
        );
        parser.parse(builder);
        final List<Production> productions = builder.getProductions();
        final Deque<SyntaxNode> stack = new ArrayDeque<>();
        SyntaxNode root = null;
        for (final Production production : productions) {
            final String type = String.valueOf(production.getTokenType());
            final SyntaxNode current = new SyntaxNode(type, production.getStartOffset(), production.getEndOffset());
            if (!stack.isEmpty() && stack.peek().sameRange(current)) {
                stack.pop();
                continue;
            }
            if (root == null) {
                root = current;
            } else if (!stack.isEmpty()) {
                current.parent = stack.peek();
                current.parent.children.add(current);
            }
            stack.push(current);
        }
        if (root == null) {
            throw new IllegalStateException("No syntax nodes produced for source.");
        }
        attachText(root, new SourceTokens(sourceText, tokens, parser.getWhitespaces(), parser.getComments()));
        return new SyntaxTree(root, laterConditionalAlternatives(tokens));
    }

    /**
     * Marks the source text that lies in any alternative of an {@code #if} group other than the
     * first: the text after an {@code #elif} or {@code #else} directive, up to the group's
     * {@code #endif}. Directives are not evaluated, so every alternative is parsed as code; the
     * first alternative stands in for the configuration the file is read under.
     */
    private static BitSet laterConditionalAlternatives(final ArrayTokenSequence tokens) {
        final BitSet later = new BitSet();
        final Deque<Boolean> groups = new ArrayDeque<>();
        int laterGroups = 0;
        int laterStart = 0;
        for (int i = 0; i < tokens.getLexemeCount(); i += 1) {
            final String tokenType = String.valueOf(tokens.lexType(i));
            if ("PP_IF_SECTION".equals(tokenType)) {
                groups.push(Boolean.FALSE);
            } else if (("PP_ELIF_SECTION".equals(tokenType) || "PP_ELSE_SECTION".equals(tokenType))
                    && !groups.isEmpty() && !groups.peek()) {
                groups.pop();
                groups.push(Boolean.TRUE);
                if (laterGroups == 0) {
                    laterStart = tokens.lexStart(i);
                }
                laterGroups += 1;
            } else if ("PP_ENDIF".equals(tokenType) && !groups.isEmpty()) {
                if (groups.pop()) {
                    laterGroups -= 1;
                    if (laterGroups == 0) {
                        later.set(laterStart, tokens.lexStart(i));
                    }
                }
            }
        }
        if (laterGroups > 0) {
            later.set(laterStart, tokens.getTextLength());
        }
        return later;
    }

    /**
     * A production's range runs up to the next significant token, so it also covers any
     * whitespace and comments that follow its last token. Every node's text stops at its last
     * significant token, and name-bearing nodes (identifiers, type and member usages) additionally drop
     * the comments inside them, so a declared or referenced name is its tokens alone.
     */
    private static void attachText(final SyntaxNode node, final SourceTokens source) {
        if (NAME_NODES.contains(node.type)) {
            node.text = source.textWithoutComments(node.startOffset, node.endOffset);
        } else {
            node.text = source.text(node.startOffset, source.significantEnd(node.startOffset, node.endOffset));
        }
        for (final SyntaxNode child : node.children) {
            attachText(child, source);
        }
    }

    /**
     * Collects the usings and type declarations under {@code node}.
     *
     * <p>A namespace declaration names the declarations it encloses unless it lies in a later
     * alternative of an {@code #if} group, or is a block namespace inside a file-scoped namespace.
     * Alternatives that each name a namespace parse as one declaration nested in another, and a block
     * namespace cannot follow a file-scoped one in valid C#; joining either to the enclosing name
     * would produce a namespace that exists under no build configuration. The enclosed declarations
     * keep the enclosing namespace instead, which is the one the first alternative names.
     */
    private static void parseTopLevelNode(final SyntaxNode node,
                                          final CSharpModel.CSharpFileModel fileModel,
                                          final String currentNamespace,
                                          final BitSet laterAlternatives,
                                          final boolean insideFileScopedNamespace) {
        if (node == null) {
            return;
        }
        final String type = node.type;
        final boolean fileScoped = "cs:namespace-file-scope-declaration".equals(type);
        if (fileScoped || "cs:namespace-block-declaration".equals(type)) {
            final boolean namesDeclarations = !laterAlternatives.get(node.startOffset)
                    && (fileScoped || !insideFileScopedNamespace);
            String namespaceName = currentNamespace;
            if (namesDeclarations) {
                namespaceName = combineNamespace(currentNamespace, namespaceName(node));
            }
            final boolean nestedInsideFileScoped = insideFileScopedNamespace || fileScoped;
            for (final SyntaxNode child : node.children) {
                if ("cs:block-list".equals(child.type)) {
                    for (final SyntaxNode nested : child.children) {
                        parseTopLevelNode(nested, fileModel, namespaceName, laterAlternatives,
                                nestedInsideFileScoped);
                    }
                } else if ("cs:namespace-header-node-statement".equals(child.type)) {
                    addUsingEndingHeader(child, fileModel);
                } else if (!"cs:id-role".equals(child.type)) {
                    parseTopLevelNode(child, fileModel, namespaceName, laterAlternatives,
                            nestedInsideFileScoped);
                }
            }
            return;
        }
        if ("cs:block-list".equals(type) || TOP_LEVEL_WRAPPERS.contains(type)) {
            for (final SyntaxNode child : node.children) {
                parseTopLevelNode(child, fileModel, currentNamespace, laterAlternatives,
                        insideFileScopedNamespace);
            }
            return;
        }
        if ("cs:using-list-role".equals(type)) {
            for (final SyntaxNode child : node.children) {
                if ("cs:using-directive-statement".equals(child.type)) {
                    fileModel.usings.add(parseUsing(child.text));
                }
            }
            return;
        }
        if (MISPLACED_USING_STATEMENTS.contains(type)
                && USING_DIRECTIVE_PATTERN.matcher(normalizeWhitespace(node.text)).matches()) {
            fileModel.usings.add(parseUsing(node.text));
            return;
        }
        if (TYPE_DECLARATIONS.contains(type)) {
            fileModel.types.add(parseType(node, fileModel, currentNamespace, null));
        }
    }

    private static void validateBalancedDelimiters(final String sourceText) {
        int braces = 0;
        int parens = 0;
        int brackets = 0;
        boolean inString = false;
        boolean verbatimString = false;
        char stringDelimiter = 0;
        for (int i = 0; i < sourceText.length(); i += 1) {
            final char ch = sourceText.charAt(i);
            if (inString) {
                if (verbatimString) {
                    if (ch == '"' && i + 1 < sourceText.length() && sourceText.charAt(i + 1) == '"') {
                        i += 1;
                        continue;
                    }
                    if (ch == stringDelimiter) {
                        inString = false;
                        verbatimString = false;
                    }
                } else if (ch == stringDelimiter && !isEscapedByOddBackslashes(sourceText, i)) {
                    inString = false;
                }
                continue;
            }
            if (ch == '/' && i + 1 < sourceText.length()) {
                final char next = sourceText.charAt(i + 1);
                if (next == '/') {
                    i += 2;
                    while (i < sourceText.length() && sourceText.charAt(i) != '\n') {
                        i += 1;
                    }
                    continue;
                }
                if (next == '*') {
                    i += 2;
                    while (i + 1 < sourceText.length()
                            && !(sourceText.charAt(i) == '*' && sourceText.charAt(i + 1) == '/')) {
                        i += 1;
                    }
                    i += 1;
                    continue;
                }
            }
            if (isVerbatimStringStart(sourceText, i)) {
                inString = true;
                verbatimString = true;
                stringDelimiter = '"';
                if (sourceText.charAt(i) == '@' && i + 1 < sourceText.length()
                        && sourceText.charAt(i + 1) == '"') {
                    i += 1;
                } else {
                    i += 2;
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                inString = true;
                verbatimString = false;
                stringDelimiter = ch;
                continue;
            }
            switch (ch) {
                case '{':
                    braces += 1;
                    break;
                case '}':
                    braces -= 1;
                    break;
                case '(':
                    parens += 1;
                    break;
                case ')':
                    parens -= 1;
                    break;
                case '[':
                    brackets += 1;
                    break;
                case ']':
                    brackets -= 1;
                    break;
                default:
                    break;
            }
        }
        if (braces != 0 || parens != 0 || brackets != 0) {
            throw new IllegalStateException("Unbalanced delimiters in C# source.");
        }
    }

    private static boolean isEscapedByOddBackslashes(final String sourceText, final int index) {
        int backslashCount = 0;
        for (int i = index - 1; i >= 0 && sourceText.charAt(i) == '\\'; i -= 1) {
            backslashCount += 1;
        }
        return backslashCount % 2 == 1;
    }

    private static boolean isVerbatimStringStart(final String sourceText, final int index) {
        if (index < 0 || index >= sourceText.length()) {
            return false;
        }
        if (sourceText.charAt(index) == '@') {
            if (index + 1 < sourceText.length() && sourceText.charAt(index + 1) == '"') {
                return true;
            }
            return index + 2 < sourceText.length()
                    && sourceText.charAt(index + 1) == '$'
                    && sourceText.charAt(index + 2) == '"';
        }
        return sourceText.charAt(index) == '$'
                && index + 2 < sourceText.length()
                && sourceText.charAt(index + 1) == '@'
                && sourceText.charAt(index + 2) == '"';
    }

    /**
     * Records the using directive a namespace header ends with, if any. Only a header that spans a
     * preprocessor directive can hold one: in valid C# a using never follows a namespace name
     * otherwise. Directives are not evaluated, so the using is recorded whichever alternative it is in.
     */
    private static void addUsingEndingHeader(final SyntaxNode header,
                                             final CSharpModel.CSharpFileModel fileModel) {
        if (!DIRECTIVE_LINE_PATTERN.matcher(header.text).find()) {
            return;
        }
        final String code = DIRECTIVE_LINE_PATTERN.matcher(
                COMMENT_PATTERN.matcher(header.text).replaceAll(" ")).replaceAll(" ");
        final Matcher using = HEADER_TRAILING_USING_PATTERN.matcher(code);
        if (using.find()) {
            fileModel.usings.add(parseUsing(using.group(1)));
        }
    }

    private static CSharpModel.CSharpUsingModel parseUsing(final String text) {
        final String normalized = normalizeWhitespace(text).toLowerCase(Locale.ROOT);
        final boolean globalImport = normalized.startsWith("global using ");
        final boolean staticImport = normalized.contains(" using static ")
                || normalized.startsWith("using static ")
                || normalized.startsWith("global using static ");
        String raw = normalizeWhitespace(text);
        if (raw.startsWith("global ")) {
            raw = raw.substring("global ".length()).trim();
        }
        if (raw.startsWith("using ")) {
            raw = raw.substring("using ".length()).trim();
        }
        if (raw.startsWith("static ")) {
            raw = raw.substring("static ".length()).trim();
        }
        if (raw.endsWith(";")) {
            raw = raw.substring(0, raw.length() - 1).trim();
        }
        if (raw.isEmpty()) {
            return new CSharpModel.CSharpUsingModel(null, "", false, globalImport, staticImport);
        }
        final int equalsIndex = raw.indexOf('=');
        if (equalsIndex >= 0) {
            final String alias = raw.substring(0, equalsIndex).trim();
            final String target = raw.substring(equalsIndex + 1).trim();
            return new CSharpModel.CSharpUsingModel(alias, target, true, globalImport, staticImport);
        }
        return new CSharpModel.CSharpUsingModel(null, raw, false, globalImport, staticImport);
    }

    private static CSharpModel.CSharpTypeModel parseType(final SyntaxNode node,
                                                         final CSharpModel.CSharpFileModel fileModel,
                                                         final String namespaceName,
                                                         final String parentTypeComponentName) {
        final CSharpModel.CSharpTypeModel typeModel = new CSharpModel.CSharpTypeModel();
        typeModel.kind = mapTypeKind(node.type);
        if ("record".equals(typeModel.kind)) {
            final String normalized = normalizeWhitespace(node.text).toLowerCase(Locale.ROOT);
            if (normalized.startsWith("record struct ")
                    || normalized.contains(" partial record struct ")
                    || normalized.contains(" public record struct ")
                    || normalized.contains(" private record struct ")
                    || normalized.contains(" protected record struct ")
                    || normalized.contains(" internal record struct ")) {
                typeModel.kind = "recordStruct";
            }
        }
        typeModel.name = eraseTypeParameterList(firstIdentifier(node));
        if (namespaceName == null) {
            typeModel.namespaceName = "";
        } else {
            typeModel.namespaceName = namespaceName;
        }
        typeModel.moduleName = fileModel.moduleName;
        typeModel.sourcePath = fileModel.sourceFile.path();
        typeModel.sourceText = fileModel.sourceText;
        typeModel.comment = extractLeadingComment(fileModel.sourceText, node.startOffset);
        typeModel.modifiers = parseModifiers(node.text);
        typeModel.annotations = precedingAttributeNames(node);
        typeModel.partial = typeModel.modifiers.contains("partial");
        typeModel.startOffset = node.startOffset;
        typeModel.endOffset = node.endOffset;
        typeModel.imports = importTargets(fileModel.usings);
        typeModel.usingAliases = usingAliases(fileModel.usings);
        if (parentTypeComponentName == null || parentTypeComponentName.isEmpty()) {
            typeModel.componentName = typeModel.name;
        } else {
            typeModel.componentName = parentTypeComponentName + "." + typeModel.name;
        }
        typeModel.codeFragment = buildTypeCodeFragment(node.text);
        typeModel.implementationHash = implementationHash(node.text);

        for (final SyntaxNode child : node.children) {
            if ("cs:type-usage-role".equals(child.type) && isDirectBaseType(child, node)) {
                typeModel.baseTypes.add(trimTypeText(child.text));
            }
        }

        final SyntaxNode blockNode = firstChild(node, "cs:block-list");
        if (blockNode != null) {
            for (final SyntaxNode child : blockNode.children) {
                if (TYPE_DECLARATIONS.contains(child.type)) {
                    typeModel.nestedTypes.add(parseType(child, fileModel, namespaceName, typeModel.componentName));
                } else if (MEMBER_DECLARATIONS.contains(child.type)) {
                    typeModel.members.addAll(parseMember(child, fileModel, typeModel));
                }
            }
        }

        if ("delegate".equals(typeModel.kind)) {
            typeModel.members.addAll(parseParametersAsMembers(node, "parameter"));
        }
        if ("record".equals(typeModel.kind) || "recordStruct".equals(typeModel.kind)) {
            final SyntaxNode parentList = firstChild(node, "cs:parent-list");
            if (parentList != null) {
                for (final SyntaxNode child : parentList.children) {
                    if ("cs:inplace-record-field-declaration".equals(child.type)) {
                        typeModel.members.addAll(parseMember(child, fileModel, typeModel));
                    }
                }
            }
        }
        if ("enum".equals(typeModel.kind) && blockNode != null) {
            for (final SyntaxNode child : blockNode.children) {
                if ("cs:enum-member-declaration".equals(child.type)) {
                    typeModel.members.addAll(parseMember(child, fileModel, typeModel));
                }
            }
        }
        return typeModel;
    }

    private static List<CSharpModel.CSharpMemberModel> parseMember(final SyntaxNode node,
                                                                   final CSharpModel.CSharpFileModel fileModel,
                                                                   final CSharpModel.CSharpTypeModel ownerType) {
        final List<CSharpModel.CSharpMemberModel> members = new ArrayList<>();
        switch (node.type) {
            case "cs:field-declaration":
            case "cs:const-declaration":
            case "cs:multiple-fields-declaration":
                members.addAll(parseFieldLike(node, fileModel, ownerType, "field"));
                break;
            case "cs:property-declaration":
                members.add(buildSingleMember(node, fileModel, ownerType, "property",
                        firstIdentifier(node),
                        firstDirectChildText(node, "cs:type-usage-role"),
                        null));
                break;
            case "cs:event-declaration":
                members.add(buildSingleMember(node, fileModel, ownerType, "event",
                        firstIdentifier(node),
                        firstDirectChildText(node, "cs:type-usage-role"),
                        null));
                break;
            case "cs:method-declaration":
                members.add(parseCallable(node, fileModel, ownerType, "method"));
                break;
            case "cs:ctor-declaration":
                members.add(parseCallable(node, fileModel, ownerType, "constructor"));
                break;
            case "cs:inplace-record-field-declaration":
                members.add(buildSingleMember(node, fileModel, ownerType, "recordField",
                        firstIdentifier(node),
                        firstDirectChildText(node, "cs:type-usage-role"),
                        null));
                break;
            case "cs:enum-member-declaration":
                members.add(buildSingleMember(node, fileModel, ownerType, "enumMember",
                        firstIdentifier(node), null, null));
                break;
            default:
                break;
        }
        // The member's attributes sit on the declaration node and apply to every component it yields
        // (a `[Required] public int A, B;` decorates both fields).
        final List<String> attributeNames = precedingAttributeNames(node);
        if (!attributeNames.isEmpty()) {
            for (final CSharpModel.CSharpMemberModel member : members) {
                member.annotations = new ArrayList<>(attributeNames);
            }
        }
        return members;
    }

    private static List<CSharpModel.CSharpMemberModel> parseFieldLike(final SyntaxNode node,
                                                                      final CSharpModel.CSharpFileModel fileModel,
                                                                      final CSharpModel.CSharpTypeModel ownerType,
                                                                      final String kind) {
        final List<CSharpModel.CSharpMemberModel> members = new ArrayList<>();
        final String declaredType = firstDirectChildText(node, "cs:type-usage-role");
        final List<String> names = directChildTexts(node, "cs:id-role");
        if (names.isEmpty()) {
            final Matcher matcher = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=|;|,)").matcher(node.text);
            while (matcher.find()) {
                names.add(matcher.group(1));
            }
        }
        for (final String name : new LinkedHashSet<>(names)) {
            members.add(buildSingleMember(node, fileModel, ownerType, kind, name, declaredType, null));
        }
        return members;
    }

    private static CSharpModel.CSharpMemberModel parseCallable(final SyntaxNode node,
                                                               final CSharpModel.CSharpFileModel fileModel,
                                                               final CSharpModel.CSharpTypeModel ownerType,
                                                               final String kind) {
        final CSharpModel.CSharpMemberModel member = buildSingleMember(
                node,
                fileModel,
                ownerType,
                kind,
                firstIdentifier(node),
                null,
                firstDirectChildText(node, "cs:type-usage-role")
        );
        final SyntaxNode parentList = firstChild(node, "cs:parent-list");
        if (parentList != null) {
            for (final SyntaxNode child : parentList.children) {
                if ("cs:parameter-declaration".equals(child.type)) {
                    final CSharpModel.CSharpParameterModel parameter = new CSharpModel.CSharpParameterModel();
                    parameter.name = firstIdentifier(child);
                    parameter.declaredType = firstDirectChildText(child, "cs:type-usage-role");
                    parameter.modifiers = parseModifiers(child.text);
                    parameter.implementationHash = implementationHash(child.text);
                    // `this` is not in the modifier vocabulary, but it is what makes the method an
                    // extension of the type this parameter names.
                    parameter.extensionReceiver = normalizeWhitespace(child.text).startsWith("this ");
                    member.parameters.add(parameter);
                }
            }
        }
        final SyntaxNode block = firstChild(node, "cs:block-list");
        if (block != null) {
            member.cyclo = calculateCyclo(block.text);
            member.locals.addAll(parseLocals(block, fileModel, ownerType));
            collectBodyRefs(block, member, fileModel.sourceText);
        }
        return member;
    }

    private static List<CSharpModel.CSharpMemberModel> parseLocals(final SyntaxNode block,
                                                                   final CSharpModel.CSharpFileModel fileModel,
                                                                   final CSharpModel.CSharpTypeModel ownerType) {
        final List<CSharpModel.CSharpMemberModel> locals = new ArrayList<>();
        for (final SyntaxNode descendant : descendants(block)) {
            if ("cs:var-def-statement".equals(descendant.type) || "cs:const-def-statement".equals(descendant.type)) {
                final String localName = firstIdentifier(descendant);
                if (localName == null || localName.isEmpty()) {
                    continue;
                }
                String declaredType = firstDirectChildText(descendant, "cs:type-usage-role");
                boolean inferred = false;
                if (declaredType == null || declaredType.isEmpty() || "var".equals(trimTypeText(declaredType))) {
                    final SyntaxNode newExpression = firstDescendant(descendant, "cs:new-expression");
                    if (newExpression != null) {
                        declaredType = firstDirectChildText(newExpression, "cs:type-usage-role");
                        inferred = declaredType != null && !declaredType.isEmpty();
                    }
                }
                final CSharpModel.CSharpMemberModel local = buildSingleMember(
                        descendant,
                        fileModel,
                        ownerType,
                        "local",
                        localName,
                        declaredType,
                        null
                );
                local.inferredType = inferred;
                locals.add(local);
            }
        }
        return locals;
    }

    private static void collectBodyRefs(final SyntaxNode block,
                                        final CSharpModel.CSharpMemberModel member,
                                        final String sourceText) {
        for (final SyntaxNode descendant : descendants(block)) {
            if ("cs:new-expression".equals(descendant.type)) {
                final String typeText = firstDirectChildText(descendant, "cs:type-usage-role");
                if (typeText != null && !typeText.isEmpty()) {
                    member.simpleTypeUsages.add(typeText);
                }
            } else if ("cs:field-usage-role".equals(descendant.type)) {
                final String fieldName = descendant.text.trim();
                if (!fieldName.isEmpty() && namesAMemberOfTheEnclosingType(sourceText, descendant)) {
                    member.memberUsages.add(fieldName);
                }
            } else if ("cs:line-statement".equals(descendant.type)) {
                final Matcher assignMatcher = THIS_MEMBER_ASSIGN_PATTERN.matcher(descendant.text);
                while (assignMatcher.find()) {
                    member.memberUsages.add(assignMatcher.group(1));
                }
                final Matcher staticReceiverMatcher = STATIC_RECEIVER_PATTERN.matcher(descendant.text);
                while (staticReceiverMatcher.find()) {
                    member.simpleTypeUsages.add(staticReceiverMatcher.group(1));
                }
            }
        }
    }

    /**
     * Whether a name used in a body can be a member of the type that body belongs to.
     *
     * <p>A bare name can be: it is resolved in the enclosing type's own scope. A name reached
     * through a receiver cannot, unless that receiver is {@code this}. This parser does not resolve
     * the type of a receiver, so binding {@code maybe.Value} to a member called {@code Value} on the
     * enclosing type invents a dependency between two types that have nothing to do with each other
     * - the {@code .Value} of a {@code Nullable<T>} is not a user class's property.
     *
     * @param sourceText The file's source.
     * @param usage      The node naming the member.
     * @return True if the name may be resolved against the enclosing type.
     */
    private static boolean namesAMemberOfTheEnclosingType(final String sourceText, final SyntaxNode usage) {
        if (sourceText == null || usage.startOffset <= 0 || usage.startOffset > sourceText.length()) {
            return true;
        }
        int index = usage.startOffset - 1;
        while (index >= 0 && Character.isWhitespace(sourceText.charAt(index))) {
            index -= 1;
        }
        if (index < 0 || sourceText.charAt(index) != '.') {
            return true;
        }
        int receiverEnd = index;
        while (receiverEnd > 0 && Character.isWhitespace(sourceText.charAt(receiverEnd - 1))) {
            receiverEnd -= 1;
        }
        int receiverStart = receiverEnd;
        while (receiverStart > 0 && (Character.isLetterOrDigit(sourceText.charAt(receiverStart - 1))
                || sourceText.charAt(receiverStart - 1) == '_')) {
            receiverStart -= 1;
        }
        return "this".equals(sourceText.substring(receiverStart, receiverEnd));
    }

    private static CSharpModel.CSharpMemberModel buildSingleMember(final SyntaxNode node,
                                                                   final CSharpModel.CSharpFileModel fileModel,
                                                                   final CSharpModel.CSharpTypeModel ownerType,
                                                                   final String kind,
                                                                   final String name,
                                                                   final String declaredType,
                                                                   final String returnType) {
        final CSharpModel.CSharpMemberModel member = new CSharpModel.CSharpMemberModel();
        member.kind = kind;
        member.name = name;
        member.declaredType = trimTypeText(declaredType);
        member.returnType = trimTypeText(returnType);
        member.sourcePath = fileModel.sourceFile.path();
        member.sourceText = fileModel.sourceText;
        member.moduleName = fileModel.moduleName;
        member.ownerTypeUniqueName = ownerType.uniqueName;
        member.modifiers = parseModifiers(node.text);
        member.comment = extractLeadingComment(fileModel.sourceText, node.startOffset);
        member.startOffset = node.startOffset;
        member.endOffset = node.endOffset;
        member.imports = new LinkedHashSet<>(ownerType.imports);
        member.codeFragment = buildMemberCodeFragment(kind, name, member.declaredType, member.returnType, node.text);
        member.implementationHash = implementationHash(node.text);
        if (member.declaredType != null && !member.declaredType.isEmpty()) {
            member.simpleTypeUsages.add(member.declaredType);
        }
        if (member.returnType != null && !member.returnType.isEmpty()) {
            member.simpleTypeUsages.add(member.returnType);
        }
        return member;
    }

    private static List<CSharpModel.CSharpMemberModel> parseParametersAsMembers(final SyntaxNode node,
                                                                                 final String kind) {
        final List<CSharpModel.CSharpMemberModel> members = new ArrayList<>();
        final SyntaxNode parentList = firstChild(node, "cs:parent-list");
        if (parentList == null) {
            return members;
        }
        for (final SyntaxNode child : parentList.children) {
            if ("cs:parameter-declaration".equals(child.type)) {
                final CSharpModel.CSharpMemberModel member = new CSharpModel.CSharpMemberModel();
                member.kind = kind;
                member.name = firstIdentifier(child);
                member.declaredType = firstDirectChildText(child, "cs:type-usage-role");
                member.modifiers = parseModifiers(child.text);
                member.codeFragment = trimTypeText(member.declaredType);
                member.implementationHash = implementationHash(child.text);
                if (member.declaredType != null && !member.declaredType.isEmpty()) {
                    member.simpleTypeUsages.add(member.declaredType);
                }
                members.add(member);
            }
        }
        return members;
    }

    private static Set<String> importTargets(final List<CSharpModel.CSharpUsingModel> usings) {
        final Set<String> imports = new LinkedHashSet<>();
        for (final CSharpModel.CSharpUsingModel usingModel : usings) {
            if (usingModel.target != null && !usingModel.target.isEmpty()) {
                imports.add(usingModel.target);
            }
        }
        return imports;
    }

    private static java.util.Map<String, String> usingAliases(final List<CSharpModel.CSharpUsingModel> usings) {
        final java.util.Map<String, String> aliases = new java.util.LinkedHashMap<>();
        for (final CSharpModel.CSharpUsingModel usingModel : usings) {
            if (usingModel.aliasImport && usingModel.alias != null
                    && !usingModel.alias.isEmpty() && usingModel.target != null) {
                aliases.put(usingModel.alias, usingModel.target);
            }
        }
        return aliases;
    }

    private static boolean isDirectBaseType(final SyntaxNode child, final SyntaxNode parent) {
        if (child.parent != parent) {
            return false;
        }
        final SyntaxNode block = firstChild(parent, "cs:block-list");
        return block == null || child.endOffset <= block.startOffset;
    }

    /**
     * The names of the attributes applied to a type or member declaration.
     *
     * <p>An attribute section ({@code [ApiController]}) is not a child of the declaration it decorates;
     * the parser emits it as a {@code cs:attribute-declaration} sibling immediately preceding the
     * declaration in the same block. So the attributes for {@code node} are the run of
     * {@code cs:attribute-declaration} siblings directly before it, and each attribute's name is the
     * {@code cs:id-role} it wraps ({@code Route} in {@code [Route("/x")]}). A single section may carry
     * several attributes ({@code [A, B]}), which appear as several {@code cs:id-role} children.
     */
    private static List<String> precedingAttributeNames(final SyntaxNode node) {
        final List<String> names = new ArrayList<>();
        if (node == null || node.parent == null) {
            return names;
        }
        final List<SyntaxNode> siblings = node.parent.children;
        final int index = siblings.indexOf(node);
        if (index < 0) {
            return names;
        }
        for (int i = index - 1; i >= 0; i -= 1) {
            final SyntaxNode sibling = siblings.get(i);
            if (!"cs:attribute-declaration".equals(sibling.type)) {
                break;
            }
            final List<String> attributeNames = directChildTexts(sibling, "cs:id-role");
            // Prepend so declaration order is preserved across the backward walk.
            names.addAll(0, attributeNames);
        }
        return names;
    }

    /**
     * Hashes a declaration's full source text - body included - with all whitespace stripped, so that
     * reformatting is invisible but any change to the implementation is not. Zero is reserved for "no
     * text to hash", which lets callers fall back to a signature-derived hash.
     */
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

    private static String buildTypeCodeFragment(final String text) {
        return normalizeWhitespace(cutAtAny(text, "{", ";"));
    }

    private static String buildMemberCodeFragment(final String kind,
                                                  final String name,
                                                  final String declaredType,
                                                  final String returnType,
                                                  final String text) {
        if ("field".equals(kind) || "property".equals(kind) || "event".equals(kind)
                || "recordField".equals(kind) || "local".equals(kind)) {
            if (declaredType == null || declaredType.isEmpty()) {
                return name;
            }
            return name + " : " + trimTypeText(declaredType);
        }
        if ("enumMember".equals(kind)) {
            return name;
        }
        String fragment = normalizeWhitespace(cutAtAny(text, "{", ";"));
        if ("method".equals(kind) && returnType != null && !returnType.isEmpty()
                && !fragment.contains(" : ")) {
            return fragment;
        }
        return fragment;
    }

    private static String cutAtAny(final String text, final String... markers) {
        if (text == null) {
            return null;
        }
        int end = text.length();
        for (final String marker : markers) {
            final int idx = text.indexOf(marker);
            if (idx >= 0) {
                end = Math.min(end, idx);
            }
        }
        return text.substring(0, end).trim();
    }

    private static String normalizeWhitespace(final String text) {
        if (text == null) {
            return null;
        }
        return text.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String trimTypeText(final String text) {
        if (text == null) {
            return null;
        }
        return normalizeWhitespace(text).replaceAll("\\s+([>\\],)])", "$1");
    }

    private static int calculateCyclo(final String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cyclo = 1;
        cyclo += countWord(text, "if");
        cyclo += countWord(text, "for");
        cyclo += countWord(text, "foreach");
        cyclo += countWord(text, "while");
        cyclo += countWord(text, "case");
        cyclo += countWord(text, "catch");
        cyclo += countLiteral(text, "&&");
        cyclo += countLiteral(text, "||");
        return cyclo;
    }

    private static int countWord(final String text, final String word) {
        final Matcher matcher = Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text);
        int count = 0;
        while (matcher.find()) {
            count += 1;
        }
        return count;
    }

    private static int countLiteral(final String text, final String literal) {
        int count = 0;
        int index = text.indexOf(literal);
        while (index >= 0) {
            count += 1;
            index = text.indexOf(literal, index + literal.length());
        }
        return count;
    }

    private static String extractLeadingComment(final String sourceText, final int startOffset) {
        if (sourceText == null || sourceText.isEmpty() || startOffset <= 0) {
            return "";
        }
        final String prefix = sourceText.substring(0, Math.min(startOffset, sourceText.length()));
        final String[] lines = prefix.split("\\R", -1);
        final List<String> commentLines = new ArrayList<>();
        boolean inBlock = false;
        for (int i = lines.length - 1; i >= 0; i -= 1) {
            String line = lines[i].stripTrailing();
            if (line.trim().isEmpty()) {
                if (!commentLines.isEmpty() || inBlock) {
                    break;
                }
                continue;
            }
            final String trimmed = line.trim();
            if (trimmed.startsWith("///") || trimmed.startsWith("//")) {
                commentLines.add(0, stripLineCommentMarker(trimmed));
                continue;
            }
            if (trimmed.endsWith("*/")) {
                commentLines.add(0, trimmed);
                inBlock = true;
                continue;
            }
            if (inBlock) {
                commentLines.add(0, trimmed);
                if (trimmed.startsWith("/*")) {
                    break;
                }
                continue;
            }
            break;
        }
        if (commentLines.isEmpty()) {
            return "";
        }
        return String.join("\n", commentLines) + "\n";
    }

    /**
     * A line comment's text without the marker that introduced it.
     *
     * <p>Only the marker at the start of the line is removed, and at most one space after it, so
     * everything the author wrote survives - a {@code //} later in the line, in a URL for instance,
     * is part of the text and is left alone.
     *
     * @param line A trimmed source line known to start with a line-comment marker.
     * @return The comment text, marker removed.
     */
    private static String stripLineCommentMarker(final String line) {
        String text = line;
        if (text.startsWith("///") || text.startsWith("//!")) {
            text = text.substring(3);
        } else if (text.startsWith("//")) {
            text = text.substring(2);
        }
        if (text.startsWith(" ")) {
            text = text.substring(1);
        }
        return text;
    }

    private static List<String> parseModifiers(final String text) {
        final List<String> modifiers = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return modifiers;
        }
        final String header = normalizeWhitespace(cutAtAny(text, "{", "(", ";"));
        final String[] parts = header.split(" ");
        final Set<String> supported = Set.of(
                "public", "private", "protected", "internal", "static", "abstract", "sealed",
                "readonly", "partial", "virtual", "override", "async", "const", "unsafe"
        );
        for (final String part : parts) {
            final String lower = part.toLowerCase(Locale.ROOT);
            if (supported.contains(lower)) {
                modifiers.add(lower);
            }
        }
        return modifiers;
    }

    private static String mapTypeKind(final String type) {
        switch (type) {
            case "cs:class-declaration":
                return "class";
            case "cs:interface-declaration":
                return "interface";
            case "cs:struct-declaration":
                return "struct";
            case "cs:record-declaration":
                return "record";
            case "cs:struct-record-declaration":
                return "recordStruct";
            case "cs:enum-declaration":
                return "enum";
            case "cs:delegate-declaration":
                return "delegate";
            default:
                throw new IllegalArgumentException("Unsupported type declaration: " + type);
        }
    }

    static List<String> extractTypeTokens(final String rawType) {
        final List<String> results = new ArrayList<>();
        if (rawType == null || rawType.isBlank()) {
            return results;
        }
        final Matcher matcher = TYPE_TOKEN_PATTERN.matcher(rawType);
        while (matcher.find()) {
            final String token = matcher.group();
            if ("new".equals(token) || "var".equals(token) || "global".equals(token)) {
                continue;
            }
            if (Character.isLowerCase(token.charAt(0)) && !token.contains(".")) {
                if (!isBuiltinType(token)) {
                    continue;
                }
            }
            results.add(token);
        }
        return results;
    }

    static boolean isBuiltinType(final String token) {
        return Set.of(
                "string", "int", "long", "short", "byte", "bool", "double", "float",
                "decimal", "char", "object", "void"
        ).contains(token);
    }

    private static String firstIdentifier(final SyntaxNode node) {
        return firstDirectChildText(node, "cs:id-role");
    }

    /**
     * Generic type declarations carry their type-parameter list in the identifier text
     * ("Repo&lt;T&gt;"), but references are resolved against the erased name ("Repo").
     * Register declarations under the erased name so generic types are reachable as
     * reference targets; without this every generic class or interface is an orphan
     * component with zero afferent coupling. C#'s arity-overloaded types (IFoo and
     * IFoo&lt;T&gt;) collapse onto one component, which matches how references, which
     * carry no arity, are resolved anyway.
     */
    private static String eraseTypeParameterList(final String identifier) {
        if (identifier == null) {
            return "";
        }
        final int typeParamStart = identifier.indexOf('<');
        if (typeParamStart < 0) {
            return identifier;
        }
        return identifier.substring(0, typeParamStart).trim();
    }

    private static String namespaceName(final SyntaxNode node) {
        final SyntaxNode header = firstChild(node, "cs:namespace-header-node-statement");
        final List<String> ids;
        if (header == null) {
            ids = directChildTexts(node, "cs:id-role");
        } else {
            ids = directChildTexts(header, "cs:id-role");
        }
        if (ids.isEmpty()) {
            return "";
        }
        return String.join(".", ids);
    }

    private static String firstDirectChildText(final SyntaxNode node, final String type) {
        final SyntaxNode child = firstChild(node, type);
        if (child == null) {
            return null;
        }
        return normalizeWhitespace(child.text);
    }

    private static String combineNamespace(final String currentNamespace, final String declaredNamespace) {
        if (currentNamespace == null || currentNamespace.isEmpty()) {
            if (declaredNamespace == null) {
                return "";
            }
            return declaredNamespace;
        }
        if (declaredNamespace == null || declaredNamespace.isEmpty()) {
            return currentNamespace;
        }
        if (declaredNamespace.equals(currentNamespace)
                || declaredNamespace.startsWith(currentNamespace + ".")) {
            return declaredNamespace;
        }
        return currentNamespace + "." + declaredNamespace;
    }

    private static SyntaxNode firstChild(final SyntaxNode node, final String type) {
        for (final SyntaxNode child : node.children) {
            if (type.equals(child.type)) {
                return child;
            }
        }
        return null;
    }

    private static SyntaxNode firstDescendant(final SyntaxNode node, final String type) {
        for (final SyntaxNode descendant : descendants(node)) {
            if (type.equals(descendant.type)) {
                return descendant;
            }
        }
        return null;
    }

    private static List<String> directChildTexts(final SyntaxNode node, final String type) {
        final List<String> texts = new ArrayList<>();
        for (final SyntaxNode child : node.children) {
            if (type.equals(child.type)) {
                texts.add(normalizeWhitespace(child.text));
            }
        }
        return texts;
    }

    private static List<SyntaxNode> descendants(final SyntaxNode node) {
        final List<SyntaxNode> descendants = new ArrayList<>();
        final Deque<SyntaxNode> stack = new ArrayDeque<>(node.children);
        while (!stack.isEmpty()) {
            final SyntaxNode child = stack.pop();
            descendants.add(child);
            for (int i = child.children.size() - 1; i >= 0; i -= 1) {
                stack.push(child.children.get(i));
            }
        }
        return descendants;
    }

    private static String safeSubstring(final String text, final int start, final int end) {
        int safeStart = Math.max(0, Math.min(start, text.length()));
        int safeEnd = Math.max(safeStart, Math.min(end, text.length()));
        return text.substring(safeStart, safeEnd);
    }

    private record SyntaxTree(SyntaxNode root, BitSet laterAlternatives) {
    }

    /**
     * The lexed source of one file, answering which parts of a character range are trivia
     * (whitespace or comments) rather than significant tokens.
     */
    private static final class SourceTokens {
        private final String sourceText;
        private final ArrayTokenSequence tokens;
        private final TokenSet whitespaces;
        private final TokenSet comments;

        private SourceTokens(final String sourceText, final ArrayTokenSequence tokens,
                             final TokenSet whitespaces, final TokenSet comments) {
            this.sourceText = sourceText;
            this.tokens = tokens;
            this.whitespaces = whitespaces;
            this.comments = comments;
        }

        private String text(final int start, final int end) {
            return safeSubstring(sourceText, start, end);
        }

        /** The end of the last significant token in [start, end), or start if there is none. */
        private int significantEnd(final int start, final int end) {
            if (end <= start || tokens.getLexemeCount() == 0) {
                return end;
            }
            int index = tokens.lexemeIndexByChar(Math.min(end, tokens.getTextLength()) - 1);
            while (index >= 0 && tokens.lexStart(index) >= start && isTrivia(index)) {
                index -= 1;
            }
            if (index < 0) {
                return start;
            }
            return Math.max(start, Math.min(end, lexEnd(index)));
        }

        /**
         * The source of [start, end) without its comments. A run of trivia that holds a comment
         * becomes a single space where it separates two identifier characters and nothing
         * otherwise; whitespace-only runs are kept as written.
         */
        private String textWithoutComments(final int start, final int end) {
            if (end <= start || tokens.getLexemeCount() == 0) {
                return text(start, end).trim();
            }
            final StringBuilder text = new StringBuilder();
            final StringBuilder pendingTrivia = new StringBuilder();
            boolean pendingComment = false;
            int index = tokens.lexemeIndexByChar(Math.min(start, tokens.getTextLength() - 1));
            while (index >= 0 && index < tokens.getLexemeCount() && tokens.lexStart(index) < end) {
                final String lexeme = text(Math.max(start, tokens.lexStart(index)), Math.min(end, lexEnd(index)));
                if (isTrivia(index)) {
                    pendingComment |= comments.contains(tokens.lexType(index));
                    pendingTrivia.append(lexeme);
                } else {
                    if (!pendingComment) {
                        text.append(pendingTrivia);
                    } else if (text.length() > 0 && !lexeme.isEmpty()
                            && Character.isJavaIdentifierPart(text.charAt(text.length() - 1))
                            && Character.isJavaIdentifierPart(lexeme.charAt(0))) {
                        text.append(' ');
                    }
                    pendingTrivia.setLength(0);
                    pendingComment = false;
                    text.append(lexeme);
                }
                index += 1;
            }
            return text.toString().trim();
        }

        private boolean isTrivia(final int index) {
            final IElementType type = tokens.lexType(index);
            return whitespaces.contains(type) || comments.contains(type);
        }

        private int lexEnd(final int index) {
            if (index + 1 < tokens.getLexemeCount()) {
                return tokens.lexStart(index + 1);
            }
            return tokens.getTextLength();
        }
    }

    private static final class SyntaxNode {
        private final String type;
        private final int startOffset;
        private final int endOffset;
        private final List<SyntaxNode> children = new ArrayList<>();
        private SyntaxNode parent;
        private String text = "";

        private SyntaxNode(final String type, final int startOffset, final int endOffset) {
            this.type = type;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        private boolean sameRange(final SyntaxNode other) {
            return other != null
                    && this.startOffset == other.startOffset
                    && this.endOffset == other.endOffset
                    && this.type.equals(other.type);
        }
    }
}
