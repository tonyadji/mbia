package com.lehnade.mbia.identity.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    Optional<UserJpaEntity> findByIdentityProviderSubject(String identityProviderSubject);

    /**
     * A concurrent insert of the same subject waits for the other transaction, then inserts
     * nothing instead of failing. The conflict target is left out on purpose: two concurrent
     * inserts of one user also collide on the email index, which must not fail either.
     *
     * @return 1 when inserted, 0 when the subject or the email already exists
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO users (id, identity_provider_subject, email, display_name, preferred_locale,
                               status, created_at, updated_at, version)
            VALUES (:id, :subject, :email, :displayName, :preferredLocale, 'ACTIVE', :now, :now, 0)
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(UUID id, String subject, String email, String displayName, String preferredLocale, Instant now);
}
