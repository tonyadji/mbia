package com.lehnade.mbia.memory.domain;

import java.util.UUID;

/**
 * The only place where object storage keys are formed (Phase 3 plan §3.5):
 * {@code families/{familyId}/media/{mediaAssetId}/{upload|display|thumbnail}}. A key is never
 * derived from a file name, and never returned, logged or audited.
 */
public final class MediaStorageKeys {

    private MediaStorageKeys() {}

    /** Where the browser uploads the original. */
    public static String upload(UUID familyId, MediaAssetId id) {
        return key(familyId, id, "upload");
    }

    /** The display derivative (ADR-007). */
    public static String display(UUID familyId, MediaAssetId id) {
        return key(familyId, id, "display");
    }

    /** The thumbnail derivative (ADR-007). */
    public static String thumbnail(UUID familyId, MediaAssetId id) {
        return key(familyId, id, "thumbnail");
    }

    private static String key(UUID familyId, MediaAssetId id, String object) {
        return "families/" + familyId + "/media/" + id.value() + "/" + object;
    }
}
