package com.lehnade.mbia.identity.domain;

import java.util.Arrays;
import java.util.Optional;

/** Language of a User: French (default) or English (ADR-006, data-model.md §3). */
public enum PreferredLocale {
    FR("fr"),
    EN("en");

    private final String code;

    PreferredLocale(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<PreferredLocale> ofCode(String code) {
        return Arrays.stream(values()).filter(locale -> locale.code.equals(code)).findFirst();
    }

    /** The identity provider's {@code locale} claim: {@code fr} or {@code en}, otherwise French. */
    public static PreferredLocale fromClaim(String claim) {
        return ofCode(claim).orElse(FR);
    }
}
