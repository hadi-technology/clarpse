package com.hadi.clarpse.reference;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Objects;

/**
 * Reference indicating that a component uses one named member of a type, such as a method it calls.
 *
 * <p>It is the finer companion of the type reference to the same type: where a
 * {@link SimpleTypeReference} says "this component uses {@code Order}", a member reference says
 * "this component calls {@code Order.total}". A consumer that wants only the type-level graph may
 * ignore this kind; it then sees the same graph it would see without it, and at most a reference
 * to a type it already references.
 *
 * <p><b>The owner.</b> {@link #invokedComponent()} and {@link #ownerType()} are the same value:
 * the name of the owning type, written the way a type reference to that type is written in the
 * language -- the internal component name for a type of the codebase, the external name form for a
 * library type. The owner is therefore classified (internal, external, not loaded) and resolved
 * exactly as a type reference to it is, and {@link #resolutionKind()} follows the receiver type's
 * resolution.
 * <ul>
 *   <li>For a type of the codebase, the owner is the type that declares the member when the parse
 *   finds it on the receiver's type or on its parsed supertypes, and the receiver's type
 *   otherwise.</li>
 *   <li>For a library type, the owner is the receiver's type, since the library's own declaring
 *   type is not read.</li>
 * </ul>
 *
 * <p><b>The member.</b> {@link #member()} is the simple name as written, with no parameters:
 * overloads are not told apart, so the member name is the unit. {@link #memberKind()} says how the
 * member is used.
 *
 * <p><b>The unit.</b> For an owner outside the codebase, {@link #externalUnit()} is the unit that
 * supplies the owner: the npm package, the Java or Kotlin package, the C# namespace or the Python
 * module. Node built-in modules are always spelled with the {@code node:} prefix, so
 * {@code import "fs"} and {@code import "node:fs"} both give {@code node:fs}. It is {@code null}
 * for an owner inside the codebase.
 *
 * <p><b>When it is recorded.</b> Only when the receiver's type is known exactly from the source --
 * a written type, a parameter or field type, a local the front end types, or a static call through
 * a type name -- and, for a library type, only when the type's name and unit are known exactly from
 * an import or a resolved declaration. A call on a receiver of unknown type adds nothing.
 *
 * <p><b>Equality</b> covers the class, the owner, the member and the kind, so one component can
 * reference several members of one type. The unit and the resolution kind take no part in it.
 *
 * <p><b>TypeScript.</b> A call on a library-typed receiver is also still recorded as a
 * {@link SimpleTypeReference} whose target is the string {@code <package>:<Type>.<member>}. That
 * string form is deprecated, is kept only for compatibility with earlier releases, and is removed
 * in 12.0. Use this kind instead.
 */
public final class MemberReference extends ComponentReference implements Serializable {

    private static final long serialVersionUID = 3350227816420775210L;
    @JsonIgnore
    public final String type = "member";
    private String member = "";
    private MemberKind memberKind = MemberKind.CALL;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String externalUnit;

    public MemberReference() {
        super();
    }

    /**
     * A reference to a member of a type of the codebase, or of a library type whose unit is not
     * recorded.
     *
     * @param ownerType The owning type's name, as a type reference to it would write it.
     * @param member    The member's simple name.
     * @param kind      How the member is used.
     */
    public MemberReference(final String ownerType, final String member, final MemberKind kind) {
        this(ownerType, member, kind, null);
    }

    /**
     * A reference to a member of a type.
     *
     * @param ownerType    The owning type's name, as a type reference to it would write it.
     * @param member       The member's simple name.
     * @param kind         How the member is used.
     * @param externalUnit The package, namespace, module or npm package of an external owner, or
     *                     {@code null} for an owner inside the codebase.
     */
    public MemberReference(final String ownerType, final String member, final MemberKind kind,
                           final String externalUnit) {
        super(ownerType);
        if (member != null) {
            this.member = member.intern();
        }
        if (kind != null) {
            this.memberKind = kind;
        }
        if (externalUnit != null) {
            this.externalUnit = externalUnit.intern();
        }
    }

    /**
     * The owning type; the same value as {@link #invokedComponent()}.
     *
     * @return The owning type's name.
     */
    public String ownerType() {
        return invokedComponent();
    }

    /**
     * The member's simple name, as written.
     *
     * @return The name, without parameters.
     */
    public String member() {
        return member;
    }

    /**
     * How the member is used.
     *
     * @return The kind of use.
     */
    public MemberKind memberKind() {
        return memberKind;
    }

    /**
     * The unit supplying an owner outside the codebase.
     *
     * @return The npm package, Java or Kotlin package, C# namespace or Python module; {@code null}
     *         when the owner is inside the codebase or its unit is not known.
     */
    public String externalUnit() {
        if (!isExternal()) {
            return null;
        }
        return externalUnit;
    }

    @Override
    public int priority() {
        return 2;
    }

    @Override
    public String toString() {
        return "MemberReference[" + invokedComponent() + "#" + member + "]";
    }

    @Override
    public boolean equals(final Object obj) {
        if (!super.equals(obj)) {
            return false;
        }
        final MemberReference other = (MemberReference) obj;
        return member.equals(other.member) && memberKind == other.memberKind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), member, memberKind);
    }

    @Override
    public Object clone() {
        final MemberReference copy = new MemberReference(invokedComponent(), member, memberKind, externalUnit);
        copy.setResolutionKind(resolutionKind());
        return copy;
    }
}
