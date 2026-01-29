package com.hadi.clarpse.listener;

import com.hadi.antlr.golang.GoParser;
import com.hadi.antlr.golang.GoParserBaseListener;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.Package;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

@SuppressWarnings("unchecked")
public class GoLangTreeListener extends GoParserBaseListener {

    private static final Logger LOGGER = LogManager.getLogger(GoLangTreeListener.class);
    private final Stack<Component> componentStack = new Stack<>();
    private final Set<String> currentImports = new HashSet<>();
    private final OOPSourceCodeModel srcModel;
    private final Map<String, String> currentImportsMap = new HashMap<>();
    private final ProjectFile sourceFile;
    private final TreeSet<Package> modulePkgs;
    private final List<Map.Entry<String, Component>> structWaitingList;
    private Package currPkg;
    private String lastParsedTypeIdentifier = null;
    private boolean inReceiverContext = false;
    private boolean inResultContext = false;
    private int currCyclomaticComplexity = 0;

    public GoLangTreeListener(final OOPSourceCodeModel srcModel,
            final TreeSet<Package> modulePkgs, final ProjectFile sourceFile,
            final List<Map.Entry<String, Component>> structWaitingList) {
        this.srcModel = srcModel;
        this.sourceFile = sourceFile;
        this.modulePkgs = modulePkgs;
        this.structWaitingList = structWaitingList;
    }

    private void completeComponent(final Component completedComponent) {
        // update cyclomatic complexity if component is a method
        if (completedComponent.componentType().isMethodComponent()
                && !ParseUtil.componentStackContainsInterface(componentStack)) {
            completedComponent.setCyclo(currCyclomaticComplexity);
        }
        // To handle the case where struct methods are parsed before their parent struct
        // were,
        // check if the completed component is such a parent and update accordingly.
        structWaitingList.forEach(entry -> {
            if (entry.getKey().equals(completedComponent.uniqueName())) {
                updateStructChild(completedComponent, entry.getValue());
            }
        });
        if (completedComponent.componentType().isMethodComponent()
                && srcModel.containsComponent(completedComponent.parentUniqueName())) {
            final Component parentCmp = srcModel.getComponent(completedComponent.parentUniqueName()).get();
            for (final ComponentReference componentReference : completedComponent.references()) {
                ParseUtil.insertCmpRef(parentCmp, componentReference, this.componentStack);
            }
        }
        ParseUtil.copyRefsToParents(completedComponent, componentStack);
        srcModel.insertComponent(completedComponent);
    }

    private void popAndCompleteComponent() {
        if (!componentStack.isEmpty()) {
            completeComponent(componentStack.pop());
        }
    }

    /**
     * Creates a new component based on the given ParseRuleContext.
     */
    private Component createComponent(final OOPSourceModelConstants.ComponentType componentType,
            ParserRuleContext ctx) {
        final Component newCmp = new Component();
        newCmp.setPkg(currPkg);
        newCmp.setComponentType(componentType);
        newCmp.setSourceFilePath(sourceFile.path());
        newCmp.setCodeHash(ParseUtil.originalText(ctx).hashCode());
        return newCmp;
    }

    @Override
    public void enterSourceFile(final GoParser.SourceFileContext ctx) {
        super.enterSourceFile(ctx);
    }

    @Override
    public void enterPackageClause(final GoParser.PackageClauseContext ctx) {
        String pkgName = ctx.IDENTIFIER().getText();
        String sourceFileBasePath = pkgName;
        if (sourceFile.path().contains("/")) {
            sourceFileBasePath = sourceFile.path().substring(0, sourceFile.path().lastIndexOf("/"));
            Iterator<Package> iterator = this.modulePkgs.iterator();
            while (iterator.hasNext()) {
                Package tmpPkg = iterator.next();
                if (sourceFileBasePath.endsWith(tmpPkg.path())) {
                    sourceFileBasePath = tmpPkg.path();
                    break;
                }
            }
        }
        this.currPkg = new Package(pkgName, sourceFileBasePath);
        currentImports.clear();
        if (!componentStack.isEmpty()) {
            LOGGER.info("Found new package declaration while component stack not empty! "
                    + "component stack size is: " + componentStack.size());
        }
    }

    @Override
    public final void enterImportSpec(final GoParser.ImportSpecContext ctx) {
        String fullImportPath = ctx.importPath().getText().replaceAll("\"", "");
        String shortImportName = null;
        Iterator<Package> iterator = this.modulePkgs.iterator();
        // Attempt to find a local package that matches the import statement ..
        while (iterator.hasNext()) {
            Package tmpPkg = iterator.next();
            if (!tmpPkg.path().isEmpty()) {
                String modPkgPath = StringUtils.strip(tmpPkg.path(), "/");
                if (modPkgPath.equalsIgnoreCase(fullImportPath)) {
                    fullImportPath = modPkgPath;
                    shortImportName = tmpPkg.name();
                    break;
                }
            }
        }
        currentImports.add(fullImportPath.replaceAll("/", "."));
        if (ctx.IDENTIFIER() != null && ctx.IDENTIFIER().getText() != null) {
            shortImportName = ctx.IDENTIFIER().getText();
        } else if (shortImportName == null) {
            if (ctx.importPath().getText().contains("/")) {
                shortImportName = ctx.importPath().getText().substring(ctx.importPath().getText().lastIndexOf(
                        "/") + 1).replace("\"", "");
            } else {
                shortImportName = ctx.importPath().getText().replaceAll("\"", "");
            }
        }
        currentImportsMap.put(shortImportName, fullImportPath);
    }

    @Override
    public final void enterStructType(final GoParser.StructTypeContext ctx) {
        if (lastParsedTypeIdentifier != null) {
            if (stackContainsMethod()) {
                // skip over structs defined within methods.
                exitStructType(ctx);
            } else {
                final Component cmp = createComponent(OOPSourceModelConstants.ComponentType.STRUCT, ctx);
                final String comments = ParseUtil.goLangComments(ctx.getStart().getLine(),
                        Arrays.asList(sourceFile.content().split("\n")));
                cmp.setComment(comments);
                cmp.setName(lastParsedTypeIdentifier);
                cmp.setComponentName(ParseUtil.generateComponentName(lastParsedTypeIdentifier,
                        componentStack));
                cmp.setImports(currentImports);
                ParseUtil.pointParentsToGivenChild(cmp, componentStack);
                cmp.insertAccessModifier(visibility(cmp.name()));
                componentStack.push(cmp);
            }
        }
    }

    @Override
    public final void enterInterfaceType(final GoParser.InterfaceTypeContext ctx) {
        if (!stackContainsMethod() && !stackContainsStructOrInterface()) {
            if (lastParsedTypeIdentifier != null) {
                final Component cmp = createComponent(OOPSourceModelConstants.ComponentType.INTERFACE, ctx);
                final String comments = ParseUtil.goLangComments(ctx.getStart().getLine(),
                        Arrays.asList(sourceFile.content().split("\n")));
                cmp.setComment(comments);
                cmp.setName(lastParsedTypeIdentifier);
                cmp.setComponentName(ParseUtil.generateComponentName(lastParsedTypeIdentifier,
                        componentStack));
                cmp.setImports(currentImports);
                ParseUtil.pointParentsToGivenChild(cmp, componentStack);
                cmp.insertAccessModifier(visibility(cmp.name()));
                componentStack.push(cmp);
            }
        } else {
            exitInterfaceType(ctx);
        }
    }

    @Override
    public final void enterMethodSpec(final GoParser.MethodSpecContext ctx) {
        if (ctx.IDENTIFIER() != null) {
            final Component cmp = createComponent(OOPSourceModelConstants.ComponentType.METHOD,
                    ctx);
            final String comments = ParseUtil.goLangComments(ctx.getStart().getLine(),
                    Arrays.asList(sourceFile.content().split("\n")));
            cmp.setComment(comments);
            cmp.setName(ctx.IDENTIFIER().getText());
            cmp.setCodeFragment(cmp.name() + "(");
            cmp.insertAccessModifier(visibility(cmp.name()));
            if (ctx.parameters() != null) {
                setCodeFragmentFromParameters(ctx.parameters(), cmp);
            }
            if (ctx.result() != null) {
                processResult(ctx.result(), cmp);
            }
            cmp.setName(cmp.codeFragment());
            cmp.setComponentName(ParseUtil.generateComponentName(cmp.name(), componentStack));
            ParseUtil.pointParentsToGivenChild(cmp, componentStack);
            componentStack.push(cmp);
            processParameters(ctx.parameters());

        } else if (ctx.typeName() != null) {
            insertExtensionIntoStackBaseComponent(ctx.typeName().getText());
        }
    }

    /**
     * Searches the children of the given context for a TypeNameContext
     * and returns its resolved type value. If there are multiple relevant child
     * nodes,
     * we will return the resolved types for all of them.
     *
     * @param ctx            Given context in which to search
     * @param containedTypes Initially empty Set used to return retrieved type
     *                       values.
     */
    private void fetchContainedTypes(final RuleContext ctx, Set<String> containedTypes) {
        if (ctx != null) {
            if (ctx instanceof GoParser.TypeNameContext) {
                containedTypes.add(resolveType(ctx.getText()));
            }
            for (int i = 0; i < ctx.getChildCount(); i++) {
                if (!(ctx.getChild(i) instanceof TerminalNodeImpl)) {
                    fetchContainedTypes((RuleContext) ctx.getChild(i), containedTypes);
                }
            }
        }
    }

    private GoParser.VarSpecContext findParentVarSpecContext(final RuleContext ctx) {
        if (ctx instanceof GoParser.VarSpecContext) {
            return (GoParser.VarSpecContext) ctx;
        } else if (ctx.getParent() != null) {
            return findParentVarSpecContext(ctx.getParent());
        } else {
            return null;
        }
    }

    @Override
    public final void enterMethodDecl(final GoParser.MethodDeclContext ctx) {
        if (ctx.IDENTIFIER() != null && ctx.signature() != null) {
            final Component cmp = createComponent(OOPSourceModelConstants.ComponentType.METHOD,
                    ctx);
            final String comments = ParseUtil.goLangComments(ctx.getStart().getLine(),
                    Arrays.asList(sourceFile.content().split("\n")));
            cmp.setComment(comments);
            cmp.setName(ctx.IDENTIFIER().getText());
            ParseUtil.pointParentsToGivenChild(cmp, componentStack);
            cmp.setCodeFragment(cmp.name() + "(");
            cmp.insertAccessModifier(visibility(cmp.name()));
            if (ctx.signature().parameters() != null) {
                setCodeFragmentFromParameters(ctx.signature().parameters(), cmp);
            }
            if (ctx.signature().result() != null) {
                processResult(ctx.signature().result(), cmp);
            }
            cmp.setName(cmp.codeFragment());
            cmp.setComponentName(ParseUtil.generateComponentName(cmp.name(), componentStack));
            currCyclomaticComplexity = 1 + countLogicalBinaryOperators(ctx);
            componentStack.push(cmp);
            processParameters(ctx.signature().parameters());
        }
    }

    @Override
    public final void enterFunctionDecl(final GoParser.FunctionDeclContext ctx) {
        exitFunctionDecl(ctx);
    }

    @Override
    public final void enterExpression(final GoParser.ExpressionContext ctx) {
        final String origText = ParseUtil.originalText(ctx);
        if (origText != null) {
            currCyclomaticComplexity += StringUtils.countMatches(origText, " && ");
            currCyclomaticComplexity += StringUtils.countMatches(origText, " || ");
            exitExpression(ctx);
        }
    }

    private void setCodeFragmentFromParameters(final GoParser.ParametersContext ctx,
            final Component currMethodCmp) {
        if (ctx.parameterDecl() != null) {
            for (final GoParser.ParameterDeclContext paramCtx : ctx.parameterDecl()) {
                final String type = ParseUtil.originalText(paramCtx.type_());
                int interval = 1;
                if (paramCtx.identifierList() != null) {
                    interval = paramCtx.identifierList().IDENTIFIER().size();
                }
                for (int i = 0; i < interval; i++) {
                    if (currMethodCmp.codeFragment().endsWith("(")) {
                        currMethodCmp.setCodeFragment(currMethodCmp.codeFragment() + type);
                    } else {
                        currMethodCmp.setCodeFragment(currMethodCmp.codeFragment() + ", " + type);
                    }
                }
            }
        }
        currMethodCmp.setCodeFragment(currMethodCmp.codeFragment() + ")");
    }

    private void processParameters(final GoParser.ParametersContext ctx) {
        if (ctx != null && ctx.parameterDecl() != null) {
            final LetterProvider letterProvider = new LetterProvider();
            final List<Component> paramCmps = new ArrayList<>();
            if (!inReceiverContext && !inResultContext) {
                for (final GoParser.ParameterDeclContext paramCtx : ctx.parameterDecl()) {
                    Set<String> discoveredTypes = new HashSet<>();
                    fetchContainedTypes(paramCtx, discoveredTypes);
                    final List<String> argumentNames = new ArrayList<>();
                    if (paramCtx.identifierList() == null) {
                        // no name provided for method arg, we have to name it ourselves.
                        argumentNames.add(letterProvider.getLetter());
                    } else {
                        paramCtx.identifierList().IDENTIFIER().forEach(nameCtx -> argumentNames.add(nameCtx.getText()));
                    }
                    for (final String methodArgName : argumentNames) {
                        final Component cmp = createComponent(
                                OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT, ctx);
                        cmp.setName(methodArgName);
                        cmp.setComponentName(ParseUtil.generateComponentName(cmp.name(),
                                componentStack));
                        if (!componentStack.isEmpty()) {
                            final Component completedCmp = componentStack.peek();
                            cmp.setPkg(completedCmp.pkg());
                        }
                        ParseUtil.pointParentsToGivenChild(cmp, componentStack);
                        for (final String discoveredType : discoveredTypes) {
                            ParseUtil.insertCmpRef(cmp, new SimpleTypeReference(discoveredType), this.componentStack);
                        }
                        paramCmps.add(cmp);
                    }
                }
            }
            paramCmps.forEach(this::completeComponent);
        }
    }

    @Override
    public final void exitMethodDecl(final GoParser.MethodDeclContext ctx) {
        if (!componentStack.isEmpty()) {
            final Component cmp = componentStack.peek();
            if (cmp.componentType().isMethodComponent()) {
                if (cmp.codeFragment().endsWith("(")) {
                    cmp.setCodeFragment(cmp.codeFragment() + ")");
                }
                cmp.setName(ctx.IDENTIFIER().getText());
                popAndCompleteComponent();
            }
        }
        lastParsedTypeIdentifier = null;
    }

    private int countLogicalBinaryOperators(final ParserRuleContext ctx) {
        int logicalBinaryOperators = 0;
        final String[] codeLines = ParseUtil.originalText(ctx).split("\\r?\\n");
        for (final String codeLine : codeLines) {
            if (!codeLine.trim().startsWith("/")) {
                logicalBinaryOperators += StringUtils.countMatches(codeLine, " && ");
                logicalBinaryOperators += StringUtils.countMatches(codeLine, " || ");
            }
        }
        return logicalBinaryOperators;
    }

    @Override
    public final void exitMethodSpec(final GoParser.MethodSpecContext ctx) {
        if (!componentStack.isEmpty()) {
            final Component cmp = componentStack.peek();
            if (cmp.componentType().isMethodComponent()) {
                if (cmp.codeFragment().endsWith("(")) {
                    cmp.setCodeFragment(cmp.codeFragment() + ")");
                }
                cmp.setName(ctx.IDENTIFIER().getText());
                popAndCompleteComponent();
            }
        }
    }

    @Override
    public final void enterResult(final GoParser.ResultContext ctx) {
        inResultContext = true;
    }

    @Override
    public final void enterReceiver(final GoParser.ReceiverContext ctx) {
        inReceiverContext = true;
    }

    @Override
    public final void enterTypeName(final GoParser.TypeNameContext ctx) {
        final String resolvedType = resolveType(ctx.getText());
        if (!componentStack.isEmpty() && componentStack.peek().componentType().isMethodComponent()) {
            final Component cmp = componentStack.pop();
            if (inResultContext) {
                int vars = 1;
                if (ctx.getParent().getParent() instanceof GoParser.ParameterDeclContext) {
                    final GoParser.ParameterDeclContext pctx = (GoParser.ParameterDeclContext) ctx.getParent()
                            .getParent();
                    if (pctx.identifierList() != null) {
                        vars = pctx.identifierList().IDENTIFIER().size();
                    }
                }
                for (int i = 0; i < vars; i++) {
                    if (cmp.value() == null || cmp.value().isEmpty()) {
                        cmp.setValue(resolvedType);
                    } else {
                        cmp.setValue(cmp.value() + ", " + resolvedType);
                    }
                }
            }
            if (inReceiverContext) {
                if (srcModel.containsComponent(resolvedType)) {
                    final Optional<Component> structCmp = srcModel.getComponent(resolvedType);
                    structCmp.ifPresent(component -> updateStructChild(component, cmp));
                } else {
                    structWaitingList.add(new AbstractMap.SimpleEntry<>(resolvedType, cmp));
                }
            }
            componentStack.push(cmp);
            final GoParser.VarSpecContext tmpContext = findParentVarSpecContext(ctx);
            if (tmpContext != null) {
                for (final TerminalNode identifier : tmpContext.identifierList().IDENTIFIER()) {
                    final Component localVarCmp = createComponent(OOPSourceModelConstants.ComponentType.LOCAL, ctx);
                    localVarCmp.setName(identifier.getText());
                    localVarCmp.setComponentName(ParseUtil.generateComponentName(identifier.getText(), componentStack));
                    ParseUtil.insertCmpRef(localVarCmp, new SimpleTypeReference(resolvedType), this.componentStack);
                    ParseUtil.pointParentsToGivenChild(localVarCmp, componentStack);
                    completeComponent(localVarCmp);
                }
            }
        }
    }

    private void updateStructChild(final Component structCmp, final Component structChildCmp) {
        if (srcModel.containsComponent(structChildCmp.uniqueName())) {
            srcModel.removeComponent(structChildCmp.uniqueName());
        }
        structChildCmp.setComponentName(structCmp.componentName() + "." + structChildCmp.codeFragment());
        structChildCmp.setPkg(structCmp.pkg());
        srcModel.insertComponent(structChildCmp);
        final List<String> childrenToBeRemoved = new ArrayList<>();
        final List<String> childrenToBeAdded = new ArrayList<>();
        for (final String child : structChildCmp.children()) {
            final Optional<Component> childCmpOptional = srcModel.getComponent(child);
            if (childCmpOptional.isPresent()) {
                Component childCmp = childCmpOptional.get();
                childCmp.setComponentName(structChildCmp.componentName() + "." + childCmp.name());
                childCmp.setPkg(structChildCmp.pkg());
                childrenToBeAdded.add(childCmp.uniqueName());
                if (!child.equals(childCmp.uniqueName())) {
                    childrenToBeRemoved.add(child);
                    srcModel.removeComponent(child);
                    srcModel.insertComponent(childCmp);
                }
            }
        }
        childrenToBeRemoved.forEach(item -> structChildCmp.children().remove(item));
        childrenToBeAdded.forEach(structChildCmp::insertChildComponent);
        structCmp.insertChildComponent(structChildCmp.uniqueName());
    }

    @Override
    public final void exitResult(final GoParser.ResultContext ctx) {
        inResultContext = false;
    }

    private void processResult(final GoParser.ResultContext ctx, final Component methodCmp) {
        if ((ctx.parameters() != null && !ctx.parameters().isEmpty() && ctx.parameters().parameterDecl() != null)
                || ctx.type_() != null && !ctx.type_().getText().isEmpty()) {
            if (!methodCmp.codeFragment().contains(":")) {
                if (!methodCmp.codeFragment().endsWith(")")) {
                    methodCmp.setCodeFragment(methodCmp.codeFragment() + ") : (");
                } else {
                    methodCmp.setCodeFragment(methodCmp.codeFragment() + " : (");
                }
                if (ctx.parameters() != null && ctx.parameters().parameterDecl() != null) {
                    for (final GoParser.ParameterDeclContext paramCtx : ctx.parameters().parameterDecl()) {
                        final String paramType = ParseUtil.originalText(paramCtx.type_());
                        int iterations = 1;
                        if (paramCtx.identifierList() != null) {
                            iterations = paramCtx.identifierList().IDENTIFIER().size();
                        }
                        for (int i = 0; i < iterations; i++) {
                            if (methodCmp.codeFragment().trim().endsWith("(")) {
                                methodCmp.setCodeFragment(methodCmp.codeFragment() + paramType);
                            } else {
                                methodCmp.setCodeFragment(methodCmp.codeFragment() + ", " + paramType);
                            }
                        }
                    }
                } else if (ctx.type_() != null) {
                    final String type = ParseUtil.originalText(ctx.type_());
                    if (methodCmp.codeFragment().trim().endsWith("(")) {
                        methodCmp.setCodeFragment(methodCmp.codeFragment() + type);
                    } else {
                        methodCmp.setCodeFragment(methodCmp.codeFragment() + ", " + type);
                    }
                }
                methodCmp.setCodeFragment(methodCmp.codeFragment() + ")");
            }
        }
    }

    @Override
    public final void exitReceiver(final GoParser.ReceiverContext ctx) {
        inReceiverContext = false;
    }

    @Override
    public final void enterForStmt(final GoParser.ForStmtContext ctx) {
        currCyclomaticComplexity += 1;
    }

    @Override
    public final void enterExprSwitchCase(final GoParser.ExprSwitchCaseContext ctx) {
        if (ctx.children != null && ctx.children.size() > 0 && ctx.children.get(0).toString().equals("case")) {
            currCyclomaticComplexity += 1;
        }
    }

    @Override
    public final void enterTypeSwitchCase(final GoParser.TypeSwitchCaseContext ctx) {
        if (ctx.children != null && ctx.children.size() > 0 && ctx.children.get(0).toString().equals("case")) {
            currCyclomaticComplexity += 1;
        }
    }

    @Override
    public final void enterCommCase(final GoParser.CommCaseContext ctx) {
        if (ctx.children != null && ctx.children.size() > 0 && ctx.children.get(0).toString().equals("case")) {
            currCyclomaticComplexity += 1;
        }
    }

    @Override
    public final void enterIfStmt(final GoParser.IfStmtContext ctx) {
        currCyclomaticComplexity += 1;
    }

    private String visibility(final String goLangComponentName) {
        if (Character.isUpperCase(goLangComponentName.charAt(0))) {
            return "public";
        } else {
            return "private";
        }
    }

    @Override
    public final void exitStructType(final GoParser.StructTypeContext ctx) {
        if (!componentStack.isEmpty() && componentStack.peek().componentType().isBaseComponent()) {
            popAndCompleteComponent();
        }
        lastParsedTypeIdentifier = null;
    }

    @Override
    public final void exitInterfaceType(final GoParser.InterfaceTypeContext ctx) {
        if (!componentStack.isEmpty()
                && componentStack.peek().componentType() == OOPSourceModelConstants.ComponentType.INTERFACE) {
            popAndCompleteComponent();
        }
        lastParsedTypeIdentifier = null;
    }

    @Override
    public final void enterFieldDecl(final GoParser.FieldDeclContext ctx) {
        if (!componentStack.isEmpty() && componentStack.peek().componentType().isBaseComponent()) {
            if (ctx.identifierList() != null && !ctx.identifierList().isEmpty()) {
                final List<Component> fieldVars = new ArrayList<>();
                for (final TerminalNode token : ctx.identifierList().IDENTIFIER()) {
                    final Component cmp = createComponent(OOPSourceModelConstants.ComponentType.FIELD, ctx);
                    cmp.setName(token.getText());
                    cmp.setComment(ParseUtil.goLangComments(ctx.getStart().getLine(),
                            Arrays.asList(sourceFile.content().split("\n"))));
                    cmp.setComponentName(ParseUtil.generateComponentName(token.getText(),
                            componentStack));
                    if (ctx.type_().getText().contains("func")) {
                        String line = sourceFile.content().split("\n")[ctx.type_().start.getLine() - 1];
                        if (line.trim().endsWith("}")) {
                            line = line.substring(0, line.indexOf("}")).trim();
                        }
                        if (line.contains("//")) {
                            line = line.substring(0, line.lastIndexOf("//"));
                        }
                        cmp.setCodeFragment(cmp.name() + " : " + line.substring(line.indexOf(
                                "func")).trim());
                    } else {
                        cmp.setCodeFragment(cmp.name() + " : " + ctx.type_().getText());
                    }
                    cmp.insertAccessModifier(visibility(cmp.name()));
                    ParseUtil.pointParentsToGivenChild(cmp, componentStack);
                    Set<String> discoveredTypes = new HashSet<>();
                    fetchContainedTypes(ctx.type_(), discoveredTypes);
                    for (final String discoveredType : discoveredTypes) {
                        ParseUtil.insertCmpRef(cmp, new SimpleTypeReference(resolveType(discoveredType)),
                                this.componentStack);
                    }
                    fieldVars.add(cmp);
                }
                fieldVars.forEach(this::completeComponent);
            } else if (ctx.anonymousField() != null) {
                Set<String> discoveredTypes = new HashSet<>();
                fetchContainedTypes(ctx.anonymousField(), discoveredTypes);
                for (final String discoveredType : discoveredTypes) {
                    insertExtensionIntoStackBaseComponent(discoveredType);
                }
            }
        } else {
            exitFieldDecl(ctx);
        }
    }

    @Override
    public final void enterVarDecl(final GoParser.VarDeclContext ctx) {
        if (componentStack.isEmpty() || !componentStack.peek().componentType().isMethodComponent()) {
            exitVarDecl(ctx);
        }
    }

    private void insertExtensionIntoStackBaseComponent(final String extendsComponent) {
        final List<Component> tmp = new ArrayList<>();
        while (!componentStack.isEmpty()) {
            final Component stackCmp = componentStack.pop();
            tmp.add(stackCmp);
            if (stackCmp.componentType().isBaseComponent()) {
                ParseUtil.insertCmpRef(stackCmp, new TypeExtensionReference(resolveType(extendsComponent)),
                        this.componentStack);
                break;
            }
        }
        tmp.forEach(componentStack::push);
    }

    /**
     * Tries to return the full, unique type name of the given type.
     */
    private String resolveType(String type) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Type to be resolved cannot be empty!");
        }
        type = type.replace("*", "");
        if (currentImportsMap.containsKey(type)) {
            return currentImportsMap.get(type).replaceAll("/", ".");
        }
        for (final Map.Entry<String, String> pair : currentImportsMap.entrySet()) {
            if (type.startsWith(pair.getKey())) {
                return ((pair.getValue()).replaceAll("/", ".")) + "." + type.replace(pair.getKey() + ".", "");
            }
        }
        if (type.contains(".")) {
            return type;
        } else if (baseType(type)) {
            return type;
        } else {
            // must be a local type...
            if (currPkg.path() != null && !currPkg.path().isEmpty()) {
                return currPkg.ellipsisSeparatedPkgPath() + "." + type;
            } else {
                return currPkg.name() + "." + type;
            }
        }
    }

    private boolean baseType(final String type) {
        return (type.equals("string") || type.equals("int") || type.equals("int8") || type.equals("int16")
                || type.equals("int32") || type.equals("int64") || type.equals("uint") || type.equals("uint8")
                || type.equals("uint16") || type.equals("uint32") || type.equals("uint64") || type.equals("uintptr")
                || type.equals("byte") || type.equals("rune") || type.equals("float32") || type.equals("float64")
                || type.equals("complex64") || type.equals("complex128") || type.equals("bool"));
    }

    @Override
    public final void enterTypeSpec(final GoParser.TypeSpecContext ctx) {
        lastParsedTypeIdentifier = ctx.IDENTIFIER().getText();
    }

    private boolean stackContainsMethod() {
        for (final Component cmp : componentStack) {
            if (cmp.componentType().isMethodComponent()) {
                return true;
            }
        }
        return false;
    }

    private boolean stackContainsStructOrInterface() {
        for (final Component cmp : componentStack) {
            if (cmp.componentType().isBaseComponent()) {
                return true;
            }
        }
        return false;
    }

    static class LetterProvider {
        private final String[] letters = new String[] {"a", "b", "c", "d", "e", "f", "g", "h",
                "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y",
                "z" };
        private int count = -1;

        /**
         * Will hit array index out of bounds after 26 letters, but that should never
         * happen.
         */
        String getLetter() {
            count += 1;
            return letters[count];
        }
    }
}