package com.lehnade.mbia.genealogy.domain;

/**
 * What a target Person is to a reference Person (person-relationships-collaboration.md §10). The
 * gendered codes follow the target Person's gender (localization-and-kinship-labels.md §2).
 */
public enum KinshipCode {
    SELF,
    FATHER,
    MOTHER,
    PARENT,
    SON,
    DAUGHTER,
    CHILD,
    GRANDFATHER,
    GRANDMOTHER,
    GRANDPARENT,
    GRANDSON,
    GRANDDAUGHTER,
    GRANDCHILD,
    BROTHER,
    SISTER,
    SIBLING,
    UNCLE,
    AUNT,
    PARENT_SIBLING,
    NEPHEW,
    NIECE,
    SIBLING_CHILD,
    FIRST_COUSIN,
    PARTNER,
    /** A path exists, but no supported concise label. */
    RELATED,
    /** No path in the current Mbia graph; never means that the people are unrelated. */
    NONE_KNOWN
}
