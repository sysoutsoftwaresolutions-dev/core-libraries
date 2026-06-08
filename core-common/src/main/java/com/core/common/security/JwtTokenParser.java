package com.core.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Parser for verifying and extracting claims from JSON Web Tokens.
 * Supports RSA public keys and HMAC secrets, with fallback to parsing
 * unsigned JWTs for integration testing environment setup.
 */
@Component
public class JwtTokenParser {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenParser.class);

    @Value("${core.security.jwt.secret:}")
    private String secret;

    @Value("${core.security.jwt.public-key:}")
    private String publicKeyPem;

    private JwtParser jwtParser;
    private boolean isSignatureVerificationEnabled = false;

    @PostConstruct
    public void init() {
        var parserBuilder = Jwts.parserBuilder();
        if (publicKeyPem != null && !publicKeyPem.isBlank()) {
            try {
                String pem = publicKeyPem
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
                byte[] der = Base64.getDecoder().decode(pem);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(der);
                Key publicKey = keyFactory.generatePublic(keySpec);
                parserBuilder.setSigningKey(publicKey);
                isSignatureVerificationEnabled = true;
                log.info("[JwtTokenParser] Initialized with RSA public key verification");
            } catch (Exception e) {
                log.error("[JwtTokenParser] Failed to load RSA public key", e);
                throw new IllegalStateException("Failed to load RSA public key", e);
            }
        } else if (secret != null && !secret.isBlank()) {
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                byte[] paddedBytes = new byte[32];
                System.arraycopy(keyBytes, 0, paddedBytes, 0, Math.min(keyBytes.length, 32));
                keyBytes = paddedBytes;
            }
            Key signingKey = Keys.hmacShaKeyFor(keyBytes);
            parserBuilder.setSigningKey(signingKey);
            isSignatureVerificationEnabled = true;
            log.info("[JwtTokenParser] Initialized with HMAC secret key verification");
        } else {
            log.warn("[JwtTokenParser] No verification key configured. Unsigned parsing fallback active (testing mode).");
        }
        this.jwtParser = parserBuilder.build();
    }

    /**
     * Parses the JWT token and verifies its signature.
     *
     * @param token clean JWT token string
     * @return the claims contained in the JWT
     */
    public Claims parseToken(String token) {
        if (isSignatureVerificationEnabled) {
            return jwtParser.parseClaimsJws(token).getBody();
        } else {
            // Unsigned/Testing mode fallback
            return parseTokenWithoutSignature(token);
        }
    }

    /**
     * Helper to parse unsigned token payloads for integration testing/mocking contexts.
     */
    public Claims parseTokenWithoutSignature(String token) {
        try {
            int lastDot = token.lastIndexOf('.');
            if (lastDot == -1) {
                throw new IllegalArgumentException("Invalid JWT format (missing dot separators)");
            }
            // Strip the signature portion if present
            String unsignedToken = token.substring(0, lastDot + 1);
            return Jwts.parserBuilder().build().parseClaimsJwt(unsignedToken).getBody();
        } catch (Exception e) {
            log.error("[JwtTokenParser] Failed to parse unsigned JWT token", e);
            throw new IllegalArgumentException("Failed to parse JWT payload: " + e.getMessage(), e);
        }
    }

    public String getTenant(Claims claims) {
        Object val = claims.get("tenantId");
        if (val == null) {
            val = claims.get("tenant");
        }
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        Object rolesVal = claims.get("roles");
        if (rolesVal == null) {
            rolesVal = claims.get("authorities");
        }
        if (rolesVal instanceof List) {
            return (List<String>) rolesVal;
        }
        if (rolesVal instanceof String) {
            return List.of(((String) rolesVal).split(","));
        }
        return Collections.emptyList();
    }

    public boolean isSignatureVerificationEnabled() {
        return isSignatureVerificationEnabled;
    }
}
