package com.lehnade.mbia.memory.application.listpersonmemories;

import com.lehnade.mbia.family.application.FamilyAccess;
import com.lehnade.mbia.genealogy.application.PersonNotFound;
import com.lehnade.mbia.genealogy.application.RelatedPerson;
import com.lehnade.mbia.genealogy.application.RelatedPersons;
import com.lehnade.mbia.memory.application.MemoryAuthors;
import com.lehnade.mbia.memory.application.MemoryView;
import com.lehnade.mbia.memory.domain.Memory;
import com.lehnade.mbia.memory.domain.MemoryRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ACTIVE Memories of a Person, most recently added first, for any ACTIVE member of its Family
 * (openapi {@code listPersonMemories}, SCREEN-005, OQ-034). The Person may be archived: archiving
 * never hides its Memories (OQ-035). The Persons and creators of the whole page are read in one query
 * each, so the number of queries does not grow with the number of Memories.
 */
@Service
public class ListPersonMemoriesUseCase {

    private final FamilyAccess familyAccess;
    private final MemoryRepository memories;
    private final RelatedPersons relatedPersons;
    private final MemoryAuthors authors;

    public ListPersonMemoriesUseCase(FamilyAccess familyAccess, MemoryRepository memories,
            RelatedPersons relatedPersons, MemoryAuthors authors) {
        this.familyAccess = familyAccess;
        this.memories = memories;
        this.relatedPersons = relatedPersons;
        this.authors = authors;
    }

    @Transactional(readOnly = true)
    public PersonMemoriesView list(ListPersonMemoriesCommand command) {
        UUID familyId = command.familyId();
        familyAccess.requireActiveMember(familyId);
        if (relatedPersons.describe(familyId, List.of(command.personId())).isEmpty()) {
            throw PersonNotFound.exception();
        }
        List<Memory> page = memories.findActiveForPerson(familyId, command.personId(), command.page(),
                command.size());
        long total = memories.countActiveForPerson(familyId, command.personId());
        if (page.isEmpty()) {
            return new PersonMemoriesView(List.of(), command.page(), command.size(), total);
        }
        Map<UUID, RelatedPerson> persons = relatedPersons.describe(familyId, page.stream()
                .flatMap(memory -> memory.relatedPersonIds().stream())
                .collect(Collectors.toSet()));
        Map<UUID, MemoryAuthors.Author> creators = authors.authors(page.stream()
                .map(Memory::createdBy)
                .collect(Collectors.toSet()));
        List<MemoryView> items = page.stream()
                .map(memory -> MemoryView.of(memory, personsOf(memory.relatedPersonIds(), persons),
                        creators.get(memory.createdBy())))
                .toList();
        return new PersonMemoriesView(items, command.page(), command.size(), total);
    }

    private static List<RelatedPerson> personsOf(Set<UUID> ids, Map<UUID, RelatedPerson> persons) {
        return ids.stream().map(persons::get).filter(Objects::nonNull).toList();
    }
}
