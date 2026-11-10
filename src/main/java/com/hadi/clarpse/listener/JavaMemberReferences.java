package com.hadi.clarpse.listener;

import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.hadi.clarpse.reference.MemberKind;
import com.hadi.clarpse.reference.MemberReference;
import com.hadi.clarpse.reference.ResolutionKind;

/**
 * Builds the {@link MemberReference} for a Java method call whose receiver type the symbol solver
 * has resolved to a declaration.
 *
 * <p>The owner is decided from declarations, never from spelling. A receiver type that the parse
 * declares (it has a syntax tree) is a type of the codebase; its member is attributed to the type
 * that declares the called method when that is also a type of the codebase, which is how a method
 * inherited from a parsed base is found, and to the receiver's type otherwise. Any other resolved
 * declaration is a library type, whose own package is its unit, and the owner is the receiver's
 * type.
 */
final class JavaMemberReferences {

    private JavaMemberReferences() {
    }

    /**
     * The reference for a call, or null when the receiver's type has no usable name.
     *
     * @param receiver  The resolved declaration of the receiver's type.
     * @param declaring The declaration of the type declaring the called method, or null when the
     *                  call itself did not resolve.
     * @param member    The called method's simple name.
     * @return The member reference, or null when no name for the owner can be given.
     */
    static MemberReference of(final ResolvedReferenceTypeDeclaration receiver,
                              final ResolvedReferenceTypeDeclaration declaring, final String member) {
        if (isParsed(receiver)) {
            ResolvedReferenceTypeDeclaration owner = receiver;
            if (declaring != null && isParsed(declaring)) {
                owner = declaring;
            }
            return reference(owner, member, null);
        }
        return reference(receiver, member, receiver.getPackageName());
    }

    private static MemberReference reference(final ResolvedReferenceTypeDeclaration owner, final String member,
                                             final String unit) {
        final String name = owner.getQualifiedName();
        if (name == null || name.isEmpty() || member == null || member.isEmpty()
                || !Character.isUpperCase(name.charAt(name.lastIndexOf('.') + 1))) {
            return null;
        }
        String packageName = unit;
        if (packageName != null && packageName.isEmpty()) {
            packageName = null;
        }
        final MemberReference reference = new MemberReference(name, member, MemberKind.CALL, packageName);
        reference.setResolutionKind(ResolutionKind.EXACT);
        return reference;
    }

    private static boolean isParsed(final ResolvedReferenceTypeDeclaration declaration) {
        try {
            return declaration.toAst().isPresent();
        } catch (final RuntimeException unknown) {
            return false;
        }
    }
}
