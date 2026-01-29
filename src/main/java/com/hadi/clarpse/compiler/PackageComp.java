package com.hadi.clarpse.compiler;

import com.hadi.clarpse.sourcemodel.Package;

import java.io.Serializable;
import java.util.Comparator;

public class PackageComp implements Comparator<Package>, Serializable {
    @Override
    public int compare(Package o1, Package o2) {
        if (o1.equals(o2)) {
            return 0;
        } else if (o2.path().length() <= o1.path().length()) {
            return -1;
        } else {
            return 1;
        }
    }
}
