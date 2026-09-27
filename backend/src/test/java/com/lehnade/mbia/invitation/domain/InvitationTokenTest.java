package com.lehnade.mbia.invitation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** data-model.md §8: 32 CSPRNG bytes, base64url; only the SHA-256 hash is stored. */
class InvitationTokenTest {

    @Test
    void aTokenIs32RandomBytesInBase64UrlWithoutPadding() {
        String value = InvitationToken.generate().value();

        assertThat(value).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(value)).hasSize(32);
    }

    @Test
    void everyTokenIsDifferent() {
        Set<String> values = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            values.add(InvitationToken.generate().value());
        }

        assertThat(values).hasSize(1_000);
    }

    @Test
    void theHashIsTheLowercaseHexadecimalSha256OfTheRawValue() {
        InvitationToken token = InvitationToken.generate();

        assertThat(token.hash()).hasSize(64).matches("[0-9a-f]+").isEqualTo(token.hash());
        assertThat(token.hash()).isNotEqualTo(InvitationToken.generate().hash());
    }

    @Test
    void theRawValueNeverAppearsInItsTextForm() {
        InvitationToken token = InvitationToken.generate();

        assertThat(token.toString()).doesNotContain(token.value()).doesNotContain(token.hash());
    }
}
