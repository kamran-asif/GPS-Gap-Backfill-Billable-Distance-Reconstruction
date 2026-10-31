package com.moveinsync.distance.controller;

import com.moveinsync.distance.security.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller to issue and validate JWT tokens for enterprise corporate identity federation.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public record TokenRequest(String username, String tenantId, String role) {}
    public record TokenResponse(String token, String tokenType, String tenantId, String role, long expiresInSeconds) {}

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> generateToken(@RequestBody TokenRequest request) {
        String username = (request.username() != null && !request.username().isBlank()) ? request.username() : "operator-user";
        String tenantId = (request.tenantId() != null && !request.tenantId().isBlank()) ? request.tenantId() : "moveinsync-client-1";
        String role = (request.role() != null && !request.role().isBlank()) ? request.role() : "ROLE_OPERATOR";
        if (!role.startsWith("ROLE_")) {
            role = "ROLE_" + role;
        }

        String token = jwtTokenProvider.generateToken(username, tenantId, role);
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", tenantId, role, 86400));
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing Authorization: Bearer <token> header"));
        }
        String token = authHeader.substring(7).trim();
        JwtTokenProvider.JwtClaims claims = jwtTokenProvider.validateAndParseToken(token);
        if (claims == null) {
            return ResponseEntity.status(401).body(Map.of("valid", false, "error", "Invalid or expired token"));
        }
        return ResponseEntity.ok(Map.of(
                "valid", true,
                "subject", claims.subject(),
                "tenantId", claims.tenantId(),
                "role", claims.role(),
                "expiresAt", claims.expiresAt().toString()
        ));
    }
}

