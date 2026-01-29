package com.hadi.clarpse.listener.es6;

import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.Node;
import com.hadi.clarpse.compiler.ProjectFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Listener for JavaScript ES6+ import, export and class declaration statements.
 */
public class ES6ModulesListener implements Callback {

    private static final Logger LOGGER = LogManager.getLogger(ES6ModulesListener.class);
    private final ES6Module module;

    public ES6ModulesListener(final ProjectFile projectFile, final ModulesMap modulesMap) throws Exception {
        module = new ES6Module(projectFile.path());
        modulesMap.insertModule(module);
        LOGGER.info("Parsing module: " + module.modulePath());
    }

    @Override
    public boolean shouldTraverse(final NodeTraversal nodeTraversal, final Node n, final Node parent) {
        try {
            return shouldTraverse(n);
        } catch (final Exception e) {
            LOGGER.error("Failed to determine whether to traverse node " + n + ".", e);
            return true;
        }
    }

    @Override
    public void visit(final NodeTraversal nodeTraversal, final Node node, final Node node1) {
    }

    private boolean shouldTraverse(final Node n) {
        if (n.isExport()) {
            module.insertModuleExportNode(n);
        } else if (n.isImport()) {
            module.insertModuleImportNode(n);
        } else if (n.isClass()) {
            if (NodeUtil.isNameDeclaration(n.getParent().getParent())) {
                if (n.getParent().isName()) {
                    module.insertDeclaredClass(n.getParent().getString());
                }
            } else if (n.hasChildren() && n.getFirstChild().isName()) {
                module.insertDeclaredClass(n.getFirstChild().getString());
            }
        }
        return true;
    }
}
