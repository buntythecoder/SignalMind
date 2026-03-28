package pl.piomin.signalmind.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;
import pl.piomin.signalmind.auth.config.JwtProperties;
import pl.piomin.signalmind.auth.domain.AppUser;

import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey secretKey;

    public JwtService(JwtProperties props) {
        this.props = props;
        byte[] keyBytes = Base64.getDecoder().decode(props.secret());
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Build a short-lived access JWT containing the user's email (subject) and role.
     */
    public String generateAccessToken(AppUser user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + props.accessTokenExpiry()))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generate an opaque refresh token (UUID). The raw value is sent to the client;
     * only its SHA-256 hash is stored in the database.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Extract the email (subject) from a valid JWT.
     */
    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Returns {@code true} if the token is structurally valid and not expired.
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Expose access-token expiry for testing purposes.
     */
    long getAccessTokenExpiry() {
        return props.accessTokenExpiry();
    }

    /**
     * Expose the secret key for testing sub-classes that need a custom expiry.
     */
    SecretKey getSecretKey() {
        return secretKey;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
