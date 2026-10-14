package io.wakeharbor.control.domain.enums;

public enum ErrorCategory {
    DNS_FAILURE("Unable to resolve Supabase project host"),
    CONNECTION_TIMEOUT("Database or gateway connection timed out"),
    HTTP_TIMEOUT("HTTP request timed out before response was received"),
    AUTHENTICATION_FAILED("Supabase API key is invalid or expired"),
    PERMISSION_DENIED("API key does not have permission to execute wakeharbor_heartbeat function"),
    FUNCTION_NOT_FOUND("The wakeharbor_heartbeat SQL function does not exist in the public schema"),
    RATE_LIMITED("Request was throttled by Supabase or upstream gateway"),
    PROJECT_PAUSED("Supabase responded that the project is paused or unavailable"),
    INVALID_RESPONSE_PAYLOAD("RPC endpoint returned an invalid or malformed response"),
    SSRF_REJECTED("Target URL was rejected by security policy (invalid host or IP)"),
    REMOTE_SERVER_ERROR("Supabase returned an internal server error (5xx)"),
    UNEXPECTED_ERROR("An unexpected exception occurred during execution");

    private final String description;

    ErrorCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPermanent() {
        return this == AUTHENTICATION_FAILED 
            || this == PERMISSION_DENIED 
            || this == FUNCTION_NOT_FOUND 
            || this == SSRF_REJECTED;
    }
}
