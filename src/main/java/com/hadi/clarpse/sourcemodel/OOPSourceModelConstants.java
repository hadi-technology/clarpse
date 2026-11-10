package com.hadi.clarpse.sourcemodel;

import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Central constants and enums used by the language-agnostic source model.
 */
public final class OOPSourceModelConstants {

    static final Map<String, String> JAVA_COLLECTIONS = new HashMap<String, String>();

    static final Map<String, String> JAVA_ANNOTATIONS = new HashMap<String, String>();
    private static final Map<AccessModifiers, String> ACCESS_MODIFIER_MAP = new HashMap<AccessModifiers, String>();
    private static final Map<ComponentType, String> COMPONENT_TYPES = new HashMap<ComponentType, String>();
    public static final String JAVA_DEFAULT_PKG = "java.lang.";
    /**
     * Every public top-level type of {@code java.lang} in Java 21 (classes, interfaces, enums,
     * records, annotations and exceptions), by simple name, which every compilation unit imports on
     * demand; plus the primitive keywords, which map to their wrapper. No nested type belongs here,
     * and neither does a type of another package: a name listed is read as that type wherever nothing
     * closer in scope declares one. Names are case-sensitive.
     *
     * <p>The list is fixed in the source and is not read from the running JDK, so the model does not
     * depend on which JDK runs clarpse. Preview-only types ({@code ScopedValue},
     * {@code StringTemplate}) are not included, because a program cannot name them without
     * {@code --enable-preview}.
     */
    static final Map<String, String> JAVA_DEFAULT_CLASSES = new HashMap<>();
    private static final String[] JAVA_LANG_TYPES = {
            "AbstractMethodError", "Appendable", "ArithmeticException", "ArrayIndexOutOfBoundsException",
            "ArrayStoreException", "AssertionError", "AutoCloseable", "Boolean", "BootstrapMethodError",
            "Byte", "CharSequence", "Character", "Class", "ClassCastException", "ClassCircularityError",
            "ClassFormatError", "ClassLoader", "ClassNotFoundException", "ClassValue",
            "CloneNotSupportedException", "Cloneable", "Comparable", "Deprecated", "Double", "Enum",
            "EnumConstantNotPresentException", "Error", "Exception", "ExceptionInInitializerError", "Float",
            "FunctionalInterface", "IllegalAccessError", "IllegalAccessException", "IllegalArgumentException",
            "IllegalCallerException", "IllegalMonitorStateException", "IllegalStateException",
            "IllegalThreadStateException", "IncompatibleClassChangeError", "IndexOutOfBoundsException",
            "InheritableThreadLocal", "InstantiationError", "InstantiationException", "Integer",
            "InternalError", "InterruptedException", "Iterable", "LayerInstantiationException", "LinkageError",
            "Long", "MatchException", "Math", "Module", "ModuleLayer", "NegativeArraySizeException",
            "NoClassDefFoundError", "NoSuchFieldError", "NoSuchFieldException", "NoSuchMethodError",
            "NoSuchMethodException", "NullPointerException", "Number", "NumberFormatException", "Object",
            "OutOfMemoryError", "Override", "Package", "Process", "ProcessBuilder", "ProcessHandle",
            "Readable", "Record", "ReflectiveOperationException", "Runnable", "Runtime", "RuntimeException",
            "RuntimePermission", "SafeVarargs", "SecurityException", "SecurityManager", "Short",
            "StackOverflowError", "StackTraceElement", "StackWalker", "StrictMath", "String", "StringBuffer",
            "StringBuilder", "StringIndexOutOfBoundsException", "SuppressWarnings", "System", "Thread",
            "ThreadDeath", "ThreadGroup", "ThreadLocal", "Throwable", "TypeNotPresentException",
            "UnknownError", "UnsatisfiedLinkError", "UnsupportedClassVersionError",
            "UnsupportedOperationException", "VerifyError", "VirtualMachineError", "Void",
            "WrongThreadException"
    };
    static {
        for (final String name : JAVA_LANG_TYPES) {
            JAVA_DEFAULT_CLASSES.put(name, JAVA_DEFAULT_PKG + name);
        }
        JAVA_DEFAULT_CLASSES.put("boolean", JAVA_DEFAULT_PKG + "Boolean");
        JAVA_DEFAULT_CLASSES.put("double", JAVA_DEFAULT_PKG + "Double");
        JAVA_DEFAULT_CLASSES.put("float", JAVA_DEFAULT_PKG + "Float");
        JAVA_DEFAULT_CLASSES.put("int", JAVA_DEFAULT_PKG + "Integer");
        JAVA_DEFAULT_CLASSES.put("long", JAVA_DEFAULT_PKG + "Long");
        JAVA_DEFAULT_CLASSES.put("byte", JAVA_DEFAULT_PKG + "Byte");
    }

    static {
        // Java
        ACCESS_MODIFIER_MAP.put(AccessModifiers.PRIVATE, "private");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.PROTECTED, "protected");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.PUBLIC, "public");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.VOLATILE, "volatile");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.TRANSIENT, "transient");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.SYNCHRONIZED, "synchronized");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.STRICTFP, "strictfp");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.STATIC, "static");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.NATIVE, "native");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.ABSTRACT, "abstract");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.INTERFACE, "interface");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.FINAL, "final");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.DEFAULT, "default");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.SEALED, "sealed");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.NON_SEALED, "non-sealed");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.TRANSITIVE, "transitive");
        // C#
        ACCESS_MODIFIER_MAP.put(AccessModifiers.INTERNAL, "internal");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.PARTIAL, "partial");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.READONLY, "readonly");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.VIRTUAL, "virtual");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.OVERRIDE, "override");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.UNSAFE, "unsafe");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.REQUIRED, "required");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.REF, "ref");
        // Java
        ACCESS_MODIFIER_MAP.put(AccessModifiers.RECORD, "record");
        // C# and TypeScript
        ACCESS_MODIFIER_MAP.put(AccessModifiers.ASYNC, "async");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.CONST, "const");
        // TypeScript
        ACCESS_MODIFIER_MAP.put(AccessModifiers.EXPORT, "export");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.DECLARE, "declare");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.LET, "let");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.VAR, "var");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.GET, "get");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.SET, "set");
        // Kotlin
        ACCESS_MODIFIER_MAP.put(AccessModifiers.OPEN, "open");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.DATA, "data");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.INNER, "inner");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.COMPANION, "companion");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.VALUE, "value");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.LATEINIT, "lateinit");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.SUSPEND, "suspend");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.INLINE, "inline");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.OPERATOR, "operator");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.INFIX, "infix");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.TAILREC, "tailrec");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.EXTERNAL, "external");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.EXPECT, "expect");
        ACCESS_MODIFIER_MAP.put(AccessModifiers.ACTUAL, "actual");
    }

    static {
        getJavaComponentTypes().put(ComponentType.INTERFACE, "interface");
        getJavaComponentTypes().put(ComponentType.STRUCT, "struct");
        getJavaComponentTypes().put(ComponentType.ENUM, "enum");
        getJavaComponentTypes().put(ComponentType.ENUM_CONSTANT, "enumConstant");
        getJavaComponentTypes().put(ComponentType.INTERFACE_CONSTANT, "interfaceConstant");
        getJavaComponentTypes().put(ComponentType.ANNOTATION, "annotation");
        getJavaComponentTypes().put(ComponentType.METHOD, "method");
        getJavaComponentTypes().put(ComponentType.CONSTRUCTOR, "constructor");
        getJavaComponentTypes().put(ComponentType.FIELD, "field");
        getJavaComponentTypes().put(ComponentType.LOCAL, "localVar");
        getJavaComponentTypes().put(ComponentType.CLASS, "class");
    }

    private OOPSourceModelConstants() {
    }

    public static Map<String, String> getJavaDefaultClasses() {
        return JAVA_DEFAULT_CLASSES;
    }

    /**
     * Every modifier token the model accepts, across all supported languages, and the vocabulary
     * {@code Component} validates against - so that a C# {@code partial} or a TypeScript
     * {@code export} is as legal as a Java {@code final}.
     *
     * @return Mapping of modifier enum to its lower-case source token.
     */
    public static Map<AccessModifiers, String> getAccessModifierMap() {
        return ACCESS_MODIFIER_MAP;
    }

    /**
     * Resolves a modifier token to its enum, so callers can reach
     * {@link AccessModifiers#getUMLClassDigramSymbol()} without guessing at the enum name.
     *
     * @param modifier Modifier token, in any case.
     * @return The matching enum, or {@code null} if the token is not a supported modifier.
     */
    public static AccessModifiers accessModifier(final String modifier) {
        if (modifier == null) {
            return null;
        }
        final String normalized = modifier.toLowerCase(Locale.ROOT).trim();
        for (final Map.Entry<AccessModifiers, String> entry : ACCESS_MODIFIER_MAP.entrySet()) {
            if (entry.getValue().equals(normalized)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static Map<ComponentType, String> getJavaComponentTypes() {
        return COMPONENT_TYPES;
    }

    /**
     * Supported access/modifier tokens with UML visibility symbols.
     */
    public enum AccessModifiers {
        FINAL(""), ABSTRACT(""), INTERFACE(""), NATIVE(""), PRIVATE("-"), PROTECTED("#"), PUBLIC("+"), STATIC(
                ""), STRICTFP(""), SYNCHRONIZED(""), TRANSIENT(""), NONE("~"), VOLATILE(""),
        DEFAULT(""), SEALED(""), NON_SEALED(""), TRANSITIVE(""),
        INTERNAL("~"), PARTIAL(""), READONLY(""), VIRTUAL(""), OVERRIDE(""), UNSAFE(""),
        ASYNC(""), CONST(""), EXPORT(""), DECLARE(""), LET(""), VAR(""), GET(""), SET(""),
        OPEN(""), DATA(""), INNER(""), COMPANION(""), VALUE(""), LATEINIT(""), SUSPEND(""), INLINE(""),
        OPERATOR(""), INFIX(""), TAILREC(""), EXTERNAL(""), EXPECT(""), ACTUAL(""), REQUIRED(""), RECORD(""), REF("");

        private String umlClassDigramSymbol = null;

        AccessModifiers(final String uMLClassDigramSymbol) {
            umlClassDigramSymbol = uMLClassDigramSymbol;
        }

        public String getUMLClassDigramSymbol() {
            return umlClassDigramSymbol;
        }
    }

    /**
     * Categories of component references captured by the model.
     */
    public enum TypeReferences {

        SIMPLE(SimpleTypeReference.class),
        EXTENSION(TypeExtensionReference.class),
        IMPLEMENTATION(TypeImplementationReference.class),
        ANNOTATION(AnnotationReference.class);

        private Class<? extends ComponentReference> matchingClass = null;

        TypeReferences(final Class<? extends ComponentReference> matchingClass) {
            this.matchingClass = matchingClass;
        }

        public Class<? extends ComponentReference> getMatchingClass() {
            return matchingClass;
        }
    }

    /**
     * Normalized component kinds used across all language implementations.
     */
    public enum ComponentType implements Serializable {

        CLASS("class", true, false, false), STRUCT("class", true, false, false), INTERFACE("interface", true, false,
                false), INTERFACE_CONSTANT("interface_constant", false, false, true), ENUM("enum", true, false,
                false), ANNOTATION("annotation", true, false, false), METHOD("method", false, true,
                false), FUNCTION("function", false, true, false), CONSTRUCTOR("method", false, true, false),
                ENUM_CONSTANT("enum_constant", false, false, true), FIELD("field_variable", false, false, true),
                MODULE_FIELD("module_field", false, false, true),
                // A name given to a type expression (TypeScript `type X = ...`). It names a type but
                // declares no members of its own, so it is not a base component.
                TYPE_ALIAS("type_alias", false, false, false),
                // A TypeScript `namespace N { }` block: a named scope that holds the block's exported
                // declarations as its children. It is not a type.
                NAMESPACE("namespace", false, false, false),
                METHOD_PARAMETER_COMPONENT("method_parameter", false, false, true),
                CONSTRUCTOR_PARAMETER_COMPONENT("constructor_parameter", false, false, true),
                LOCAL("local_variable", false, false, true);

        private final boolean isBaseComponent;
        private final boolean isMethodComponent;
        private final boolean isVariableComponent;
        private final String value;

        ComponentType(final String value, final boolean isBaseComponent, final boolean isMethodComponent,
                      final boolean isVariableComponent) {
            this.isBaseComponent = isBaseComponent;
            this.isMethodComponent = isMethodComponent;
            this.isVariableComponent = isVariableComponent;
            this.value = value;
        }

        public boolean isBaseComponent() {
            return isBaseComponent;
        }

        public boolean isMethodComponent() {
            return isMethodComponent;
        }

        public boolean isVariableComponent() {
            return isVariableComponent;
        }

        public String getValue() {
            return value;
        }
    }
}
