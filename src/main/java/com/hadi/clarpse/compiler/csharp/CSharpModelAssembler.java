package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.listener.ParseUtil;
import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
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
import java.util.Optional;
import java.util.Set;
import java.util.Stack;

/**
 * Builds the final {@link OOPSourceCodeModel} from parsed C# file models.
 * This stage merges partial declarations, applies global imports, constructs
 * components, and performs fast repo-local type/member resolution using a
 * precomputed index rather than repeated tree scans.
 */
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

    /**
     * Library type names a repository class may shadow. {@link #BUILTIN_TYPES} holds the language
     * keywords; these are the framework generics and helpers that appear unqualified in ordinary
     * code and are never the repository's own type unless something in scope says so.
     *
     * <p>Only the last-resort single-match guess consults this. A repository type genuinely called
     * `List` still resolves by exact name, nesting, namespace or import.
     */
    private static final Set<String> FRAMEWORK_TYPE_NAMES = Set.of(
            "List", "IList", "Dictionary", "IDictionary", "HashSet", "ISet", "Queue", "Stack",
            "IEnumerable", "IEnumerator", "ICollection", "IReadOnlyList", "IReadOnlyCollection",
            "IReadOnlyDictionary", "IQueryable", "IAsyncEnumerable",
            "Task", "ValueTask", "Action", "Func", "Predicate", "Comparer", "EqualityComparer",
            "Nullable", "Tuple", "ValueTuple", "KeyValuePair", "Lazy", "Span", "Memory",
            "Array", "Object", "String", "Exception", "Attribute", "Type", "Guid", "Uri",
            "TimeSpan", "DateTime", "DateTimeOffset", "Version", "Random", "Timer",
            "Stream", "TextReader", "TextWriter", "Encoding", "Regex", "Match",
            "CancellationToken", "IServiceProvider", "IDisposable", "IAsyncDisposable");

    private CSharpModelAssembler() {
    }

    /**
     * Builds the model of the parsed files, resolving their names against the parsed files and the
     * declaration-only stubs of every other file, and emits only the components declared in
     * {@code emittedPaths}. A stub is never emitted: it exists so a name declared elsewhere in the
     * repository resolves to the unique name it has there.
     *
     * @param parsed       File models of the parsed files; listed first, so a partial type merged
     *                     with a stub part keeps a parsed part's identity.
     * @param stubs        Declaration-only file models of the files not parsed.
     * @param emittedPaths Paths of the files whose components the model holds.
     * @return The model.
     */
    static OOPSourceCodeModel buildModel(final Collection<CSharpModel.CSharpFileModel> parsed,
                                         final Collection<CSharpModel.CSharpFileModel> stubs,
                                         final Set<String> emittedPaths) {
        final List<CSharpModel.CSharpFileModel> fileModels = new ArrayList<>(parsed);
        fileModels.addAll(stubs);
        final OOPSourceCodeModel resolved = buildModel(fileModels);
        final OOPSourceCodeModel model = new OOPSourceCodeModel();
        resolved.components()
                .filter(component -> emittedPaths.contains(component.sourceFile()))
                .forEach(model::insertComponent);
        return model;
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
        linkExtensionMethods(mergedTypes, typeIndex, model);
        return model;
    }

    /**
     * Links an extension method to the type it extends.
     *
     * <p>`public static void Reset(this Widget w)` is declared on a static class, which is where it
     * was recorded and where it stayed. Callers write `widget.Reset()`, so asking a type what it can
     * do had to answer without it. The method keeps its place on the declaring class, and the
     * extended type gains a reference to it, so it is reachable from both.
     *
     * <p>Only a type in this repository is linked: an extension on `string` or on a framework type
     * resolves to nothing the model holds, and is left alone.
     */
    private static void linkExtensionMethods(final List<CSharpModel.CSharpTypeModel> types,
                                             final TypeIndex typeIndex,
                                             final OOPSourceCodeModel model) {
        for (final CSharpModel.CSharpTypeModel typeModel : types) {
            if (typeModel.modifiers == null || !typeModel.modifiers.contains("static")) {
                continue;
            }
            for (final CSharpModel.CSharpMemberModel member : typeModel.members) {
                if (!"method".equals(member.kind) || member.parameters.isEmpty()) {
                    continue;
                }
                final CSharpModel.CSharpParameterModel receiver = member.parameters.get(0);
                if (!receiver.extensionReceiver || receiver.declaredType == null) {
                    continue;
                }
                final Resolution extendedType = typeIndex.resolveType(receiver.declaredType, typeModel, member);
                if (extendedType == null) {
                    continue;
                }
                final Optional<Component> extended = model.copyOfComponent(extendedType.name());
                if (extended.isEmpty()) {
                    continue;
                }
                final String methodUniqueName =
                        typeModel.uniqueName + "." + memberComponentIdentifier(member, typeModel);
                if (methodUniqueName.equals(extendedType.name())) {
                    continue;
                }
                final Component component = extended.get();
                final SimpleTypeReference reference = new SimpleTypeReference(methodUniqueName);
                reference.setResolutionKind(ResolutionKind.EXACT);
                component.insertCmpRef(reference);
                model.insertComponent(component);
            }
        }
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
            // Summed rather than folded in sequence, so that the merged hash does not depend on the
            // order the partial declarations happened to be parsed in.
            existing.implementationHash += typeModel.implementationHash;
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
        attachAnnotationReferences(typeComponent, typeModel.annotations, typeIndex, typeModel);
        stack.push(typeComponent);
        for (final String baseType : typeModel.baseTypes) {
            for (final String rawToken : CSharpFileParser.extractTypeTokens(baseType)) {
                final Resolution resolved = typeIndex.resolveType(rawToken, typeModel, null);
                if (resolved == null || resolved.name().equals(typeComponent.uniqueName())) {
                    continue;
                }
                if ("interface".equals(typeModel.kind) || "class".equals(typeModel.kind)
                        || "record".equals(typeModel.kind) || "recordStruct".equals(typeModel.kind)
                        || "struct".equals(typeModel.kind)) {
                    final ComponentReference ref;
                    if (isInterfaceTarget(rawToken, resolved.name(), typeIndex)) {
                        ref = new TypeImplementationReference(resolved.name());
                    } else {
                        ref = new TypeExtensionReference(resolved.name());
                    }
                    ref.setResolutionKind(resolved.kind());
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
        }
        applyCodeHash(component, typeModel.implementationHash, typeModel.codeFragment);
        return component;
    }

    /**
     * Sets the component's code hash, preferring the hash over its full declaration so that a body edit
     * is detectable, and falling back to the component's signature when there was no text to hash. Every
     * component ends up with a non-zero hash, so a zero unambiguously means "never computed".
     */
    private static void applyCodeHash(final Component component, final int implementationHash, final String fallback) {
        int hash = implementationHash;
        if (hash == 0 && fallback != null) {
            hash = fallback.hashCode();
        }
        if (hash == 0) {
            hash = component.componentName().hashCode();
        }
        if (hash == 0) {
            hash = 1;
        }
        component.setCodeHash(hash);
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
        attachAnnotationReferences(component, memberModel.annotations, typeIndex, ownerType);
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
        // Imports belong to the file, and in the model to the type that file declares.
        // Copying them onto members, parameters and locals made `imports()` mean something
        // different in C# than in Java, where only type components carry them: a field would
        // report the whole file's using list as if it were its own. See issue #156.
        component.setAccessModifiers(memberModel.modifiers);
        component.setComponentName(ownerType.componentName + "." + memberComponentIdentifier(memberModel, ownerType));
        if (memberModel.codeFragment != null && !memberModel.codeFragment.isEmpty()) {
            component.setCodeFragment(memberModel.codeFragment);
        }
        applyCodeHash(component, memberModel.implementationHash, memberModel.codeFragment);
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
        component.setAccessModifiers(parameter.modifiers);
        component.setComponentName(ownerComponent.componentName() + "." + parameter.name);
        if (parameter.declaredType != null && !parameter.declaredType.isEmpty()) {
            component.setCodeFragment(parameter.declaredType);
        }
        applyCodeHash(component, parameter.implementationHash, parameter.declaredType);
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
        component.setAccessModifiers(localModel.modifiers);
        component.setComponentName(ownerComponent.componentName() + "." + localModel.name);
        if (localModel.codeFragment != null && !localModel.codeFragment.isEmpty()) {
            component.setCodeFragment(localModel.codeFragment);
        }
        applyCodeHash(component, localModel.implementationHash, localModel.codeFragment);
        return component;
    }

    /**
     * Records each applied attribute as an {@link AnnotationReference} on the component -- the same
     * mechanism base types use for extension/implementation, so an attribute is a distinct kind of
     * reference rather than a separate field. The attribute type is resolved to a repository unique
     * name where one exists, and otherwise kept as the name written in source: an attribute is a
     * real fact whether or not its declaring type is on the parse path, so an unresolved one is
     * still recorded (unlike a base type, which is dropped when it cannot be resolved).
     */
    private static void attachAnnotationReferences(final Component component,
                                                   final Collection<String> annotationNames,
                                                   final TypeIndex typeIndex,
                                                   final CSharpModel.CSharpTypeModel ownerType) {
        if (annotationNames == null) {
            return;
        }
        final Set<String> seen = new LinkedHashSet<>();
        for (final String annotationName : annotationNames) {
            if (annotationName == null || annotationName.isBlank()) {
                continue;
            }
            final String token = annotationName.trim();
            final Resolution resolved = typeIndex.resolveType(token, ownerType, null);
            String invoked = token;
            ResolutionKind kind = ResolutionKind.UNRESOLVED;
            if (resolved != null) {
                invoked = resolved.name();
                kind = resolved.kind();
            }
            if (invoked.isEmpty() || invoked.equals(component.uniqueName()) || !seen.add(invoked)) {
                continue;
            }
            final AnnotationReference reference = new AnnotationReference(invoked);
            reference.setResolutionKind(kind);
            component.insertCmpRef(reference);
        }
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
                final Resolution resolved = typeIndex.resolveType(token, ownerType, memberModel);
                if (resolved == null || resolved.name().equals(component.uniqueName())
                        || !seen.add(resolved.name())) {
                    continue;
                }
                final SimpleTypeReference reference = new SimpleTypeReference(resolved.name());
                reference.setResolutionKind(resolved.kind());
                component.insertCmpRef(reference);
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
            // A member of a named type, reached in that type's own scope: nothing was guessed at.
            final SimpleTypeReference reference = new SimpleTypeReference(resolved);
            reference.setResolutionKind(ResolutionKind.EXACT);
            component.insertCmpRef(reference);
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

    /** A resolved type name, and the evidence that settled it. */
    private static final class Resolution {
        private final String name;
        private final ResolutionKind kind;

        Resolution(final String name, final ResolutionKind kind) {
            this.name = name;
            this.kind = kind;
        }

        String name() {
            return name;
        }

        ResolutionKind kind() {
            return kind;
        }
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
                    // A constant is a member of its type, but reading one is not a dependency on a
                    // type -- `return NOTE;` says nothing about the structure of the codebase. It is
                    // modelled as a member and kept out of the index that turns a name used in a
                    // body into a reference.
                    if (member.modifiers != null && member.modifiers.contains("const")) {
                        continue;
                    }
                    if (member.name != null && !member.name.isEmpty()) {
                        memberByTypeAndName.put(type.uniqueName + "#" + member.name,
                                type.uniqueName + "." + memberComponentIdentifier(member, type));
                    }
                }
            }
        }

        private Resolution resolveType(final String rawToken,
                                       final CSharpModel.CSharpTypeModel ownerType,
                                       final CSharpModel.CSharpMemberModel memberModel) {
            if (rawToken == null || rawToken.isBlank()) {
                return null;
            }
            final String cleaned = rawToken.replace("?", "").trim();
            final String builtin = BUILTIN_TYPES.get(cleaned.toLowerCase(Locale.ROOT));
            if (builtin != null) {
                return new Resolution(builtin, ResolutionKind.EXACT);
            }
            final String aliasTarget = ownerType.usingAliases.get(cleaned);
            if (aliasTarget != null && !aliasTarget.isEmpty()) {
                return new Resolution(aliasTarget, ResolutionKind.EXACT);
            }
            if (typesByUniqueName.containsKey(cleaned)) {
                return new Resolution(cleaned, ResolutionKind.EXACT);
            }
            final String nestedCandidate = resolveNested(cleaned, ownerType);
            if (nestedCandidate != null) {
                return new Resolution(nestedCandidate, ResolutionKind.EXACT);
            }
            final String namespaceCandidate = resolveNamespace(cleaned, ownerType.namespaceName);
            if (namespaceCandidate != null) {
                return new Resolution(namespaceCandidate, ResolutionKind.EXACT);
            }
            // Imports are consulted before any repository-wide guess. They used to come after, and
            // `resolveNamespace` ended in a guess, so the guess always won: a file declaring
            // `using ...Modules.Meetings.Application.Contracts;` and extending `CommandBase<Unit>`
            // bound to Administration's `CommandBase` -- one of five identically named types, chosen
            // by insertion order. A `using` is evidence; a name match across the repository is not.
            final String usingCandidate = resolveUsing(cleaned, ownerType.imports);
            if (usingCandidate != null) {
                return new Resolution(usingCandidate, ResolutionKind.EXACT);
            }
            final String soleCandidate = soleTypeNamed(cleaned);
            if (soleCandidate != null) {
                return new Resolution(soleCandidate, ResolutionKind.UNIQUE_SIMPLE_NAME);
            }
            // Nothing in scope named this type. Returning the bare token leaves it unresolved, and a
            // consumer drops an edge that points outside the codebase -- which is the right outcome.
            // Silence costs a missing edge; a guess costs a fabricated one, and a fabricated edge
            // reads as a fact. Which of the two this is -- several types carry the name and none was
            // chosen, or none does -- is reported rather than left for the consumer to infer.
            final List<String> sharingTheName = typesBySimpleName.get(cleaned);
            if (sharingTheName != null && sharingTheName.size() > 1) {
                return new Resolution(cleaned, ResolutionKind.AMBIGUOUS);
            }
            return new Resolution(cleaned, ResolutionKind.UNRESOLVED);
        }

        /**
         * The only type carrying this short name, or null when several do or the name belongs to a
         * framework type a repository class happens to shadow.
         *
         * <p>The framework guard is not redundant with {@link #BUILTIN_TYPES}: that map holds the
         * language keywords (`int`, `string`), not the library generics. `List<OrderItem>` in a
         * Domain class bound to `Clean.Architecture.Web.Contributors.List` -- a user endpoint class
         * in another project -- and became the witness of a false layering violation. A repository
         * type genuinely named `List` still resolves through exact name, nesting, namespace or
         * import; only the last-resort guess is refused.
         */
        private String soleTypeNamed(final String simpleName) {
            if (FRAMEWORK_TYPE_NAMES.contains(simpleName)) {
                return null;
            }
            final List<String> matches = typesBySimpleName.get(simpleName);
            if (matches == null || matches.size() != 1) {
                return null;
            }
            return matches.get(0);
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

        // Namespace only. This used to fall back to `simpleLookup`, which returned
        // `matches.get(0)` -- an arbitrary type sharing the short name, decided by insertion order.
        // Because this method is tried before imports, that guess pre-empted the `using` that held
        // the answer. Measured across 28 repositories, 222 of 1,490 decidable C# edges onto an
        // ambiguous short name were resolved to the wrong type (15%, and 57% on
        // ardalis/CleanArchitecture, 34% on kgrzybek/modular-monolith-with-ddd -- the repositories
        // that duplicate a type per module, which is the pattern module-boundary rules exist for).
        private String resolveNamespace(final String simpleName, final String namespaceName) {
            if (namespaceName == null || namespaceName.isEmpty()) {
                return null;
            }
            final String candidate = namespaceName + "." + simpleName;
            if (typesByUniqueName.containsKey(candidate)) {
                return candidate;
            }
            // Enclosing namespaces, as C# lookup does: a type in `A.B` is visible from `A.B.C`.
            String enclosing = namespaceName;
            while (true) {
                final int lastDot = enclosing.lastIndexOf('.');
                if (lastDot < 0) {
                    return null;
                }
                enclosing = enclosing.substring(0, lastDot);
                final String outer = enclosing + "." + simpleName;
                if (typesByUniqueName.containsKey(outer)) {
                    return outer;
                }
            }
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

    }
}
