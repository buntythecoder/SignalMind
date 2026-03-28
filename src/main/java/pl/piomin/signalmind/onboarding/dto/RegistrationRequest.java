package pl.piomin.signalmind.onboarding.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/auth/register (SM-34).
 */
public record RegistrationRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 2, max = 100) String name,
        @NotBlank @Size(min = 8, max = 128) String password,
        String phone   // optional
) {
}
