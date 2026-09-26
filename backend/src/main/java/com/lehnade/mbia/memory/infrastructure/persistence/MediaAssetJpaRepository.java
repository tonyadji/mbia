package com.lehnade.mbia.memory.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MediaAssetJpaRepository extends JpaRepository<MediaAssetJpaEntity, UUID> {}
