package io.wakeharbor.control.domain.enums;

public enum ProjectStatus {
    NOT_CONFIGURED,
    PENDING_VERIFICATION,
    HEALTHY,
    DEGRADED,
    CONNECTION_FAILED,
    AUTHENTICATION_ERROR,
    PAUSED_BY_USER,
    SUPABASE_PAUSED_CONFIRMED,
    SUPABASE_PAUSED_SUSPECTED,
    UNKNOWN;

    public boolean isOperational() {
        return this == HEALTHY || this == PENDING_VERIFICATION;
    }
}
