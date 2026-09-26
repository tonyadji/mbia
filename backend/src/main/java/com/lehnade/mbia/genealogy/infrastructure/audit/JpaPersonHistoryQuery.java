package com.lehnade.mbia.genealogy.infrastructure.audit;

import com.lehnade.mbia.genealogy.application.getpersonhistory.PersonHistoryQuery;
import com.lehnade.mbia.shared.application.audit.AuditEntry;
import com.lehnade.mbia.shared.infrastructure.audit.AuditEntryJpaEntity;
import com.lehnade.mbia.shared.infrastructure.audit.AuditEntryJpaRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * A page of a Person's audit entries in three queries whatever its size: the page, the total and
 * the actors (data-model.md §18).
 */
@Component
class JpaPersonHistoryQuery implements PersonHistoryQuery {

    private final AuditEntryJpaRepository entries;

    JpaPersonHistoryQuery(AuditEntryJpaRepository entries) {
        this.entries = entries;
    }

    @Override
    public Result entries(UUID familyId, UUID personId, Set<String> actions, int page, int size) {
        List<AuditEntryJpaEntity> rows = entries.findPage(familyId, AuditEntry.PERSON, personId, actions, size,
                (long) page * size);
        long total = entries.countPage(familyId, AuditEntry.PERSON, personId, actions);
        Map<UUID, Actor> actors = actorsOf(rows);
        return new Result(rows.stream()
                .map(row -> new Entry(row.id(), row.action(), actors.get(row.actorUserId()), row.oldValue(),
                        row.newValue(), row.occurredAt()))
                .toList(), total);
    }

    private Map<UUID, Actor> actorsOf(List<AuditEntryJpaEntity> rows) {
        Set<UUID> ids = new HashSet<>();
        rows.forEach(row -> ids.add(row.actorUserId()));
        ids.remove(null);
        Map<UUID, Actor> actors = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] actor : entries.findActors(ids)) {
                boolean deleted = "DELETED".equals(actor[2]);
                actors.put((UUID) actor[0], new Actor((UUID) actor[0], deleted ? null : (String) actor[1], deleted));
            }
        }
        return actors;
    }
}
