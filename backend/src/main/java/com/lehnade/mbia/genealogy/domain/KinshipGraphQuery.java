package com.lehnade.mbia.genealogy.domain;

import java.util.UUID;

/** The graph the kinship resolver walks (genealogy.md §9). */
public interface KinshipGraphQuery {

    /** @return the ACTIVE relationships of the Family whose two Persons are ACTIVE */
    KinshipGraph activeGraph(UUID familyId);
}
