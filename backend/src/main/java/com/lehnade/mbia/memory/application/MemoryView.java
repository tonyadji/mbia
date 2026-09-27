package com.lehnade.mbia.memory.application;

import com.lehnade.mbia.genealogy.application.RelatedPerson;
import com.lehnade.mbia.memory.domain.Memory;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * A Memory with what the contract shows around it ({@code MemoryResponse}): its Persons, ordered by
 * display name then id, its creator and its photos, in position order.
 */
public record MemoryView(Memory memory, List<RelatedPerson> relatedPersons, MemoryAuthors.Author createdBy,
        List<MemoryPhotoView> photos) {

    private static final Comparator<RelatedPerson> ORDER = Comparator
            .comparing(RelatedPerson::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(RelatedPerson::id);

    public static MemoryView of(Memory memory, Collection<RelatedPerson> relatedPersons,
            MemoryAuthors.Author createdBy, List<MemoryPhotoView> photos) {
        return new MemoryView(memory, relatedPersons.stream().sorted(ORDER).toList(), createdBy, List.copyOf(photos));
    }
}
