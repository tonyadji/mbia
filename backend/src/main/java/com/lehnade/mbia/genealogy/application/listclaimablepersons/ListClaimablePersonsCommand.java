package com.lehnade.mbia.genealogy.application.listclaimablepersons;

import java.util.UUID;

/**
 * @param search the text typed by the User, or {@code null}
 * @param page the zero-based page number
 */
public record ListClaimablePersonsCommand(UUID familyId, String search, int page, int size) {}
