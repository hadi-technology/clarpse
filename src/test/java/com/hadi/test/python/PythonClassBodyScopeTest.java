package com.hadi.test.python;

import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

import static com.hadi.test.python.PythonInlineProject.lines;

/**
 * The value of a class attribute is read in the class body's scope: a name the class body binds -- a
 * nested class, a method, another attribute -- is that binding, never an import or a module-level
 * name spelled the same.
 */
public class PythonClassBodyScopeTest {

    private static PythonInlineProject project() throws Exception {
        return PythonInlineProject.compile(
                "/app/__init__.py", "",
                "/app/settings.py", lines(
                        "class Defaults:",
                        "    size = 1",
                        "class Status:",
                        "    ACTIVE = 1"),
                "/app/widget.py", lines(
                        "from enum import Enum",
                        "from app.settings import Defaults, Status",
                        "class Widget:",
                        "    class Defaults:",
                        "        size = 3",
                        "    default = Defaults()",
                        "    size = Defaults.size",
                        "class Order:",
                        "    class Status(Enum):",
                        "        ACTIVE = 1",
                        "    current = Status.ACTIVE",
                        "    made = Status('x')",
                        "class Plain:",
                        "    default = Defaults()",
                        "    current = Status.ACTIVE",
                        "class Helper:",
                        "    def Defaults(self):",
                        "        return None",
                        "    default = Defaults()"));
    }

    @Test
    public void aNestedClassOfTheNameHidesTheImportedOne() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertEquals(Set.of(), project.references("app.widget.Widget.default"));
        Assert.assertEquals(Set.of(), project.references("app.widget.Widget.size"));
        Assert.assertFalse(project.references("app.widget.Widget").contains("app.settings.Defaults"));
    }

    @Test
    public void aNestedEnumHidesTheImportedClassForAMemberRead() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertEquals(Set.of(), project.references("app.widget.Order.current"));
        Assert.assertEquals(Set.of(), project.references("app.widget.Order.made"));
    }

    @Test
    public void aMethodOfTheNameHidesTheImportedOne() throws Exception {
        Assert.assertEquals(Set.of(), project().references("app.widget.Helper.default"));
    }

    @Test
    public void aClassThatBindsNoSuchNameStillReferencesTheImportedOne() throws Exception {
        final PythonInlineProject project = project();
        Assert.assertEquals(Set.of("app.settings.Defaults"), project.references("app.widget.Plain.default"));
        Assert.assertEquals(Set.of("app.settings.Status"), project.references("app.widget.Plain.current"));
    }
}
