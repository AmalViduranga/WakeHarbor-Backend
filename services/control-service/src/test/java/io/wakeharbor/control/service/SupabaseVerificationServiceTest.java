package io.wakeharbor.control.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.wakeharbor.control.domain.enums.ErrorCategory;
import io.wakeharbor.control.dto.project.ValidateCredentialsResponse;
import io.wakeharbor.control.security.SsrfValidator;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SupabaseVerificationServiceTest {

    private MockWebServer mockWebServer;
    private SupabaseVerificationService verificationService;

    // Test double avoiding ByteBuddy bytecode instrumentation on bleeding-edge JDKs
    static class TestDoubleSsrfValidator extends SsrfValidator {
        private final String baseUrl;
        private final String rpcEndpoint;

        TestDoubleSsrfValidator(String baseUrl, String rpcEndpoint) {
            this.baseUrl = baseUrl;
            this.rpcEndpoint = rpcEndpoint;
        }

        @Override
        public ValidatedSupabaseTarget validateUrl(String rawUrl) {
            return new ValidatedSupabaseTarget("test-project", baseUrl, rpcEndpoint);
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        ObjectMapper objectMapper = new ObjectMapper();

        String baseUrl = mockWebServer.url("/").toString();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String rpcEndpoint = baseUrl + "/rest/v1/rpc/wakeharbor_heartbeat";

        SsrfValidator testValidator = new TestDoubleSsrfValidator(baseUrl, rpcEndpoint);
        verificationService = new SupabaseVerificationService(testValidator, objectMapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("HTTP 200 with valid {'ok': true} payload should result in SUCCESS")
    void testSuccessfulHeartbeat() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"ok\": true, \"server_time\": \"2026-10-14T18:00:00Z\"}"));

        ValidateCredentialsResponse response = verificationService.validate("https://test-project.supabase.co", "sb_publishable_key_123");

        assertTrue(response.valid());
        assertEquals("SUCCESS", response.status());
        assertEquals(200, response.httpStatusCode());
        assertNull(response.errorCategory());
    }

    @Test
    @DisplayName("HTTP 401 should be categorized as AUTHENTICATION_FAILED")
    void testAuthenticationFailure() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Invalid API key\",\"code\":\"PGRST301\"}"));

        ValidateCredentialsResponse response = verificationService.validate("https://test-project.supabase.co", "bad_key");

        assertFalse(response.valid());
        assertEquals("AUTH_ERROR", response.status());
        assertEquals(401, response.httpStatusCode());
        assertEquals(ErrorCategory.AUTHENTICATION_FAILED.name(), response.errorCategory());
    }

    @Test
    @DisplayName("HTTP 404 should be categorized as FUNCTION_NOT_FOUND with actionable message")
    void testMissingFunction() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Could not find function public.wakeharbor_heartbeat()\",\"code\":\"PGRST202\"}"));

        ValidateCredentialsResponse response = verificationService.validate("https://test-project.supabase.co", "valid_key");

        assertFalse(response.valid());
        assertEquals("FAILURE", response.status());
        assertEquals(404, response.httpStatusCode());
        assertEquals(ErrorCategory.FUNCTION_NOT_FOUND.name(), response.errorCategory());
        assertTrue(response.message().contains("SQL function was not found"));
    }

    @Test
    @DisplayName("HTTP 429 should be categorized as RATE_LIMITED")
    void testRateLimited() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"message\":\"Too many requests\"}"));

        ValidateCredentialsResponse response = verificationService.validate("https://test-project.supabase.co", "valid_key");

        assertFalse(response.valid());
        assertEquals("RATE_LIMITED", response.status());
        assertEquals(429, response.httpStatusCode());
        assertEquals(ErrorCategory.RATE_LIMITED.name(), response.errorCategory());
    }

    @Test
    @DisplayName("HTTP 503 should be categorized as PROJECT_PAUSED")
    void testPausedProject() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setBody("<html><body>Project is paused due to inactivity</body></html>"));

        ValidateCredentialsResponse response = verificationService.validate("https://test-project.supabase.co", "valid_key");

        assertFalse(response.valid());
        assertEquals("PAUSED_CONFIRMED", response.status());
        assertEquals(503, response.httpStatusCode());
        assertEquals(ErrorCategory.PROJECT_PAUSED.name(), response.errorCategory());
    }

    @Test
    @DisplayName("HTTP 200 with invalid payload (missing ok: true) should be categorized as INVALID_RESPONSE")
    void testInvalidPayload() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"unexpected\": \"data\"}"));

        ValidateCredentialsResponse response = verificationService.validate("https://test-project.supabase.co", "valid_key");

        assertFalse(response.valid());
        assertEquals("INVALID_RESPONSE", response.status());
        assertEquals(ErrorCategory.INVALID_RESPONSE_PAYLOAD.name(), response.errorCategory());
    }
}
