package io.wakeharbor.worker.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.regex.Pattern;

@Component
public class SupabaseHeartbeatExecutor {

    private static final Logger log = LoggerFactory.getLogger(SupabaseHeartbeatExecutor.class);

    private static final Pattern VALID_HOST_PATTERN = Pattern.compile(
            "^https://[a-z0-9_-]{1,64}\\.supabase\\.co(/.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int timeoutSeconds;

    public SupabaseHeartbeatExecutor(
            ObjectMapper objectMapper,
            @Value("${wakeharbor.worker.request-timeout-seconds:10}") int timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    public record ExecutionOutcome(
            String status,
            Integer httpStatusCode,
            Long latencyMs,
            String errorCategory,
            String errorDetails,
            String responsePayloadSummary
    ) {}

    public ExecutionOutcome execute(String rpcEndpoint, String apiKey) {
        if (!VALID_HOST_PATTERN.matcher(rpcEndpoint).matches()) {
            return new ExecutionOutcome(
                    "FAILURE",
                    400,
                    0L,
                    "SSRF_REJECTED",
                    "Target RPC endpoint rejected by security policy (must match *.supabase.co)",
                    null
            );
        }

        long startTime = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(rpcEndpoint))
                    .header("apikey", apiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "WakeHarbor-Worker/1.0")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startTime;

            int code = response.statusCode();
            String rawBody = response.body();
            String summary = (rawBody != null && rawBody.length() > 500) ? rawBody.substring(0, 500) + "..." : rawBody;

            if (code == 200) {
                return parseSuccess(rawBody, latency, code);
            } else if (code == 401 || code == 403) {
                String cat = (code == 403) ? "PERMISSION_DENIED" : "AUTHENTICATION_FAILED";
                return new ExecutionOutcome("AUTH_ERROR", code, latency, cat, "Supabase authentication failed (HTTP " + code + ")", summary);
            } else if (code == 404) {
                return new ExecutionOutcome("FAILURE", code, latency, "FUNCTION_NOT_FOUND", "Function wakeharbor_heartbeat not found in public schema", summary);
            } else if (code == 429) {
                return new ExecutionOutcome("RATE_LIMITED", code, latency, "RATE_LIMITED", "Supabase rate limit exceeded (HTTP 429)", summary);
            } else if (code == 503 || code == 521 || code == 522 || code == 524) {
                return new ExecutionOutcome("PAUSED_CONFIRMED", code, latency, "PROJECT_PAUSED", "Supabase project paused or temporarily unreachable (HTTP " + code + ")", summary);
            } else if (code >= 500) {
                return new ExecutionOutcome("FAILURE", code, latency, "REMOTE_SERVER_ERROR", "Remote server error (HTTP " + code + ")", summary);
            } else {
                return new ExecutionOutcome("FAILURE", code, latency, "UNEXPECTED_ERROR", "Unexpected response status: " + code, summary);
            }

        } catch (HttpTimeoutException e) {
            long latency = System.currentTimeMillis() - startTime;
            return new ExecutionOutcome("TIMEOUT", 0, latency, "HTTP_TIMEOUT", "Heartbeat request timed out after " + timeoutSeconds + "s", null);
        } catch (java.net.UnknownHostException e) {
            long latency = System.currentTimeMillis() - startTime;
            return new ExecutionOutcome("FAILURE", 0, latency, "DNS_FAILURE", "DNS resolution failed for Supabase project host", null);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("Heartbeat execution encountered exception for {}: {}", rpcEndpoint, e.getMessage());
            return new ExecutionOutcome("FAILURE", 0, latency, "UNEXPECTED_ERROR", "Network failure: " + e.getMessage(), null);
        }
    }

    private ExecutionOutcome parseSuccess(String rawBody, long latency, int code) {
        if (rawBody == null || rawBody.isBlank()) {
            return new ExecutionOutcome("INVALID_RESPONSE", code, latency, "INVALID_RESPONSE_PAYLOAD", "Empty response body from function", "");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            boolean ok = false;
            if (root.isObject() && root.has("ok") && root.get("ok").asBoolean()) {
                ok = true;
            } else if (root.isBoolean() && root.asBoolean()) {
                ok = true;
            }

            if (ok) {
                return new ExecutionOutcome("SUCCESS", code, latency, null, "Heartbeat verified successfully", rawBody.length() > 200 ? rawBody.substring(0, 200) : rawBody);
            } else {
                return new ExecutionOutcome("INVALID_RESPONSE", code, latency, "INVALID_RESPONSE_PAYLOAD", "Function returned 200 but payload did not contain {'ok': true}", rawBody);
            }
        } catch (Exception e) {
            return new ExecutionOutcome("INVALID_RESPONSE", code, latency, "INVALID_RESPONSE_PAYLOAD", "Failed to parse JSON response: " + e.getMessage(), rawBody);
        }
    }
}
