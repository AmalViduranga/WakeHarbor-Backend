package io.wakeharbor.control.domain.enums;

public enum ExecutionStatus {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    RATE_LIMITED,
    AUTH_ERROR,
    PAUSED_CONFIRMED,
    INVALID_RESPONSE
}
