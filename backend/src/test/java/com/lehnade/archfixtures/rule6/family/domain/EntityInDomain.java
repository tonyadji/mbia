package com.lehnade.archfixtures.rule6.family.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class EntityInDomain {

    @Id
    private Long id;
}
