package pl.piomin.signalmind.onboarding.dto;

/**
 * Response body for GET /api/onboarding/status (SM-34).
 */
public record OnboardingStatusResponse(
        int step,
        boolean emailVerified,
        boolean disclaimerAccepted,
        boolean telegramVerified,
        boolean onboardingComplete,
        String name,
        String email,
        String telegramChatId,
        int watchlistSize
) {
}
