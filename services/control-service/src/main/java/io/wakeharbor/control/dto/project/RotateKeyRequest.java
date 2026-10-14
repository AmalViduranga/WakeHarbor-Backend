package io.wakeharbor.control.dto.project;

import jakarta.validation.constraints.NotBlank;

public record RotateKeyRequest(
        @NotBlank(message = "New publishable key is required")
        String publishableKey
) {}
