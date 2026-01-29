package com.hadi.clarpse.listener.es6;

public class ES6ClassImport {

    /**
     * Fully qualified class name of this exported class.
     */
    private final String qualifiedClassName;
    /**
     * Is default export?
     */
    private final boolean isDefault;
    /**
     * What an import statement would have to reference this import as.
     */
    private final String namedImportValue;

    public ES6ClassImport(final String qualifiedClassName, final String namedImportValue, final boolean isDefault) {
        this.qualifiedClassName = qualifiedClassName;
        this.namedImportValue = namedImportValue;
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
                + "\nNamed ImportValue: " + namedImportValue
                + "\nIs Default? " + isDefault;
    }

    String namedImportValue() {
        return namedImportValue;
    }

    String qualifiedClassName() {
        return qualifiedClassName;
    }
}
