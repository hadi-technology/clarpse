package com.hadi.clarpse.compiler.python.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw Python import directive emitted by the daemon: one imported module of an {@code import}
 * statement, one {@code from ... import} statement, or a literal {@code importlib.import_module} /
 * {@code __import__} call.
 */
public class PythonImportDirectiveModel {

    /** The module as written, a relative module keeping its leading dots. */
    public String target;

    /** {@code single} or {@code on_demand}. */
    public String kind;

    /** Whether the module is written relative to the importing file. */
    public boolean relative;

    /** The members taken by name, as the module defines them. */
    public List<String> names = new ArrayList<>();

    /** The local names bound to {@link #names}. */
    public List<String> binds = new ArrayList<>();

    /** The local name the module as a whole is bound to, or null. */
    public String alias;

    /** Whether the directive binds or loads a module as a whole. */
    public boolean importsModule;

    /** Whether the import runs when its code runs. */
    public boolean dynamic;

    /** Whether the directive brings in names for the type checker only. */
    public boolean typeOnly;

    /** Whether the module resolves to a file of the repository. */
    public boolean inProject;
}
