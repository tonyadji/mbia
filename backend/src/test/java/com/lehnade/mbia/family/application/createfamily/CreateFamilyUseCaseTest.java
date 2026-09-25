package com.lehnade.mbia.family.application.createfamily;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.lehnade.mbia.ApiTestSupport;
import com.lehnade.mbia.family.application.FamilyView;
import com.lehnade.mbia.family.domain.FamilyMembershipRepository;
import com.lehnade.mbia.family.domain.MembershipRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** PR-13 / architecture.md §9: a Family and its creator's ADMIN membership are created atomically. */
class CreateFamilyUseCaseTest extends ApiTestSupport {

    @Autowired
    CreateFamilyUseCase useCase;

    @MockitoSpyBean
    FamilyMembershipRepository memberships;

    @Test
    void createsOneFamilyAndOneActiveAdminMembership() {
        UUID userId = insertUser();

        FamilyView view = useCase.create(new CreateFamilyCommand(userId, " Famille Mbida "));

        assertThat(countFamiliesCreatedBy(userId)).isEqualTo(1);
        Map<String, Object> family = jdbc.sql("SELECT * FROM families WHERE id = ?")
                .param(view.id()).query().singleRow();
        assertThat(family)
                .containsEntry("name", "Famille Mbida")
                .containsEntry("status", "ACTIVE")
                .containsEntry("created_by", userId)
                .containsEntry("version", 0L);

        assertThat(jdbc.sql("SELECT count(*) FROM family_memberships WHERE family_id = ?")
                .param(view.id()).query(Long.class).single()).isEqualTo(1);
        Map<String, Object> membership = jdbc.sql("SELECT * FROM family_memberships WHERE family_id = ?")
                .param(view.id()).query().singleRow();
        assertThat(membership)
                .containsEntry("user_id", userId)
                .containsEntry("role", "ADMIN")
                .containsEntry("status", "ACTIVE")
                .containsEntry("removed_at", null)
                .containsEntry("version", 0L);
        assertThat(membership.get("joined_at")).isNotNull();

        assertThat(view.name()).isEqualTo("Famille Mbida");
        assertThat(view.myRole()).isEqualTo(MembershipRole.ADMIN);
        assertThat(view.version()).isZero();
        assertThat(view.stats().activeMemberCount()).isEqualTo(1);
    }

    @Test
    void failureOfTheMembershipInsertRollsBackTheFamily() {
        UUID userId = insertUser();
        doThrow(new IllegalStateException("membership insert failed")).when(memberships).insert(any());

        assertThatThrownBy(() -> useCase.create(new CreateFamilyCommand(userId, "Famille Mbida")))
                .hasMessageContaining("membership insert failed");

        assertThat(countFamiliesCreatedBy(userId)).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM family_memberships WHERE user_id = ?")
                .param(userId).query(Long.class).single()).isZero();
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.sql("""
                INSERT INTO users (id, identity_provider_subject, email, preferred_locale, status, created_at, updated_at)
                VALUES (?, ?, ?, 'fr', 'ACTIVE', ?, ?)
                """)
                .params(id, id.toString(), id + "@mbia.test", Timestamp.from(now), Timestamp.from(now))
                .update();
        return id;
    }

    private long countFamiliesCreatedBy(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM families WHERE created_by = ?").param(userId).query(Long.class).single();
    }
}
