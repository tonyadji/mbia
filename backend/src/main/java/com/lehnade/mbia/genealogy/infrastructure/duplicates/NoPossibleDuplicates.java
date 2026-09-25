package com.lehnade.mbia.genealogy.infrastructure.duplicates;

import com.lehnade.mbia.genealogy.application.PossibleDuplicates;
import com.lehnade.mbia.genealogy.domain.PersonDetails;
import com.lehnade.mbia.genealogy.domain.PersonId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * <strong>Temporary</strong> (Phase 2, PR-17): never finds a candidate. PR-27 replaces it with the
 * rule of person-relationships-collaboration.md §4.1.
 */
@Component
class NoPossibleDuplicates implements PossibleDuplicates {

    @Override
    public List<PersonId> candidatesFor(UUID familyId, PersonDetails details) {
        return List.of();
    }
}
