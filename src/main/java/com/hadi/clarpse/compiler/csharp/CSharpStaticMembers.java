package com.hadi.clarpse.compiler.csharp;

/**
 * Answers whether a parsed C# type declares a member that is reached through the type's own name:
 * a static method, field or property, a constant, an enum member or a nested type. The members a
 * type inherits are not considered here; callers ask each parsed ancestor in turn.
 */
final class CSharpStaticMembers {

    private CSharpStaticMembers() {
    }

    /**
     * Whether the type itself declares a member of the name that {@code Type.name} can denote.
     *
     * @param type The parsed type.
     * @param name The member name, without type arguments.
     * @return True for a static member, constant, enum member or nested type of that name.
     */
    static boolean declares(final CSharpModel.CSharpTypeModel type, final String name) {
        for (final CSharpModel.CSharpTypeModel nested : type.nestedTypes) {
            if (name.equals(nested.name)) {
                return true;
            }
        }
        for (final CSharpModel.CSharpMemberModel member : type.members) {
            if (member.name == null || "constructor".equals(member.kind)
                    || !name.equals(member.name.replaceAll("<.*$", ""))) {
                continue;
            }
            if ("enumMember".equals(member.kind) || member.modifiers.contains("static")
                    || member.modifiers.contains("const")) {
                return true;
            }
        }
        return false;
    }

    /** The member a static receiver's leading name is used with: the next segment, else the name after the dot. */
    static String memberAfter(final CSharpModel.CSharpMemberModel member, final int index) {
        final String[] segments = member.staticReceivers.get(index).split("\\.");
        if (segments.length > 1) {
            return segments[1];
        }
        if (index < member.staticReceiverMembers.size()) {
            return member.staticReceiverMembers.get(index);
        }
        return "";
    }

    /** Whether the named type, or a type enclosing it, declares a member of the name. */
    static boolean enclosingDeclares(final java.util.Map<String, CSharpModel.CSharpTypeModel> types,
                                     final String uniqueName, final String name) {
        for (String level = uniqueName; level != null && !level.isEmpty();
             level = level.substring(0, Math.max(0, level.lastIndexOf('.')))) {
            final CSharpModel.CSharpTypeModel enclosing = types.get(level);
            if (enclosing != null && enclosing.members.stream().anyMatch(m -> name.equals(m.name))) {
                return true;
            }
        }
        return false;
    }
}
