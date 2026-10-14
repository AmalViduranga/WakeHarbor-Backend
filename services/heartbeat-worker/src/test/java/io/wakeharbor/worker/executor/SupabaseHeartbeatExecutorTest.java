package io.wakeharbor.worker.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SupabaseHeartbeatExecutorTest {

    private SupabaseHeartbeatExecutor executor;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        executor = new SupabaseHeartbeatExecutor(objectMapper, 5);
    }

    @Test
    @DisplayName("Non-supabase target endpoint must be rejected immediately by worker SSRF guard")
    void testSsrfRejection() {
        var outcome = executor.execute("https://evil-destination.com/rest/v1/rpc/test", "key_123");

        assertEquals("FAILURE", outcome.status());
        assertEquals("SSRF_REJECTED", outcome.errorCategory());
        assertEquals(400, outcome.httpStatusCode());
    }

    @Test
    @DisplayName("Valid host pattern must pass SSRF check")
    void testValidHostPattern() {
        // Will attempt to execute and fail with DNS or connection, but NOT SSRF_REJECTED
        var outcome = executor.execute("https://non-existent-proj-ref-12345.supabase.co/rest/v1/rpc/wakeharbor_heartbeat", "key_123");

        assertNotEquals("SSRF_REJECTED", outcome.errorCategory());
    }
}
