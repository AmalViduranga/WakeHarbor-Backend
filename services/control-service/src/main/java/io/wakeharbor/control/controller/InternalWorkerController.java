package io.wakeharbor.control.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.wakeharbor.control.dto.common.ApiResponse;
import io.wakeharbor.control.dto.worker.ClaimJobsRequest;
import io.wakeharbor.control.dto.worker.ClaimedJobDto;
import io.wakeharbor.control.dto.worker.SubmitJobResultRequest;
import io.wakeharbor.control.dto.worker.WorkerHeartbeatRequest;
import io.wakeharbor.control.service.JobLeasingService;
import io.wakeharbor.control.service.WorkerMonitorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/internal/v1")
@Hidden // Hidden from public Swagger documentation
public class InternalWorkerController {

    private final JobLeasingService jobLeasingService;
    private final WorkerMonitorService workerMonitorService;

    public InternalWorkerController(JobLeasingService jobLeasingService, WorkerMonitorService workerMonitorService) {
        this.jobLeasingService = jobLeasingService;
        this.workerMonitorService = workerMonitorService;
    }

    @PostMapping("/jobs/claim")
    public ResponseEntity<ApiResponse<List<ClaimedJobDto>>> claimJobs(@Valid @RequestBody ClaimJobsRequest request) {
        List<ClaimedJobDto> jobs = jobLeasingService.claimJobs(request);
        return ResponseEntity.ok(ApiResponse.ok(jobs));
    }

    @PostMapping("/jobs/{id}/result")
    public ResponseEntity<ApiResponse<Void>> submitResult(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitJobResultRequest request) {
        if (!id.equals(request.jobId())) {
            throw new IllegalArgumentException("Path ID and request payload Job ID mismatch");
        }
        jobLeasingService.submitJobResult(request);
        return ResponseEntity.ok(ApiResponse.ok("Job result processed successfully", null));
    }

    @PostMapping("/workers/heartbeat")
    public ResponseEntity<ApiResponse<Void>> recordWorkerHeartbeat(@Valid @RequestBody WorkerHeartbeatRequest request) {
        workerMonitorService.recordHeartbeat(request);
        return ResponseEntity.ok(ApiResponse.ok("Worker heartbeat acknowledged", null));
    }
}
