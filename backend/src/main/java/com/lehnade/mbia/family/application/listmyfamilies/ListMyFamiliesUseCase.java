package com.lehnade.mbia.family.application.listmyfamilies;

import com.lehnade.mbia.family.application.FamilyRole;
import com.lehnade.mbia.family.application.FamilyStats;
import com.lehnade.mbia.family.application.FamilyStatsPort;
import com.lehnade.mbia.family.application.FamilyView;
import com.lehnade.mbia.family.domain.FamilyId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lists the Families where the current user's membership is ACTIVE (openapi {@code listMyFamilies}). */
@Service
public class ListMyFamiliesUseCase {

    private final MyFamiliesQuery query;
    private final FamilyStatsPort stats;

    public ListMyFamiliesUseCase(MyFamiliesQuery query, FamilyStatsPort stats) {
        this.query = query;
        this.stats = stats;
    }

    @Transactional(readOnly = true)
    public List<FamilyView> list(UUID userId) {
        List<MyFamiliesQuery.MyFamily> rows = query.findActiveFor(userId);
        Map<FamilyId, FamilyStatsPort.ContentCounts> counts =
                stats.contentCounts(rows.stream().map(row -> row.family().id()).toList());
        return rows.stream().map(row -> {
            FamilyStatsPort.ContentCounts content = counts.get(row.family().id());
            return new FamilyView(row.family().id().value(), row.family().name(), FamilyRole.of(row.myRole()),
                    new FamilyStats(content.personCount(), content.memoryCount(), row.activeMemberCount()),
                    row.family().version(), row.family().createdAt(), row.family().updatedAt());
        }).toList();
    }
}
