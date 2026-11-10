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
     * The top-level types of {@code java.lang}, by simple name, which every compilation unit imports
     * on demand; plus the primitive keywords, which map to their wrapper. Nothing outside
     * {@code java.lang} and no nested type belongs here: a name listed is read as that type wherever
     * nothing closer in scope declares one, so a name that merely sounds standard would be bound
     * wrongly. Names are case-sensitive.
     */
    static final Map<String, String> JAVA_DEFAULT_CLASSES = new HashMap<>();
    static {
        JAVA_DEFAULT_CLASSES.put("Appendable", JAVA_DEFAULT_PKG + "Appendable");
        JAVA_DEFAULT_CLASSES.put("AutoCloseable", JAVA_DEFAULT_PKG + "AutoCloseable");
        JAVA_DEFAULT_CLASSES.put("CharSequence", JAVA_DEFAULT_PKG + "CharSequence");
        JAVA_DEFAULT_CLASSES.put("Cloneable", JAVA_DEFAULT_PKG + "Cloneable");
        JAVA_DEFAULT_CLASSES.put("Comparable", JAVA_DEFAULT_PKG + "Comparable");
        JAVA_DEFAULT_CLASSES.put("Iterable", JAVA_DEFAULT_PKG + "Iterable");
        JAVA_DEFAULT_CLASSES.put("Readable", JAVA_DEFAULT_PKG + "Readable");
        JAVA_DEFAULT_CLASSES.put("Runnable", JAVA_DEFAULT_PKG + "Runnable");
        JAVA_DEFAULT_CLASSES.put("Boolean", JAVA_DEFAULT_PKG + "Boolean");
        JAVA_DEFAULT_CLASSES.put("boolean", JAVA_DEFAULT_PKG + "Boolean");
        JAVA_DEFAULT_CLASSES.put("Byte", JAVA_DEFAULT_PKG + "Byte");
        JAVA_DEFAULT_CLASSES.put("Character", JAVA_DEFAULT_PKG + "Character");
        JAVA_DEFAULT_CLASSES.put("Class", JAVA_DEFAULT_PKG + "Class");
        JAVA_DEFAULT_CLASSES.put("ClassLoader", JAVA_DEFAULT_PKG + "ClassLoader");
        JAVA_DEFAULT_CLASSES.put("ClassValue", JAVA_DEFAULT_PKG + "ClassValue");
        JAVA_DEFAULT_CLASSES.put("Double", JAVA_DEFAULT_PKG + "Double");
        JAVA_DEFAULT_CLASSES.put("double", JAVA_DEFAULT_PKG + "Double");
        JAVA_DEFAULT_CLASSES.put("float", JAVA_DEFAULT_PKG + "Float");
        JAVA_DEFAULT_CLASSES.put("int", JAVA_DEFAULT_PKG + "Integer");
        JAVA_DEFAULT_CLASSES.put("long", JAVA_DEFAULT_PKG + "Long");
        JAVA_DEFAULT_CLASSES.put("byte", JAVA_DEFAULT_PKG + "Byte");
        JAVA_DEFAULT_CLASSES.put("Enum", JAVA_DEFAULT_PKG + "Enum");
        JAVA_DEFAULT_CLASSES.put("Float", JAVA_DEFAULT_PKG + "Float");
        JAVA_DEFAULT_CLASSES.put("InheritableThreadLocal", JAVA_DEFAULT_PKG + "InheritableThreadLocal");
        JAVA_DEFAULT_CLASSES.put("Integer", JAVA_DEFAULT_PKG + "Integer");
        JAVA_DEFAULT_CLASSES.put("Long", JAVA_DEFAULT_PKG + "Long");
        JAVA_DEFAULT_CLASSES.put("Math", JAVA_DEFAULT_PKG + "Math");
        JAVA_DEFAULT_CLASSES.put("Number", JAVA_DEFAULT_PKG + "Number");
        JAVA_DEFAULT_CLASSES.put("Object", JAVA_DEFAULT_PKG + "Object");
        JAVA_DEFAULT_CLASSES.put("Package", JAVA_DEFAULT_PKG + "Package");
        JAVA_DEFAULT_CLASSES.put("Process", JAVA_DEFAULT_PKG + "Process");
        JAVA_DEFAULT_CLASSES.put("ProcessBuilder", JAVA_DEFAULT_PKG + "ProcessBuilder");
        JAVA_DEFAULT_CLASSES.put("Runtime", JAVA_DEFAULT_PKG + "Runtime");
        JAVA_DEFAULT_CLASSES.put("RuntimePermission", JAVA_DEFAULT_PKG + "RuntimePermission");
        JAVA_DEFAULT_CLASSES.put("SecurityManager", JAVA_DEFAULT_PKG + "SecurityManager");
        JAVA_DEFAULT_CLASSES.put("Short", JAVA_DEFAULT_PKG + "Short");
        JAVA_DEFAULT_CLASSES.put("StackTraceElement", JAVA_DEFAULT_PKG + "StackTraceElement");
        JAVA_DEFAULT_CLASSES.put("StrictMath", JAVA_DEFAULT_PKG + "StrictMath");
        JAVA_DEFAULT_CLASSES.put("String", JAVA_DEFAULT_PKG + "String");
        JAVA_DEFAULT_CLASSES.put("StringBuffer", JAVA_DEFAULT_PKG + "StringBuffer");
        JAVA_DEFAULT_CLASSES.put("StringBuilder", JAVA_DEFAULT_PKG + "StringBuilder");
        JAVA_DEFAULT_CLASSES.put("System", JAVA_DEFAULT_PKG + "System");
        JAVA_DEFAULT_CLASSES.put("Thread", JAVA_DEFAULT_PKG + "Thread");
        JAVA_DEFAULT_CLASSES.put("ThreadGroup", JAVA_DEFAULT_PKG + "ThreadGroup");
        JAVA_DEFAULT_CLASSES.put("ThreadLocal", JAVA_DEFAULT_PKG + "ThreadLocal");
        JAVA_DEFAULT_CLASSES.put("Throwable", JAVA_DEFAULT_PKG + "Throwable");
        JAVA_DEFAULT_CLASSES.put("ArithmeticException", JAVA_DEFAULT_PKG + "ArithmeticException");
        JAVA_DEFAULT_CLASSES.put("ArrayIndexOutOfBoundsException", JAVA_DEFAULT_PKG + "ArrayIndexOutOfBoundsException");
        JAVA_DEFAULT_CLASSES.put("ArrayStoreException", JAVA_DEFAULT_PKG + "ArrayStoreException");
        JAVA_DEFAULT_CLASSES.put("ClassCastException", JAVA_DEFAULT_PKG + "ClassCastException");
        JAVA_DEFAULT_CLASSES.put("ClassNotFoundException", JAVA_DEFAULT_PKG + "ClassNotFoundException");
        JAVA_DEFAULT_CLASSES.put("CloneNotSupportedException", JAVA_DEFAULT_PKG + "CloneNotSupportedException");
        JAVA_DEFAULT_CLASSES.put("EnumConstantNotPresentException",
                                 JAVA_DEFAULT_PKG + "EnumConstantNotPresentException");
        JAVA_DEFAULT_CLASSES.put("Exception", JAVA_DEFAULT_PKG + "Exception");
        JAVA_DEFAULT_CLASSES.put("IllegalAccessException", JAVA_DEFAULT_PKG + "IllegalAccessException");
        JAVA_DEFAULT_CLASSES.put("IllegalArgumentException", JAVA_DEFAULT_PKG + "IllegalArgumentException");
        JAVA_DEFAULT_CLASSES.put("IllegalMonitorStateException", JAVA_DEFAULT_PKG + "IllegalMonitorStateException");
        JAVA_DEFAULT_CLASSES.put("IllegalStateException", JAVA_DEFAULT_PKG + "IllegalStateException");
        JAVA_DEFAULT_CLASSES.put("IllegalThreadStateException", JAVA_DEFAULT_PKG + "IllegalThreadStateException");
        JAVA_DEFAULT_CLASSES.put("IndexOutOfBoundsException", JAVA_DEFAULT_PKG + "IndexOutOfBoundsException");
        JAVA_DEFAULT_CLASSES.put("InstantiationException", JAVA_DEFAULT_PKG + "InstantiationException");
        JAVA_DEFAULT_CLASSES.put("InterruptedException", JAVA_DEFAULT_PKG + "InterruptedException");
        JAVA_DEFAULT_CLASSES.put("NegativeArraySizeException", JAVA_DEFAULT_PKG + "NegativeArraySizeException");
        JAVA_DEFAULT_CLASSES.put("NoSuchFieldException", JAVA_DEFAULT_PKG + "NoSuchFieldException");
        JAVA_DEFAULT_CLASSES.put("NoSuchMethodException", JAVA_DEFAULT_PKG + "NoSuchMethodException");
        JAVA_DEFAULT_CLASSES.put("NullPointerException", JAVA_DEFAULT_PKG + "NullPointerException");
        JAVA_DEFAULT_CLASSES.put("NumberFormatException", JAVA_DEFAULT_PKG + "NumberFormatException");
        JAVA_DEFAULT_CLASSES.put("ReflectiveOperationException", JAVA_DEFAULT_PKG + "ReflectiveOperationException");
        JAVA_DEFAULT_CLASSES.put("RuntimeException", JAVA_DEFAULT_PKG + "RuntimeException");
        JAVA_DEFAULT_CLASSES.put("SecurityException", JAVA_DEFAULT_PKG + "SecurityException");
        JAVA_DEFAULT_CLASSES.put("StringIndexOutOfBoundsException",
                                 JAVA_DEFAULT_PKG + "StringIndexOutOfBoundsException");
        JAVA_DEFAULT_CLASSES.put("TypeNotPresentException", JAVA_DEFAULT_PKG + "TypeNotPresentException");
        JAVA_DEFAULT_CLASSES.put("UnsupportedOperationException", JAVA_DEFAULT_PKG + "UnsupportedOperationException");
        JAVA_DEFAULT_CLASSES.put("AbstractMethodError", JAVA_DEFAULT_PKG + "AbstractMethodError");
        JAVA_DEFAULT_CLASSES.put("AssertionError", JAVA_DEFAULT_PKG + "AssertionError");
        JAVA_DEFAULT_CLASSES.put("BootstrapMethodError", JAVA_DEFAULT_PKG + "BootstrapMethodError");
        JAVA_DEFAULT_CLASSES.put("ClassCircularityError", JAVA_DEFAULT_PKG + "ClassCircularityError");
        JAVA_DEFAULT_CLASSES.put("ClassFormatError", JAVA_DEFAULT_PKG + "ClassFormatError");
        JAVA_DEFAULT_CLASSES.put("Error", JAVA_DEFAULT_PKG + "Error");
        JAVA_DEFAULT_CLASSES.put("ExceptionInInitializerError", JAVA_DEFAULT_PKG + "ExceptionInInitializerError");
        JAVA_DEFAULT_CLASSES.put("IllegalAccessError", JAVA_DEFAULT_PKG + "IllegalAccessError");
        JAVA_DEFAULT_CLASSES.put("IncompatibleClassChangeError", JAVA_DEFAULT_PKG + "IncompatibleClassChangeError");
        JAVA_DEFAULT_CLASSES.put("InstantiationError", JAVA_DEFAULT_PKG + "InstantiationError");
        JAVA_DEFAULT_CLASSES.put("InternalError", JAVA_DEFAULT_PKG + "InternalError");
        JAVA_DEFAULT_CLASSES.put("LinkageError", JAVA_DEFAULT_PKG + "LinkageError");
        JAVA_DEFAULT_CLASSES.put("NoClassDefFoundError", JAVA_DEFAULT_PKG + "NoClassDefFoundError");
        JAVA_DEFAULT_CLASSES.put("NoSuchFieldError", JAVA_DEFAULT_PKG + "NoSuchFieldError");
        JAVA_DEFAULT_CLASSES.put("NoSuchMethodError", JAVA_DEFAULT_PKG + "NoSuchMethodError");
        JAVA_DEFAULT_CLASSES.put("OutOfMemoryError", JAVA_DEFAULT_PKG + "OutOfMemoryError");
        JAVA_DEFAULT_CLASSES.put("StackOverflowError", JAVA_DEFAULT_PKG + "StackOverflowError");
        JAVA_DEFAULT_CLASSES.put("ThreadDeath", JAVA_DEFAULT_PKG + "ThreadDeath");
        JAVA_DEFAULT_CLASSES.put("UnknownError", JAVA_DEFAULT_PKG + "UnknownError");
        JAVA_DEFAULT_CLASSES.put("UnsatisfiedLinkError", JAVA_DEFAULT_PKG + "UnsatisfiedLinkError");
        JAVA_DEFAULT_CLASSES.put("UnsupportedClassVersionError", JAVA_DEFAULT_PKG + "UnsupportedClassVersionError");
        JAVA_DEFAULT_CLASSES.put("InternalError", JAVA_DEFAULT_PKG + "InternalError");
        JAVA_DEFAULT_CLASSES.put("VerifyError", JAVA_DEFAULT_PKG + "VerifyError");
        JAVA_DEFAULT_CLASSES.put("VirtualMachineError", JAVA_DEFAULT_PKG + "VirtualMachineError");
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
