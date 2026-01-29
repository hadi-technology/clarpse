package com.hadi.clarpse.listener.es6;

public class ES6ClassExport {

    /**
     * Fully qualified class name of this exported class.
     */
    private final String qualifiedClassName;
    /**
     * Is default export?
     */
    private final boolean isDefault;
    /**
     * What an import statement would have to reference this export as.
     */
    private final String namedExportValue;
    /**
     * The class name this export represents.
     */
    private String className;

    public ES6ClassExport(final String qualifiedClassName, final String namedExportValue, final boolean isDefault) {
        this.qualifiedClassName = qualifiedClassName;
        this.namedExportValue = namedExportValue;
        this.isDefault = isDefault;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public String className() {
        if (qualifiedClassName.contains(".")) {
            return qualifiedClassName.substring(qualifiedClassName.lastIndexOf(".") + 1);
        } else {
            return qualifiedClassName;
        }
    }

    public String asText() {
        return "Qualified Class Name: " + qualifiedClassName
                + "\nNamed ImportValue: " + namedExportValue
                + "\nIs Default? " + isDefault;
    }

    public String namedExportValue() {
        return namedExportValue;
    }

    public String qualifiedClassName() {
        return qualifiedClassName;
    }
}
