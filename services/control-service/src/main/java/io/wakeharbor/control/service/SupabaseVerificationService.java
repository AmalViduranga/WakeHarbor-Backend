package io.wakeharbor.control.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wakeharbor.control.domain.enums.ErrorCategory;
import io.wakeharbor.control.domain.enums.ExecutionStatus;
import io.wakeharbor.control.dto.project.ValidateCredentialsResponse;
import io.wakeharbor.control.security.SsrfValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Executes a safe, read-only verification call to the Supabase RPC endpoint.
 * Validates the database function `public.wakeharbor_heartbeat()` response payload.
 */
@Service
public class SupabaseVerificationService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseVerificationService.class);
    private static final int TIMEOUT_SECONDS = 10;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024; // 64 KB limit

    private final SsrfValidator ssrfValidator;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SupabaseVerificationService(SsrfValidator ssrfValidator, ObjectMapper objectMapper) {
        this.ssrfValidator = ssrfValidator;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER) // Strictly no redirects
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
    }

    public record ExecutionResult(
            ExecutionStatus status,
            Integer httpStatusCode,
            Long latencyMs,
            ErrorCategory errorCategory,
            String errorMessage,
            String payloadSummary
    ) {}

    public ValidateCredentialsResponse validate(String supabaseUrl, String apiKey) {
        ExecutionResult result = executeHeartbeat(supabaseUrl, apiKey);
        boolean isValid = result.status() == ExecutionStatus.SUCCESS;
        String message = isValid 
                ? "Connection verified! Safe heartbeat function responded successfully."
                : result.errorMessage();

        return new ValidateCredentialsResponse(
                isValid,
                result.status().name(),
                result.httpStatusCode(),
                result.latencyMs(),
                message,
                result.errorCategory() != null ? result.errorCategory().name() : null,
                result.payloadSummary()
        );
    }

    public ExecutionResult executeHeartbeat(String supabaseUrl, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return new ExecutionResult(
                    ExecutionStatus.AUTH_ERROR,
                    400,
                    0L,
                    ErrorCategory.AUTHENTICATION_FAILED,
                    "Supabase publishable key is missing or blank",
                    null
            );
        }

        SsrfValidator.ValidatedSupabaseTarget target;
        try {
            target = ssrfValidator.validateUrl(supabaseUrl);
        } catch (IllegalArgumentException e) {
            return new ExecutionResult(
                    ExecutionStatus.FAILURE,
                    400,
                    0L,
                    ErrorCategory.SSRF_REJECTED,
                    "URL validation failed: " + e.getMessage(),
                    null
            );
        }

        long startTime = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target.rpcEndpoint()))
                    .header("apikey", apiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "WakeHarbor-Heartbeat/1.0")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startTime;

            int statusCode = response.statusCode();
            String rawBody = response.body();
            if (rawBody != null && rawBody.length() > MAX_RESPONSE_BYTES) {
                rawBody = rawBody.substring(0, MAX_RESPONSE_BYTES);
            }
            String truncatedBody = (rawBody != null && rawBody.length() > 500) 
                    ? rawBody.substring(0, 500) + "..." 
                    : rawBody;

            // Classify response
            if (statusCode == 200) {
                return parseSuccessResponse(rawBody, latency, statusCode);
            } else if (statusCode == 401 || statusCode == 403) {
                return parseAuthFailure(rawBody, statusCode, latency);
            } else if (statusCode == 404) {
                return parseNotFound(rawBody, statusCode, latency);
            } else if (statusCode == 429) {
                return new ExecutionResult(
                        ExecutionStatus.RATE_LIMITED,
                        statusCode,
                        latency,
                        ErrorCategory.RATE_LIMITED,
                        "Rate limit exceeded on Supabase project endpoint (HTTP 429)",
                        truncatedBody
                );
            } else if (statusCode == 503 || statusCode == 521 || statusCode == 522 || statusCode == 524) {
                return new ExecutionResult(
                        ExecutionStatus.PAUSED_CONFIRMED,
                        statusCode,
                        latency,
                        ErrorCategory.PROJECT_PAUSED,
                        "Supabase project appears to be paused or temporarily unavailable (HTTP " + statusCode + ")",
                        truncatedBody
                );
            } else if (statusCode >= 500) {
                return new ExecutionResult(
                        ExecutionStatus.FAILURE,
                        statusCode,
                        latency,
                        ErrorCategory.REMOTE_SERVER_ERROR,
                        "Supabase returned remote server error (HTTP " + statusCode + ")",
                        truncatedBody
                );
            } else {
                return new ExecutionResult(
                        ExecutionStatus.FAILURE,
                        statusCode,
                        latency,
                        ErrorCategory.UNEXPECTED_ERROR,
                        "Unexpected HTTP status code: " + statusCode,
                        truncatedBody
                );
            }

        } catch (HttpTimeoutException e) {
            long latency = System.currentTimeMillis() - startTime;
            return new ExecutionResult(
                    ExecutionStatus.TIMEOUT,
                    0,
                    latency,
                    ErrorCategory.HTTP_TIMEOUT,
                    "Request timed out after " + TIMEOUT_SECONDS + " seconds",
                    null
            );
        } catch (java.net.UnknownHostException e) {
            long latency = System.currentTimeMillis() - startTime;
            return new ExecutionResult(
                    ExecutionStatus.FAILURE,
                    0,
                    latency,
                    ErrorCategory.DNS_FAILURE,
                    "Failed to resolve host for Supabase project (DNS lookup failure)",
                    null
            );
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Unexpected error executing Supabase heartbeat for {}: {}", supabaseUrl, e.getMessage());
            return new ExecutionResult(
                    ExecutionStatus.FAILURE,
                    0,
                    latency,
                    ErrorCategory.UNEXPECTED_ERROR,
                    "Network error: " + e.getMessage(),
                    null
            );
        }
    }

    private ExecutionResult parseSuccessResponse(String rawBody, long latency, int statusCode) {
        if (rawBody == null || rawBody.isBlank()) {
            return new ExecutionResult(
                    ExecutionStatus.INVALID_RESPONSE,
                    statusCode,
                    latency,
                    ErrorCategory.INVALID_RESPONSE_PAYLOAD,
                    "Supabase returned HTTP 200 with an empty body",
                    ""
            );
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            // Expected body is either {"ok": true, "server_time": ...} or boolean true or json object containing ok
            boolean isOk = false;
            if (root.isObject() && root.has("ok") && root.get("ok").asBoolean()) {
                isOk = true;
            } else if (root.isBoolean() && root.asBoolean()) {
                isOk = true;
            }

            if (isOk) {
                return new ExecutionResult(
                        ExecutionStatus.SUCCESS,
                        statusCode,
                        latency,
                        null,
                        "Heartbeat succeeded",
                        rawBody.length() > 200 ? rawBody.substring(0, 200) : rawBody
                );
            } else {
                return new ExecutionResult(
                        ExecutionStatus.INVALID_RESPONSE,
                        statusCode,
                        latency,
                        ErrorCategory.INVALID_RESPONSE_PAYLOAD,
                        "Function returned HTTP 200 but response does not contain 'ok': true",
                        rawBody
                );
            }
        } catch (Exception e) {
            return new ExecutionResult(
                    ExecutionStatus.INVALID_RESPONSE,
                    statusCode,
                    latency,
                    ErrorCategory.INVALID_RESPONSE_PAYLOAD,
                    "Failed to parse JSON response from function: " + e.getMessage(),
                    rawBody
            );
        }
    }

    private ExecutionResult parseAuthFailure(String rawBody, int statusCode, long latency) {
        String message = "Authentication failed (HTTP " + statusCode + "). Please check your publishable key.";
        ErrorCategory category = ErrorCategory.AUTHENTICATION_FAILED;

        if (rawBody != null && rawBody.contains("PGRST301")) {
            message = "JWT/Key expired or invalid signature (PGRST301)";
        } else if (statusCode == 403) {
            category = ErrorCategory.PERMISSION_DENIED;
            message = "Permission denied. Please ensure 'GRANT EXECUTE ON FUNCTION wakeharbor_heartbeat() TO anon' was executed.";
        }

        return new ExecutionResult(
                ExecutionStatus.AUTH_ERROR,
                statusCode,
                latency,
                category,
                message,
                rawBody
        );
    }

    private ExecutionResult parseNotFound(String rawBody, int statusCode, long latency) {
        String message = "The 'wakeharbor_heartbeat' SQL function was not found in the public schema. Please run the setup SQL script in your Supabase SQL editor.";
        return new ExecutionResult(
                ExecutionStatus.FAILURE,
                statusCode,
                latency,
                ErrorCategory.FUNCTION_NOT_FOUND,
                message,
                rawBody
        );
    }
}
