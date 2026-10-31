package com.moveinsync.distance.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "MyTestSecretKeyWithAtLeast32BytesLengthForHmacSHA256!",
                3600000 // 1 hour
        );
    }

    @Test
    @DisplayName("Should generate, sign and successfully parse valid JWT token")
    void shouldGenerateAndValidateToken() {
        String token = jwtTokenProvider.generateToken("ops-user-1", "tenant-alpha", "ROLE_OPERATOR");
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);

        JwtTokenProvider.JwtClaims claims = jwtTokenProvider.validateAndParseToken(token);
        assertThat(claims).isNotNull();
        assertThat(claims.subject()).isEqualTo("ops-user-1");
        assertThat(claims.tenantId()).isEqualTo("tenant-alpha");
        assertThat(claims.role()).isEqualTo("ROLE_OPERATOR");
        assertThat(claims.expiresAt()).isNotNull();
    }

    @Test
    @DisplayName("Should reject tampered JWT token signature")
    void shouldRejectTamperedToken() {
        String token = jwtTokenProvider.generateToken("ops-user-1", "tenant-alpha", "ROLE_OPERATOR");
        String[] parts = token.split("\\.");
        // Modify payload slightly
        String tamperedToken = parts[0] + "." + parts[1] + "tampered." + parts[2];

        JwtTokenProvider.JwtClaims claims = jwtTokenProvider.validateAndParseToken(tamperedToken);
        assertThat(claims).isNull();
    }

    @Test
    @DisplayName("Should reject expired JWT token")
    void shouldRejectExpiredToken() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(
                "MyTestSecretKeyWithAtLeast32BytesLengthForHmacSHA256!",
                -5000 // Expired 5 seconds ago
        );
        String token = expiredProvider.generateToken("ops-user-1", "tenant-alpha", "ROLE_OPERATOR");

        JwtTokenProvider.JwtClaims claims = expiredProvider.validateAndParseToken(token);
        assertThat(claims).isNull();
    }
}

