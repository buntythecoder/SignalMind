package pl.piomin.signalmind.onboarding.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/onboarding/telegram (SM-34).
 */
public record TelegramConnectRequest(
        @NotBlank String chatId
) {
}
