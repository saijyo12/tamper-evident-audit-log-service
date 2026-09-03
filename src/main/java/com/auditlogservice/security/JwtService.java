package com.auditlogservice.security;

import com.auditlogservice.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Issues and validates short-lived HMAC-SHA256 JWT bearer tokens. */
@Service
public class JwtService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private final JwtProperties properties;
    private final ObjectMapper objectMapper;

    public JwtService(JwtProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (properties.getJwtSecret() == null || properties.getJwtSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }
    }

    public String issueToken(String subject) {
        Instant now = Instant.now();
        long expiresAt = now.plusSeconds(properties.getTokenValiditySeconds()).getEpochSecond();
        String header = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"sub\":" + jsonString(subject) + ",\"iat\":" + now.getEpochSecond()
                + ",\"exp\":" + expiresAt + ",\"roles\":[\"AUDIT_READ\",\"AUDIT_WRITE\"]}");
        String unsignedToken = header + "." + payload;
        return unsignedToken + "." + sign(unsignedToken);
    }

    public String validateAndGetSubject(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !constantTimeEquals(sign(parts[0] + "." + parts[1]), parts[2])) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }
        try {
            JsonNode claims = objectMapper.readTree(new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8));
            JsonNode subject = claims.get("sub");
            JsonNode expiry = claims.get("exp");
            if (subject == null || !subject.isTextual() || expiry == null || expiry.asLong() <= Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("JWT is expired or malformed");
            }
            return subject.asText();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("JWT is malformed", exception);
        }
    }

    public long tokenValiditySeconds() { return properties.getTokenValiditySeconds(); }

    private String encode(String value) { return URL_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8)); }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign JWT", exception);
        }
    }

    private boolean constantTimeEquals(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }

    private String jsonString(String value) { return objectMapper.valueToTree(value).toString(); }
}
