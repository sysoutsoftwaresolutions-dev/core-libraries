package com.core.common.security;

import com.core.common.exception.CoreException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.SigningKeyResolverAdapter;
import io.jsonwebtoken.JwsHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parser for verifying and extracting claims from JSON Web Tokens.
 * Supports RSA public keys, HMAC secrets, and dynamic JWKS resolution.
 */
@Component
public class JwtTokenParser {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenParser.class);

    @Value("${core.security.jwt.secret:}")
    private String secret;

    @Value("${core.security.jwt.public-key:}")
    private String publicKeyPem;

    @Value("${core.security.jwt.jwks-uri:}")
    private String jwksUri;

    @Value("${core.security.jwt.issuer:school-security-service}")
    private String expectedIssuer;

    @Value("${core.security.jwt.audience:}")
    private String expectedAudience;

    @Value("${core.security.jwt.allow-unsigned:false}")
    private boolean allowUnsigned;

    private JwtParser jwtParser;
    private boolean isSignatureVerificationEnabled = false;

    private final Map<String, Key> jwkCache = new ConcurrentHashMap<>();
    private long lastCacheRefreshTime = 0;

    @PostConstruct
    public void init() {
        var parserBuilder = Jwts.parserBuilder();

        if (jwksUri != null && !jwksUri.isBlank()) {
            parserBuilder.setSigningKeyResolver(new SigningKeyResolverAdapter() {
                @Override
                public Key resolveSigningKey(JwsHeader header, Claims claims) {
                    String kid = header.getKeyId();
                    if (kid == null || kid.isBlank()) {
                        throw new IllegalArgumentException("JWT is missing key ID (kid) in header");
                    }
                    return getPublicKeyFromJwks(kid);
                }
            });
            isSignatureVerificationEnabled = true;
            log.info("[JwtTokenParser] Initialized with dynamic JWKS verification from URI: {}", jwksUri);
        } else if (publicKeyPem != null && !publicKeyPem.isBlank()) {
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
            log.warn("[JwtTokenParser] No verification key or JWKS URI configured. Unsigned parsing fallback active.");
        }
        this.jwtParser = parserBuilder.build();
    }

    private Key getPublicKeyFromJwks(String kid) {
        Key cachedKey = jwkCache.get(kid);
        if (cachedKey != null) {
            return cachedKey;
        }

        synchronized (this) {
            cachedKey = jwkCache.get(kid);
            if (cachedKey != null) {
                return cachedKey;
            }

            long now = System.currentTimeMillis();
            // Rate limit JWKS refresh to once every 10 seconds
            if (now - lastCacheRefreshTime > 10000) {
                refreshJwkCache();
                lastCacheRefreshTime = now;
            }

            cachedKey = jwkCache.get(kid);
            if (cachedKey == null) {
                throw new IllegalStateException("PublicKey not found in JWKS for kid: " + kid);
            }
            return cachedKey;
        }
    }

    public void refreshJwkCache() {
        try {
            log.info("[JwtTokenParser] Refreshing JWK cache from URI: {}", jwksUri);
            JWKSet jwkSet = JWKSet.load(new URL(jwksUri));
            for (JWK jwk : jwkSet.getKeys()) {
                if (jwk instanceof RSAKey rsaKey) {
                    String kid = rsaKey.getKeyID();
                    Key publicKey = rsaKey.toPublicKey();
                    jwkCache.put(kid, publicKey);
                }
            }
        } catch (Exception e) {
            log.error("[JwtTokenParser] Failed to refresh JWK cache from URI: {}", jwksUri, e);
            throw new RuntimeException("Fail-closed: JWKS could not be loaded: " + e.getMessage(), e);
        }
    }

    /**
     * Parses the JWT token and verifies its signature and standard claims.
     *
     * @param token clean JWT token string
     * @return the claims contained in the JWT
     */
    public Claims parseToken(String token) {
        if (isSignatureVerificationEnabled) {
            Claims claims = jwtParser.parseClaimsJws(token).getBody();
            validateClaims(claims);
            return claims;
        } else {
            if (!allowUnsigned) {
                throw new SecurityException("Signature verification is disabled and unsigned parsing is not allowed.");
            }
            Claims claims = parseTokenWithoutSignature(token);
            validateClaims(claims);
            return claims;
        }
    }

    private void validateClaims(Claims claims) {
        // Validate Issuer (iss)
        String issuer = claims.getIssuer();
        if (expectedIssuer != null && !expectedIssuer.isBlank()) {
            if (issuer == null || !expectedIssuer.equalsIgnoreCase(issuer)) {
                throw new SecurityException("Token issuer '" + issuer + "' does not match expected '" + expectedIssuer + "'");
            }
        }

        // Validate Audience (aud)
        if (expectedAudience != null && !expectedAudience.isBlank()) {
            String audience = claims.getAudience();
            if (audience == null || !audience.contains(expectedAudience)) {
                throw new SecurityException("Token audience '" + audience + "' does not match expected '" + expectedAudience + "'");
            }
        }
    }

    /**
     * Helper to parse unsigned token payloads for integration testing/mocking contexts.
     */
    public Claims parseTokenWithoutSignature(String token) {
        try {
            if (!allowUnsigned) {
                throw new SecurityException("Unsigned token parsing is forbidden.");
            }
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
