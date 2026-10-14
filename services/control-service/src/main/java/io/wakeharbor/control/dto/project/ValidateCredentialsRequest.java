package io.wakeharbor.control.dto.project;

import jakarta.validation.constraints.NotBlank;

public record ValidateCredentialsRequest(
        @NotBlank(message = "Supabase URL is required")
        String supabaseUrl,

        @NotBlank(message = "API key is required")
        String publishableKey
) {}
