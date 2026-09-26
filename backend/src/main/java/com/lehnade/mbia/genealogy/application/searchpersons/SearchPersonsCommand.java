package com.lehnade.mbia.genealogy.application.searchpersons;

import com.lehnade.mbia.genealogy.domain.PersonStatus;
import java.util.UUID;

/**
 * @param status ACTIVE or ARCHIVED
 * @param search the text typed by the User, or {@code null}
 * @param page the zero-based page number
 */
public record SearchPersonsCommand(UUID familyId, PersonStatus status, String search, int page, int size) {}
