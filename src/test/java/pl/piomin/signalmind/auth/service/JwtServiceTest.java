package pl.piomin.signalmind.auth.service;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pl.piomin.signalmind.auth.config.JwtProperties;
import pl.piomin.signalmind.auth.domain.AppUser;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "Y2hhbmdlbWVpbnByb2R1Y3Rpb24hY2hhbmdlbWVpbnByb2R1Y3Rpb24h";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(SECRET, 900_000L, 604_800_000L);
        jwtService = new JwtService(props);
    }

    @Test
    @DisplayName("generateAccessToken contains the user email as subject")
    void generateAccessToken_containsEmailSubject() {
        AppUser user = new AppUser("alice@example.com", "hash", "USER");
        String token = jwtService.generateAccessToken(user);

        String email = jwtService.extractEmail(token);
        assertThat(email).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("generateAccessToken produces a valid token")
    void generateAccessToken_isValid() {
        AppUser user = new AppUser("bob@example.com", "hash", "ADMIN");
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid returns false for an expired token")
    void isTokenValid_returnsFalse_forExpiredToken() {
        // Build a token that is already expired
        AppUser user = new AppUser("expired@example.com", "hash", "USER");
        String expiredToken = Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole())
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(jwtService.getSecretKey())
                .compact();

        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    @DisplayName("isTokenValid returns false for a tampered token")
    void isTokenValid_returnsFalse_forTamperedToken() {
        AppUser user = new AppUser("charlie@example.com", "hash", "USER");
        String token = jwtService.generateAccessToken(user);
        // Tamper with the payload by flipping a character
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("extractEmail returns the correct email from a valid token")
    void extractEmail_returnsCorrectEmail() {
        AppUser user = new AppUser("dave@example.com", "hash", "ADMIN");
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.extractEmail(token)).isEqualTo("dave@example.com");
    }
}
