package com.lehnade.mbia.family.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FamilyMembershipJpaRepository extends JpaRepository<FamilyMembershipJpaEntity, UUID> {

    long countByFamilyIdAndStatus(UUID familyId, String status);
}
