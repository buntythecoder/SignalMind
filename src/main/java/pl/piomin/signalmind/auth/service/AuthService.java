package pl.piomin.signalmind.auth.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.piomin.signalmind.auth.config.JwtProperties;
import pl.piomin.signalmind.auth.domain.AppUser;
import pl.piomin.signalmind.auth.domain.RefreshToken;
import pl.piomin.signalmind.auth.repository.AppUserRepository;
import pl.piomin.signalmind.auth.repository.RefreshTokenRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class AuthService {

    private final AppUserRepository userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AppUserRepository userRepo,
                       RefreshTokenRepository refreshRepo,
                       JwtService jwtService,
                       JwtProperties jwtProperties,
                       PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.refreshRepo = refreshRepo;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Authenticate a user by email and password.
     * Returns an {@link AuthResult} containing a new access token and raw refresh token.
     */
    @Transactional
    public AuthResult login(String email, String rawPassword) {
        AppUser user = userRepo.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account disabled");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return issueTokens(user);
    }

    /**
     * Rotate a refresh token: revoke the old one, issue a new pair.
     */
    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        RefreshToken rt = refreshRepo.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (rt.isRevoked() || rt.isExpired()) {
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        rt.revoke();
        refreshRepo.save(rt);

        AppUser user = userRepo.findById(rt.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        return issueTokens(user);
    }

    /**
     * Revoke the refresh token identified by the raw value (logout).
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = hashToken(rawRefreshToken);
        refreshRepo.findByTokenHash(hash).ifPresent(rt -> {
            rt.revoke();
            refreshRepo.save(rt);
        });
    }

    // ── internals ───────────────────────────────────────────────────────────

    private AuthResult issueTokens(AppUser user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefresh = jwtService.generateRefreshToken();
        String refreshHash = hashToken(rawRefresh);

        RefreshToken rt = new RefreshToken(
                user.getId(),
                refreshHash,
                Instant.now().plusMillis(jwtProperties.refreshTokenExpiry()));
        refreshRepo.save(rt);

        return new AuthResult(accessToken, rawRefresh, user.getEmail(), user.getRole());
    }

    /**
     * Deterministic SHA-256 hex hash of the raw refresh token for database lookup.
     */
    static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Immutable result of a successful authentication or token refresh.
     */
    public record AuthResult(String accessToken, String refreshToken, String email, String role) {
    }
}
