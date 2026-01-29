package com.hadi.clarpse.sourcemodel;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;

/**
 * Represents the metadata of a package in a code base.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class Package implements Serializable {


    private final String packageName;
    private final String packagePath;
    private final String ellipsisSeparatedPkg;

    public Package(final String packageName, final String packagePath) {
        this.packageName = packageName;
        this.packagePath = packagePath;
        this.ellipsisSeparatedPkg = StringUtils.strip(packagePath.replaceAll("/", "."), ".");
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

    @Override
    public int hashCode() {
        return (this.packageName + ":" + this.packagePath).hashCode();
    }

    public String ellipsisSeparatedPkgPath() {
        return this.ellipsisSeparatedPkg;
    }
}
