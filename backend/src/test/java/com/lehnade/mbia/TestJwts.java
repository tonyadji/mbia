package com.lehnade.mbia;

import com.lehnade.mbia.shared.api.security.SecurityConfiguration;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Access tokens signed by a test-only RSA key, shaped like Keycloak's (ADR-005).
 *
 * <p>{@link #decoder()} replaces the production decoder in tests: it trusts only the test key but
 * applies the production validator, so issuer, audience and expiry are really checked.
 */
public final class TestJwts {

    /** Same value as {@code mbia.security.issuer-uri} in the {@code test} profile. */
    public static final String ISSUER = "https://issuer.test/realms/mbia";
    public static final String AUDIENCE = "mbia-api";

    private static final RSAKey TRUSTED_KEY = generateKey();
    private static final RSAKey UNKNOWN_KEY = generateKey();

    private TestJwts() {}

    /** Factory method for {@code @TestBean JwtDecoder}. */
    public static JwtDecoder decoder() {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TRUSTED_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(SecurityConfiguration.jwtValidator(ISSUER, AUDIENCE));
            return decoder;
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A valid token of a verified user; adjust it with the {@code with…} methods. */
    public static Token token(String subject) {
        return new Token(subject);
    }

    /** A valid token of a new verified user with a unique subject and email. */
    public static Token newUserToken() {
        String id = UUID.randomUUID().toString();
        return token(id).email(id + "@mbia.test");
    }

    public static final class Token {

        private final String subject;
        private String email;
        private Boolean emailVerified = true;
        private String name = "Test User";
        private String locale = "fr";
        private String issuer = ISSUER;
        private String audience = AUDIENCE;
        private Instant expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        private RSAKey signingKey = TRUSTED_KEY;

        private Token(String subject) {
            this.subject = subject;
            this.email = subject + "@mbia.test";
        }

        public Token email(String email) {
            this.email = email;
            return this;
        }

        public Token emailVerified(Boolean emailVerified) {
            this.emailVerified = emailVerified;
            return this;
        }

        public Token name(String name) {
            this.name = name;
            return this;
        }

        public Token locale(String locale) {
            this.locale = locale;
            return this;
        }

        public Token issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Token audience(String audience) {
            this.audience = audience;
            return this;
        }

        public Token expiredAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Token signedWithUnknownKey() {
            this.signingKey = UNKNOWN_KEY;
            return this;
        }

        public String subject() {
            return subject;
        }

        /** The {@code Authorization} header value. */
        public String bearer() {
            return "Bearer " + encode();
        }

        public String encode() {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .audience(audience)
                    .issueTime(Date.from(expiresAt.minus(Duration.ofMinutes(5))))
                    .expirationTime(Date.from(expiresAt))
                    .claim("email", email)
                    .claim("email_verified", emailVerified)
                    .claim("name", name)
                    .claim("locale", locale)
                    .build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT)
                    .keyID(signingKey.getKeyID())
                    .build();
            try {
                SignedJWT jwt = new SignedJWT(header, claims);
                jwt.sign(new RSASSASigner(signingKey));
                return jwt.serialize();
            } catch (JOSEException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
