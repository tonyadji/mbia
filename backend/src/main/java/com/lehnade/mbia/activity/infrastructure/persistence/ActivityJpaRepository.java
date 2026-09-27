package com.lehnade.mbia.activity.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ActivityJpaRepository extends JpaRepository<ActivityJpaEntity, UUID> {}
