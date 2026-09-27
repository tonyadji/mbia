package com.lehnade.mbia.invitation.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The raw secret of an invitation link (data-model.md §8): 32 random bytes from a CSPRNG,
 * base64url-encoded without padding. Only its {@link #hash()} is stored; the raw value is returned
 * once, in the creation or renewal response, and is never logged, audited or stored.
 */
public final class InvitationToken {

    private static final int RANDOM_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String value;

    private InvitationToken(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static InvitationToken generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return new InvitationToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** A token received in a link, to find its invitation by {@link #hash()}. */
    public static InvitationToken of(String value) {
        return new InvitationToken(value);
    }

    /** The raw value, only to build the link returned to the ADMIN. */
    public String value() {
        return value;
    }

    /** @return the SHA-256 of the raw value, in lowercase hexadecimal: what {@code token_hash} stores */
    public String hash() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every Java platform.", impossible);
        }
    }

    /** Never the raw value, so that a log line cannot leak it. */
    @Override
    public String toString() {
        return "InvitationToken[***]";
    }
}
