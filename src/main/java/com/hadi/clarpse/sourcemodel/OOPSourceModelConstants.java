package com.hadi.clarpse.sourcemodel;

import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public final class OOPSourceModelConstants {

    static final Map<String, String> JAVA_COLLECTIONS = new HashMap<String, String>();

    static final Map<String, String> JAVA_ANNOTATIONS = new HashMap<String, String>();
    private static final Map<AccessModifiers, String> JAVA_ACCESS_MODIFIER_MAP = new HashMap<AccessModifiers, String>();
    private static final Map<ComponentType, String> COMPONENT_TYPES = new HashMap<ComponentType, String>();
    public static final String JAVA_DEFAULT_PKG = "java.lang.";
    static final Map<String, String> JAVA_DEFAULT_CLASSES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    static {
        JAVA_DEFAULT_CLASSES.put("Appendable", JAVA_DEFAULT_PKG + "Appendable");
        JAVA_DEFAULT_CLASSES.put("AutoCloseable", JAVA_DEFAULT_PKG + "AutoCloseable");
        JAVA_DEFAULT_CLASSES.put("CharSequence", JAVA_DEFAULT_PKG + "CharSequence");
        JAVA_DEFAULT_CLASSES.put("Cloneable", JAVA_DEFAULT_PKG + "Cloneable");
        JAVA_DEFAULT_CLASSES.put("Comparable", JAVA_DEFAULT_PKG + "Comparable");
        JAVA_DEFAULT_CLASSES.put("Iterable", JAVA_DEFAULT_PKG + "Iterable");
        JAVA_DEFAULT_CLASSES.put("Collection", JAVA_DEFAULT_PKG + "Collection");
        JAVA_DEFAULT_CLASSES.put("Readable", JAVA_DEFAULT_PKG + "Readable");
        JAVA_DEFAULT_CLASSES.put("ArrayList", "java.util." + "ArrayList");
        JAVA_DEFAULT_CLASSES.put("Runnable", JAVA_DEFAULT_PKG + "Runnable");
        JAVA_DEFAULT_CLASSES.put("UncaughtExceptionHandler", JAVA_DEFAULT_PKG + "UncaughtExceptionHandler");
        JAVA_DEFAULT_CLASSES.put("Boolean", JAVA_DEFAULT_PKG + "Boolean");
        JAVA_DEFAULT_CLASSES.put("boolean", JAVA_DEFAULT_PKG + "Boolean");
        JAVA_DEFAULT_CLASSES.put("Byte", JAVA_DEFAULT_PKG + "Byte");
        JAVA_DEFAULT_CLASSES.put("Character", JAVA_DEFAULT_PKG + "Character");
        JAVA_DEFAULT_CLASSES.put("Subset", JAVA_DEFAULT_PKG + "Character.Subset");
        JAVA_DEFAULT_CLASSES.put("UnicodeBlock", JAVA_DEFAULT_PKG + "Character.UnicodeBlock");
        JAVA_DEFAULT_CLASSES.put("Class", JAVA_DEFAULT_PKG + "Class");
        JAVA_DEFAULT_CLASSES.put("ClassLoader", JAVA_DEFAULT_PKG + "ClassLoader");
        JAVA_DEFAULT_CLASSES.put("ClassValue", JAVA_DEFAULT_PKG + "ClassValue");
        JAVA_DEFAULT_CLASSES.put("Compiler", JAVA_DEFAULT_PKG + "Compiler");
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
        JAVA_DEFAULT_CLASSES.put("Redirect", JAVA_DEFAULT_PKG + "ProcessBuilder.Redirect");
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
        JAVA_DEFAULT_CLASSES.put("UnicodeScript", JAVA_DEFAULT_PKG + "Character.UnicodeScript");
        JAVA_DEFAULT_CLASSES.put("Type", JAVA_DEFAULT_PKG + "ProcessBuilder.Redirect.Type");
        JAVA_DEFAULT_CLASSES.put("State", JAVA_DEFAULT_PKG + "Thread.State");
        JAVA_DEFAULT_CLASSES.put("ArithmeticException", JAVA_DEFAULT_PKG + "ArithmeticException");
        JAVA_DEFAULT_CLASSES.put("ArrayIndexOutOfBoundsException", JAVA_DEFAULT_PKG + "ArrayIndexOutOfBoundsException");
        JAVA_DEFAULT_CLASSES.put("ArrayStoreException", JAVA_DEFAULT_PKG + "ArrayStoreException");
        JAVA_DEFAULT_CLASSES.put("ClassCastException", JAVA_DEFAULT_PKG + "ClassCastException");
        JAVA_DEFAULT_CLASSES.put("ClassNotFoundException", JAVA_DEFAULT_PKG + "ClassNotFoundException");
        JAVA_DEFAULT_CLASSES.put("CloneNotSupportedException", JAVA_DEFAULT_PKG + "CloneNotSupportedException");
        JAVA_DEFAULT_CLASSES.put("EnumConstantNotPresentException",
                                 JAVA_DEFAULT_PKG + "EnumConstantNotPresentException  ");
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
        JAVA_DEFAULT_CLASSES.put("ArrayList", "java.util.ArrayList");
        JAVA_DEFAULT_CLASSES.put("Set", "java.util.Set");
        JAVA_DEFAULT_CLASSES.put("SortedSet", "java.util.SortedSet");
        JAVA_DEFAULT_CLASSES.put("Collection", "java.util.Collection");
        JAVA_DEFAULT_CLASSES.put("AbstractCollection", "java.util.AbstractCollection");
        JAVA_DEFAULT_CLASSES.put("AbstarctList", "java.util.AbstractList");
        JAVA_DEFAULT_CLASSES.put("AbstractQueue", "java.util.AbstractQueue");
        JAVA_DEFAULT_CLASSES.put("AbstractSequentialList", "java.util.AbstractSequentialList");
        JAVA_DEFAULT_CLASSES.put("AbstractSet", "java.util.AbstractSet");
        JAVA_DEFAULT_CLASSES.put("ArrayDeque", "java.util.ArrayDeque");
        JAVA_DEFAULT_CLASSES.put("AttributeList", "java.util.AttributeList");
        JAVA_DEFAULT_CLASSES.put("BeanContextServiceSupport", "java.util.BeanContextServiceSupport");
        JAVA_DEFAULT_CLASSES.put("BeanContextServicesSupport", "java.util.BeanContextServicesSupport");
        JAVA_DEFAULT_CLASSES.put("BeanContextSupport", "java.util.BeanContextSupport");
        JAVA_DEFAULT_CLASSES.put("ConcurrentLinkedDeque", "java.util.ConcurrentLinkedDeque");
        JAVA_DEFAULT_CLASSES.put("ConcurrentLinkedQueue", "java.util.ConcurrentLinkedQueue");
        JAVA_DEFAULT_CLASSES.put("ConcurrentSkipListSet", "java.util.ConcurrentSkipListSet");
        JAVA_DEFAULT_CLASSES.put("CopyOnWriteArrayList", "java.util.CopyOnWriteArrayList");
        JAVA_DEFAULT_CLASSES.put("CopyOnWriteArraySet", "java.util.CopyOnWriteArraySet");
        JAVA_DEFAULT_CLASSES.put("DelayQueue", "java.util.DelayQueue");
        JAVA_DEFAULT_CLASSES.put("EnumSet", "java.util.EnumSet");
        JAVA_DEFAULT_CLASSES.put("HashSet", "java.util.HashSet");
        JAVA_DEFAULT_CLASSES.put("JobStateReasons", "java.util.JobStateReasons");
        JAVA_DEFAULT_CLASSES.put("LinkedBlockingDeque", "java.util.LinkedBlockingDeque");
        JAVA_DEFAULT_CLASSES.put("LinkedBlockingQueue", "java.util.LinkedBlockingQueue");
        JAVA_DEFAULT_CLASSES.put("LinkedHashSet", "java.util.LinkedHashSet");
        JAVA_DEFAULT_CLASSES.put("LinkedList", "java.util.LinkedList");
        JAVA_DEFAULT_CLASSES.put("LinkedTransferQueue", "java.util.LinkedTransferQueue");
        JAVA_DEFAULT_CLASSES.put("PriorityBlockingQueue", "java.util.PriorityBlockingQueue");
        JAVA_DEFAULT_CLASSES.put("PriorityQueue", "java.util.PriorityQueue");
        JAVA_DEFAULT_CLASSES.put("RoleList", "java.util.RoleList");
        JAVA_DEFAULT_CLASSES.put("RoleUnresolvedList", "java.util.RoleUnresolvedList");
        JAVA_DEFAULT_CLASSES.put("Stack", "java.util.Stack");
        JAVA_DEFAULT_CLASSES.put("SynchronousQueue", "java.util.SynchronousQueue");
        JAVA_DEFAULT_CLASSES.put("TreeSet", "java.util.TreeSet");
        JAVA_DEFAULT_CLASSES.put("Vector", "java.util.Vector");
        JAVA_DEFAULT_CLASSES.put("RoleList", "java.util.RoleList");
        JAVA_DEFAULT_CLASSES.put("AbstractMap", "java.util.AbstractMap");
        JAVA_DEFAULT_CLASSES.put("Attributes", "java.util.Attributes");
        JAVA_DEFAULT_CLASSES.put("AuthProvider", "java.util.AuthProvider");
        JAVA_DEFAULT_CLASSES.put("ConcurrentHashMap", "java.util.ConcurrentHashMap");
        JAVA_DEFAULT_CLASSES.put("ConcurrentSkipListMap", "java.util.ConcurrentSkipListMap");
        JAVA_DEFAULT_CLASSES.put("EnumMap", "java.util.EnumMap");
        JAVA_DEFAULT_CLASSES.put("HashMap", "java.util.HashMap");
        JAVA_DEFAULT_CLASSES.put("Hashtable", "java.util.Hashtable");
        JAVA_DEFAULT_CLASSES.put("IdentityHashMap", "java.util.IdentityHashMap");
        JAVA_DEFAULT_CLASSES.put("LinkedHashMap", "java.util.LinkedHashMap");
        JAVA_DEFAULT_CLASSES.put("PrinterStateReasons", "java.util.PrinterStateReasons");
        JAVA_DEFAULT_CLASSES.put("Properties", "java.util.Properties");
        JAVA_DEFAULT_CLASSES.put("Provider", "java.util.Provider");
        JAVA_DEFAULT_CLASSES.put("RenderingHints", "java.util.RenderingHints");
        JAVA_DEFAULT_CLASSES.put("SimpleBindings", "java.util.SimpleBindings");
        JAVA_DEFAULT_CLASSES.put("TabularDataSupport", "java.util.TabularDataSupport");
        JAVA_DEFAULT_CLASSES.put("TreeMap", "java.util.TreeMap");
        JAVA_DEFAULT_CLASSES.put("UIDefaults", "java.util.UIDefaults");
        JAVA_DEFAULT_CLASSES.put("WeakHashMap", "java.util.WeakHashMap");
    }

    static {
        getJavaAccessModifierMap().put(AccessModifiers.PRIVATE, "private");
        getJavaAccessModifierMap().put(AccessModifiers.PROTECTED, "protected");
        getJavaAccessModifierMap().put(AccessModifiers.PUBLIC, "public");
        getJavaAccessModifierMap().put(AccessModifiers.VOLATILE, "volatile");
        getJavaAccessModifierMap().put(AccessModifiers.TRANSIENT, "transient");
        getJavaAccessModifierMap().put(AccessModifiers.SYNCHRONIZED, "synchronized");
        getJavaAccessModifierMap().put(AccessModifiers.STRICTFP, "strictfp");
        getJavaAccessModifierMap().put(AccessModifiers.STATIC, "static");
        getJavaAccessModifierMap().put(AccessModifiers.NATIVE, "native");
        getJavaAccessModifierMap().put(AccessModifiers.ABSTRACT, "abstract");
        getJavaAccessModifierMap().put(AccessModifiers.INTERFACE, "interface");
        getJavaAccessModifierMap().put(AccessModifiers.FINAL, "final");

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

    public static Map<AccessModifiers, String> getJavaAccessModifierMap() {
        return JAVA_ACCESS_MODIFIER_MAP;
    }

    public static Map<ComponentType, String> getJavaComponentTypes() {
        return COMPONENT_TYPES;
    }

    public enum AccessModifiers {
        FINAL(""), ABSTRACT(""), INTERFACE(""), NATIVE(""), PRIVATE("-"), PROTECTED("#"), PUBLIC("+"), STATIC(
                ""), STRICTFP(""), SYNCHRONIZED(""), TRANSIENT(""), NONE("~"), VOLATILE("");

        private String umlClassDigramSymbol = null;

        AccessModifiers(final String uMLClassDigramSymbol) {
            umlClassDigramSymbol = uMLClassDigramSymbol;
        }

        public String getUMLClassDigramSymbol() {
            return umlClassDigramSymbol;
        }
    }

    public enum TypeReferences {

        SIMPLE(SimpleTypeReference.class),
        EXTENSION(TypeExtensionReference.class),
        IMPLEMENTATION(TypeImplementationReference.class);

        private Class<? extends ComponentReference> matchingClass = null;

        TypeReferences(final Class<? extends ComponentReference> matchingClass) {
            this.matchingClass = matchingClass;
        }

        public Class<? extends ComponentReference> getMatchingClass() {
            return matchingClass;
        }
    }

    public enum ComponentType implements Serializable {

        CLASS("class", true, false, false), STRUCT("class", true, false, false), INTERFACE("interface", true, false,
                false), INTERFACE_CONSTANT("interface_constant", false, false, true), ENUM("enum", true, false,
                false), ANNOTATION("annotation", false, false, false), METHOD("method", false, true,
                false), CONSTRUCTOR("method", false, true, false), ENUM_CONSTANT("enum_constant", false,
                false,
                true), FIELD("field_variable", false, false, true), METHOD_PARAMETER_COMPONENT(
                "method_parameter", false, false,
                true), CONSTRUCTOR_PARAMETER_COMPONENT("constructor_parameter", false,
                false, true), LOCAL("local_variable", false, false, true);

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
