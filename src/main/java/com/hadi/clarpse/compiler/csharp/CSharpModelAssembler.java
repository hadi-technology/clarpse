package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.listener.ParseUtil;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants;
import com.hadi.clarpse.sourcemodel.Package;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

final class CSharpModelAssembler {

    private static final Map<String, String> BUILTIN_TYPES = Map.ofEntries(
            Map.entry("string", "System.String"),
            Map.entry("int", "System.Int32"),
            Map.entry("long", "System.Int64"),
            Map.entry("short", "System.Int16"),
            Map.entry("byte", "System.Byte"),
            Map.entry("bool", "System.Boolean"),
            Map.entry("double", "System.Double"),
            Map.entry("float", "System.Single"),
            Map.entry("decimal", "System.Decimal"),
            Map.entry("char", "System.Char"),
            Map.entry("object", "System.Object"),
            Map.entry("void", "System.Void")
    );

    private CSharpModelAssembler() {
    }

    static OOPSourceCodeModel buildModel(final Collection<CSharpModel.CSharpFileModel> fileModels) {
        applyGlobalUsings(fileModels);
        final List<CSharpModel.CSharpTypeModel> mergedTypes = mergePartials(fileModels);
        final TypeIndex typeIndex = new TypeIndex(mergedTypes);
        final OOPSourceCodeModel model = new OOPSourceCodeModel();
        final Stack<Component> stack = new Stack<>();
        mergedTypes.sort(Comparator.comparing(type -> type.uniqueName));
        for (final CSharpModel.CSharpTypeModel typeModel : mergedTypes) {
            if (!typeModel.componentName.contains(".")) {
                insertType(typeModel, typeIndex, model, stack);
            }
        }
        return model;
    }

    private static List<CSharpModel.CSharpTypeModel> mergePartials(final Collection<CSharpModel.CSharpFileModel> fileModels) {
        final List<CSharpModel.CSharpTypeModel> flattened = new ArrayList<>();
        for (final CSharpModel.CSharpFileModel fileModel : fileModels) {
            for (final CSharpModel.CSharpTypeModel typeModel : fileModel.types) {
                assignIdentity(typeModel, "");
                flattened.add(typeModel);
                flattened.addAll(flattenNested(typeModel));
            }
        }
        final Map<String, CSharpModel.CSharpTypeModel> merged = new LinkedHashMap<>();
        for (final CSharpModel.CSharpTypeModel typeModel : flattened) {
            final String key = typeKey(typeModel);
            final CSharpModel.CSharpTypeModel existing = merged.get(key);
            if (existing == null) {
                merged.put(key, typeModel);
                continue;
            }
            existing.partial = existing.partial || typeModel.partial;
            existing.baseTypes.addAll(typeModel.baseTypes);
            existing.members.addAll(typeModel.members);
            existing.nestedTypes.addAll(typeModel.nestedTypes);
            existing.modifiers = mergeStrings(existing.modifiers, typeModel.modifiers);
            existing.imports.addAll(typeModel.imports);
            if ((existing.comment == null || existing.comment.isEmpty()) && typeModel.comment != null) {
                existing.comment = typeModel.comment;
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static void applyGlobalUsings(final Collection<CSharpModel.CSharpFileModel> fileModels) {
        final Set<String> globalImports = new LinkedHashSet<>();
        final Map<String, String> globalAliases = new LinkedHashMap<>();
        for (final CSharpModel.CSharpFileModel fileModel : fileModels) {
            for (final CSharpModel.CSharpUsingModel usingModel : fileModel.usings) {
                if (!usingModel.globalImport || usingModel.target == null || usingModel.target.isEmpty()) {
                    continue;
                }
                globalImports.add(usingModel.target);
                if (usingModel.aliasImport && usingModel.alias != null && !usingModel.alias.isEmpty()) {
                    globalAliases.put(usingModel.alias, usingModel.target);
                }
            }
        }
        if (globalImports.isEmpty() && globalAliases.isEmpty()) {
            return;
        }
        for (final CSharpModel.CSharpFileModel fileModel : fileModels) {
            for (final CSharpModel.CSharpTypeModel typeModel : fileModel.types) {
                applyGlobalUsings(typeModel, globalImports, globalAliases);
            }
        }
    }

    private static void applyGlobalUsings(final CSharpModel.CSharpTypeModel typeModel,
                                          final Set<String> globalImports,
                                          final Map<String, String> globalAliases) {
        typeModel.imports.addAll(globalImports);
        globalAliases.forEach(typeModel.usingAliases::putIfAbsent);
        for (final CSharpModel.CSharpMemberModel member : typeModel.members) {
            member.imports.addAll(globalImports);
        }
        for (final CSharpModel.CSharpTypeModel nested : typeModel.nestedTypes) {
            applyGlobalUsings(nested, globalImports, globalAliases);
        }
    }

    private static List<CSharpModel.CSharpTypeModel> flattenNested(final CSharpModel.CSharpTypeModel typeModel) {
        final List<CSharpModel.CSharpTypeModel> result = new ArrayList<>();
        for (final CSharpModel.CSharpTypeModel nested : typeModel.nestedTypes) {
            assignIdentity(nested, typeModel.componentName);
            result.add(nested);
            result.addAll(flattenNested(nested));
        }
        return result;
    }

    private static void assignIdentity(final CSharpModel.CSharpTypeModel typeModel, final String parentComponentName) {
        if (parentComponentName == null || parentComponentName.isEmpty()) {
            typeModel.componentName = typeModel.name;
        } else {
            typeModel.componentName = parentComponentName + "." + typeModel.name;
        }
        if (typeModel.namespaceName == null || typeModel.namespaceName.isEmpty()) {
            typeModel.uniqueName = typeModel.componentName;
        } else {
            typeModel.uniqueName = typeModel.namespaceName + "." + typeModel.componentName;
        }
        for (final CSharpModel.CSharpTypeModel nested : typeModel.nestedTypes) {
            assignIdentity(nested, typeModel.componentName);
        }
    }

    private static String typeKey(final CSharpModel.CSharpTypeModel typeModel) {
        return typeModel.kind + "|" + typeModel.uniqueName;
    }

    private static void insertType(final CSharpModel.CSharpTypeModel typeModel,
                                   final TypeIndex typeIndex,
                                   final OOPSourceCodeModel model,
                                   final Stack<Component> stack) {
        final Component typeComponent = buildTypeComponent(typeModel);
        ParseUtil.pointParentsToGivenChild(typeComponent, stack);
        stack.push(typeComponent);
        for (final String baseType : typeModel.baseTypes) {
            for (final String rawToken : CSharpFileParser.extractTypeTokens(baseType)) {
                final String resolved = typeIndex.resolveType(rawToken, typeModel, null);
                if (resolved == null || resolved.equals(typeComponent.uniqueName())) {
                    continue;
                }
                if ("interface".equals(typeModel.kind) || "class".equals(typeModel.kind)
                        || "record".equals(typeModel.kind) || "recordStruct".equals(typeModel.kind)
                        || "struct".equals(typeModel.kind)) {
                    final ComponentReference ref;
                    if (isInterfaceTarget(rawToken, resolved, typeIndex)) {
                        ref = new TypeImplementationReference(resolved);
                    } else {
                        ref = new TypeExtensionReference(resolved);
                    }
                    typeComponent.insertCmpRef(ref);
                }
            }
        }
        final List<CSharpModel.CSharpTypeModel> nestedTypes = new ArrayList<>(typeModel.nestedTypes);
        nestedTypes.sort(Comparator.comparing(type -> type.uniqueName));
        for (final CSharpModel.CSharpTypeModel nested : nestedTypes) {
            insertType(nested, typeIndex, model, stack);
        }
        final List<CSharpModel.CSharpMemberModel> members = new ArrayList<>(typeModel.members);
        members.sort(Comparator.comparingInt(member -> member.startOffset));
        for (final CSharpModel.CSharpMemberModel member : members) {
            insertMember(member, typeModel, typeIndex, model, stack);
        }
        if (typeComponent.componentType().isMethodComponent() && typeModel.codeFragment != null) {
            typeComponent.setCodeHash(typeModel.codeFragment.hashCode());
        }
        model.insertComponent(typeComponent);
        stack.pop();
        ParseUtil.copyRefsToParents(typeComponent, stack);
    }

    private static Component buildTypeComponent(final CSharpModel.CSharpTypeModel typeModel) {
        final Component component = new Component();
        component.setPkg(resolvePackage(typeModel.namespaceName));
        component.setComponentName(typeModel.componentName);
        component.setComponentType(mapTypeComponent(typeModel.kind));
        component.setModule(typeModel.moduleName);
        component.setName(typeModel.name);
        component.setSourceFilePath(typeModel.sourcePath);
        if (typeModel.comment == null) {
            component.setComment("");
        } else {
            component.setComment(typeModel.comment);
        }
        component.setImports(typeModel.imports);
        component.setAccessModifiers(typeModel.modifiers);
        if (typeModel.codeFragment != null && !typeModel.codeFragment.isEmpty()) {
            component.setCodeFragment(typeModel.codeFragment);
            component.setCodeHash(typeModel.codeFragment.hashCode());
        }
        return component;
    }

    private static void insertMember(final CSharpModel.CSharpMemberModel memberModel,
                                     final CSharpModel.CSharpTypeModel ownerType,
                                     final TypeIndex typeIndex,
                                     final OOPSourceCodeModel model,
                                     final Stack<Component> stack) {
        final Component component = buildMemberComponent(memberModel, ownerType);
        if (component == null) {
            return;
        }
        ParseUtil.pointParentsToGivenChild(component, stack);
        stack.push(component);
        if (component.componentType().isMethodComponent()) {
            for (final CSharpModel.CSharpParameterModel parameter : memberModel.parameters) {
                final Component paramComponent = buildParameterComponent(parameter, memberModel, ownerType, component);
                ParseUtil.pointParentsToGivenChild(paramComponent, stack);
                attachTypeReferences(paramComponent, singletonType(parameter.declaredType),
                        typeIndex, ownerType, memberModel, false);
                model.insertComponent(paramComponent);
                ParseUtil.copyRefsToParents(paramComponent, stack);
            }
            for (final CSharpModel.CSharpMemberModel localModel : memberModel.locals) {
                final Component localComponent = buildLocalComponent(localModel, ownerType, component);
                ParseUtil.pointParentsToGivenChild(localComponent, stack);
                attachTypeReferences(localComponent, singletonType(localModel.declaredType),
                        typeIndex, ownerType, memberModel, false);
                model.insertComponent(localComponent);
                ParseUtil.copyRefsToParents(localComponent, stack);
            }
        }
        attachTypeReferences(component, memberModel.simpleTypeUsages, typeIndex, ownerType, memberModel,
                "method".equals(memberModel.kind) || "constructor".equals(memberModel.kind));
        attachMemberReferences(component, memberModel.memberUsages, typeIndex, ownerType);
        if (component.componentType().isMethodComponent()) {
            component.setCyclo(memberModel.cyclo);
        }
        model.insertComponent(component);
        stack.pop();
        ParseUtil.copyRefsToParents(component, stack);
    }

    private static Component buildMemberComponent(final CSharpModel.CSharpMemberModel memberModel,
                                                  final CSharpModel.CSharpTypeModel ownerType) {
        final OOPSourceModelConstants.ComponentType componentType = mapMemberComponent(memberModel.kind);
        if (componentType == null || memberModel.name == null || memberModel.name.isEmpty()) {
            return null;
        }
        final Component component = new Component();
        component.setPkg(resolvePackage(ownerType.namespaceName));
        component.setModule(memberModel.moduleName);
        component.setComponentType(componentType);
        component.setName(memberModel.name);
        component.setSourceFilePath(memberModel.sourcePath);
        if (memberModel.comment == null) {
            component.setComment("");
        } else {
            component.setComment(memberModel.comment);
        }
        component.setImports(memberModel.imports);
        component.setAccessModifiers(memberModel.modifiers);
        component.setComponentName(ownerType.componentName + "." + memberComponentIdentifier(memberModel, ownerType));
        if (memberModel.codeFragment != null && !memberModel.codeFragment.isEmpty()) {
            component.setCodeFragment(memberModel.codeFragment);
            component.setCodeHash(memberModel.codeFragment.hashCode());
        }
        return component;
    }

    private static Component buildParameterComponent(final CSharpModel.CSharpParameterModel parameter,
                                                     final CSharpModel.CSharpMemberModel memberModel,
                                                     final CSharpModel.CSharpTypeModel ownerType,
                                                     final Component ownerComponent) {
        final Component component = new Component();
        component.setPkg(resolvePackage(ownerType.namespaceName));
        component.setModule(memberModel.moduleName);
        if ("constructor".equals(memberModel.kind)) {
            component.setComponentType(OOPSourceModelConstants.ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT);
        } else {
            component.setComponentType(OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT);
        }
        component.setName(parameter.name);
        component.setSourceFilePath(memberModel.sourcePath);
        component.setImports(memberModel.imports);
        component.setAccessModifiers(parameter.modifiers);
        component.setComponentName(ownerComponent.componentName() + "." + parameter.name);
        if (parameter.declaredType != null && !parameter.declaredType.isEmpty()) {
            component.setCodeFragment(parameter.declaredType);
        }
        return component;
    }

    private static Component buildLocalComponent(final CSharpModel.CSharpMemberModel localModel,
                                                 final CSharpModel.CSharpTypeModel ownerType,
                                                 final Component ownerComponent) {
        final Component component = new Component();
        component.setPkg(resolvePackage(ownerType.namespaceName));
        component.setModule(localModel.moduleName);
        component.setComponentType(OOPSourceModelConstants.ComponentType.LOCAL);
        component.setName(localModel.name);
        component.setSourceFilePath(localModel.sourcePath);
        component.setImports(localModel.imports);
        component.setAccessModifiers(localModel.modifiers);
        component.setComponentName(ownerComponent.componentName() + "." + localModel.name);
        if (localModel.codeFragment != null && !localModel.codeFragment.isEmpty()) {
            component.setCodeFragment(localModel.codeFragment);
        }
        return component;
    }

    private static void attachTypeReferences(final Component component,
                                             final Collection<String> rawTypes,
                                             final TypeIndex typeIndex,
                                             final CSharpModel.CSharpTypeModel ownerType,
                                             final CSharpModel.CSharpMemberModel memberModel,
                                             final boolean applyMemberAccessFilter) {
        if (rawTypes == null) {
            return;
        }
        final Set<String> seen = new LinkedHashSet<>();
        for (final String rawType : rawTypes) {
            if (applyMemberAccessFilter && isLikelyMemberAccess(rawType, ownerType, memberModel)) {
                continue;
            }
            for (final String token : CSharpFileParser.extractTypeTokens(rawType)) {
                final String resolved = typeIndex.resolveType(token, ownerType, memberModel);
                if (resolved == null || resolved.equals(component.uniqueName()) || !seen.add(resolved)) {
                    continue;
                }
                component.insertCmpRef(new SimpleTypeReference(resolved));
            }
        }
    }

    private static void attachMemberReferences(final Component component,
                                               final Collection<String> memberUsages,
                                               final TypeIndex typeIndex,
                                               final CSharpModel.CSharpTypeModel ownerType) {
        if (memberUsages == null) {
            return;
        }
        final Set<String> seen = new LinkedHashSet<>();
        for (final String usage : memberUsages) {
            final String resolved = typeIndex.resolveMember(ownerType.uniqueName, usage);
            if (resolved == null || resolved.equals(component.uniqueName()) || !seen.add(resolved)) {
                continue;
            }
            component.insertCmpRef(new SimpleTypeReference(resolved));
        }
    }

    private static boolean isLikelyMemberAccess(final String rawType,
                                                final CSharpModel.CSharpTypeModel ownerType,
                                                final CSharpModel.CSharpMemberModel memberModel) {
        if (rawType == null || rawType.isBlank()) {
            return false;
        }
        if (memberModel == null || (!"method".equals(memberModel.kind) && !"constructor".equals(memberModel.kind))) {
            return false;
        }
        final String trimmed = rawType.trim();
        final String root;
        if (trimmed.contains(".")) {
            root = trimmed.substring(0, trimmed.indexOf('.'));
        } else {
            root = trimmed;
        }
        if (memberModel != null) {
            for (final CSharpModel.CSharpParameterModel parameter : memberModel.parameters) {
                if (root.equals(parameter.name)) {
                    return true;
                }
            }
            for (final CSharpModel.CSharpMemberModel local : memberModel.locals) {
                if (root.equals(local.name)) {
                    return true;
                }
            }
        }
        if (ownerType != null) {
            for (final CSharpModel.CSharpMemberModel ownerMember : ownerType.members) {
                if (root.equals(ownerMember.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> singletonType(final String declaredType) {
        if (declaredType == null || declaredType.isBlank()) {
            return List.of();
        }
        return List.of(declaredType);
    }

    private static boolean isInterfaceTarget(final String rawToken,
                                             final String resolved,
                                             final TypeIndex typeIndex) {
        final String simpleName;
        if (rawToken == null) {
            simpleName = "";
        } else {
            simpleName = rawToken.substring(rawToken.lastIndexOf('.') + 1);
        }
        if (simpleName.startsWith("I") && simpleName.length() > 1
                && Character.isUpperCase(simpleName.charAt(1))) {
            return true;
        }
        return typeIndex.interfaceTypes.contains(resolved);
    }

    private static Package resolvePackage(final String namespaceName) {
        if (namespaceName == null || namespaceName.isEmpty()) {
            return new Package("", "");
        }
        return new Package(namespaceName, namespaceName);
    }

    private static OOPSourceModelConstants.ComponentType mapTypeComponent(final String kind) {
        switch (kind) {
            case "class":
            case "record":
                return OOPSourceModelConstants.ComponentType.CLASS;
            case "interface":
                return OOPSourceModelConstants.ComponentType.INTERFACE;
            case "recordStruct":
            case "struct":
                return OOPSourceModelConstants.ComponentType.STRUCT;
            case "enum":
                return OOPSourceModelConstants.ComponentType.ENUM;
            case "delegate":
                return OOPSourceModelConstants.ComponentType.FUNCTION;
            default:
                return OOPSourceModelConstants.ComponentType.CLASS;
        }
    }

    private static OOPSourceModelConstants.ComponentType mapMemberComponent(final String kind) {
        switch (kind) {
            case "field":
            case "property":
            case "event":
            case "recordField":
                return OOPSourceModelConstants.ComponentType.FIELD;
            case "method":
                return OOPSourceModelConstants.ComponentType.METHOD;
            case "constructor":
                return OOPSourceModelConstants.ComponentType.CONSTRUCTOR;
            case "enumMember":
                return OOPSourceModelConstants.ComponentType.ENUM_CONSTANT;
            case "local":
                return OOPSourceModelConstants.ComponentType.LOCAL;
            case "parameter":
                return OOPSourceModelConstants.ComponentType.METHOD_PARAMETER_COMPONENT;
            default:
                return null;
        }
    }

    private static String memberComponentIdentifier(final CSharpModel.CSharpMemberModel memberModel,
                                                    final CSharpModel.CSharpTypeModel ownerType) {
        switch (memberModel.kind) {
            case "method":
                return memberModel.name + signatureSuffix(memberModel.parameters);
            case "constructor":
                return ownerType.name + signatureSuffix(memberModel.parameters);
            default:
                return memberModel.name;
        }
    }

    private static String signatureSuffix(final List<CSharpModel.CSharpParameterModel> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "()";
        }
        final List<String> types = new ArrayList<>();
        for (final CSharpModel.CSharpParameterModel parameter : parameters) {
            types.add(displayType(parameter.declaredType));
        }
        return "(" + String.join(", ", types) + ")";
    }

    private static String displayType(final String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "object";
        }
        final String normalized = rawType.trim();
        final int lastDot = normalized.lastIndexOf('.');
        if (lastDot >= 0) {
            return normalized.substring(lastDot + 1);
        }
        return normalized;
    }

    private static List<String> mergeStrings(final List<String> left, final List<String> right) {
        final LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (left != null) {
            merged.addAll(left);
        }
        if (right != null) {
            merged.addAll(right);
        }
        return new ArrayList<>(merged);
    }

    private static final class TypeIndex {
        private final Map<String, CSharpModel.CSharpTypeModel> typesByUniqueName = new LinkedHashMap<>();
        private final Map<String, List<String>> typesBySimpleName = new HashMap<>();
        private final Map<String, String> memberByTypeAndName = new HashMap<>();
        private final Set<String> interfaceTypes = new LinkedHashSet<>();

        private TypeIndex(final List<CSharpModel.CSharpTypeModel> types) {
            for (final CSharpModel.CSharpTypeModel type : types) {
                typesByUniqueName.put(type.uniqueName, type);
                typesBySimpleName.computeIfAbsent(type.name, ignored -> new ArrayList<>()).add(type.uniqueName);
                if ("interface".equals(type.kind)) {
                    interfaceTypes.add(type.uniqueName);
                }
                for (final CSharpModel.CSharpMemberModel member : type.members) {
                    if (member.name != null && !member.name.isEmpty()) {
                        memberByTypeAndName.put(type.uniqueName + "#" + member.name,
                                type.uniqueName + "." + memberComponentIdentifier(member, type));
                    }
                }
            }
        }

        private String resolveType(final String rawToken,
                                   final CSharpModel.CSharpTypeModel ownerType,
                                   final CSharpModel.CSharpMemberModel memberModel) {
            if (rawToken == null || rawToken.isBlank()) {
                return null;
            }
            final String cleaned = rawToken.replace("?", "").trim();
            final String builtin = BUILTIN_TYPES.get(cleaned.toLowerCase(Locale.ROOT));
            if (builtin != null) {
                return builtin;
            }
            final String aliasTarget = ownerType.usingAliases.get(cleaned);
            if (aliasTarget != null && !aliasTarget.isEmpty()) {
                return aliasTarget;
            }
            if (typesByUniqueName.containsKey(cleaned)) {
                return cleaned;
            }
            final String nestedCandidate = resolveNested(cleaned, ownerType);
            if (nestedCandidate != null) {
                return nestedCandidate;
            }
            final String namespaceCandidate = resolveNamespace(cleaned, ownerType.namespaceName);
            if (namespaceCandidate != null) {
                return namespaceCandidate;
            }
            final String usingCandidate = resolveUsing(cleaned, ownerType.imports);
            if (usingCandidate != null) {
                return usingCandidate;
            }
            return cleaned;
        }

        private String resolveMember(final String ownerTypeUniqueName, final String memberName) {
            if (ownerTypeUniqueName == null || memberName == null || memberName.isEmpty()) {
                return null;
            }
            String current = ownerTypeUniqueName;
            while (current != null && !current.isEmpty()) {
                final String resolved = memberByTypeAndName.get(current + "#" + memberName);
                if (resolved != null) {
                    return resolved;
                }
                final int lastDot = current.lastIndexOf('.');
                if (lastDot < 0) {
                    return null;
                }
                current = current.substring(0, lastDot);
            }
            return null;
        }

        private String resolveNested(final String simpleName, final CSharpModel.CSharpTypeModel ownerType) {
            if (ownerType == null) {
                return null;
            }
            String current = ownerType.uniqueName;
            while (current != null && !current.isEmpty()) {
                final String candidate = current + "." + simpleName;
                if (typesByUniqueName.containsKey(candidate)) {
                    return candidate;
                }
                final int lastDot = current.lastIndexOf('.');
                if (lastDot < 0) {
                    return null;
                }
                current = current.substring(0, lastDot);
            }
            return null;
        }

        private String resolveNamespace(final String simpleName, final String namespaceName) {
            if (namespaceName == null || namespaceName.isEmpty()) {
                return simpleLookup(simpleName);
            }
            final String candidate = namespaceName + "." + simpleName;
            if (typesByUniqueName.containsKey(candidate)) {
                return candidate;
            }
            return simpleLookup(simpleName);
        }

        private String resolveUsing(final String simpleName, final Set<String> imports) {
            if (imports == null) {
                return null;
            }
            for (final String importTarget : imports) {
                if (importTarget == null || importTarget.isBlank()) {
                    continue;
                }
                if (typesByUniqueName.containsKey(importTarget) && importTarget.endsWith("." + simpleName)) {
                    return importTarget;
                }
                final String candidate = importTarget + "." + simpleName;
                if (typesByUniqueName.containsKey(candidate)) {
                    return candidate;
                }
            }
            return null;
        }

        private String simpleLookup(final String simpleName) {
            final List<String> matches = typesBySimpleName.get(simpleName);
            if (matches == null || matches.isEmpty()) {
                return null;
            }
            return matches.get(0);
        }
    }
}
