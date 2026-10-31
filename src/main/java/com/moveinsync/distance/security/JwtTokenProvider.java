package com.moveinsync.distance.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * High-performance, RFC 7519-compliant JWT Provider utilizing native Java 21 crypto.
 * Signs and validates HMAC-SHA256 tokens containing tenant_id, role, and subject claims.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String HMAC_SHA256 = "HmacSHA256";

    private final byte[] secretKeyBytes;
    private final long expirationMs;
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(
            @Value("${engine.jwt.secret:MoveInSyncEnterpriseSecretKeyForBillingEngine2026!}") String secret,
            @Value("${engine.jwt.expiration-ms:86400000}") long expirationMs) {
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationMs = expirationMs;
        this.objectMapper = new ObjectMapper();
    }

    public record JwtClaims(String subject, String tenantId, String role, Instant issuedAt, Instant expiresAt) {}

    public String generateToken(String subject, String tenantId, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(expirationMs);

        try {
            // Header
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            String headerJson = objectMapper.writeValueAsString(header);
            String encodedHeader = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));

            // Payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", subject);
            payload.put("tenant_id", tenantId);
            payload.put("role", role);
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", exp.getEpochSecond());

            String payloadJson = objectMapper.writeValueAsString(payload);
            String encodedPayload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));

            // Signature
            String signingInput = encodedHeader + "." + encodedPayload;
            byte[] signature = signHmac(signingInput.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = base64UrlEncode(signature);

            return signingInput + "." + encodedSignature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT token: " + e.getMessage(), e);
        }
    }

    public JwtClaims validateAndParseToken(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            log.warn("Invalid JWT structure");
            return null;
        }

        String encodedHeader = parts[0];
        String encodedPayload = parts[1];
        String encodedSignature = parts[2];

        try {
            // 1. Verify signature
            String signingInput = encodedHeader + "." + encodedPayload;
            byte[] expectedSignature = signHmac(signingInput.getBytes(StandardCharsets.UTF_8));
            byte[] actualSignature = base64UrlDecode(encodedSignature);

            if (!MessageDigestEquals(expectedSignature, actualSignature)) {
                log.warn("JWT signature verification failed");
                return null;
            }

            // 2. Parse payload
            byte[] payloadBytes = base64UrlDecode(encodedPayload);
            JsonNode jsonNode = objectMapper.readTree(payloadBytes);

            long expSeconds = jsonNode.path("exp").asLong(0);
            Instant expiresAt = Instant.ofEpochSecond(expSeconds);
            if (Instant.now().isAfter(expiresAt)) {
                log.warn("JWT token expired at {}", expiresAt);
                return null;
            }

            String sub = jsonNode.path("sub").asText("");
            String tenantId = jsonNode.path("tenant_id").asText("");
            String role = jsonNode.path("role").asText("");
            Instant iat = Instant.ofEpochSecond(jsonNode.path("iat").asLong(0));

            return new JwtClaims(sub, tenantId, role, iat, expiresAt);

        } catch (Exception e) {
            log.warn("JWT token validation error: {}", e.getMessage());
            return null;
        }
    }

    private byte[] signHmac(byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec keySpec = new SecretKeySpec(secretKeyBytes, HMAC_SHA256);
        mac.init(keySpec);
        return mac.doFinal(data);
    }

    private static boolean MessageDigestEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] base64UrlDecode(String str) {
        return Base64.getUrlDecoder().decode(str);
    }
}
