package io.wakeharbor.worker.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wakeharbor.worker.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Component
public class ControlServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ControlServiceClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String controlServiceUrl;
    private final String workerSecret;

    public ControlServiceClient(
            ObjectMapper objectMapper,
            @Value("${wakeharbor.worker.control-service-url:http://localhost:8080}") String controlServiceUrl,
            @Value("${wakeharbor.worker.worker-secret}") String workerSecret) {
        this.objectMapper = objectMapper;
        this.controlServiceUrl = controlServiceUrl.endsWith("/") ? controlServiceUrl.substring(0, controlServiceUrl.length() - 1) : controlServiceUrl;
        this.workerSecret = workerSecret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public List<ClaimedJobDto> claimJobs(ClaimJobsRequest request) {
        try {
            String jsonBody = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(controlServiceUrl + "/api/internal/v1/jobs/claim"))
                    .header("X-Worker-Token", workerSecret)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                ApiResponse<List<ClaimedJobDto>> apiResponse = objectMapper.readValue(
                        response.body(),
                        new TypeReference<ApiResponse<List<ClaimedJobDto>>>() {}
                );
                return (apiResponse != null && apiResponse.data() != null) ? apiResponse.data() : Collections.emptyList();
            } else {
                log.warn("Failed to claim jobs from Control Service: HTTP {} - {}", response.statusCode(), response.body());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            log.error("Error communicating with Control Service job claim endpoint: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public void submitResult(SubmitJobResultRequest request) {
        try {
            String jsonBody = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(controlServiceUrl + "/api/internal/v1/jobs/" + request.jobId() + "/result"))
                    .header("X-Worker-Token", workerSecret)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Failed to submit result for job {}: HTTP {} - {}", request.jobId(), response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Error submitting job result to Control Service: {}", e.getMessage());
        }
    }

    public void sendHeartbeat(WorkerHeartbeatRequest request) {
        try {
            String jsonBody = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(controlServiceUrl + "/api/internal/v1/workers/heartbeat"))
                    .header("X-Worker-Token", workerSecret)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            httpClient.send(httpRequest, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.debug("Failed to send worker heartbeat: {}", e.getMessage());
        }
    }
}
