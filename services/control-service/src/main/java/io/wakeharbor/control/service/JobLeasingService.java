package io.wakeharbor.control.service;

import io.wakeharbor.control.domain.*;
import io.wakeharbor.control.domain.enums.*;
import io.wakeharbor.control.dto.worker.ClaimJobsRequest;
import io.wakeharbor.control.dto.worker.ClaimedJobDto;
import io.wakeharbor.control.dto.worker.SubmitJobResultRequest;
import io.wakeharbor.control.repository.*;
import io.wakeharbor.control.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class JobLeasingService {

    private static final Logger log = LoggerFactory.getLogger(JobLeasingService.class);

    private final ExecutionJobRepository jobRepository;
    private final HeartbeatScheduleRepository scheduleRepository;
    private final HeartbeatExecutionRepository executionRepository;
    private final SupabaseProjectRepository projectRepository;
    private final EncryptionService encryptionService;
    private final AlertService alertService;
    private final int leaseDurationSeconds;
    private final int defaultBatchSize;

    public JobLeasingService(
            ExecutionJobRepository jobRepository,
            HeartbeatScheduleRepository scheduleRepository,
            HeartbeatExecutionRepository executionRepository,
            SupabaseProjectRepository projectRepository,
            EncryptionService encryptionService,
            AlertService alertService,
            @Value("${wakeharbor.scheduling.lease-duration-seconds:300}") int leaseDurationSeconds,
            @Value("${wakeharbor.scheduling.claim-batch-size:10}") int defaultBatchSize) {
        this.jobRepository = jobRepository;
        this.scheduleRepository = scheduleRepository;
        this.executionRepository = executionRepository;
        this.projectRepository = projectRepository;
        this.encryptionService = encryptionService;
        this.alertService = alertService;
        this.leaseDurationSeconds = leaseDurationSeconds;
        this.defaultBatchSize = defaultBatchSize;
    }

    /**
     * Dispatcher: Checks for due schedules and queues execution jobs in PostgreSQL.
     */
    @Scheduled(fixedDelayString = "${wakeharbor.scheduling.dispatcher-interval-ms:10000}")
    @Transactional
    public void dispatchDueSchedules() {
        Instant now = Instant.now();
        List<HeartbeatSchedule> dueSchedules = scheduleRepository.findDueSchedules(now);

        for (HeartbeatSchedule schedule : dueSchedules) {
            SupabaseProject project = schedule.getProject();
            // Prevent duplicate job queues for the same project
            boolean hasPendingJob = jobRepository.existsByProjectIdAndStatusIn(
                    project.getId(),
                    List.of(JobStatus.PENDING, JobStatus.CLAIMED, JobStatus.EXECUTING)
            );

            if (!hasPendingJob) {
                ExecutionJob job = ExecutionJob.builder()
                        .project(project)
                        .triggerType(TriggerType.SCHEDULED)
                        .status(JobStatus.PENDING)
                        .attemptCount(0)
                        .maxAttempts(3)
                        .scheduledFor(now)
                        .build();

                jobRepository.save(job);
                log.info("Dispatched scheduled job {} for project {} [{}]", job.getId(), project.getName(), project.getProjectRef());
            }

            // Advance schedule to next interval
            schedule.setLastRunAt(now);
            schedule.setNextRunAt(now.plus(schedule.getIntervalHours(), ChronoUnit.HOURS));
            scheduleRepository.save(schedule);
        }
    }

    /**
     * Fault Recovery: Recovers orphaned or abandoned jobs if a worker crashes during execution.
     */
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void recoverExpiredLeases() {
        Instant now = Instant.now();
        List<ExecutionJob> expiredJobs = jobRepository.findExpiredLeases(now);

        for (ExecutionJob job : expiredJobs) {
            log.warn("Job lease expired for job {} (owner: {}, expired at: {})", 
                    job.getId(), job.getLeaseOwner(), job.getLeaseExpiresAt());

            if (job.getAttemptCount() + 1 >= job.getMaxAttempts()) {
                job.setStatus(JobStatus.FAILED);
                job.setCompletedAt(now);
                jobRepository.save(job);

                // Record failure execution
                HeartbeatExecution execution = HeartbeatExecution.builder()
                        .project(job.getProject())
                        .job(job)
                        .triggerType(job.getTriggerType())
                        .status(ExecutionStatus.TIMEOUT)
                        .errorCategory(ErrorCategory.CONNECTION_TIMEOUT.name())
                        .errorDetails("Job lease expired without worker report; exceeded maximum retry attempts (" + job.getMaxAttempts() + ")")
                        .executedAt(now)
                        .build();
                executionRepository.save(execution);

                updateProjectHealthOnFailure(job.getProject(), ErrorCategory.CONNECTION_TIMEOUT, "Worker lease timeout");
            } else {
                // Reset job back to retry queue with incremented attempt count
                job.setStatus(JobStatus.PENDING);
                job.setAttemptCount(job.getAttemptCount() + 1);
                job.setLeaseToken(null);
                job.setLeaseOwner(null);
                job.setLeaseExpiresAt(null);
                jobRepository.save(job);
                log.info("Reset job {} to PENDING queue for retry (attempt {})", job.getId(), job.getAttemptCount());
            }
        }
    }

    /**
     * Atomic Job Claiming:
     * Uses SELECT ... FOR UPDATE SKIP LOCKED to grant exclusive leases to workers without race conditions.
     */
    @Transactional
    public List<ClaimedJobDto> claimJobs(ClaimJobsRequest request) {
        Instant now = Instant.now();
        int batchSize = (request.maxBatchSize() != null) ? request.maxBatchSize() : defaultBatchSize;

        List<ExecutionJob> claimableJobs = jobRepository.claimDueJobsForUpdateSkipLocked(now, batchSize);
        if (claimableJobs.isEmpty()) {
            return Collections.emptyList();
        }

        List<ClaimedJobDto> claimedDtos = new ArrayList<>();
        Instant leaseExpiry = now.plusSeconds(leaseDurationSeconds);

        for (ExecutionJob job : claimableJobs) {
            String leaseToken = UUID.randomUUID().toString();
            job.setStatus(JobStatus.CLAIMED);
            job.setLeaseToken(leaseToken);
            job.setLeaseOwner(request.workerId());
            job.setLeaseExpiresAt(leaseExpiry);
            job.setClaimedAt(now);
            jobRepository.save(job);

            SupabaseProject project = job.getProject();
            ProjectCredentials credentials = project.getCredentials();

            if (credentials != null) {
                try {
                    String decryptedKey = encryptionService.decrypt(
                            credentials.getEncryptedApiKey(),
                            credentials.getInitializationVector(),
                            credentials.getAuthTag()
                    );

                    String rpcEndpoint = project.getSupabaseUrl() + "/rest/v1/rpc/wakeharbor_heartbeat";
                    claimedDtos.add(new ClaimedJobDto(
                            job.getId(),
                            project.getId(),
                            project.getProjectRef(),
                            project.getSupabaseUrl(),
                            rpcEndpoint,
                            decryptedKey,
                            leaseToken,
                            job.getTriggerType(),
                            job.getAttemptCount()
                    ));
                } catch (Exception e) {
                    log.error("Failed to decrypt credentials for job {}: {}", job.getId(), e.getMessage());
                    job.setStatus(JobStatus.FAILED);
                    job.setCompletedAt(now);
                    jobRepository.save(job);
                }
            }
        }

        log.info("Worker {} claimed {} due jobs (lease valid for {}s)", request.workerId(), claimedDtos.size(), leaseDurationSeconds);
        return claimedDtos;
    }

    /**
     * Submits execution results from the Heartbeat Worker idempotently.
     */
    @Transactional
    public void submitJobResult(SubmitJobResultRequest request) {
        ExecutionJob job = jobRepository.findById(request.jobId())
                .orElseThrow(() -> new IllegalArgumentException("Execution job not found: " + request.jobId()));

        // Verify lease ownership to prevent stale worker writes
        if (job.getLeaseToken() == null || !job.getLeaseToken().equals(request.leaseToken())) {
            log.warn("Ignored result for job {} due to lease token mismatch. Stored: {}, Provided: {}",
                    job.getId(), job.getLeaseToken(), request.leaseToken());
            return;
        }

        Instant now = Instant.now();
        boolean isSuccess = (request.status() == ExecutionStatus.SUCCESS);

        job.setStatus(isSuccess ? JobStatus.COMPLETED : JobStatus.FAILED);
        job.setCompletedAt(now);
        jobRepository.save(job);

        // Record execution record
        HeartbeatExecution execution = HeartbeatExecution.builder()
                .project(job.getProject())
                .job(job)
                .triggerType(job.getTriggerType())
                .status(request.status())
                .httpStatusCode(request.httpStatusCode())
                .latencyMs(request.latencyMs())
                .errorCategory(request.errorCategory() != null ? request.errorCategory().name() : null)
                .errorDetails(request.errorDetails())
                .responsePayloadSummary(request.responsePayloadSummary())
                .executedAt(now)
                .build();

        executionRepository.save(execution);

        // Update Project Operational State
        SupabaseProject project = job.getProject();
        project.setLastAttemptedAt(now);
        project.setLastLatencyMs(request.latencyMs());

        if (isSuccess) {
            project.setStatus(ProjectStatus.HEALTHY);
            project.setLastSuccessfulAt(now);
            project.setLastErrorCategory(null);
            project.setLastErrorMessage(null);
            int prevFailures = project.getConsecutiveFailures();
            project.setConsecutiveFailures(0);
            projectRepository.save(project);

            if (prevFailures >= 2) {
                // Recovery alert
                alertService.sendRecoveryAlert(project, request.latencyMs());
            }
        } else {
            updateProjectHealthOnFailure(project, request.errorCategory(), request.errorDetails());
        }

        log.info("Recorded execution result for job {} [project: {}]: status={}, latency={}ms",
                job.getId(), project.getProjectRef(), request.status(), request.latencyMs());
    }

    private void updateProjectHealthOnFailure(SupabaseProject project, ErrorCategory errorCategory, String errorMessage) {
        int failures = project.getConsecutiveFailures() + 1;
        project.setConsecutiveFailures(failures);
        project.setLastErrorCategory(errorCategory != null ? errorCategory.name() : "UNEXPECTED_ERROR");
        project.setLastErrorMessage(errorMessage);

        if (errorCategory == ErrorCategory.AUTHENTICATION_FAILED || errorCategory == ErrorCategory.PERMISSION_DENIED) {
            project.setStatus(ProjectStatus.AUTHENTICATION_ERROR);
        } else if (errorCategory == ErrorCategory.PROJECT_PAUSED) {
            project.setStatus(ProjectStatus.SUPABASE_PAUSED_CONFIRMED);
        } else if (failures >= 3) {
            project.setStatus(ProjectStatus.CONNECTION_FAILED);
        } else {
            project.setStatus(ProjectStatus.DEGRADED);
        }

        projectRepository.save(project);
        alertService.checkAndTriggerFailureAlert(project, failures, errorMessage);
    }
}
