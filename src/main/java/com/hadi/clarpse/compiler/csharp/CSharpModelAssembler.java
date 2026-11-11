package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.listener.ParseUtil;
import com.hadi.clarpse.reference.AnnotationArgument;
import com.hadi.clarpse.reference.AnnotationArguments;
import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ConstructionReference;
import com.hadi.clarpse.reference.MemberKind;
import com.hadi.clarpse.reference.MemberReference;
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
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Builds the final {@link OOPSourceCodeModel} from parsed C# file models.
 * This stage merges partial declarations, applies global imports, constructs
 * components, and performs fast repo-local type/member resolution using a
 * precomputed index rather than repeated tree scans.
 */
final class CSharpModelAssembler {

    static final Map<String, String> BUILTIN_TYPES = Map.ofEntries(
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

    /** The framework types the keywords stand for, by the names they are also written with: {@code String}. */
    static final Map<String, String> BCL_TYPES = new HashMap<>();

    static {
        for (final String name : BUILTIN_TYPES.values()) {
            BCL_TYPES.put(name.substring("System.".length()), name);
        }
    }

    private CSharpModelAssembler() {
    }

    /**
     * Builds the model of the parsed files, resolving their names against the parsed files and the
     * declaration-only stubs of every other file, and emits only the components declared in
     * {@code emittedPaths}. A stub is never emitted: it exists so a name declared elsewhere in the
     * repository resolves to the unique name it has there.
     *
     * <p>A component that names no source file is not emitted: no file claims it, so no emitted path
     * can. Its path is never looked up either, because {@code emittedPaths} may be a sorted set, and
     * a sorted set rejects a null lookup rather than answering it.
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
                .filter(component -> component.sourceFile() != null
                        && emittedPaths.contains(component.sourceFile()))
                .forEach(model::insertComponent);
        return model;
    }

    static OOPSourceCodeModel buildModel(final Collection<CSharpModel.CSharpFileModel> fileModels) {
        applyGlobalUsings(fileModels);
        final List<CSharpModel.CSharpTypeModel> mergedTypes = mergePartials(fileModels);
        final CSharpTypeIndex typeIndex = new CSharpTypeIndex(mergedTypes);
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

    /**
     * Gives every declared type its unique name and merges the declarations that share one, kind
     * and arity: the parts of a partial type. A type and its namesake of another arity
     * ({@code Foo} and {@code Foo<T>}) have distinct unique names (see {@link CSharpTypeNames}),
     * so they stay two types.
     */
    private static List<CSharpModel.CSharpTypeModel> mergePartials(final Collection<CSharpModel.CSharpFileModel> fileModels) {
        final List<CSharpModel.CSharpTypeModel> flattened = new ArrayList<>();
        final List<String> metadataNames = new ArrayList<>();
        for (final CSharpModel.CSharpFileModel fileModel : fileModels) {
            for (final CSharpModel.CSharpTypeModel typeModel : fileModel.types) {
                flatten(typeModel, namespaceOf(typeModel), flattened, metadataNames);
            }
        }
        final Map<String, String> uniqueNames = CSharpTypeNames.uniqueNames(metadataNames);
        for (int i = 0; i < flattened.size(); i++) {
            assignIdentity(flattened.get(i), uniqueNames.get(metadataNames.get(i)));
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
            existing.constraintTypes.addAll(typeModel.constraintTypes);
            existing.typeParameters.addAll(typeModel.typeParameters);
            existing.primaryParameterTypes.addAll(typeModel.primaryParameterTypes);
            existing.members.addAll(typeModel.members);
            existing.nestedTypes.addAll(typeModel.nestedTypes);
            existing.modifiers = mergeStrings(existing.modifiers, typeModel.modifiers);
            existing.imports.addAll(typeModel.imports);
            for (final com.hadi.clarpse.sourcemodel.ImportDirective directive : typeModel.importDirectives) {
                if (!existing.importDirectives.contains(directive)) {
                    existing.importDirectives.add(directive);
                }
            }
            existing.importedFiles.addAll(typeModel.importedFiles);
            typeModel.usingAliases.forEach(existing.usingAliases::putIfAbsent);
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

    /** Lists a type and the types nested in it, each beside its metadata name. */
    private static void flatten(final CSharpModel.CSharpTypeModel typeModel, final String scopeMetadataName,
                                final List<CSharpModel.CSharpTypeModel> flattened,
                                final List<String> metadataNames) {
        final String metadataName = CSharpTypeNames.qualify(scopeMetadataName,
                CSharpTypeNames.metadataSegment(typeModel.name, typeModel.arity));
        flattened.add(typeModel);
        metadataNames.add(metadataName);
        for (final CSharpModel.CSharpTypeModel nested : typeModel.nestedTypes) {
            flatten(nested, metadataName, flattened, metadataNames);
        }
    }

    private static String namespaceOf(final CSharpModel.CSharpTypeModel typeModel) {
        if (typeModel.namespaceName == null) {
            return "";
        }
        return typeModel.namespaceName;
    }

    private static void assignIdentity(final CSharpModel.CSharpTypeModel typeModel, final String uniqueName) {
        typeModel.uniqueName = uniqueName;
        final String namespace = namespaceOf(typeModel);
        if (namespace.isEmpty()) {
            typeModel.componentName = uniqueName;
        } else {
            typeModel.componentName = uniqueName.substring(namespace.length() + 1);
        }
    }

    private static String typeKey(final CSharpModel.CSharpTypeModel typeModel) {
        return typeModel.kind + "|" + typeModel.uniqueName + "|" + typeModel.arity;
    }

    private static void insertType(final CSharpModel.CSharpTypeModel typeModel,
                                   final CSharpTypeIndex typeIndex,
                                   final OOPSourceCodeModel model,
                                   final Stack<Component> stack) {
        final Component typeComponent = buildTypeComponent(typeModel);
        ParseUtil.pointParentsToGivenChild(typeComponent, stack);
        attachAnnotationReferences(typeComponent, typeModel.annotations, typeIndex, typeModel);
        stack.push(typeComponent);
        for (final String baseType : typeModel.baseTypes) {
            boolean baseTypeToken = true;
            for (final CSharpFileParser.TypeToken token : CSharpFileParser.extractTypeReferences(baseType)) {
                final boolean isBase = baseTypeToken;
                baseTypeToken = false;
                final String rawToken = token.name();
                if (typeModel.typeParameters.contains(rawToken)) {
                    continue;
                }
                final CSharpTypeIndex.Resolution resolved = typeIndex.resolveType(rawToken, token.arity(), typeModel, null);
                if (resolved == null || resolved.name().equals(typeComponent.uniqueName())) {
                    continue;
                }
                if ("interface".equals(typeModel.kind) || "class".equals(typeModel.kind)
                        || "record".equals(typeModel.kind) || "recordStruct".equals(typeModel.kind)
                        || "struct".equals(typeModel.kind)) {
                    // Only the first name of a base-list entry is the base type; the names after it are
                    // its type arguments, which the type merely mentions.
                    final ComponentReference ref;
                    if (!isBase) {
                        ref = new SimpleTypeReference(resolved.name());
                    } else if ("interface".equals(typeModel.kind)) {
                        // Every entry of an interface's base list is an interface it extends.
                        ref = new TypeExtensionReference(resolved.name());
                    } else if (isInterfaceTarget(rawToken, resolved.name(), typeIndex)) {
                        ref = new TypeImplementationReference(resolved.name());
                    } else {
                        ref = new TypeExtensionReference(resolved.name());
                    }
                    ref.setResolutionKind(resolved.kind());
                    typeComponent.insertCmpRef(ref);
                }
            }
        }
        for (final String constraint : typeModel.constraintTypes) {
            for (final CSharpFileParser.TypeToken token : CSharpFileParser.extractTypeReferences(constraint)) {
                if (typeModel.typeParameters.contains(token.name())) {
                    continue;
                }
                final CSharpTypeIndex.Resolution resolved = typeIndex.resolveType(token.name(), token.arity(), typeModel, null);
                if (resolved == null || resolved.name().equals(typeComponent.uniqueName())) {
                    continue;
                }
                final SimpleTypeReference reference = new SimpleTypeReference(resolved.name());
                reference.setResolutionKind(resolved.kind());
                typeComponent.insertCmpRef(reference);
            }
        }
        attachTypeReferences(typeComponent, typeModel.primaryParameterTypes, typeIndex, typeModel, null, false);
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
        component.setImportedFiles(typeModel.importedFiles);
        component.setImportDirectives(typeModel.importDirectives);
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
                                     final CSharpTypeIndex typeIndex,
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
            final Map<CSharpModel.CSharpMemberModel, WrittenType> inferred =
                    inferLocalTypes(memberModel, ownerType, typeIndex);
            for (final CSharpModel.CSharpMemberModel localModel : memberModel.locals) {
                final Component localComponent = buildLocalComponent(localModel, ownerType, component);
                ParseUtil.pointParentsToGivenChild(localComponent, stack);
                attachTypeReferences(localComponent, singletonType(localModel.declaredType),
                        typeIndex, ownerType, memberModel, false);
                final WrittenType fromMember = inferred.get(localModel);
                if (fromMember != null) {
                    attachTypeReferences(localComponent, singletonType(fromMember.text()),
                            typeIndex, fromMember.scope(), fromMember.member(), false);
                }
                model.insertComponent(localComponent);
                ParseUtil.copyRefsToParents(localComponent, stack);
            }
        }
        attachTypeReferences(component, memberModel.constraintTypes, typeIndex, ownerType, memberModel, false);
        attachTypeReferences(component, memberModel.simpleTypeUsages, typeIndex, ownerType, memberModel,
                "method".equals(memberModel.kind) || "constructor".equals(memberModel.kind));
        attachConstructions(component, memberModel.constructedTypes, typeIndex, ownerType, memberModel,
                "method".equals(memberModel.kind) || "constructor".equals(memberModel.kind));
        attachStaticReceivers(component, memberModel, typeIndex, ownerType);
        attachExtensionCalls(component, memberModel, typeIndex, ownerType);
        attachMemberCalls(component, memberModel, typeIndex, ownerType);
        // The operand of typeof is a type whatever else shares its name, so no member-access filter.
        attachTypeReferences(component, memberModel.typeofOperands, typeIndex, ownerType, memberModel, false);
        attachTypeReferences(component, memberModel.typeTestOperands, typeIndex, ownerType, memberModel, false);
        attachCandidateTypes(component, memberModel.typeCandidates, true, typeIndex, ownerType, memberModel);
        attachCandidateTypes(component, memberModel.castTypes, false, typeIndex, ownerType, memberModel);
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
                                                   final Collection<CSharpModel.CSharpAttribute> attributes,
                                                   final CSharpTypeIndex typeIndex,
                                                   final CSharpModel.CSharpTypeModel ownerType) {
        if (attributes == null) {
            return;
        }
        final Set<String> seen = new LinkedHashSet<>();
        for (final CSharpModel.CSharpAttribute attribute : attributes) {
            final String annotationName = attribute.name();
            if (annotationName == null || annotationName.isBlank()) {
                continue;
            }
            final String token = annotationName.trim();
            // A type an argument names, as in `[Foo(typeof(Bar))]`, is referenced from the attributed component.
            attachTypeReferences(component, CSharpBodyText.typeofOperands(attribute.arguments()),
                    typeIndex, ownerType, null, false);
            final CSharpTypeIndex.Resolution resolved = resolveAttribute(token, typeIndex, ownerType);
            if (resolved == null) {
                // `[Mark]` could be `Mark` or `MarkAttribute`, both visible: C# rejects it.
                continue;
            }
            final String invoked = resolved.name();
            final ResolutionKind kind = resolved.kind();
            if (invoked.isEmpty() || invoked.equals(component.uniqueName()) || !seen.add(invoked)) {
                continue;
            }
            final AnnotationReference reference = new AnnotationReference(invoked);
            reference.setResolutionKind(kind);
            reference.setArguments(attributeArguments(attribute, typeIndex, ownerType));
            component.insertCmpRef(reference);
        }
    }

    /**
     * The type an attribute name denotes. {@code [Mark]} names {@code Mark} or {@code MarkAttribute}:
     * the one a visible type carries. Both visible is an error in C#, so it names neither (null);
     * neither visible keeps the name as written.
     */
    private static CSharpTypeIndex.Resolution resolveAttribute(final String written, final CSharpTypeIndex typeIndex,
                                               final CSharpModel.CSharpTypeModel ownerType) {
        final CSharpTypeIndex.Resolution plain = typeIndex.resolveType(written, ownerType, null);
        if (written.endsWith("Attribute") || written.indexOf('<') >= 0 || written.startsWith("@")) {
            return plain;
        }
        final CSharpTypeIndex.Resolution suffixed = typeIndex.resolveType(written + "Attribute", ownerType, null);
        if (suffixed == null || suffixed.kind() != ResolutionKind.EXACT) {
            return plain;
        }
        if (plain != null && plain.kind() != ResolutionKind.UNRESOLVED) {
            return null;
        }
        return suffixed;
    }

    private static Map<String, AnnotationArgument> attributeArguments(final CSharpModel.CSharpAttribute attribute,
                                                                      final CSharpTypeIndex typeIndex,
                                                                      final CSharpModel.CSharpTypeModel ownerType) {
        final AnnotationArguments arguments = new AnnotationArguments();
        for (final CSharpAttributeArguments.Argument argument : CSharpAttributeArguments.parse(attribute.arguments())) {
            final AnnotationArgument value = attributeValue(argument.value(), typeIndex, ownerType);
            if (argument.name() == null) {
                arguments.unnamed(value);
            } else {
                arguments.named(argument.name(), value);
            }
        }
        return arguments.build();
    }

    private static AnnotationArgument attributeValue(final CSharpAttributeArguments.Value value,
                                                     final CSharpTypeIndex typeIndex,
                                                     final CSharpModel.CSharpTypeModel ownerType) {
        switch (value.kind()) {
            case STRING:
                return AnnotationArgument.literal(AnnotationArgument.Kind.STRING, value.text());
            case NUMBER:
                return AnnotationArgument.literal(AnnotationArgument.Kind.NUMBER, value.text());
            case BOOLEAN:
                return AnnotationArgument.literal(AnnotationArgument.Kind.BOOLEAN, value.text());
            case CLASS:
                return attributeClass(value.text(), typeIndex, ownerType);
            case QUALIFIED:
                return attributeEnumConstant(value.text(), typeIndex, ownerType);
            case ARRAY:
                return attributeArray(value, typeIndex, ownerType);
            default:
                return AnnotationArgument.unresolved();
        }
    }

    private static AnnotationArgument attributeClass(final String written, final CSharpTypeIndex typeIndex,
                                                     final CSharpModel.CSharpTypeModel ownerType) {
        final CSharpTypeIndex.Resolution resolved = typeIndex.resolveType(written, ownerType, null);
        if (resolved == null) {
            return AnnotationArgument.literal(AnnotationArgument.Kind.CLASS, written);
        }
        return AnnotationArgument.literal(AnnotationArgument.Kind.CLASS, resolved.name());
    }

    /** A dotted name is an enum constant only when its qualifier resolves to an enum of the repository. */
    private static AnnotationArgument attributeEnumConstant(final String written, final CSharpTypeIndex typeIndex,
                                                            final CSharpModel.CSharpTypeModel ownerType) {
        final int split = written.lastIndexOf('.');
        final CSharpTypeIndex.Resolution owner = typeIndex.resolveType(written.substring(0, split), ownerType, null);
        if (owner != null && typeIndex.isEnum(owner.name())) {
            return AnnotationArgument.literal(AnnotationArgument.Kind.ENUM, written);
        }
        return AnnotationArgument.unresolved();
    }

    private static AnnotationArgument attributeArray(final CSharpAttributeArguments.Value value,
                                                     final CSharpTypeIndex typeIndex,
                                                     final CSharpModel.CSharpTypeModel ownerType) {
        final List<AnnotationArgument> elements = new ArrayList<>();
        for (final CSharpAttributeArguments.Value element : value.elements()) {
            elements.add(attributeValue(element, typeIndex, ownerType));
        }
        return AnnotationArgument.array(elements);
    }

    private static void attachTypeReferences(final Component component,
                                             final Collection<String> rawTypes,
                                             final CSharpTypeIndex typeIndex,
                                             final CSharpModel.CSharpTypeModel ownerType,
                                             final CSharpModel.CSharpMemberModel memberModel,
                                             final boolean applyMemberAccessFilter) {
        if (rawTypes == null) {
            return;
        }
        final Set<String> seen = new LinkedHashSet<>();
        for (final String rawType : rawTypes) {
            if (applyMemberAccessFilter && isLikelyMemberAccess(rawType, ownerType, memberModel, false, typeIndex)) {
                continue;
            }
            for (final CSharpFileParser.TypeToken token : CSharpFileParser.extractTypeReferences(rawType)) {
                // A type parameter names no type of the repository.
                if (ownerType.typeParameters.contains(token.name())
                        || (memberModel != null && memberModel.typeParameters.contains(token.name()))) {
                    continue;
                }
                final CSharpTypeIndex.Resolution resolved = typeIndex.resolveType(token.name(), token.arity(), ownerType, memberModel);
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

    /**
     * Records a construction of the type each {@code new T(...)} names. The constructed type is the head
     * of the written type, so the type arguments of {@code new List<Order>()} are not constructed.
     */
    private static void attachConstructions(final Component component,
                                            final Collection<String> constructed,
                                            final CSharpTypeIndex typeIndex,
                                            final CSharpModel.CSharpTypeModel ownerType,
                                            final CSharpModel.CSharpMemberModel memberModel,
                                            final boolean applyMemberAccessFilter) {
        final Set<String> seen = new LinkedHashSet<>();
        for (final String written : constructed) {
            if (applyMemberAccessFilter && isLikelyMemberAccess(written, ownerType, memberModel, false, typeIndex)) {
                continue;
            }
            final List<CSharpFileParser.TypeToken> tokens = CSharpFileParser.extractTypeReferences(written);
            if (tokens.isEmpty()) {
                continue;
            }
            final CSharpFileParser.TypeToken head = tokens.get(0);
            if (ownerType.typeParameters.contains(head.name())
                    || (memberModel != null && memberModel.typeParameters.contains(head.name()))) {
                continue;
            }
            final CSharpTypeIndex.Resolution resolved = typeIndex.resolveType(head.name(), head.arity(), ownerType, memberModel);
            if (resolved == null || resolved.name().equals(component.uniqueName()) || !seen.add(resolved.name())) {
                continue;
            }
            final ConstructionReference reference = new ConstructionReference(resolved.name());
            reference.setResolutionKind(resolved.kind());
            component.insertCmpRef(reference);
        }
    }

    /**
     * Records the types that written names denote when they are types and nothing else could stand
     * there: {@code nameof(T)}, a pattern {@code o is T} and the parenthesised name of a cast
     * {@code (T)x} may equally be a constant, a member or a value. A name is recorded only when it
     * resolves to a type through the scoping rules and is not the name of a variable or member in
     * scope. With {@code guardInherited}, a bare name in a class that may inherit a member of the
     * name from a base the parse does not hold is not recorded either.
     */
    private static void attachCandidateTypes(final Component component,
                                             final Collection<String> candidates,
                                             final boolean guardInherited,
                                             final CSharpTypeIndex typeIndex,
                                             final CSharpModel.CSharpTypeModel ownerType,
                                             final CSharpModel.CSharpMemberModel memberModel) {
        final Set<String> seen = new LinkedHashSet<>();
        for (final String written : candidates) {
            if (isLikelyMemberAccess(written, ownerType, memberModel, true, typeIndex)) {
                continue;
            }
            boolean first = true;
            for (final CSharpFileParser.TypeToken token : CSharpFileParser.extractTypeReferences(written)) {
                final boolean head = first;
                first = false;
                if (ownerType.typeParameters.contains(token.name())
                        || memberModel.typeParameters.contains(token.name())) {
                    continue;
                }
                final CSharpTypeIndex.Resolution resolved = typeIndex.resolveType(token.name(), token.arity(), ownerType, memberModel);
                if (resolved == null || resolved.kind() != ResolutionKind.EXACT
                        || resolved.name().equals(component.uniqueName())
                        || (head && guardInherited
                            && isPossiblyInherited(token.name(), token.name(), "", resolved, ownerType, memberModel, typeIndex))
                        || !seen.add(resolved.name())) {
                    continue;
                }
                final SimpleTypeReference reference = new SimpleTypeReference(resolved.name());
                reference.setResolutionKind(ResolutionKind.EXACT);
                component.insertCmpRef(reference);
            }
        }
    }

    /**
     * Binds each dotted name written before a member access to the longest of its leading
     * segments that is a type: {@code Tools.Registry.Inner} in {@code Tools.Registry.Inner.Run()}
     * is the nested type, and {@code Host.Helper} in {@code Host.Helper.Go()} is {@code Host}.
     * A name whose segments name no known type is looked up whole, like any other written name.
     */
    private static void attachStaticReceivers(final Component component,
                                              final CSharpModel.CSharpMemberModel memberModel,
                                              final CSharpTypeIndex typeIndex,
                                              final CSharpModel.CSharpTypeModel ownerType) {
        final Set<String> seen = new LinkedHashSet<>();
        for (int i = 0; i < memberModel.staticReceivers.size(); i += 1) {
            final String receiver = memberModel.staticReceivers.get(i);
            final StaticBinding binding = bindStaticReceiver(receiver, CSharpStaticMembers.memberAfter(memberModel, i),
                    component, memberModel, typeIndex, ownerType);
            if (binding == null || !seen.add(binding.resolution().name())) {
                continue;
            }
            final SimpleTypeReference reference = new SimpleTypeReference(binding.resolution().name());
            reference.setResolutionKind(binding.resolution().kind());
            component.insertCmpRef(reference);
        }
    }

    /** The type a static receiver names, and the leading part of the receiver that names it. */
    private record StaticBinding(CSharpTypeIndex.Resolution resolution, String bound) {
    }

    private static StaticBinding bindStaticReceiver(final String receiver, final String following,
                                                    final Component component,
                                                    final CSharpModel.CSharpMemberModel memberModel,
                                                    final CSharpTypeIndex typeIndex,
                                                    final CSharpModel.CSharpTypeModel ownerType) {
        if (isLikelyMemberAccess(receiver, ownerType, memberModel, true, typeIndex)) {
            return null;
        }
        CSharpTypeIndex.Resolution resolved = null;
        String prefix = receiver;
        String bound = receiver;
        while (true) {
            final CSharpTypeIndex.Resolution candidate = typeIndex.resolveType(prefix, ownerType, memberModel);
            // A leading run of segments may be a namespace or a member rather than a type, so
            // it needs an exact resolution.
            if (candidate != null && candidate.kind() == ResolutionKind.EXACT) {
                resolved = candidate;
                bound = prefix;
                break;
            }
            final int cut = prefix.lastIndexOf('.');
            if (cut < 0) {
                break;
            }
            prefix = prefix.substring(0, cut);
        }
        if (resolved == null) {
            resolved = typeIndex.resolveType(receiver, ownerType, memberModel);
        }
        if (resolved == null || resolved.name().equals(component.uniqueName())
                || (resolved.kind() != ResolutionKind.EXACT
                    && typeIndex.isNamespace(receiver, ownerType, memberModel))
                || isPossiblyInherited(receiver, bound, following, resolved, ownerType, memberModel, typeIndex)) {
            return null;
        }
        return new StaticBinding(resolved, bound);
    }

    /**
     * Records the member each call {@code receiver.Name(...)} uses when the receiver's type is known
     * exactly: a parameter, local, lambda parameter or field whose type is written or inferred from
     * a plain initializer, or a type named in the source. A parsed type owns the member unless a
     * parsed base declares it; a name no parsed type declares is not credited while the repository
     * has an extension method of that name. The built-in types (string, int, ...) are owned by their
     * {@code System} type, only in a file whose usings bring in nothing that could add an extension
     * method.
     */
    private static void attachMemberCalls(final Component component,
                                          final CSharpModel.CSharpMemberModel memberModel,
                                          final CSharpTypeIndex typeIndex,
                                          final CSharpModel.CSharpTypeModel ownerType) {
        if (memberModel.extensionCalls.isEmpty()) {
            return;
        }
        final Map<CSharpModel.CSharpMemberModel, WrittenType> inferred =
                inferLocalTypes(memberModel, ownerType, typeIndex);
        for (final CSharpModel.ExtensionCall call : memberModel.extensionCalls) {
            final String receiver = call.receiver();
            if (receiver == null || "this".equals(receiver) || "base".equals(receiver)) {
                continue;
            }
            final CSharpModel.CSharpTypeModel model;
            final String builtin;
            WrittenType written = null;
            if (receiver.indexOf('.') < 0) {
                written = receiverDeclaration(receiver, memberModel, ownerType, typeIndex, inferred);
            }
            if (written != null) {
                final String text = written.text().trim().replace("?", "");
                if ("var".equals(text) || "dynamic".equals(text) || CSharpTypeIndex.isArrayOrPointer(text)) {
                    continue;
                }
                model = typeIndex.typeModelOf(written);
                builtin = BUILTIN_TYPES.get(text);
            } else {
                final StaticBinding binding = bindStaticReceiver(receiver, "", component, memberModel, typeIndex,
                        ownerType);
                if (binding == null || !binding.bound().equals(receiver)) {
                    continue;
                }
                model = typeIndex.typeModelsByName.get(binding.resolution().name());
                builtin = BUILTIN_TYPES.get(receiver);
            }
            CSharpModel.CSharpTypeModel owner = null;
            if (model != null) {
                owner = typeIndex.declaringTypeOf(model, call.name());
                if (owner == null) {
                    if (written != null && typeIndex.hasExtensionMethodNamed(call.name())) {
                        continue;
                    }
                    owner = model;
                }
            } else if (builtin == null || !onlyRepositoryUsings(memberModel, ownerType, typeIndex)) {
                continue;
            }
            String ownerName = builtin;
            String unit = "System";
            if (owner != null) {
                ownerName = owner.uniqueName;
                unit = null;
            }
            if (ownerName.equals(ownerType.uniqueName) || ownerName.equals(component.uniqueName())) {
                continue;
            }
            final MemberReference reference = new MemberReference(ownerName, call.name(),
                    MemberKind.CALL, unit);
            reference.setResolutionKind(ResolutionKind.EXACT);
            component.insertCmpRef(reference);
        }
    }

    /** Whether every using of the file names {@code System} or a namespace the repository declares. */
    private static boolean onlyRepositoryUsings(final CSharpModel.CSharpMemberModel memberModel,
                                                final CSharpModel.CSharpTypeModel ownerType,
                                                final CSharpTypeIndex typeIndex) {
        final Set<String> usings = new LinkedHashSet<>(ownerType.imports);
        usings.addAll(memberModel.imports);
        usings.addAll(ownerType.usingAliases.values());
        usings.addAll(memberModel.usingAliases.values());
        for (final String using : usings) {
            if (!"System".equals(using) && !typeIndex.namespaces.contains(using)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the leading name of a receiver may be a member the enclosing class inherits from a
     * base the parse does not hold, and so is not the type it spells. {@code User.FindFirst()} in a
     * class deriving from a framework controller reads the inherited {@code User} property.
     * A receiver written with its full namespace or through an alias names a type whatever the base
     * declares.
     */
    private static boolean isPossiblyInherited(final String receiver, final String bound, final String following,
                                               final CSharpTypeIndex.Resolution resolved,
                                               final CSharpModel.CSharpTypeModel ownerType,
                                               final CSharpModel.CSharpMemberModel memberModel,
                                               final CSharpTypeIndex typeIndex) {
        final String root = receiver.split("\\.", 2)[0];
        if (ownerType.usingAliases.containsKey(root) || memberModel.usingAliases.containsKey(root)) {
            return false;
        }
        if (resolved.kind() == ResolutionKind.EXACT && bound.indexOf('.') >= 0
                && resolved.name().equals(bound)) {
            return false;
        }
        return typeIndex.mayBeInheritedMember(root, ownerType)
                && !typeIndex.isStaticUseOfType(root, following, ownerType, memberModel);
    }

    /**
     * Binds a call {@code receiver.Name(...)} to the static class that declares the extension method
     * it can be, when exactly one class visible from the calling file does.
     */
    private static void attachExtensionCalls(final Component component,
                                             final CSharpModel.CSharpMemberModel memberModel,
                                             final CSharpTypeIndex typeIndex,
                                             final CSharpModel.CSharpTypeModel ownerType) {
        if (memberModel.extensionCalls.isEmpty()) {
            return;
        }
        Map<CSharpModel.CSharpMemberModel, WrittenType> inferred = null;
        for (final CSharpModel.ExtensionCall call : memberModel.extensionCalls) {
            if (!typeIndex.hasExtensionMethodNamed(call.name())) {
                continue;
            }
            if (inferred == null) {
                inferred = inferLocalTypes(memberModel, ownerType, typeIndex);
            }
            final String declaringClass = typeIndex.soleExtensionClass(call, memberModel, ownerType, inferred);
            if (declaringClass == null || declaringClass.equals(ownerType.uniqueName)
                    || declaringClass.equals(component.uniqueName())) {
                continue;
            }
            final SimpleTypeReference reference = new SimpleTypeReference(declaringClass);
            reference.setResolutionKind(ResolutionKind.EXACT);
            component.insertCmpRef(reference);
            final MemberReference extension = new MemberReference(declaringClass, call.name(), MemberKind.CALL);
            extension.setResolutionKind(ResolutionKind.EXACT);
            component.insertCmpRef(extension);
        }
    }

    private static void attachMemberReferences(final Component component,
                                               final Collection<String> memberUsages,
                                               final CSharpTypeIndex typeIndex,
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
                                                final CSharpModel.CSharpMemberModel memberModel,
                                                final boolean anyMemberKind,
                                                final CSharpTypeIndex typeIndex) {
        if (rawType == null || rawType.isBlank()) {
            return false;
        }
        if (memberModel == null) {
            return false;
        }
        if (!anyMemberKind && !"method".equals(memberModel.kind) && !"constructor".equals(memberModel.kind)) {
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
            return typeIndex != null && typeIndex.inheritsMember(ownerType, root);
        }
        return false;
    }

    /** A type as written in a declaration, with the type and member whose scope the name is read in. */
    record WrittenType(String text, CSharpModel.CSharpTypeModel scope, CSharpModel.CSharpMemberModel member) {
    }

    /** A member found on a type or on a base type of it, with the type that declares it. */
    record FoundMember(CSharpModel.CSharpMemberModel member, CSharpModel.CSharpTypeModel declaring) {
    }

    /**
     * The type a {@code var} local takes from the member it is initialised from: the declared type of
     * a field or property, the return type of a method, followed through each member of a chain and
     * through locals that were themselves inferred. A local whose initializer reads a name that cannot
     * be tied to exactly one declaration, or a member that is not declared once on the receiver's type
     * or a base of it in the parse, is left without a type.
     */
    private static Map<CSharpModel.CSharpMemberModel, WrittenType> inferLocalTypes(
            final CSharpModel.CSharpMemberModel memberModel,
            final CSharpModel.CSharpTypeModel ownerType,
            final CSharpTypeIndex typeIndex) {
        final Map<CSharpModel.CSharpMemberModel, WrittenType> inferred = new java.util.IdentityHashMap<>();
        for (final CSharpModel.CSharpMemberModel local : memberModel.locals) {
            if (local.initializerAccess.isEmpty()) {
                continue;
            }
            final WrittenType type = typeOfAccess(local.initializerAccess, memberModel, ownerType, typeIndex, inferred);
            if (type != null) {
                inferred.put(local, type);
            }
        }
        return inferred;
    }

    private static WrittenType typeOfAccess(final List<String> access,
                                            final CSharpModel.CSharpMemberModel memberModel,
                                            final CSharpModel.CSharpTypeModel ownerType,
                                            final CSharpTypeIndex typeIndex,
                                            final Map<CSharpModel.CSharpMemberModel, WrittenType> inferred) {
        final String root = access.get(0);
        CSharpModel.CSharpTypeModel current;
        int next = 1;
        if ("this".equals(root)) {
            current = ownerType;
        } else {
            final WrittenType receiver = receiverDeclaration(root, memberModel, ownerType, typeIndex, inferred);
            if (receiver == null) {
                return null;
            }
            current = typeIndex.typeModelOf(receiver);
        }
        WrittenType result = null;
        for (; next < access.size(); next++) {
            if (current == null) {
                return null;
            }
            final String segment = access.get(next);
            final boolean call = segment.endsWith("()");
            final FoundMember found = typeIndex.findMember(current, segment.replace("()", ""), call);
            if (found == null) {
                return null;
            }
            final String text;
            if (call) {
                text = found.member().returnType;
            } else {
                text = found.member().declaredType;
            }
            if (text == null || text.isBlank() || "void".equals(text.trim())) {
                return null;
            }
            result = new WrittenType(text, found.declaring(), found.member());
            if (next < access.size() - 1) {
                current = typeIndex.typeModelOf(result);
            }
        }
        return result;
    }

    /**
     * The one declaration a receiver name stands for: a parameter or local of the member, else a field
     * or property of the type or a base of it. Null when the name is declared more than once with
     * different types, is a local whose type is not known, or may be re-declared in the body.
     */
    private static WrittenType receiverDeclaration(final String name,
                                                   final CSharpModel.CSharpMemberModel memberModel,
                                                   final CSharpModel.CSharpTypeModel ownerType,
                                                   final CSharpTypeIndex typeIndex,
                                                   final Map<CSharpModel.CSharpMemberModel, WrittenType> inferred) {
        final Set<String> distinct = new LinkedHashSet<>();
        WrittenType declared = null;
        int count = 0;
        for (final CSharpModel.CSharpParameterModel parameter : memberModel.parameters) {
            if (name.equals(parameter.name)) {
                count += 1;
                if (parameter.declaredType == null || parameter.declaredType.isBlank()) {
                    return null;
                }
                distinct.add(parameter.declaredType.trim());
                declared = new WrittenType(parameter.declaredType, ownerType, memberModel);
            }
        }
        for (final CSharpModel.CSharpMemberModel local : memberModel.locals) {
            if (!name.equals(local.name)) {
                continue;
            }
            count += 1;
            final WrittenType fromMember = inferred.get(local);
            if (fromMember != null) {
                distinct.add(fromMember.scope().uniqueName + "|" + fromMember.text().trim());
                declared = fromMember;
            } else if (local.inferredType && !local.plainNewInitializer) {
                return null;
            } else if (local.declaredType != null && !local.declaredType.isBlank()
                    && !"var".equals(local.declaredType.trim())) {
                distinct.add(local.declaredType.trim());
                declared = new WrittenType(local.declaredType, ownerType, memberModel);
            } else {
                return null;
            }
        }
        if (count > 0) {
            if (distinct.size() == 1 && !CSharpDeclaredNames.bodyMayDeclare(memberModel, name, count)) {
                return declared;
            }
            return null;
        }
        final FoundMember field = typeIndex.findMember(ownerType, name, false);
        if (field == null || field.member().declaredType == null || field.member().declaredType.isBlank()
                || CSharpDeclaredNames.bodyMayDeclare(memberModel, name)) {
            return null;
        }
        return new WrittenType(field.member().declaredType, field.declaring(), field.member());
    }

    private static List<String> singletonType(final String declaredType) {
        if (declaredType == null || declaredType.isBlank()) {
            return List.of();
        }
        return List.of(declaredType);
    }

    private static boolean isInterfaceTarget(final String rawToken,
                                             final String resolved,
                                             final CSharpTypeIndex typeIndex) {
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

    static String memberComponentIdentifier(final CSharpModel.CSharpMemberModel memberModel,
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
}
