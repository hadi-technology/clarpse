package com.hadi.clarpse.listener.es6;

import com.google.javascript.rhino.Node;
import com.hadi.clarpse.ClarpseUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ES6Module {

    private static final Logger LOGGER = LogManager.getLogger(ES6Module.class);
    private final String name;
    private final ArrayList<Node> moduleExportNodes;
    private final ArrayList<Node> moduleImportNodes;
    private final String modulePath;
    private final String modulePkg;
    private final List<String> declaredClasses;
    private final List<ES6ClassExport> classExports;
    private final List<ES6ClassImport> classImports;
    private final String pkgPath;

    ES6Module(final String modulePath) {
        pkgPath = generatePackagePath(modulePath);
        modulePkg = StringUtils.strip(FilenameUtils.removeExtension(
                pkgPath.replaceAll("/$", ""))
                                                   .replace("/", "."), ".");
        name = FilenameUtils.getBaseName(modulePath);
        this.modulePath = FilenameUtils.removeExtension(modulePath);
        classExports = new ArrayList<>();
        classImports = new ArrayList<>();
        moduleExportNodes = new ArrayList<Node>();
        moduleImportNodes = new ArrayList<Node>();
        declaredClasses = new ArrayList<>();
    }

    public List<String> declaredClasses() {
        return declaredClasses;
    }

    public ArrayList<Node> moduleImportNodes() {
        return moduleImportNodes;
    }

    void insertDeclaredClass(final String declaredClass) {
        declaredClasses.add(declaredClass);
    }

    public String name() {
        return name;
    }

    public String pkgName() {
        return this.pkgPath.substring(this.pkgPath.lastIndexOf('/') + 1);
    }

    public String modulePkg() {
        return modulePkg;
    }

    void insertModuleExportNode(final Node n) {
        moduleExportNodes.add(n);
    }

    public List<Node> moduleExportNodes() {
        return moduleExportNodes;
    }

    public List<ES6ClassExport> classExports() {
        return classExports;
    }

    public String modulePath() {
        return modulePath;
    }

    public void insertClassExport(final ES6ClassExport eS6ClassExport) {
        LOGGER.info("Inserting class export into module " + modulePath + ":\n" + eS6ClassExport.asText());
        classExports.add(eS6ClassExport);
    }

    public List<ES6ClassExport> matchingExportsByName(final String namedExportValue) {
        return classExports.stream().filter(classExport -> classExport.namedExportValue().equals(namedExportValue)).collect(Collectors.toList());
    }

    public List<ES6ClassImport> matchingImportsByName(final String namedImportValue) {
        return classImports.stream().filter(es6ClassImport -> es6ClassImport
                .namedImportValue().equals(namedImportValue)).collect(Collectors.toList());
    }

    /**
     * Sets the given import as an export in the current module.
     */
    public void exportClassImport(final ES6ClassImport classImport, final String exportAlias, final boolean isDefault) {
        classExports().add(new ES6ClassExport(classImport.qualifiedClassName(), exportAlias, isDefault));
    }

    /**
     * Re-exports the given export in the current module.
     */
    public void exportClassExport(final ES6ClassExport classExport, final String exportAlias, final boolean isDefault) {
        classExports().add(new ES6ClassExport(classExport.qualifiedClassName(), exportAlias, isDefault));
    }

    public void insertClassImport(final ES6ClassImport es6ClassImport) {
        LOGGER.info("Inserting class import into module " + modulePath + "\n:" + es6ClassImport.asText());
        classImports.add(es6ClassImport);
    }

    List<ES6ClassImport> getClassImports() {
        return classImports;
    }

    /**
     * Given a file/module path (e.g. ./test.js or foo/bar/test.js, this method returns the
     * corresponding package name (e.g. ./test.js returns "/" and /bar/lol/test.js --> "bar.lol".
     * It is assumed that the given module Path is an absolute path.
     */
    private String generatePackagePath(final String modulePath) {
        if (!modulePath.contains("/")) {
            return "/";
        } else {
            final Path f = Paths.get(modulePath);
            return f.getParent().toString();
        }
    }

    public String pkgPath() {
        return pkgPath;
    }

    public void insertClassExports(final List<ES6ClassExport> classExports) {
        classExports.forEach(classExport -> insertClassExport(classExport));
    }

    public ES6ClassExport getDefaultClassExport() {
        final ES6ClassExport defaultExport = classExports.stream().filter(classExport -> classExport.isDefault())
                                                         .collect(ClarpseUtil.toSingleton());
        return defaultExport;
    }

    void insertModuleImportNode(final Node n) {
        moduleImportNodes.add(n);
    }

    public void insertClassImports(final List<ES6ClassExport> classExports, final String importedModuleAlias) {
        classExports.stream().forEach(export -> insertClassImport(
                new ES6ClassImport(export.qualifiedClassName(), importedModuleAlias, false)
        ));
    }
}
