package com.lehnade.mbia.genealogy.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * The tree read model of genealogy.md §10: a bounded number of SQL queries per tree, whatever the
 * size of the Family.
 */
public interface TreeQuery {

    /**
     * "Another suitable Person" of family-tree-ux.md §6: the ACTIVE Person with the most ACTIVE
     * relationships to ACTIVE Persons; ties broken by earliest creation, then lowest id.
     *
     * @return empty when the Family has no ACTIVE Person
     */
    Optional<PersonId> mostConnectedActivePerson(UUID familyId);

    /**
     * The focus, its parents, partners, children and siblings (Persons sharing at least one parent
     * with it); with {@code depth} 2, also the parents of the parents and the children of the
     * children (openapi {@code getFamilyTree}).
     *
     * @param focus an ACTIVE Person of the Family
     * @param depth 1 or 2
     */
    FamilyTree neighbourhood(UUID familyId, PersonId focus, int depth);
}
