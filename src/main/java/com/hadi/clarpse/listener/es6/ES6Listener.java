package com.hadi.clarpse.listener.es6;

import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.hadi.clarpse.compiler.ProjectFile;
import com.hadi.clarpse.listener.ParseUtil;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.Package;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * Listener for JavaScript ES6+ source files, based on google's closure
 * compiler.
 */
public class ES6Listener implements Callback {

    private static final Logger LOGGER = LogManager.getLogger(ES6Listener.class);
    private final Stack<Component> componentStack = new Stack<>();
    private final ES6Module module;
    private final OOPSourceCodeModel srcModel;
    private final ProjectFile file;
    private final Package currPkg;
    private int currCyclomaticComplexity = 0;

    public ES6Listener(final OOPSourceCodeModel srcModel, final ProjectFile file,
                       final Collection<ProjectFile> files, final ModulesMap modulesMap) throws Exception {
        this.srcModel = srcModel;
        this.file = file;
        module = modulesMap.module(FilenameUtils.removeExtension(this.file.path()));
        this.currPkg = new Package(module.pkgName(), module.pkgPath());
        LOGGER.info("Parsing New JS File: " + file.path());
    }

    private static String declarationSnippet(final Token token) {
        switch (token) {
            case TRUE:
            case FALSE:
                return "Boolean";
            case STRING:
            case STRING_TYPE:
            case STRING_KEY:
                return "String";
            case NUMBER:
                return "Number";
            case ARRAYLIT:
            case ARRAY_PATTERN:
            case ARRAY_TYPE:
                return "Array";
            case OBJECTLIT:
            case OBJECT_PATTERN:
                return "Object";
            default:
                break;
        }
        return null;
    }

    private static String generateCodeFragment(final List<Component> components) {
        StringBuilder codeFragment = new StringBuilder("(");
        for (final Component cmp : components) {
            codeFragment.append(cmp.name()).append(", ");
        }
        codeFragment = new StringBuilder(codeFragment.toString().trim());
        if (codeFragment.toString().endsWith(",")) {
            codeFragment = new StringBuilder(codeFragment.substring(0, codeFragment.length() - 1));
        }
        codeFragment.append(")");
        return codeFragment.toString();
    }

    @Override
    public void visit(final NodeTraversal t, final Node n, final Node parent) {

        if (n.isClass()) {
            while (!componentStack.isEmpty()) {
                final Component latestComponent = componentStack.peek();
                if (latestComponent.componentType().isBaseComponent()) {
                    completeComponent();
                    break;
                } else {
                    completeComponent();
                }
            }
        } else if (n.isMemberFunctionDef() || n.isGetterDef() || n.isSetterDef()) {
            while (!componentStack.isEmpty()) {
                final Component latestComponent = componentStack.peek();
                if (latestComponent.componentType().isMethodComponent()) {
                    completeComponent();
                    break;
                } else {
                    completeComponent();
                }
            }
        } else if (n.isThis() || n.isVar() || n.isLet()) {
            while (!componentStack.isEmpty()) {
                final Component latestComponent = componentStack.peek();
                if (latestComponent.componentType().isVariableComponent()) {
                    completeComponent();
                    break;
                } else {
                    completeComponent();
                }
            }
        }
    }

    @Override
    public boolean shouldTraverse(final NodeTraversal nodeTraversal, final Node n,
                                  final Node parent) {
        try {
            return shouldTraverse(n);
        } catch (final Exception e) {
            LOGGER.error("Error while determining whether to traverse node " + n + ".", e);
            return true;
        }
    }

    private boolean shouldTraverse(final Node n) throws Exception {
        if (n.isClass()) {
            processClass(n);
        } else if (n.isMemberFunctionDef()) {
            processMemberFunctionDef(n);
        } else if (n.isGetterDef()) {
            processGetterDef(n);
        } else if (n.isSetterDef()) {
            processSetterDef(n);
        } else if (validParamList(n)) {
            processParams(n);
        } else if (isFieldVar(n)) {
            processFieldVar(n);
        } else if (isLocalVar(n)) {
            processLocalVar(n);
        } else if (n.isCase() || n.isSwitch() || n.isIf() || n.isAnd() || n.isOr() || n.isHook()) {
            currCyclomaticComplexity += 1;
        } else if (n.isName() && !n.isEmpty() && resolveType(n.getString()) != null) {
            processTypeReference(n);
        }
        return true;
    }

    private void processTypeReference(final Node n) throws Exception {
        if (!componentStack.isEmpty()) {
            final Component latestCmp = componentStack.peek();
            final String cmpType = resolveType(n.getString());
            if (cmpType != null && !cmpType.equals(latestCmp.uniqueName())) {
                latestCmp.insertCmpRef(new SimpleTypeReference(cmpType));
                updateParentChildrenData(latestCmp);
                LOGGER.info("Associated type reference: " + cmpType + " with component: " + latestCmp.componentName());
            }
        }
    }

    private void processLocalVar(final Node n) throws Exception {
        final Component cmp;
        final String localVarName = n.getFirstChild().getString();
        LOGGER.info("Found local variable: " + localVarName);
        cmp = createComponent(OOPSourceModelConstants.ComponentType.LOCAL, n);
        cmp.setComponentName(ParseUtil.generateComponentName(localVarName, componentStack));
        cmp.setName(localVarName);
        processVariableAssignment(cmp, n.getFirstChild().getFirstChild());
        updateParentChildrenData(cmp);
        componentStack.push(cmp);
    }

    private boolean isLocalVar(final Node n) throws Exception {
        return !componentStack.isEmpty() && (n.isVar() || n.isLet())
            && (ParseUtil.newestMethodComponent(componentStack).componentType() == OOPSourceModelConstants.ComponentType.METHOD
            || ParseUtil.newestMethodComponent(componentStack).componentType() == OOPSourceModelConstants.ComponentType.CONSTRUCTOR);
    }

    private void processFieldVar(final Node n) throws Exception {
        if (n.getFirstChild().getSecondChild().isString()) {
            final String fieldVarname = n.getFirstChild().getSecondChild().getString();
            final Component cmp = createComponent(OOPSourceModelConstants.ComponentType.FIELD, n);
            cmp.setComponentName(generateComponentName(fieldVarname
            ));
            cmp.setName(fieldVarname);
            cmp.insertAccessModifier("private");
            processVariableAssignment(cmp, n.getSecondChild());
            updateParentChildrenData(cmp);
            LOGGER.info("Processed field variable: " + cmp.componentName());
            componentStack.push(cmp);
        }

    }

    private boolean isFieldVar(final Node n) throws Exception {
        return n.isAssign() && n.getFirstChild().hasChildren() && n.getFirstChild().getFirstChild().isThis()
            && !componentStack.isEmpty() && ParseUtil.newestMethodComponent(
            componentStack).componentType() == OOPSourceModelConstants.ComponentType.CONSTRUCTOR;
    }

    private void processParams(final Node n) throws Exception {
        Component cmp;
        final List<Component> generatedParamComponents = new ArrayList<>();
        // determine type of param component to create based on type of current component at the
        // top of stack
        OOPSourceModelConstants.ComponentType paramComponentType =
            OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT;
        if (componentStack.peek().componentType() == OOPSourceModelConstants.ComponentType.CONSTRUCTOR) {
            paramComponentType =
                OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT;
        }
        for (final Node param : n.children()) {
            String paramName;
            if (param.isString() || param.isName()) {
                paramName = param.getString();
            } else if (param.isDefaultValue()) {
                paramName = param.getFirstChild().getString();
            } else {
                throw new Exception("Unrecognized function param type! " + param.getString());
            }
            cmp = createComponent(paramComponentType, n);
            cmp.setComponentName(ParseUtil.generateComponentName(paramName, componentStack));
            cmp.setName(paramName);
            updateParentChildrenData(cmp);
            generatedParamComponents.add(cmp);
        }
        // Set parent method code Fragment using param list
        final Component parentMethod = componentStack.peek();
        parentMethod.setCodeFragment(parentMethod.name() + generateCodeFragment(generatedParamComponents));
        LOGGER.info("Generated code fragment: " + parentMethod.codeFragment() + " for component: "
                        + parentMethod.componentName());
        // Complete method param components
        for (final Component paramCmp : generatedParamComponents) {
            componentStack.push(paramCmp);
        }
    }

    private boolean validParamList(final Node n) {
        return n.isParamList() && !componentStack.isEmpty() && componentStack.peek().componentType().isMethodComponent();
    }

    private void processSetterDef(final Node n) throws Exception {
        final Component cmp;
        currCyclomaticComplexity = 1;
        final String cmpName = "set_" + n.getString();
        LOGGER.info("Found setter definition: " + cmpName);
        cmp = createComponent(OOPSourceModelConstants.ComponentType.METHOD, n.getFirstChild());
        cmp.setComponentName(ParseUtil.generateComponentName(cmpName, componentStack));
        cmp.setName(cmpName);
        cmp.insertAccessModifier("public");
        if (n.isStaticMember()) {
            cmp.insertAccessModifier("static");
        }
        updateParentChildrenData(cmp);
        componentStack.push(cmp);
    }

    private void processGetterDef(final Node n) throws Exception {
        final Component cmp;
        currCyclomaticComplexity = 1;
        final String cmpName = "get_" + n.getString();
        LOGGER.info("Found getter definition: " + cmpName);
        cmp = createComponent(OOPSourceModelConstants.ComponentType.METHOD, n.getFirstChild());
        cmp.setComponentName(ParseUtil.generateComponentName(cmpName, componentStack));
        cmp.setName(cmpName);
        updateParentChildrenData(cmp);
        if (n.isStaticMember()) {
            cmp.insertAccessModifier("static");
        }
        componentStack.push(cmp);
    }

    private void processClass(final Node n) throws Exception {
        Component cmp = createComponent(OOPSourceModelConstants.ComponentType.CLASS, n);
        String name = null;
        if (NodeUtil.isNameDeclaration(n.getParent().getParent())) {
            if (n.getParent().isName()) {
                name = n.getParent().getString();
            }
        } else if (n.hasChildren() && n.getFirstChild().isName()) {
            name = n.getFirstChild().getString();
        } else {
            name = file.shortName();
        }
        cmp.setComponentName(ParseUtil.generateComponentName(name, componentStack));
        cmp.setName(name);
        updateParentChildrenData(cmp);
        if (n.getSecondChild().isName()) {
            // this class extends another class
            LOGGER.info("this class extends " + n.getSecondChild().getString());
            if (resolveType(n.getSecondChild().getString()) != null) {
                cmp.insertCmpRef(new TypeExtensionReference(resolveType(n.getSecondChild().getString())));
            }
        }
        componentStack.push(cmp);
    }

    private void processMemberFunctionDef(final Node n) throws Exception {
        currCyclomaticComplexity = 1;
        Component cmp;
        if (n.hasOneChild() && NodeUtil.isEs6Constructor(n.getFirstChild())) {
            LOGGER.info("Found constructor");
            cmp = createComponent(OOPSourceModelConstants.ComponentType.CONSTRUCTOR,
                                  n.getFirstChild());
            cmp.setComponentName(ParseUtil.generateComponentName("constructor", componentStack));
            cmp.setName("constructor");
            updateParentChildrenData(cmp);
            componentStack.push(cmp);
        } else {
            LOGGER.info("Found instance method: " + n.getString());
            cmp = createComponent(OOPSourceModelConstants.ComponentType.METHOD, n.getFirstChild());
            cmp.setComponentName(ParseUtil.generateComponentName(n.getString(), componentStack));
            cmp.setName(n.getString());
            if (n.isStaticMember()) {
                cmp.insertAccessModifier("static");
            }
            cmp.insertAccessModifier("public");
            updateParentChildrenData(cmp);
            componentStack.push(cmp);
        }
    }

    private void processVariableAssignment(final Component cmp, final Node assignmentNode) {
        if (assignmentNode != null && NodeUtil.isLiteralValue(assignmentNode, false)) {
            cmp.setCodeFragment(cmp.name() + " : " + declarationSnippet(assignmentNode.getToken()));
        } else if (assignmentNode != null && assignmentNode.hasChildren() && assignmentNode.isNew()
            && (assignmentNode.getFirstChild().isName() || assignmentNode.getFirstChild().isGetProp())) {
            final String invokedType;
            if (assignmentNode.getFirstChild().isGetProp()) {
                invokedType = assignmentNode.getFirstChild().getFirstChild().getString();
                if (resolveType(invokedType) != null) {
                    cmp.insertCmpRef(new SimpleTypeReference(resolveType(invokedType)));
                }
            } else {
                invokedType = assignmentNode.getFirstChild().getString();
                if (resolveType(invokedType) != null) {
                    cmp.insertCmpRef(new SimpleTypeReference(resolveType(invokedType)));
                }
            }
            cmp.setCodeFragment(cmp.name() + " : " + invokedType);
        } else {
            cmp.setCodeFragment(cmp.name());
        }
    }

    private void completeComponent() {
        if (!componentStack.isEmpty()) {
            final Component completedCmp = componentStack.pop();
            LOGGER.info("Completing component: " + completedCmp.uniqueName());
            ParseUtil.copyRefsToParents(completedCmp, componentStack);
            // update cyclomatic complexity if component is a method
            if (completedCmp.componentType().isMethodComponent()
                && !ParseUtil.componentStackContainsInterface(componentStack)) {
                completedCmp.setCyclo(currCyclomaticComplexity);
            } else if (completedCmp.componentType() == OOPSourceModelConstants.ComponentType.CLASS) {
                completedCmp.setCyclo(ParseUtil.calculateClassCyclo(completedCmp, srcModel));
                completedCmp.setImports(
                    new HashSet<String>(module.getClassImports().stream().map(
                        ES6ClassImport::qualifiedClassName).collect(Collectors.toSet())) {
                });
            }
            srcModel.insertComponent(completedCmp);
        }
    }

    private String generateComponentName(final String identifier) throws Exception {
        return ParseUtil.newestBaseComponent(componentStack).componentName() + "." + identifier;
    }

    /**
     * Creates a new component representing the given node object, see
     * {@link Component}.
     */
    private Component createComponent(final OOPSourceModelConstants.ComponentType componentType,
                                      final Node n) {
        final Component newCmp = new Component();
        newCmp.setComponentType(componentType);
        newCmp.setSourceFilePath(file.path());
        newCmp.setCodeHash(file.content().substring(
            n.getSourceOffset(), n.getSourceOffset() + n.getLength()).hashCode());
        newCmp.setPkg(this.currPkg);
        if (NodeUtil.getBestJSDocInfo(n) != null) {
            final String doc = NodeUtil.getBestJSDocInfo(n).getOriginalCommentString();
            if (doc != null) {
                newCmp.setComment(doc);
            }
        }
        return newCmp;
    }

    /**
     * Updates the parent's list of children to include the given child component for parent
     * components of the given
     * component if they exist.
     */
    private void updateParentChildrenData(final Component childCmp) throws Exception {
        if (!childCmp.componentType().isBaseComponent()) {
            if (childCmp.componentType() == OOPSourceModelConstants.ComponentType.FIELD) {
                ParseUtil.newestBaseComponent(componentStack).insertChildComponent(childCmp.componentName());
            } else {
                if (!componentStack.isEmpty()) {
                    final String parentName = childCmp.parentUniqueName();
                    for (int i = componentStack.size() - 1; i >= 0; i--) {
                        if (componentStack.get(i).uniqueName().equals(parentName)) {
                            componentStack.get(i).insertChildComponent(childCmp.uniqueName());
                        }
                    }
                }
            }
        }
    }

    /**
     * Tries to return the full, unique type name of the given type, null otherwise.
     */
    private String resolveType(final String type) {
        final List<ES6ClassImport> tmpType = module.matchingImportsByName(type);
        if (!tmpType.isEmpty()) {
            return tmpType.get(0).qualifiedClassName();
        } else if (module.declaredClasses().contains(type)) {
            if (module.modulePkg().isEmpty()) {
                return type;
            } else {
                return module.modulePkg() + "." + type;
            }
        } else {
            return null;
        }
    }
}
