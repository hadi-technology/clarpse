package com.hadi.clarpse.sourcemodel;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

/**
 * Represents the metadata of a package in a code base.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class Package implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String packageName;
    private final String packagePath;
    private final String ellipsisSeparatedPkg;
    /** Lazily computed; transient so that adding it cannot change this class's serialized form. */
    @JsonIgnore
    private transient int hash;

    public Package(final String packageName, final String packagePath) {
        this.packageName = packageName;
        this.packagePath = packagePath;
        this.ellipsisSeparatedPkg = StringUtils.strip(packagePath.replace("/", "."), ".");
    }

    @Override
    public String toString() {
        return this.packageName + ": " + this.packagePath;
    }

    public String name() {
        return packageName;
    }

    public String path() {
        return packagePath;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Package ref = (Package) obj;
        return this.packageName.equals(ref.packageName) && this.packagePath.equals(ref.packagePath);
    }

    /**
     * Computed once at construction, from the two fields that define equality.
     *
     * <p>It was built by concatenating them on every call, which allocated a string per hash. That is
     * only a detail until packages are pooled by value, at which point every component inserted into
     * a model hashes its package.
     *
     * @return This package's hash.
     */
    @Override
    public int hashCode() {
        int cached = this.hash;
        if (cached == 0) {
            cached = 31 * this.packageName.hashCode() + this.packagePath.hashCode();
            this.hash = cached;
        }
        return cached;
    }

    public String ellipsisSeparatedPkgPath() {
        return this.ellipsisSeparatedPkg;
    }
}
