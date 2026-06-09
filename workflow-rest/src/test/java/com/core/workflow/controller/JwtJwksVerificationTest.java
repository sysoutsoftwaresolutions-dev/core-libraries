package com.core.workflow.controller;

import com.core.common.security.JwtTokenParser;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the JwtTokenParser verifying signature validation, audience check,
 * fail-closed unknown kid checks, and unsigned token rejection behavior.
 */
public class JwtJwksVerificationTest {

    private JwtTokenParser jwtTokenParser;
    private RSAKey rsaKey;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String kid;

    @BeforeEach
    public void setUp() throws Exception {
        // Generate a test RSA Key
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        publicKey = (RSAPublicKey) keyPair.getPublic();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
        kid = "test-kid-123";

        rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(kid)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();

        // Subclass JwtTokenParser to mock the JWKS network load out without hitting network endpoints
        jwtTokenParser = new JwtTokenParser() {
            @Override
            public void refreshJwkCache() {
                @SuppressWarnings("unchecked")
                java.util.Map<String, java.security.Key> cache = 
                        (java.util.Map<String, java.security.Key>) ReflectionTestUtils.getField(this, "jwkCache");
                try {
                    cache.put(kid, rsaKey.toPublicKey());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                ReflectionTestUtils.setField(this, "lastCacheRefreshTime", System.currentTimeMillis());
            }
        };

        ReflectionTestUtils.setField(jwtTokenParser, "jwksUri", "http://localhost:8080/.well-known/jwks.json");
        ReflectionTestUtils.setField(jwtTokenParser, "expectedIssuer", "school-security-service");
        ReflectionTestUtils.setField(jwtTokenParser, "expectedAudience", "school-erp-services");
        ReflectionTestUtils.setField(jwtTokenParser, "allowUnsigned", false);

        jwtTokenParser.init();
    }

    @Test
    public void testJwksTokenValidationSuccess() {
        String token = Jwts.builder()
                .setHeaderParam("kid", kid)
                .setIssuer("school-security-service")
                .setAudience("school-erp-services")
                .setSubject("user_123")
                .claim("tenantId", "schoola")
                .claim("roles", List.of("STAFF"))
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();

        Claims claims = jwtTokenParser.parseToken(token);
        assertEquals("user_123", claims.getSubject());
        assertEquals("schoola", jwtTokenParser.getTenant(claims));
        assertTrue(jwtTokenParser.getRoles(claims).contains("STAFF"));
    }

    @Test
    public void testAudienceCheckFailure() {
        String token = Jwts.builder()
                .setHeaderParam("kid", kid)
                .setIssuer("school-security-service")
                .setAudience("wrong-audience")
                .setSubject("user_123")
                .claim("tenantId", "schoola")
                .claim("roles", List.of("STAFF"))
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();

        assertThrows(Exception.class, () -> {
            jwtTokenParser.parseToken(token);
        });
    }

    @Test
    public void testFailClosedOnUnknownKid() {
        String token = Jwts.builder()
                .setHeaderParam("kid", "unknown-kid")
                .setIssuer("school-security-service")
                .setAudience("school-erp-services")
                .setSubject("user_123")
                .claim("tenantId", "schoola")
                .claim("roles", List.of("STAFF"))
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();

        assertThrows(Exception.class, () -> {
            jwtTokenParser.parseToken(token);
        });
    }

    @Test
    public void testUnsignedTokenForbiddenByDefault() {
        String token = Jwts.builder()
                .setHeaderParam("kid", kid)
                .setIssuer("school-security-service")
                .setAudience("school-erp-services")
                .setSubject("user_123")
                .claim("tenantId", "schoola")
                .claim("roles", List.of("STAFF"))
                .setExpiration(new Date(System.currentTimeMillis() + 60000))
                .compact();

        assertThrows(Exception.class, () -> {
            jwtTokenParser.parseToken(token);
        });
    }
}
