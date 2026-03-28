package pl.piomin.signalmind.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration properties bound from {@code jwt.*} in application.yml.
 *
 * @param secret              Base64-encoded HMAC-SHA256 signing key (min 32 bytes decoded)
 * @param accessTokenExpiry   Access token lifetime in milliseconds (default 900_000 = 15 min)
 * @param refreshTokenExpiry  Refresh token lifetime in milliseconds (default 604_800_000 = 7 days)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpiry,
        long refreshTokenExpiry
) {
}
