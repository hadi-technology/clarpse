package com.hadi.clarpse.reference;

/**
 * How a member is used by the component holding a {@link MemberReference}.
 */
public enum MemberKind {

    /** The member is called: {@code receiver.member(...)}, or a static call through a type name. */
    CALL,

    /** The member is read as a field or property: {@code receiver.member}. */
    FIELD_READ
}
