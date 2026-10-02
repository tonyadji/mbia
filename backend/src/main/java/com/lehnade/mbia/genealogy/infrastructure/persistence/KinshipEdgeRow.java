package com.lehnade.mbia.genealogy.infrastructure.persistence;

import java.util.UUID;

record KinshipEdgeRow(String type, UUID sourcePersonId, UUID targetPersonId) {}
