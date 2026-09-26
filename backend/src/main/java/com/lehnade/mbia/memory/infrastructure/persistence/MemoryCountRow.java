package com.lehnade.mbia.memory.infrastructure.persistence;

import java.util.UUID;

record MemoryCountRow(UUID familyId, long count) {}
