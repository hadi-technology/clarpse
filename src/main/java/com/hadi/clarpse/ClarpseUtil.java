package com.hadi.clarpse;

import com.hadi.clarpse.sourcemodel.Component;

import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class ClarpseUtil {

    public static Component getParentBaseComponent(Component cmp, final Map<String, Component> map) {
        String currParentClassName = cmp.parentUniqueName();
        Component parent = map.get(currParentClassName);
        while (parent != null && !parent.componentType().isBaseComponent()) {
            currParentClassName = parent.parentUniqueName();
            parent = map.get(currParentClassName);
        }
        return parent;
    }

    public static <T> Collector<T, ?, T> toSingleton() {
        return Collectors.collectingAndThen(
                Collectors.toList(),
                list -> {
                    if (list.size() != 1) {
                        throw new IllegalStateException();
                    }
                    return list.get(0);
                }
        );
    }
}
