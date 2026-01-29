package com.hadi.clarpse.compiler;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.JsAst;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.parsing.Config.JsDocParsing;
import com.google.javascript.rhino.Node;
import com.hadi.clarpse.ResolvedRelativePath;
import com.hadi.clarpse.listener.es6.ES6ClassExport;
import com.hadi.clarpse.listener.es6.ES6ClassImport;
import com.hadi.clarpse.listener.es6.ES6Listener;
import com.hadi.clarpse.listener.es6.ES6Module;
import com.hadi.clarpse.listener.es6.ES6ModulesListener;
import com.hadi.clarpse.listener.es6.ModulesMap;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compiles JavaScript code.
 */
public class ClarpseES6Compiler implements ClarpseCompiler {

    private static final Logger LOGGER = LogManager.getLogger(ClarpseES6Compiler.class);

    private CompileResult compileFiles(final Collection<ProjectFile> files) {
        final OOPSourceCodeModel model = new OOPSourceCodeModel();
        final Compiler compiler = setupCompiler();
        final ModulesMap modulesMap = new ModulesMap();
        // Stage 1 - Populate modules map on initial pass.
        populateModulesMap(files, compiler, modulesMap);
        // Stage 2 - Now that initial pass is completed, resolve module exports/imports in each
        // module.
        resolveModuleDependencies(modulesMap);
        // Stage 3 - Final parse of all files, populate source code model.
        Set<ProjectFile> failures = parseAllSourceCode(files, model, compiler, modulesMap);
        return new CompileResult(model, failures);
    }

    private Set<ProjectFile> parseAllSourceCode(final Collection<ProjectFile> files,
                                                final OOPSourceCodeModel model,
                                                final Compiler compiler,
                                                final ModulesMap modulesMap) {
        Set<ProjectFile> failures = new HashSet<>();
        LOGGER.info("<<< Executing third pass to parse all ES6 source files.. >>>");
        files.forEach(file -> {
            try {
                final Node root = new JsAst(com.google.javascript.jscomp.SourceFile.fromCode(
                    file.path(), file.content())).getAstRoot(compiler);
                if (root.getFirstChild() == null || file.content().isEmpty()) {
                    LOGGER.warn("File: " + file.path() + " could not be parsed!");
                    failures.add(file);
                }
                final NodeTraversal.Callback jsListener = new ES6Listener(
                    model, file, files, modulesMap);
                NodeTraversal.traverse(compiler, root, jsListener);
            } catch (final Exception e) {
                LOGGER.error("Error while parsing file: " + file.path() + ".", e);
                failures.add(file);
            }
        });
        return failures;
    }

    private void resolveModuleDependencies(final ModulesMap modulesMap) {
        final Set<String> recursedModules = new HashSet<>();
        LOGGER.info("<<< Executing second pass to resolve module imports and exports.. >>>");
        modulesMap.modules().forEach((module) -> {
            try {
                resolveModuleImportsAndExports(module, recursedModules, modulesMap);
            } catch (final Exception e) {
                LOGGER.error("Failed to resolve module dependencies for " + module.name() + ".", e);
            }
        });
    }

    private void populateModulesMap(final Collection<ProjectFile> files, final Compiler compiler,
                                    final ModulesMap modulesMap) {
        LOGGER.info("<<< Compiling ES6 files, executing initial pass to generate modules map.. "
                        + ">>>");
        files.forEach(file -> {
            try {
                final Node root = new JsAst(com.google.javascript.jscomp.SourceFile.fromCode(
                    file.path(), file.content())).getAstRoot(compiler);
                final NodeTraversal.Callback jsListener = new ES6ModulesListener(file, modulesMap);
                NodeTraversal.traverse(compiler, root, jsListener);
            } catch (final Exception e) {
                LOGGER.error("Failed to parse module info for " + file.path() + ".", e);
            }
        });
    }

    private Compiler setupCompiler() {
        final Compiler compiler = new Compiler();
        final CompilerOptions options = new CompilerOptions();
        options.setParseJsDocDocumentation(JsDocParsing.INCLUDE_DESCRIPTIONS_WITH_WHITESPACE);
        compiler.initOptions(options);
        return compiler;
    }

    private void resolveModuleImportsAndExports(final ES6Module currModule,
                                                final Set<String> recursedModules,
                                                final ModulesMap modulesMap)
        throws Exception {
        LOGGER.info("Resolving import/exports for module: " + currModule.modulePath());
        // Register module to avoid infinite recursion from cyclical dependencies
        recursedModules.add(currModule.modulePath());
        processModuleImports(currModule, recursedModules, modulesMap);
        processModuleExports(currModule, recursedModules, modulesMap);
    }

    private void processModuleExports(final ES6Module currModule, final Set<String> recursedModules,
                                      final ModulesMap modulesMap) throws Exception {
        // PROCESS EXPORTS - Process module exports of the current module.
        for (final Node exportNode : currModule.moduleExportNodes()) {
            ES6Module importedModule = null;
            // STEP 1: If the export/import statement references another module, recursively
            // process it first.
            if (referencesAnotherModule(exportNode)) {
                importedModule = processImportedModule(currModule, modulesMap, exportNode, 1);
                if (importedModule == null) {
                    // The referenced module is not local, no point trying to resolve this
                    // export/import..
                    continue;
                } else {
                    // Ensure we are not processing module exports in a circular recursion
                    if (!recursedModules.contains(importedModule.modulePath())) {
                        resolveModuleImportsAndExports(importedModule, recursedModules, modulesMap);
                    }
                }
            }
            // STEP 2: Resolve the given export/import statement in the current module
            if (exportNode.getBooleanProp(Node.EXPORT_ALL_FROM)) {
                currModule.insertClassExports(importedModule.classExports());
            } else if (exportNode.getFirstChild() != null && exportNode.getFirstChild().isExportSpecs()) {
                for (final Node exportSpecsChildNode : exportNode.getFirstChild().children()) {
                    if (exportSpecsChildNode.isExportSpec()) {
                        final String exportVal =
                            exportSpecsChildNode.getChildAtIndex(0).getString();
                        final String namedExport =
                            exportSpecsChildNode.getChildAtIndex(1).getString();
                        if (exportVal.equals("default") && namedExport.equals("default")) {
                            // Scenario: export { default } from …;
                            final ES6ClassExport matchedExport =
                                importedModule.getDefaultClassExport();
                            currModule.insertClassExport(new ES6ClassExport(
                                matchedExport.qualifiedClassName(),
                                matchedExport.qualifiedClassName(),
                                true));
                        } else if (namedExport.equals("default")) {
                            // Scenario: export { name1 as default, … };
                            final List<ES6ClassImport> matchingImport =
                                currModule.matchingImportsByName(exportVal);
                            if (!matchingImport.isEmpty()) {
                                currModule.exportClassImport(matchingImport.get(0), exportVal,
                                                             true);
                            } else if (currModule.declaredClasses().contains(exportVal)) {
                                insertClassExport(currModule, currModule.declaredClasses().get(0),
                                                  currModule.declaredClasses().get(0), true);
                            }
                        } else {
                            // Scenario: Aggregating Modules -> export { import1 as name1,
                            // import2 as name2, …, nameN
                            // } from …;
                            if (importedModule != null) {
                                final List<ES6ClassExport> matchingExports =
                                    importedModule.matchingExportsByName(namedExport);
                                if (!matchingExports.isEmpty()) {
                                    currModule.exportClassExport(matchingExports.get(0),
                                                                 exportVal, false);
                                }
                            } else {
                                final List<ES6ClassImport> matchingImport =
                                    currModule.matchingImportsByName(exportVal);
                                if (!matchingImport.isEmpty()) {
                                    currModule.exportClassImport(matchingImport.get(0), exportVal,
                                                                 true);
                                } else if (currModule.declaredClasses().contains(exportVal)) {
                                    insertClassExport(currModule, exportVal, namedExport, false);
                                }
                            }
                        }
                    }
                }
            } else if (exportNode.getBooleanProp(Node.EXPORT_DEFAULT)) {
                /**
                 * Handle Default exports of the form:
                 * 1) export default expression;
                 * 2) export default class (…) { … } // also class, function*
                 * 3) export default class name1(…) { … } // also class, function*
                 * 4) export default class {};
                 */
                if (exportNode.getFirstChild() != null && exportNode.getFirstChild().isClass()
                    || (exportNode.getFirstChild().isAssign() && exportNode.getFirstChild().getSecondChild().isClass())
                    || (exportNode.getFirstChild().isName())) {
                    if (exportNode.getChildAtIndex(0).getFirstChild() != null) {
                        if (exportNode.getFirstChild().getFirstChild().isName()) {
                            final String className =
                                exportNode.getFirstChild().getFirstChild().getString();
                            insertClassExport(currModule, className, className, true);
                        } else if (exportNode.getFirstChild().getFirstChild().isEmpty()) {
                            // No class name provided! Use module name.
                            insertClassExport(currModule, currModule.name(), currModule.name(),
                                              true);
                        }
                    } else {
                        if (exportNode.getFirstChild().isName() && currModule.declaredClasses().contains(exportNode.getFirstChild().getString())) {
                            insertClassExport(currModule, exportNode.getFirstChild().getString(),
                                              exportNode.getFirstChild().getString(), true);
                        }
                    }
                }
            } else if (exportNode.getFirstChild().isName() && currModule.declaredClasses().contains(exportNode.getFirstChild().getString())) {
                final String className = exportNode.getFirstChild().getString();
                insertClassExport(currModule, className, className, true);

            } else if (exportNode.getFirstChild().isClass() && exportNode.getFirstChild().getFirstChild().isName()) {
                final String className = exportNode.getFirstChild().getFirstChild().getString();
                insertClassExport(currModule, className, className, false);
            }
        }
    }


    private void processModuleImports(final ES6Module currModule, final Set<String> recursedModules,
                                      final ModulesMap modulesMap) throws Exception {
        for (final Node importNode : currModule.moduleImportNodes()) {
            ES6Module importedModule = null;
            if (referencesAnotherModule(importNode)) {
                importedModule = processImportedModule(currModule, modulesMap, importNode, 2);
                if (importedModule == null) {
                    // The referenced module is not local, no point trying to resolve this
                    // export/import..
                    continue;
                } else {
                    // Ensure we are not processing module exports in a circular recursion
                    if (!recursedModules.contains(importedModule.modulePath())) {
                        resolveModuleImportsAndExports(importedModule, recursedModules, modulesMap);
                    }
                }
            }
            /**
             * Process imports of the following types:
             * import { export1 } from "module-name";
             * import { export1 as alias1 } from "module-name";
             * import { export1 , export2 } from "module-name";
             * import { foo , bar } from "module-name/path/to/specific/un-exported/file";
             * import { export1 , export2 as alias2 , [...] } from "module-name";
             * import defaultExport, { export1 [ , [...] ] } from "module-name";
             */
            for (final Node importChildNode : importNode.children()) {
                if (importChildNode.isImportSpecs()) {
                    for (final Node importSpecChildNode : importChildNode.children()) {
                        if (importSpecChildNode.isImport()) {
                            processImportNode(currModule, importedModule, importSpecChildNode);
                        } else if (importSpecChildNode.isImportSpec()) {
                            processImportSpecNode(currModule, importedModule, importSpecChildNode);
                        }
                    }
                } else if (importChildNode.isName()) {
                    processDefaultImport(currModule, importChildNode, importedModule);
                } else if (importChildNode.isImportStar()) {
                    final String moduleImportAlias = importNode.getSecondChild().getString();
                    currModule.insertClassImports(importedModule.classExports(), moduleImportAlias);
                }
            }
        }
    }

    private void processDefaultImport(final ES6Module currModule, final Node importNode,
                                      final ES6Module importedModule) {
        final ES6ClassExport matchedExport = importedModule.getDefaultClassExport();
        String namedImportVal = importNode.getString();
        if (namedImportVal == null && matchedExport.className() != null) {
            namedImportVal = matchedExport.namedExportValue();
        }
        currModule.insertClassImport(new ES6ClassImport(
            matchedExport.qualifiedClassName(),
            namedImportVal,
            true));
    }

    private boolean isDefault(final Node importNode) {
        return importNode.hasChildren() && importNode.getFirstChild().isName()
            && importNode.getFirstChild().isDefaultValue();
    }

    private void processImportSpecNode(final ES6Module currModule, final ES6Module importedModule,
                                       final Node importSpecChildNode) {
        final String importClassName = importSpecChildNode.getChildAtIndex(0).getString();
        final String importAlias = importSpecChildNode.getChildAtIndex(1).getString();
        final List<ES6ClassExport> matchingExport =
            importedModule.matchingExportsByName(importClassName);
        if (!matchingExport.isEmpty()) {
            currModule.insertClassImport(new ES6ClassImport(
                matchingExport.get(0).qualifiedClassName(), importAlias,
                false));
        }
    }

    private void processImportNode(final ES6Module currModule, final ES6Module importedModule,
                                   final Node importSpecChildNode) {
        final String importClassName = importSpecChildNode.getChildAtIndex(0).getString();
        final List<ES6ClassExport> matchingExport =
            importedModule.matchingExportsByName(importClassName);
        if (!matchingExport.isEmpty()) {
            currModule.insertClassImport(new ES6ClassImport(
                matchingExport.get(0).qualifiedClassName(), matchingExport.get(0).className(),
                false));
        }
    }

    private boolean referencesAnotherModule(final Node importNode) {
        return importNode.hasMoreThanOneChild();
    }

    private ES6Module processImportedModule(final ES6Module currModule, final ModulesMap modulesMap,
                                            final Node importNode,
                                            final int dirChildIndex) throws Exception {
        final ES6Module importedModule;
        final String importedModuleDir = importNode.getChildAtIndex(dirChildIndex).getString();
        if (absoluteModuleImport(importedModuleDir)) {
            importedModule = processAbsoluteImportModule(modulesMap, importedModuleDir);
        } else {
            importedModule = processRelativeImportModule(currModule, modulesMap, importedModuleDir);
        }
        return importedModule;
    }

    private ES6Module processRelativeImportModule(final ES6Module currModule,
                                                  final ModulesMap modulesMap,
                                                  String importedModuleDir) throws Exception {
        LOGGER.info("Attempting to resolve relative import: " + importedModuleDir);
        final ES6Module importedModule; // Relative import path provided..
        if (!relativeModuleImport(importedModuleDir)) {
            importedModuleDir = "./" + importedModuleDir.trim();
        }
        final String importedModuleName = FilenameUtils.removeExtension(importedModuleDir.substring(
            importedModuleDir.lastIndexOf("/") + 1));
        importedModuleDir = importedModuleDir.substring(
            0, importedModuleDir.lastIndexOf("/") + 1);
        String importedModuleRelativePath = new ResolvedRelativePath(currModule.pkgPath(),
                                                                     importedModuleDir).value();
        if (importedModuleRelativePath.equals("/")) {
            importedModuleRelativePath = importedModuleRelativePath.substring(1);
        }
        importedModule = modulesMap.module(importedModuleRelativePath + "/"
                                               + importedModuleName);
        if (importedModule != null) {
            LOGGER.info("Successfully matched relative import with module: "
                            + importedModule.modulePkg());
        } else {
            LOGGER.warn("Was not able to match relative import with any local modules.");
        }
        return importedModule;
    }

    private boolean absoluteModuleImport(final String importedModuleDir) {
        return importedModuleDir.trim().startsWith("/");
    }

    private ES6Module processAbsoluteImportModule(final ModulesMap modulesMap,
                                                  final String importedModuleDir) {
        LOGGER.info("Attempting to resolve absolute import: " + importedModuleDir);
        final List<ES6Module> matchingModules = modulesMap.matchingModules(importedModuleDir);
        ES6Module importedModule = null;
        if (matchingModules.size() == 1) {
            importedModule = modulesMap.module(matchingModules.get(0).modulePath());
            LOGGER.info("Successfully matched absolute import with module: "
                            + importedModule.modulePkg());
        } else {
            LOGGER.warn("Was not able to match absolute import with any local modules.");
        }
        return importedModule;
    }

    private boolean relativeModuleImport(final String importedModuleDir) {
        return importedModuleDir.trim().startsWith("./");
    }

    @Override
    public CompileResult compile(final ProjectFiles projectFiles) {
        final Collection<ProjectFile> files = projectFiles.files(Lang.JAVASCRIPT);
        return compileFiles(files);
    }

    private void insertClassExport(final ES6Module module, final String className,
                                   final String exportAlias,
                                   final boolean isDefault) {
        String pkgSeparator = ".";
        if (module.pkgPath().equals("/")) {
            pkgSeparator = "";
        }
        module.insertClassExport(
            new ES6ClassExport(module.modulePkg() + pkgSeparator + className, exportAlias,
                               isDefault));
    }
}
