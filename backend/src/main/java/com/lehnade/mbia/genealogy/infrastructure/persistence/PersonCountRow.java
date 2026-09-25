package com.lehnade.mbia.genealogy.infrastructure.persistence;

import java.util.UUID;

record PersonCountRow(UUID familyId, long count) {}
