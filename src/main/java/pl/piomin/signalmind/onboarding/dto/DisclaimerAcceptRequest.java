package pl.piomin.signalmind.onboarding.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/onboarding/disclaimer (SM-34).
 * The {@code accepted} field must be {@code true} — a false value is rejected at the service layer.
 */
public record DisclaimerAcceptRequest(
        @NotNull Boolean accepted
) {
}
