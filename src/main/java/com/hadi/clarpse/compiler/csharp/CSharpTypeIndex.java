package com.hadi.clarpse.compiler.csharp;

import com.hadi.clarpse.reference.ResolutionKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * The repository's types, looked up by the names source can reach them by.
 *
 * <p>Source names a type without its arity in the name: {@code Converter} and
 * {@code Converter<int>} both spell {@code Converter}, and differ in the number of type
 * arguments written after it. Every lookup therefore takes that number. A name that only one
 * type carries in a scope resolves to it whatever the number, as it did before arity was read.
 * A name several types of different arities carry resolves to the one whose arity matches, and
 * to none of them when none matches, so lookup moves on to the next scope. When the number is
 * unknown it resolves to the one with the fewest type parameters, which is the non-generic type
 * when there is one: the type a reference with no type arguments names.
 */
final class CSharpTypeIndex {

    /** A resolved type name, and the evidence that settled it. */
    static final class Resolution {
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

    /** Unique names by lookup name ({@link CSharpTypeNames#lookupName}), then by arity. */
    final Map<String, NavigableMap<Integer, String>> typesByLookupName = new HashMap<>();
    /** Every namespace the repository declares a type in, and every namespace enclosing one. */
    final Set<String> namespaces = new HashSet<>();
    final Map<String, String> memberByTypeAndName = new HashMap<>();
    final Map<String, CSharpModel.CSharpTypeModel> typeModelsByName = new HashMap<>();
    final Set<String> interfaceTypes = new LinkedHashSet<>();
    final Set<String> enumTypes = new LinkedHashSet<>();
    /** Extension methods by method name, then by the unique name of the static class that declares them. */
    final Map<String, Map<String, List<CSharpModel.CSharpMemberModel>>> extensionMethods =
            new HashMap<>();

    /** Whether the repository declares an extension method of the name. */
    boolean hasExtensionMethodNamed(final String name) {
        return extensionMethods.containsKey(name);
    }

    static String bareName(final String memberName) {
        return memberName.replaceAll("<.*$", "");
    }

    /**
     * The unique name of the one static class that declares an extension method the call can be,
     * or null when there is none, several, the receiver's declared type is not known from the
     * source, or its own type has a member of that name.
     */
    String soleExtensionClass(final CSharpModel.ExtensionCall call,
                                      final CSharpModel.CSharpMemberModel caller,
                                      final CSharpModel.CSharpTypeModel owner,
                                      final Map<CSharpModel.CSharpMemberModel, CSharpModelAssembler.WrittenType> inferred) {
        final Map<String, List<CSharpModel.CSharpMemberModel>> byClass = extensionMethods.get(call.name());
        if (byClass == null) {
            return null;
        }
        final Receiver receiver = receiverOf(call, caller, owner);
        if (receiver == null) {
            return null;
        }
        if (receiver.model() != null && declaresMemberNamed(receiver.model(), call.name())) {
            return null;
        }
        String found = null;
        for (final Map.Entry<String, List<CSharpModel.CSharpMemberModel>> entry : byClass.entrySet()) {
            final CSharpModel.CSharpTypeModel declaring = typeModelsByName.get(entry.getKey());
            if (declaring == null || !isVisibleFrom(declaring, owner, caller)) {
                continue;
            }
            boolean candidate = false;
            for (final CSharpModel.CSharpMemberModel method : entry.getValue()) {
                if (acceptsArguments(method, call.argumentCount())
                        && canTakeReceiver(method, declaring, receiver)) {
                    candidate = true;
                    break;
                }
            }
            if (!candidate) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = entry.getKey();
        }
        return found;
    }

    /**
     * The declared type of a call's receiver: a parsed type when it names one, else the type as
     * written. {@code declared} is null for {@code this}, whose type is the enclosing type.
     */
    record Receiver(CSharpModel.CSharpTypeModel model, CSharpModelAssembler.WrittenType declared) {
    }

    /**
     * The declared type of the receiver of a call, or null when it is not certain. Only a simple
     * name qualifies: {@code this}, or a parameter, local, lambda parameter, field or property whose
     * declaration writes its type. A call result, a chain of members, an implicitly typed variable
     * (apart from {@code var x = new T(...)}) and a name that is declared more than once with
     * different types give null.
     */
    Receiver receiverOf(final CSharpModel.ExtensionCall call,
                                final CSharpModel.CSharpMemberModel caller,
                                final CSharpModel.CSharpTypeModel owner) {
        final String name = call.receiver();
        if (name == null || name.indexOf('.') >= 0) {
            return null;
        }
        if ("this".equals(name)) {
            return new Receiver(owner, null);
        }
        final Set<String> distinct = new LinkedHashSet<>();
        boolean declared = false;
        CSharpModelAssembler.WrittenType written = null;
        for (final CSharpModel.CSharpParameterModel parameter : caller.parameters) {
            if (name.equals(parameter.name)) {
                declared = true;
                if (parameter.declaredType == null || parameter.declaredType.isBlank()) {
                    return null;
                }
                distinct.add(parameter.declaredType.trim());
                written = new CSharpModelAssembler.WrittenType(parameter.declaredType, owner, caller);
            }
        }
        for (final CSharpModel.CSharpMemberModel local : caller.locals) {
            if (!name.equals(local.name)) {
                continue;
            }
            declared = true;
            final String type = local.declaredType;
            if (type == null || type.isBlank() || "var".equals(type.trim())
                    || (local.inferredType && !local.plainNewInitializer)) {
                return null;
            }
            distinct.add(type.trim());
            written = new CSharpModelAssembler.WrittenType(type, owner, caller);
        }
        final List<String> lambdaTypes = caller.lambdaVariables.get(name);
        if (lambdaTypes != null) {
            for (final String type : lambdaTypes) {
                declared = true;
                if (type == null || type.isBlank()) {
                    return null;
                }
                distinct.add(type.trim());
                written = new CSharpModelAssembler.WrittenType(type, owner, caller);
            }
        }
        if (!declared) {
            final CSharpModelAssembler.FoundMember field = findMember(owner, name, false);
            if (field == null || field.member().declaredType == null || field.member().declaredType.isBlank()
                    || CSharpDeclaredNames.bodyMayDeclare(caller, name)) {
                return null;
            }
            return receiverOfType(new CSharpModelAssembler.WrittenType(field.member().declaredType, field.declaring(), field.member()));
        }
        if (distinct.size() != 1) {
            return null;
        }
        int known = 0;
        for (final CSharpModel.CSharpParameterModel parameter : caller.parameters) {
            if (name.equals(parameter.name)) {
                known += 1;
            }
        }
        for (final CSharpModel.CSharpMemberModel local : caller.locals) {
            if (name.equals(local.name)) {
                known += 1;
            }
        }
        if (lambdaTypes != null) {
            known += lambdaTypes.size();
        }
        if (CSharpDeclaredNames.bodyMayDeclare(caller, name, known)) {
            return null;
        }
        return receiverOfType(written);
    }

    Receiver receiverOfType(final CSharpModelAssembler.WrittenType written) {
        final String text = written.text().trim();
        if ("var".equals(text) || "dynamic".equals(text) || isArrayOrPointer(text)) {
            return null;
        }
        return new Receiver(typeModelOf(written), written);
    }

    /** Whether the type, or a parsed base of it, has any member of the name. */
    boolean declaresMemberNamed(final CSharpModel.CSharpTypeModel type, final String name) {
        return declaringTypeOf(type, name) != null;
    }

    /** The type itself, else the nearest parsed base, that declares a member of the name; null if none does. */
    CSharpModel.CSharpTypeModel declaringTypeOf(final CSharpModel.CSharpTypeModel type,
                                                        final String name) {
        for (final CSharpModel.CSharpTypeModel candidate : ancestorsAndSelf(type)) {
            for (final CSharpModel.CSharpMemberModel member : candidate.members) {
                if (member.name != null && name.equals(bareName(member.name))) {
                    return candidate;
                }
            }
        }
        return null;
    }

    List<CSharpModel.CSharpTypeModel> ancestorsAndSelf(final CSharpModel.CSharpTypeModel type) {
        final Map<String, CSharpModel.CSharpTypeModel> visited = new java.util.LinkedHashMap<>();
        final java.util.Deque<CSharpModel.CSharpTypeModel> pending = new java.util.ArrayDeque<>();
        pending.add(type);
        visited.put(type.uniqueName, type);
        while (!pending.isEmpty()) {
            final CSharpModel.CSharpTypeModel current = pending.poll();
            for (final String baseType : current.baseTypes) {
                for (final CSharpFileParser.TypeToken token : CSharpFileParser.extractTypeReferences(baseType)) {
                    final Resolution resolved = resolveType(token.name(), token.arity(), current, null);
                    if (resolved != null) {
                        final CSharpModel.CSharpTypeModel base = typeModelsByName.get(resolved.name());
                        if (base != null && !visited.containsKey(base.uniqueName)) {
                            visited.put(base.uniqueName, base);
                            pending.add(base);
                        }
                    }
                    break;
                }
            }
        }
        return new ArrayList<>(visited.values());
    }

    /** Whether a static class is in scope in the calling file: by namespace, enclosing namespace or using. */
    boolean isVisibleFrom(final CSharpModel.CSharpTypeModel declaring,
                                  final CSharpModel.CSharpTypeModel owner,
                                  final CSharpModel.CSharpMemberModel caller) {
        final String namespaceName = declaring.namespaceName;
        if (namespaceName == null || namespaceName.isEmpty()) {
            return true;
        }
        String current = owner.namespaceName;
        while (current != null && !current.isEmpty()) {
            if (current.equals(namespaceName)) {
                return true;
            }
            final int cut = current.lastIndexOf('.');
            if (cut < 0) {
                break;
            }
            current = current.substring(0, cut);
        }
        final Set<String> aliased = new LinkedHashSet<>(owner.usingAliases.values());
        aliased.addAll(caller.usingAliases.values());
        final Set<String> imports = new LinkedHashSet<>(owner.imports);
        imports.addAll(caller.imports);
        for (final String imported : imports) {
            if (imported.equals(declaring.uniqueName)
                    || (imported.equals(namespaceName) && !aliased.contains(imported))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the extension method can be called with that many arguments; -1 is not known. */
    static boolean acceptsArguments(final CSharpModel.CSharpMemberModel method, final int count) {
        if (count < 0) {
            return true;
        }
        int required = 0;
        boolean open = false;
        for (int i = 1; i < method.parameters.size(); i++) {
            final CSharpModel.CSharpParameterModel parameter = method.parameters.get(i);
            if (parameter.paramsArray) {
                open = true;
            } else if (!parameter.optional) {
                required += 1;
            }
        }
        final int most = method.parameters.size() - 1;
        return count >= required && (open || count <= most);
    }

    /**
     * Whether the receiver's declared type fits the type the method extends: the extended type is
     * a type parameter of the method and the receiver's type is parsed, or the same type, or a parsed base type or interface of the
     * receiver's. Two parsed types are compared as types; two types the parse does not hold are
     * compared by simple name with generic arguments dropped; a parsed type against an unparsed
     * one does not fit. A parsed receiver type with an ancestor the parse does not hold fits
     * nothing, for the ancestor may declare the member the call names.
     */
    boolean canTakeReceiver(final CSharpModel.CSharpMemberModel method,
                                    final CSharpModel.CSharpTypeModel declaring,
                                    final Receiver receiver) {
        final String extended = method.parameters.get(0).declaredType;
        if (extended == null || extended.isBlank()) {
            return false;
        }
        if (receiver.model() != null && !hasFullyParsedAncestry(receiver.model())) {
            return false;
        }
        if (method.typeParameters.stream().anyMatch(extended.trim()::equals)) {
            return receiver.model() != null;
        }
        final CSharpModel.CSharpTypeModel extendedModel = typeModelOf(new CSharpModelAssembler.WrittenType(extended, declaring, method));
        if (receiver.model() != null) {
            if (extendedModel == null) {
                return false;
            }
            for (final CSharpModel.CSharpTypeModel ancestor : ancestorsAndSelf(receiver.model())) {
                if (ancestor.uniqueName.equals(extendedModel.uniqueName)) {
                    return true;
                }
            }
            return false;
        }
        if (extendedModel != null) {
            return false;
        }
        final String received = simpleTypeName(receiver.declared().text());
        return received != null && received.equals(simpleTypeName(extended));
    }

    /** The last name of a written type with generic arguments and a nullable mark dropped, or null for a compound type. */
    static String simpleTypeName(final String written) {
        String text = written.trim().replaceAll("\\s+", "");
        text = text.replaceAll("<.*>", "");
        if (text.endsWith("?")) {
            text = text.substring(0, text.length() - 1);
        }
        if (!text.matches("[A-Za-z_@][\\w@]*(\\.[A-Za-z_@][\\w@]*)*")) {
            return null;
        }
        return text.substring(text.lastIndexOf('.') + 1);
    }

    /** Whether the repository declares the named type as an enum. */
    boolean isEnum(final String uniqueName) {
        return enumTypes.contains(uniqueName);
    }

    CSharpTypeIndex(final List<CSharpModel.CSharpTypeModel> types) {
        for (final CSharpModel.CSharpTypeModel type : types) {
            final String lookupName = CSharpTypeNames.lookupName(type.uniqueName);
            typesByLookupName.computeIfAbsent(lookupName, ignored -> new TreeMap<>())
                    .putIfAbsent(type.arity, type.uniqueName);
            typeModelsByName.putIfAbsent(type.uniqueName, type);
            for (String space = type.namespaceName; space != null && !space.isEmpty();
                 space = space.substring(0, Math.max(0, space.lastIndexOf('.')))) {
                namespaces.add(space);
            }
            if ("interface".equals(type.kind)) {
                interfaceTypes.add(type.uniqueName);
            }
            if ("enum".equals(type.kind)) {
                enumTypes.add(type.uniqueName);
            }
            if ("class".equals(type.kind) && type.modifiers.contains("static") && type.arity == 0
                    && isDeclaredDirectlyInNamespace(type)) {
                for (final CSharpModel.CSharpMemberModel member : type.members) {
                    if ("method".equals(member.kind) && member.name != null && !member.parameters.isEmpty()
                            && member.parameters.get(0).extensionReceiver) {
                        extensionMethods.computeIfAbsent(bareName(member.name), key -> new HashMap<>())
                                .computeIfAbsent(type.uniqueName, key -> new ArrayList<>()).add(member);
                    }
                }
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
                            type.uniqueName + "." + CSharpModelAssembler.memberComponentIdentifier(member, type));
                }
            }
        }
    }

    static boolean isDeclaredDirectlyInNamespace(final CSharpModel.CSharpTypeModel type) {
        if (type.namespaceName == null || type.namespaceName.isEmpty()) {
            return type.uniqueName.equals(type.name);
        }
        return type.uniqueName.equals(type.namespaceName + "." + type.name);
    }

    /**
     * Whether a base type of the type, or a base type of that, declares a member of the name.
     * A member that is inherited is in scope in a body exactly as one declared in the type.
     */
    boolean inheritsMember(final CSharpModel.CSharpTypeModel type, final String memberName) {
        final Set<String> visited = new LinkedHashSet<>();
        final java.util.Deque<CSharpModel.CSharpTypeModel> pending = new java.util.ArrayDeque<>();
        pending.add(type);
        while (!pending.isEmpty()) {
            final CSharpModel.CSharpTypeModel current = pending.poll();
            for (final String baseType : current.baseTypes) {
                for (final CSharpFileParser.TypeToken token : CSharpFileParser.extractTypeReferences(baseType)) {
                    final Resolution resolved = resolveType(token.name(), token.arity(), current, null);
                    CSharpModel.CSharpTypeModel base = null;
                    if (resolved != null) {
                        base = typeModelsByName.get(resolved.name());
                    }
                    if (base != null && visited.add(base.uniqueName)) {
                        for (final CSharpModel.CSharpMemberModel member : base.members) {
                            if (memberName.equals(member.name)) {
                                return true;
                            }
                        }
                        pending.add(base);
                    }
                    break;
                }
            }
        }
        return false;
    }

    /**
     * Whether a written name is a namespace rather than a type: one the repository declares types
     * in, or the start of one, or one a using directive of the scope names or begins.
     */
    boolean isNamespace(final String written, final CSharpModel.CSharpTypeModel type,
                                final CSharpModel.CSharpMemberModel member) {
        if (namespaces.contains(written)) {
            return true;
        }
        final Set<String> imported = new LinkedHashSet<>(type.imports);
        imported.addAll(member.imports);
        for (final String target : imported) {
            if (target.equals(written) || target.startsWith(written + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a bare name written in the type's body may be a member inherited from a base the
     * parse does not hold. Looking outward from the type, a nested type of that name hides what
     * is inherited, so it ends the search; a type whose ancestry is not fully parsed answers yes.
     */
    boolean mayBeInheritedMember(final String name, final CSharpModel.CSharpTypeModel type) {
        String current = type.uniqueName;
        while (current != null && !current.isEmpty()) {
            if (lookup(current + "." + name, CSharpTypeNames.UNKNOWN_ARITY) != null) {
                return false;
            }
            final CSharpModel.CSharpTypeModel level = typeModelsByName.get(current);
            if (level != null && !hasFullyParsedAncestry(level)) {
                return true;
            }
            final int cut = current.lastIndexOf('.');
            if (cut < 0) {
                return false;
            }
            current = current.substring(0, cut);
        }
        return false;
    }

    /**
     * Whether {@code name.member} is a static use of the repository type {@code name} resolves to
     * exactly: it or a parsed supertype declares that static member, and no class around declares {@code name}.
     */
    boolean isStaticUseOfType(final String name, final String member,
                                      final CSharpModel.CSharpTypeModel ownerType,
                                      final CSharpModel.CSharpMemberModel memberModel) {
        final Resolution resolved = resolveType(name, ownerType, memberModel);
        if (member.isEmpty() || resolved == null || resolved.kind() != ResolutionKind.EXACT
                || !typeModelsByName.containsKey(resolved.name())
                || CSharpStaticMembers.enclosingDeclares(typeModelsByName, ownerType.uniqueName, name)) {
            return false;
        }
        return ancestorsAndSelf(typeModelsByName.get(resolved.name())).stream().anyMatch(t -> CSharpStaticMembers.declares(t, member));
    }

    /**
     * Whether every ancestor of the type, to the root, is a parsed type: no base class or interface
     * anywhere in the chain is missing from the parse. Only then do the members the type and its
     * ancestors declare make up everything it can be called with.
     */
    boolean hasFullyParsedAncestry(final CSharpModel.CSharpTypeModel type) {
        final Set<String> visited = new LinkedHashSet<>();
        final java.util.Deque<CSharpModel.CSharpTypeModel> pending = new java.util.ArrayDeque<>();
        pending.add(type);
        visited.add(type.uniqueName);
        while (!pending.isEmpty()) {
            final CSharpModel.CSharpTypeModel current = pending.poll();
            for (final String baseType : current.baseTypes) {
                final List<CSharpFileParser.TypeToken> tokens = CSharpFileParser.extractTypeReferences(baseType);
                if (tokens.isEmpty()) {
                    return false;
                }
                final Resolution resolved = resolveType(tokens.get(0).name(), tokens.get(0).arity(), current, null);
                if (resolved == null || resolved.kind() == ResolutionKind.UNRESOLVED
                        || resolved.kind() == ResolutionKind.AMBIGUOUS) {
                    return false;
                }
                final CSharpModel.CSharpTypeModel base = typeModelsByName.get(resolved.name());
                if (base == null) {
                    return false;
                }
                if (visited.add(base.uniqueName)) {
                    pending.add(base);
                }
            }
        }
        return true;
    }

    /**
     * Whether a written type is an array or a pointer, whose members are not those of the element
     * type it is written with.
     */
    static boolean isArrayOrPointer(final String written) {
        return written != null && (written.indexOf('[') >= 0 || written.indexOf('*') >= 0);
    }

    /** The parsed type a written type names, when it names exactly one and not a type parameter. */
    CSharpModel.CSharpTypeModel typeModelOf(final CSharpModelAssembler.WrittenType written) {
        if (isArrayOrPointer(written.text())) {
            return null;
        }
        final List<CSharpFileParser.TypeToken> tokens = CSharpFileParser.extractTypeReferences(written.text());
        if (tokens.size() != 1) {
            return null;
        }
        final CSharpFileParser.TypeToken token = tokens.get(0);
        if (written.scope().typeParameters.contains(token.name())
                || (written.member() != null && written.member().typeParameters.contains(token.name()))) {
            return null;
        }
        final Resolution resolved = resolveType(token.name(), token.arity(), written.scope(), written.member());
        if (resolved == null || resolved.kind() == ResolutionKind.UNRESOLVED
                || resolved.kind() == ResolutionKind.AMBIGUOUS) {
            return null;
        }
        return typeModelsByName.get(resolved.name());
    }

    /**
     * The field or property -- or, for a call, the method -- of the name that the type or its
     * nearest base in the parse declares. Methods of one name with different return types, and
     * names declared in two parts of a partial type, are not decided between.
     */
    CSharpModelAssembler.FoundMember findMember(final CSharpModel.CSharpTypeModel type, final String name,
                                   final boolean call) {
        final Set<String> visited = new LinkedHashSet<>();
        final java.util.Deque<CSharpModel.CSharpTypeModel> pending = new java.util.ArrayDeque<>();
        pending.add(type);
        visited.add(type.uniqueName);
        while (!pending.isEmpty()) {
            final CSharpModel.CSharpTypeModel current = pending.poll();
            CSharpModelAssembler.FoundMember found = null;
            final Set<String> returnTypes = new LinkedHashSet<>();
            for (final CSharpModel.CSharpMemberModel member : current.members) {
                if (member.name == null || !name.equals(member.name.replaceAll("<.*$", ""))) {
                    continue;
                }
                final boolean isMethod = "method".equals(member.kind);
                final boolean isValue = "field".equals(member.kind) || "property".equals(member.kind);
                if ((call && !isMethod) || (!call && !isValue)) {
                    return null;
                }
                if (found != null && !call) {
                    return null;
                }
                found = new CSharpModelAssembler.FoundMember(member, current);
                returnTypes.add(String.valueOf(member.returnType));
            }
            if (found != null) {
                if (returnTypes.size() > 1) {
                    return null;
                }
                return found;
            }
            for (final String baseType : current.baseTypes) {
                for (final CSharpFileParser.TypeToken token : CSharpFileParser.extractTypeReferences(baseType)) {
                    final Resolution resolved = resolveType(token.name(), token.arity(), current, null);
                    CSharpModel.CSharpTypeModel base = null;
                    if (resolved != null) {
                        base = typeModelsByName.get(resolved.name());
                    }
                    if (base != null && visited.add(base.uniqueName)) {
                        pending.add(base);
                    }
                    break;
                }
            }
        }
        return null;
    }

    Resolution resolveType(final String rawToken,
                                   final CSharpModel.CSharpTypeModel ownerType,
                                   final CSharpModel.CSharpMemberModel memberModel) {
        return resolveType(rawToken, CSharpTypeNames.UNKNOWN_ARITY, ownerType, memberModel);
    }

    /**
     * The type a name written in source denotes.
     *
     * @param rawToken    The name as written, possibly qualified.
     * @param arity       The number of type arguments written with it, or
     *                    {@link CSharpTypeNames#UNKNOWN_ARITY}.
     * @param ownerType   The type the name is written in.
     * @param memberModel The member the name is written in, if any.
     * @return The resolution, or {@code null} for no name.
     */
    Resolution resolveType(final String rawToken,
                                   final int arity,
                                   final CSharpModel.CSharpTypeModel ownerType,
                                   final CSharpModel.CSharpMemberModel memberModel) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        final String cleaned = rawToken.replace("?", "").trim();
        final String builtin = CSharpModelAssembler.BUILTIN_TYPES.get(cleaned);
        if (builtin != null) {
            return new Resolution(builtin, ResolutionKind.EXACT);
        }
        String aliasTarget = null;
        if (memberModel != null) {
            aliasTarget = memberModel.usingAliases.get(cleaned);
        }
        if (aliasTarget == null) {
            aliasTarget = ownerType.usingAliases.get(cleaned);
        }
        if (aliasTarget != null && !aliasTarget.isEmpty()) {
            return new Resolution(aliasTarget, ResolutionKind.EXACT);
        }
        final String exact = lookup(cleaned, arity);
        if (exact != null) {
            return new Resolution(exact, ResolutionKind.EXACT);
        }
        final String nestedCandidate = resolveNested(cleaned, arity, ownerType);
        if (nestedCandidate != null) {
            return new Resolution(nestedCandidate, ResolutionKind.EXACT);
        }
        final String namespaceCandidate = resolveNamespace(cleaned, arity, ownerType.namespaceName);
        if (namespaceCandidate != null) {
            return new Resolution(namespaceCandidate, ResolutionKind.EXACT);
        }
        // A using is evidence; a name match across the repository is not. The usings of the
        // file are one level of lookup: a name that exactly one of them supplies resolves to it,
        // and a name that several supply is ambiguous (CS0104) and binds to none of them.
        final Set<String> supplied = resolveUsing(cleaned, arity, ownerType.imports);
        if (supplied.size() == 1) {
            return new Resolution(supplied.iterator().next(), ResolutionKind.EXACT);
        }
        if (supplied.size() > 1) {
            return new Resolution(cleaned, ResolutionKind.AMBIGUOUS);
        }
        // Nothing visible from the file's scope carries this name: not the enclosing types, the
        // namespace or the namespaces enclosing it, a using, an alias or a global using. A
        // repository type of the same short name in a namespace the name is not visible from is
        // not the type meant -- `ScrollViewer` written against `using Avalonia.Controls;` is
        // Avalonia's, however many repository types are called that, so that count decides
        // nothing. The name stays as written, and a consumer drops an edge that points outside
        // the codebase. Silence costs a missing edge; a guess costs a fabricated one.
        final String bcl = CSharpModelAssembler.BCL_TYPES.get(cleaned);
        if (bcl != null) {
            return new Resolution(bcl, ResolutionKind.EXACT);
        }
        return new Resolution(cleaned, ResolutionKind.UNRESOLVED);
    }

    /**
     * The type a lookup name denotes given the number of type arguments written with it, as
     * {@link CSharpTypeIndex} describes.
     *
     * @param lookupName A scope's unique name followed by a simple name, or a name as written.
     * @param arity      The number of type arguments, or {@link CSharpTypeNames#UNKNOWN_ARITY}.
     * @return The type's unique name, or {@code null} when no type of that name and arity exists.
     */
    String lookup(final String lookupName, final int arity) {
        final NavigableMap<Integer, String> byArity = typesByLookupName.get(lookupName);
        if (byArity == null) {
            return null;
        }
        if (byArity.size() == 1 || arity == CSharpTypeNames.UNKNOWN_ARITY) {
            return byArity.firstEntry().getValue();
        }
        return byArity.get(arity);
    }

    String resolveMember(final String ownerTypeUniqueName, final String memberName) {
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

    String resolveNested(final String simpleName, final int arity,
                                 final CSharpModel.CSharpTypeModel ownerType) {
        if (ownerType == null) {
            return null;
        }
        String current = ownerType.uniqueName;
        while (current != null && !current.isEmpty()) {
            final String candidate = lookup(current + "." + simpleName, arity);
            if (candidate != null) {
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
    String resolveNamespace(final String simpleName, final int arity, final String namespaceName) {
        if (namespaceName == null || namespaceName.isEmpty()) {
            return null;
        }
        final String candidate = lookup(namespaceName + "." + simpleName, arity);
        if (candidate != null) {
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
            final String outer = lookup(enclosing + "." + simpleName, arity);
            if (outer != null) {
                return outer;
            }
        }
    }

    /** The types the usings of a scope supply under the name: none, one, or several that clash. */
    Set<String> resolveUsing(final String simpleName, final int arity, final Set<String> imports) {
        final Set<String> supplied = new LinkedHashSet<>();
        if (imports == null) {
            return supplied;
        }
        for (final String importTarget : imports) {
            if (importTarget == null || importTarget.isBlank()) {
                continue;
            }
            if (importTarget.endsWith("." + simpleName)) {
                final String imported = lookup(importTarget, arity);
                if (imported != null) {
                    supplied.add(imported);
                }
            }
            final String candidate = lookup(importTarget + "." + simpleName, arity);
            if (candidate != null) {
                supplied.add(candidate);
            }
        }
        return supplied;
    }
}
