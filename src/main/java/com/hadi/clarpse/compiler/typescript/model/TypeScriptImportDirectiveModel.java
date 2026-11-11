package com.hadi.clarpse.compiler.typescript.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw TypeScript import directive emitted by the daemon: one {@code import} declaration,
 * {@code import x = require()} or literal {@code import()} / {@code require()} call.
 */
public class TypeScriptImportDirectiveModel {

    /** The module specifier as written. */
    public String target;

    /** {@code single} or {@code on_demand}. */
    public String kind;

    /** Whether the specifier is relative to the importing file. */
    public boolean relative;

    /** The members taken by name, as the module exports them ({@code default} for a default import). */
    public List<String> names = new ArrayList<>();

    /** The local names bound to {@link #names}. */
    public List<String> binds = new ArrayList<>();

    /** The local name the module as a whole is bound to, or null. */
    public String alias;

    /** Whether the directive binds or loads a module as a whole. */
    public boolean importsModule;

    /** Whether the import runs when its code runs. */
    public boolean dynamic;

    /** Whether the specifier resolves to a file of the repository. */
    public boolean inProject;
}
