package io.wakeharbor.control.dto.project;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "Project name is required")
        @Size(max = 255, message = "Project name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Supabase URL is required")
        String supabaseUrl,

        @NotBlank(message = "Publishable key is required")
        String publishableKey,

        @Min(value = 6, message = "Interval must be at least 6 hours")
        @Max(value = 168, message = "Interval must not exceed 168 hours (7 days)")
        Integer intervalHours,

        String notes
) {}
