package com.scriptum.backend.infrastructure.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtService")
class JwtServiceTest {

    /** Test-only signing keys. HS256 via Keys.hmacShaKeyFor needs at least 32 bytes. */
    private static final String SIGNING_KEY = "unit-test-signing-key-at-least-32-bytes-long-0123456789";
    private static final String OTHER_SIGNING_KEY = "a-different-unit-test-signing-key-also-32-bytes-9876543210";

    private static final String EMAIL = "user@example.invalid";
    private static final long ONE_DAY_MILLIS = 86_400_000L;

    private JwtService jwtService;

    private static JwtService jwtServiceWith(String key, long expirationMillis) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", key);
        ReflectionTestUtils.setField(service, "jwtExpiration", expirationMillis);
        return service;
    }

    @BeforeEach
    void setUp() {
        jwtService = jwtServiceWith(SIGNING_KEY, ONE_DAY_MILLIS);
    }

    @Test
    @DisplayName("generateToken produces a token whose subject round-trips back to the email")
    void generateTokenRoundTripsEmail() {
        String token = jwtService.generateToken(EMAIL);

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractEmail(token)).isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("generateToken produces the three dot-separated segments of a signed JWT")
    void generateTokenProducesThreeSegments() {
        String token = jwtService.generateToken(EMAIL);

        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("generateToken issues distinct tokens for distinct subjects")
    void generateTokenIssuesDistinctTokensPerSubject() {
        String first = jwtService.generateToken(EMAIL);
        String second = jwtService.generateToken("other@example.invalid");

        assertThat(first).isNotEqualTo(second);
        assertThat(jwtService.extractEmail(second)).isEqualTo("other@example.invalid");
    }

    @Test
    @DisplayName("isTokenValid accepts a freshly issued token")
    void isTokenValidAcceptsFreshToken() {
        assertThat(jwtService.isTokenValid(jwtService.generateToken(EMAIL))).isTrue();
    }

    @Test
    @DisplayName("isTokenValid rejects a token whose expiry has already passed")
    void isTokenValidRejectsExpiredToken() {
        // A negative expiration window stamps an `exp` in the past.
        JwtService issuer = jwtServiceWith(SIGNING_KEY, -ONE_DAY_MILLIS);
        String expired = issuer.generateToken(EMAIL);

        assertThat(jwtService.isTokenValid(expired)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid rejects a token signed with a different key")
    void isTokenValidRejectsForeignSignature() {
        String foreign = jwtServiceWith(OTHER_SIGNING_KEY, ONE_DAY_MILLIS).generateToken(EMAIL);

        assertThat(jwtService.isTokenValid(foreign)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid rejects a token whose payload was tampered with")
    void isTokenValidRejectsTamperedPayload() {
        String[] segments = jwtService.generateToken(EMAIL).split("\\.");
        String tampered = segments[0] + "." + segments[1] + "x." + segments[2];

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @ParameterizedTest(name = "isTokenValid(\"{0}\") is false")
    @ValueSource(strings = {"", "   ", "not-a-jwt", "a.b.c", "Bearer something"})
    @DisplayName("isTokenValid rejects malformed input instead of throwing")
    void isTokenValidRejectsMalformedInput(String malformed) {
        assertThat(jwtService.isTokenValid(malformed)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid rejects a null token instead of throwing")
    void isTokenValidRejectsNullToken() {
        assertThat(jwtService.isTokenValid(null)).isFalse();
    }

    @Test
    @DisplayName("extractEmail propagates a JwtException for a token signed with a different key")
    void extractEmailPropagatesForeignSignature() {
        String foreign = jwtServiceWith(OTHER_SIGNING_KEY, ONE_DAY_MILLIS).generateToken(EMAIL);

        assertThatThrownBy(() -> jwtService.extractEmail(foreign))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("extractEmail propagates a JwtException for malformed input")
    void extractEmailPropagatesForMalformedInput() {
        assertThatThrownBy(() -> jwtService.extractEmail("not-a-jwt"))
                .isInstanceOf(JwtException.class);
    }
}
