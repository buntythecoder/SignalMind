package pl.piomin.signalmind.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.piomin.signalmind.auth.service.AuthService;
import pl.piomin.signalmind.auth.service.LoginAttemptService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String ACCESS_COOKIE = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final long ACCESS_MAX_AGE = 900L;       // 15 min
    private static final long REFRESH_MAX_AGE = 604800L;   // 7 days

    private final AuthService authService;
    private final LoginAttemptService loginAttemptService;

    public AuthController(AuthService authService, LoginAttemptService loginAttemptService) {
        this.authService = authService;
        this.loginAttemptService = loginAttemptService;
    }

    // ── DTOs ────────────────────────────────────────────────────────────────

    public record LoginRequest(String email, String password) {
    }

    public record LoginResponse(String email, String role) {
    }

    // ── Endpoints ───────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest req,
                                                HttpServletRequest httpReq) {
        String ip = httpReq.getRemoteAddr();
        if (loginAttemptService.isBlocked(ip)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        try {
            AuthService.AuthResult result = authService.login(req.email(), req.password());
            loginAttemptService.resetAttempts(ip);

            ResponseCookie accessCookie = buildAccessCookie(result.accessToken());
            ResponseCookie refreshCookie = buildRefreshCookie(result.refreshToken());

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .body(new LoginResponse(result.email(), result.role()));
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(ip);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String rawRefresh) {
        if (rawRefresh == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            AuthService.AuthResult result = authService.refresh(rawRefresh);

            ResponseCookie accessCookie = buildAccessCookie(result.accessToken());
            ResponseCookie refreshCookie = buildRefreshCookie(result.refreshToken());

            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                    .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                    .build();
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String rawRefresh) {
        if (rawRefresh != null) {
            authService.logout(rawRefresh);
        }

        ResponseCookie clearAccess = ResponseCookie.from(ACCESS_COOKIE, "")
                .httpOnly(true).secure(true).sameSite("Strict").path("/").maxAge(0).build();
        ResponseCookie clearRefresh = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(true).sameSite("Strict")
                .path("/api/auth/refresh").maxAge(0).build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearAccess.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefresh.toString())
                .build();
    }

    // ── Cookie builders ─────────────────────────────────────────────────────

    private ResponseCookie buildAccessCookie(String token) {
        return ResponseCookie.from(ACCESS_COOKIE, token)
                .httpOnly(true).secure(true).sameSite("Strict")
                .path("/").maxAge(ACCESS_MAX_AGE)
                .build();
    }

    private ResponseCookie buildRefreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE, token)
                .httpOnly(true).secure(true).sameSite("Strict")
                .path("/api/auth/refresh").maxAge(REFRESH_MAX_AGE)
                .build();
    }
}
