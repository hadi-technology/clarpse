package com.hadi.clarpse.listener.es6;

import org.apache.commons.io.FilenameUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModulesMap {

    private final Map<String, ES6Module> modulesMap = new HashMap<>();

    void insertModule(final ES6Module module) {
        modulesMap.put(module.modulePath(), module);
    }

    public ES6Module module(final String modulePath) {
        return modulesMap.get(modulePath);
    }

    public ES6Module moduleByFilePath(final String filePath) {
        return modulesMap.get(FilenameUtils.removeExtension(filePath));
    }

    public boolean containsModule(final String modulePath) {
        return modulesMap.containsKey(modulePath);
    }

    public Collection<ES6Module> modules() {
        return modulesMap.values();
    }

    public List<ES6Module> matchingModules(final String importedModuleDir) {
        return modules().stream().filter(module -> module.modulePath()
                                                         .endsWith(FilenameUtils.removeExtension(importedModuleDir)))
                        .collect(Collectors.toList());
    }
}
