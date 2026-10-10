package com.hadi.clarpse.compiler.kotlin;

import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinFileModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinImport;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinMemberModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinParameterModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinSuperType;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.KotlinTypeModel;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.MemberKind;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.TypeKind;
import com.hadi.clarpse.compiler.kotlin.KotlinModel.TypeUsage;
import com.hadi.clarpse.compiler.kotlin.KotlinNameResolver.Resolution;
import com.hadi.clarpse.compiler.kotlin.KotlinNameResolver.Scope;
import com.hadi.clarpse.listener.ParseUtil;
import com.hadi.clarpse.reference.AnnotationReference;
import com.hadi.clarpse.reference.ComponentReference;
import com.hadi.clarpse.reference.ResolutionKind;
import com.hadi.clarpse.reference.SimpleTypeReference;
import com.hadi.clarpse.reference.TypeExtensionReference;
import com.hadi.clarpse.reference.TypeImplementationReference;
import com.hadi.clarpse.sourcemodel.Component;
import com.hadi.clarpse.sourcemodel.OOPSourceCodeModel;
import com.hadi.clarpse.sourcemodel.OOPSourceModelConstants.ComponentType;
import com.hadi.clarpse.sourcemodel.Package;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;

/**
 * Builds the {@link OOPSourceCodeModel} of parsed Kotlin files, resolving every name they write
 * against a {@link KotlinDeclarationIndex}.
 *
 * <p>A type's unique name is its package and its name, nested types joined with a dot, exactly as
 * a Java type of that package and name. A file's top-level functions and properties are members of
 * a class component named as the class they compile into ({@code <File>Kt}, or the
 * {@code @file:JvmName}), which exists only when the file declares one. A property is a field, a
 * primary constructor parameter declared with {@code val} or {@code var} is a field too, and an
 * extension function is a member of whatever declares it, its receiver written as its first
 * parameter, as on the JVM.
 *
 * <p>Every type, function, property and constructor carries one of {@code public},
 * {@code internal}, {@code protected} or {@code private}: the one written, or {@code public}, which
 * is Kotlin's default.
 */
final class KotlinModelAssembler {

    private static final Set<String> VISIBILITIES = Set.of("public", "internal", "protected", "private");

    private final KotlinNameResolver resolver;
    private final OOPSourceCodeModel model = new OOPSourceCodeModel();
    private final Stack<Component> stack = new Stack<>();

    private KotlinModelAssembler(final KotlinDeclarationIndex index) {
        this.resolver = new KotlinNameResolver(index);
    }

    /**
     * Builds the model of the given files.
     *
     * @param files The parsed files whose components the model holds.
     * @param index The declarations names are resolved against.
     * @return The model, its references not yet classified.
     */
    static OOPSourceCodeModel buildModel(final Collection<KotlinFileModel> files, final KotlinDeclarationIndex index) {
        final KotlinModelAssembler assembler = new KotlinModelAssembler(index);
        final List<KotlinFileModel> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparing(KotlinFileModel::path));
        for (final KotlinFileModel file : sorted) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Interrupted while assembling Kotlin files.");
            }
            assembler.addFile(file);
        }
        return assembler.model;
    }

    private void addFile(final KotlinFileModel file) {
        for (final KotlinTypeModel type : file.types) {
            addType(file, type, "", List.of(), Set.of(), null);
        }
        final String fileClass = KotlinDeclarationIndex.fileClassName(file);
        if (fileClass != null) {
            addFileClass(file, fileClass);
        }
    }

    private void addType(final KotlinFileModel file, final KotlinTypeModel type, final String parentComponentName,
                         final List<String> enclosingTypes, final Set<String> outerTypeParameters,
                         final MemberContext outer) {
        if (type.name == null || type.name.isEmpty()) {
            return;
        }
        final Component component = new Component();
        component.setPkg(packageOf(file));
        if (parentComponentName.isEmpty()) {
            component.setComponentName(type.name);
        } else {
            component.setComponentName(parentComponentName + "." + type.name);
        }
        component.setName(type.name);
        component.setComponentType(componentType(type.kind));
        component.setModule(file.moduleName);
        component.setSourceFilePath(file.path());
        component.setComment(nullToEmpty(type.comment));
        component.setImports(importStatements(file));
        component.setAccessModifiers(withVisibility(type.modifiers));
        component.setCodeFragment(type.codeFragment);
        component.setCodeHash(nonZeroHash(type.implementationHash, component.componentName()));

        final List<String> enclosing = new ArrayList<>();
        enclosing.add(component.uniqueName());
        enclosing.addAll(enclosingTypes);
        final Set<String> typeParameters = new LinkedHashSet<>(outerTypeParameters);
        typeParameters.addAll(type.typeParameters);
        final Scope scope = new Scope(file, List.copyOf(enclosing), typeParameters);
        final Set<String> memberNames = enclosingMemberNames(type);
        boolean inheritsMembers = !type.superTypes.isEmpty();
        if (outer != null) {
            memberNames.addAll(outer.memberNames);
            inheritsMembers |= outer.inheritsMembers;
        }
        final MemberContext context = new MemberContext(scope, memberNames, inheritsMembers);

        ParseUtil.pointParentsToGivenChild(component, stack);
        stack.push(component);
        addAnnotationReferences(component, type.annotations, scope);
        for (final KotlinSuperType superType : type.superTypes) {
            addSuperTypeReference(component, type, superType, scope);
        }
        addTypeReferences(component, type.ownUsages, scope, memberNames);

        final List<Object> declarations = new ArrayList<>(type.members);
        declarations.addAll(type.nestedTypes);
        declarations.sort(Comparator.comparingInt(KotlinModelAssembler::startOffset));
        for (final Object declaration : declarations) {
            if (declaration instanceof KotlinTypeModel) {
                addType(file, (KotlinTypeModel) declaration, component.componentName(), enclosing, typeParameters,
                        context);
            } else {
                final KotlinMemberModel member = (KotlinMemberModel) declaration;
                addMember(file, member, component, context);
                if (member.kind == MemberKind.CONSTRUCTOR) {
                    addConstructorProperties(file, member, component, scope);
                }
            }
        }
        model.insertComponent(component);
        stack.pop();
        ParseUtil.copyRefsToParents(component, stack);
    }

    private void addFileClass(final KotlinFileModel file, final String uniqueName) {
        final Component component = new Component();
        final String simpleName = uniqueName.substring(uniqueName.lastIndexOf('.') + 1);
        component.setPkg(packageOf(file));
        component.setComponentName(simpleName);
        component.setName(simpleName);
        component.setComponentType(ComponentType.CLASS);
        component.setModule(file.moduleName);
        component.setSourceFilePath(file.path());
        component.setComment("");
        component.setImports(importStatements(file));
        component.setAccessModifiers(List.of("public", "final"));
        component.setCodeFragment("class " + simpleName);
        int hash = 0;
        for (final KotlinMemberModel member : file.topLevelMembers) {
            hash += member.implementationHash;
        }
        component.setCodeHash(nonZeroHash(hash, simpleName));

        final Scope scope = new Scope(file, List.of(), Set.of());
        final MemberContext context = new MemberContext(scope, Set.of(), false);
        stack.push(component);
        for (final KotlinMemberModel member : file.topLevelMembers) {
            addMember(file, member, component, context);
        }
        stack.pop();
        // A class several files compile into (@file:JvmMultifileClass) holds the members of all of them.
        final Optional<Component> existing = model.component(component.uniqueName());
        if (existing.isPresent()) {
            for (final String child : existing.get().children()) {
                if (!component.children().contains(child)) {
                    component.insertChildComponent(child);
                }
            }
            component.insertCmpRefs(existing.get().references());
        }
        model.insertComponent(component);
    }

    private void addMember(final KotlinFileModel file, final KotlinMemberModel member, final Component owner,
                           final MemberContext context) {
        final ComponentType componentType = memberComponentType(member.kind);
        if (member.name == null || member.name.isEmpty()) {
            return;
        }
        final Component component = new Component();
        component.setPkg(owner.pkg());
        component.setModule(file.moduleName);
        component.setSourceFilePath(file.path());
        component.setComponentType(componentType);
        component.setName(member.name);
        component.setComment(nullToEmpty(member.comment));
        if (member.kind == MemberKind.ENUM_ENTRY) {
            component.setAccessModifiers(member.modifiers);
        } else {
            component.setAccessModifiers(withVisibility(member.modifiers));
        }
        component.setComponentName(owner.componentName() + "." + memberIdentifier(member));
        component.setCodeFragment(member.codeFragment);
        component.setCodeHash(nonZeroHash(member.implementationHash, component.componentName()));

        final Set<String> typeParameters = new LinkedHashSet<>(context.scope.typeParameters());
        typeParameters.addAll(member.typeParameters);
        final Scope scope = new Scope(context.scope.file(), context.scope.enclosingTypes(), typeParameters);

        ParseUtil.pointParentsToGivenChild(component, stack);
        stack.push(component);
        addAnnotationReferences(component, member.annotations, scope);
        final Set<String> closer = closerNames(member, context);
        if (componentType.isMethodComponent()) {
            for (final KotlinParameterModel parameter : member.parameters) {
                addParameter(file, parameter, member, component, scope, closer);
            }
            for (final KotlinMemberModel local : member.locals) {
                addLocal(file, local, component, scope, closer);
            }
            component.setCyclo(member.cyclo);
        }
        addTypeReferences(component, member.typeUsages, scope, closer);
        addFileClassReferences(component, member, context, scope);
        model.insertComponent(component);
        stack.pop();
        ParseUtil.copyRefsToParents(component, stack);
    }

    private void addParameter(final KotlinFileModel file, final KotlinParameterModel parameter,
                              final KotlinMemberModel member, final Component owner, final Scope scope,
                              final Set<String> closer) {
        final Component component = new Component();
        component.setPkg(owner.pkg());
        component.setModule(file.moduleName);
        component.setSourceFilePath(file.path());
        if (member.kind == MemberKind.CONSTRUCTOR) {
            component.setComponentType(ComponentType.CONSTRUCTOR_PARAMETER_COMPONENT);
        } else {
            component.setComponentType(ComponentType.METHOD_PARAMETER_COMPONENT);
        }
        component.setName(parameter.name);
        component.setComponentName(owner.componentName() + "." + parameter.name);
        component.setAccessModifiers(parameter.modifiers.stream()
                .filter(modifier -> !VISIBILITIES.contains(modifier)).toList());
        component.setCodeFragment(parameter.declaredType);
        component.setCodeHash(nonZeroHash(parameter.implementationHash, component.componentName()));
        ParseUtil.pointParentsToGivenChild(component, stack);
        stack.push(component);
        addTypeReferences(component, parameter.typeUsages, scope, closer);
        model.insertComponent(component);
        stack.pop();
        ParseUtil.copyRefsToParents(component, stack);
    }

    private void addLocal(final KotlinFileModel file, final KotlinMemberModel local, final Component owner,
                          final Scope scope, final Set<String> closer) {
        if (local.name == null || local.name.isEmpty()) {
            return;
        }
        final Component component = new Component();
        component.setPkg(owner.pkg());
        component.setModule(file.moduleName);
        component.setSourceFilePath(file.path());
        component.setComponentType(ComponentType.LOCAL);
        component.setName(local.name);
        component.setComponentName(owner.componentName() + "." + local.name);
        component.setCodeFragment(local.codeFragment);
        component.setCodeHash(nonZeroHash(local.implementationHash, component.componentName()));
        ParseUtil.pointParentsToGivenChild(component, stack);
        stack.push(component);
        addTypeReferences(component, local.typeUsages, scope, closer);
        model.insertComponent(component);
        stack.pop();
        ParseUtil.copyRefsToParents(component, stack);
    }

    /** The fields a primary constructor declares through its {@code val} and {@code var} parameters. */
    private void addConstructorProperties(final KotlinFileModel file, final KotlinMemberModel constructor,
                                          final Component owner, final Scope scope) {
        for (final KotlinParameterModel parameter : constructor.parameters) {
            if (!parameter.property) {
                continue;
            }
            final Component component = new Component();
            component.setPkg(owner.pkg());
            component.setModule(file.moduleName);
            component.setSourceFilePath(file.path());
            component.setComponentType(ComponentType.FIELD);
            component.setName(parameter.name);
            component.setComponentName(owner.componentName() + "." + parameter.name);
            final List<String> modifiers = new ArrayList<>(withVisibility(parameter.modifiers));
            if (!parameter.mutable) {
                modifiers.add("final");
            }
            component.setAccessModifiers(modifiers);
            if (parameter.declaredType == null) {
                component.setCodeFragment(parameter.name);
            } else {
                component.setCodeFragment(parameter.name + " : " + parameter.declaredType);
            }
            component.setCodeHash(nonZeroHash(parameter.implementationHash, component.componentName()));
            ParseUtil.pointParentsToGivenChild(component, stack);
            stack.push(component);
            addTypeReferences(component, parameter.typeUsages, scope);
            model.insertComponent(component);
            stack.pop();
            ParseUtil.copyRefsToParents(component, stack);
        }
    }

    private void addSuperTypeReference(final Component component, final KotlinTypeModel type,
                                       final KotlinSuperType superType, final Scope scope) {
        final Resolution resolved = resolver.resolve(superType.name, scope, false);
        if (resolved != null) {
            final ComponentReference reference;
            if (extendsSuperType(type, superType, resolved)) {
                reference = new TypeExtensionReference(resolved.name());
            } else {
                reference = new TypeImplementationReference(resolved.name());
            }
            reference.setResolutionKind(resolved.kind());
            ParseUtil.insertCmpRef(component, reference, stack);
        }
        for (final String argument : superType.typeArguments) {
            addTypeReference(component, new TypeUsage(argument, false), scope);
        }
    }

    /**
     * Whether a supertype entry is the superclass. An interface's supertypes are all interfaces,
     * which it extends, as a Java interface does. A class names its superclass with a constructor
     * call; an entry without one is an interface, unless the repository declares it as a class,
     * which a class without a primary constructor names without a call.
     */
    private boolean extendsSuperType(final KotlinTypeModel type, final KotlinSuperType superType,
                                     final Resolution resolved) {
        if (type.kind == TypeKind.INTERFACE || superType.constructorCall) {
            return true;
        }
        final TypeKind kind = resolver.index().kotlinKind(resolved.name());
        return kind == TypeKind.CLASS || kind == TypeKind.ENUM;
    }

    private void addAnnotationReferences(final Component component, final List<String> annotations,
                                         final Scope scope) {
        for (final String annotation : annotations) {
            final Resolution resolved = resolver.resolve(annotation, scope, false);
            if (resolved == null) {
                continue;
            }
            final AnnotationReference reference = new AnnotationReference(resolved.name());
            reference.setResolutionKind(resolved.kind());
            ParseUtil.insertCmpRef(component, reference, stack);
        }
    }

    private void addTypeReferences(final Component component, final Collection<TypeUsage> usages, final Scope scope) {
        addTypeReferences(component, usages, scope, Set.of());
    }

    /**
     * References to the types the usages resolve to. A name used as a value on its own counts only
     * when it reads as a type name, resolves to a repository type, and nothing closer in scope -- a
     * parameter, a local, a member, a top-level property or function of the package -- may be what
     * it names.
     */
    private void addTypeReferences(final Component component, final Collection<TypeUsage> usages, final Scope scope,
                                   final Set<String> closer) {
        final Set<String> seen = new HashSet<>();
        for (final TypeUsage usage : usages) {
            if (usage.value() && (closer.contains(usage.name())
                    || !KotlinDeclarationIndex.readsAsTypeName(usage.name())
                    || resolver.index().fileClassOf(KotlinDeclarationIndex.qualify(
                            scope.file().packageName, usage.name())) != null)) {
                continue;
            }
            if (seen.add(usage.name() + "|" + usage.expression() + "|" + usage.value())) {
                addTypeReference(component, usage, scope);
            }
        }
    }

    private void addTypeReference(final Component component, final TypeUsage usage, final Scope scope) {
        final Resolution resolved = resolver.resolve(usage.name(), scope, usage.expression());
        if (resolved == null || resolved.name().isEmpty()) {
            return;
        }
        if (usage.value() && !resolver.index().isType(resolved.name())) {
            return;
        }
        final SimpleTypeReference reference = new SimpleTypeReference(resolved.name());
        reference.setResolutionKind(resolved.kind());
        ParseUtil.insertCmpRef(component, reference, stack);
    }

    /** The names closer in scope than a type for code in a member: its parameters, locals and fellow members. */
    private static Set<String> closerNames(final KotlinMemberModel member, final MemberContext context) {
        final Set<String> closer = new HashSet<>(context.memberNames);
        for (final KotlinParameterModel parameter : member.parameters) {
            closer.add(parameter.name);
        }
        for (final KotlinMemberModel local : member.locals) {
            closer.add(local.name);
        }
        return closer;
    }

    /** References to the file classes of the top-level functions a member calls. */
    private void addFileClassReferences(final Component component, final KotlinMemberModel member,
                                        final MemberContext context, final Scope scope) {
        if (member.calledNames.isEmpty()) {
            return;
        }
        final Set<String> closer = closerNames(member, context);
        final boolean implicitReceiver = member.receiverType != null || context.inheritsMembers;
        for (final String called : member.calledNames) {
            final String fileClass = resolver.fileClassOfCall(called, scope,
                    implicitReceiver || closer.contains(called) || member.calledInLambdas.contains(called));
            if (fileClass == null) {
                continue;
            }
            final SimpleTypeReference reference = new SimpleTypeReference(fileClass);
            reference.setResolutionKind(ResolutionKind.EXACT);
            ParseUtil.insertCmpRef(component, reference, stack);
        }
    }

    /** The names a type, and its companion object, declare members by. */
    private static Set<String> enclosingMemberNames(final KotlinTypeModel type) {
        final Set<String> names = new HashSet<>();
        final List<KotlinMemberModel> members = new ArrayList<>(type.members);
        for (final KotlinTypeModel nested : type.nestedTypes) {
            if (nested.companion) {
                members.addAll(nested.members);
            }
        }
        for (final KotlinMemberModel member : members) {
            names.add(member.name);
            for (final KotlinParameterModel parameter : member.parameters) {
                if (parameter.property) {
                    names.add(parameter.name);
                }
            }
        }
        return names;
    }

    private static String memberIdentifier(final KotlinMemberModel member) {
        if (member.kind != MemberKind.FUNCTION && member.kind != MemberKind.CONSTRUCTOR) {
            return member.name;
        }
        final List<String> types = new ArrayList<>();
        if (member.receiverType != null) {
            types.add(member.receiverType);
        }
        for (final KotlinParameterModel parameter : member.parameters) {
            if (parameter.declaredType == null) {
                types.add("Any");
            } else {
                types.add(parameter.declaredType);
            }
        }
        return member.name + "(" + String.join(", ", types) + ")";
    }

    private static List<String> withVisibility(final List<String> modifiers) {
        final List<String> result = new ArrayList<>();
        boolean visible = false;
        for (final String modifier : modifiers) {
            visible |= VISIBILITIES.contains(modifier);
        }
        if (!visible) {
            result.add("public");
        }
        result.addAll(modifiers);
        return result;
    }

    private static Set<String> importStatements(final KotlinFileModel file) {
        final Set<String> imports = new LinkedHashSet<>();
        for (final KotlinImport kotlinImport : file.imports) {
            if (kotlinImport.star()) {
                imports.add(kotlinImport.target() + ".*");
            } else {
                imports.add(kotlinImport.target());
            }
        }
        return imports;
    }

    private static Package packageOf(final KotlinFileModel file) {
        return new Package(file.packageName, file.packageName);
    }

    private static ComponentType componentType(final TypeKind kind) {
        switch (kind) {
            case INTERFACE:
                return ComponentType.INTERFACE;
            case ENUM:
                return ComponentType.ENUM;
            case ANNOTATION:
                return ComponentType.ANNOTATION;
            default:
                return ComponentType.CLASS;
        }
    }

    private static ComponentType memberComponentType(final MemberKind kind) {
        switch (kind) {
            case FUNCTION:
                return ComponentType.METHOD;
            case CONSTRUCTOR:
                return ComponentType.CONSTRUCTOR;
            case ENUM_ENTRY:
                return ComponentType.ENUM_CONSTANT;
            default:
                return ComponentType.FIELD;
        }
    }

    private static int startOffset(final Object declaration) {
        if (declaration instanceof KotlinTypeModel) {
            return ((KotlinTypeModel) declaration).startOffset;
        }
        return ((KotlinMemberModel) declaration).startOffset;
    }

    private static int nonZeroHash(final int hash, final String fallback) {
        if (hash != 0) {
            return hash;
        }
        final int fallbackHash = fallback.hashCode();
        if (fallbackHash != 0) {
            return fallbackHash;
        }
        return 1;
    }

    private static String nullToEmpty(final String text) {
        if (text == null) {
            return "";
        }
        return text;
    }

    /** What a member's calls are resolved in: its scope, and what in closer scope may shadow a call. */
    private static final class MemberContext {
        private final Scope scope;
        private final Set<String> memberNames;
        private final boolean inheritsMembers;

        MemberContext(final Scope scope, final Set<String> memberNames, final boolean inheritsMembers) {
            this.scope = scope;
            this.memberNames = memberNames;
            this.inheritsMembers = inheritsMembers;
        }
    }
}
