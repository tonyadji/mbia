package com.lehnade.mbia.family.application.createfamily;

import java.util.UUID;

public record CreateFamilyCommand(UUID userId, String name) {}
