package com.lehnade.mbia.memory.application;

import com.lehnade.mbia.genealogy.application.RelatedPerson;
import com.lehnade.mbia.genealogy.application.RelatedPersons;
import com.lehnade.mbia.memory.domain.Memory;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Builds a page of Memory views. The Persons and creators of the whole page are read in one query
 * each, so the number of queries does not grow with the number of Memories.
 */
@Component
public class MemoryPages {

    private final RelatedPersons relatedPersons;
    private final MemoryAuthors authors;

    public MemoryPages(RelatedPersons relatedPersons, MemoryAuthors authors) {
        this.relatedPersons = relatedPersons;
        this.authors = authors;
    }

    public MemoryPageView of(UUID familyId, List<Memory> memories, int page, int size, long total) {
        if (memories.isEmpty()) {
            return new MemoryPageView(List.of(), page, size, total);
        }
        Map<UUID, RelatedPerson> persons = relatedPersons.describe(familyId, memories.stream()
                .flatMap(memory -> memory.relatedPersonIds().stream())
                .collect(Collectors.toSet()));
        Map<UUID, MemoryAuthors.Author> creators = authors.authors(memories.stream()
                .map(Memory::createdBy)
                .collect(Collectors.toSet()));
        List<MemoryView> items = memories.stream()
                .map(memory -> MemoryView.of(memory, personsOf(memory.relatedPersonIds(), persons),
                        creators.get(memory.createdBy())))
                .toList();
        return new MemoryPageView(items, page, size, total);
    }

    private static List<RelatedPerson> personsOf(Set<UUID> ids, Map<UUID, RelatedPerson> persons) {
        return ids.stream().map(persons::get).filter(Objects::nonNull).toList();
    }
}
