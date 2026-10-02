package com.lehnade.mbia.genealogy.infrastructure.persistence;

import java.util.UUID;

record TreeEdgeRow(UUID id, String type, UUID sourcePersonId, UUID targetPersonId, Long version) {}
