package com.lehnade.archfixtures.rule6.family.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class FamilyJpaEntity {

    @Id
    private Long id;
}
