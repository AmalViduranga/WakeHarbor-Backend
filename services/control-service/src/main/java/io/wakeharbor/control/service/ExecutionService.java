package io.wakeharbor.control.service;

import io.wakeharbor.control.domain.*;
import io.wakeharbor.control.domain.enums.*;
import io.wakeharbor.control.dto.execution.ExecutionDto;
import io.wakeharbor.control.repository.*;
import io.wakeharbor.control.security.EncryptionService;
import io.wakeharbor.control.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final SupabaseProjectRepository projectRepository;
    private final ExecutionJobRepository jobRepository;
    private final HeartbeatExecutionRepository executionRepository;
    private final EncryptionService encryptionService;
    private final SupabaseVerificationService verificationService;
    private final AlertService alertService;

    public ExecutionService(
            SupabaseProjectRepository projectRepository,
            ExecutionJobRepository jobRepository,
            HeartbeatExecutionRepository executionRepository,
            EncryptionService encryptionService,
            SupabaseVerificationService verificationService,
            AlertService alertService) {
        this.projectRepository = projectRepository;
        this.jobRepository = jobRepository;
        this.executionRepository = executionRepository;
        this.encryptionService = encryptionService;
        this.verificationService = verificationService;
        this.alertService = alertService;
    }

    /**
     * Executes an immediate, on-demand manual heartbeat and records the outcome.
     */
    @Transactional
    public ExecutionDto runNow(UserPrincipal principal, UUID projectId) {
        SupabaseProject project = projectRepository.findByIdAndUserId(projectId, principal.id())
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));

        ProjectCredentials credentials = project.getCredentials();
        if (credentials == null) {
            throw new IllegalStateException("Credentials missing for project: " + project.getName());
        }

        String decryptedKey = encryptionService.decrypt(
                credentials.getEncryptedApiKey(),
                credentials.getInitializationVector(),
                credentials.getAuthTag()
        );

        Instant now = Instant.now();

        // Create high-priority manual ExecutionJob record
        ExecutionJob job = ExecutionJob.builder()
                .project(project)
                .triggerType(TriggerType.MANUAL)
                .status(JobStatus.EXECUTING)
                .scheduledFor(now)
                .claimedAt(now)
                .build();
        jobRepository.save(job);

        // Perform actual RPC heartbeat request
        SupabaseVerificationService.ExecutionResult result = verificationService.executeHeartbeat(
                project.getSupabaseUrl(),
                decryptedKey
        );

        boolean isSuccess = (result.status() == ExecutionStatus.SUCCESS);
        job.setStatus(isSuccess ? JobStatus.COMPLETED : JobStatus.FAILED);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);

        // Save execution log
        HeartbeatExecution execution = HeartbeatExecution.builder()
                .project(project)
                .job(job)
                .triggerType(TriggerType.MANUAL)
                .status(result.status())
                .httpStatusCode(result.httpStatusCode())
                .latencyMs(result.latencyMs())
                .errorCategory(result.errorCategory() != null ? result.errorCategory().name() : null)
                .errorDetails(result.errorMessage())
                .responsePayloadSummary(result.payloadSummary())
                .executedAt(now)
                .build();

        HeartbeatExecution savedExecution = executionRepository.save(execution);

        // Update project state
        project.setLastAttemptedAt(now);
        project.setLastLatencyMs(result.latencyMs());

        if (isSuccess) {
            project.setStatus(ProjectStatus.HEALTHY);
            project.setLastSuccessfulAt(now);
            project.setLastErrorCategory(null);
            project.setLastErrorMessage(null);
            int prevFailures = project.getConsecutiveFailures();
            project.setConsecutiveFailures(0);
            if (prevFailures >= 2) {
                alertService.sendRecoveryAlert(project, result.latencyMs());
            }
        } else {
            int failures = project.getConsecutiveFailures() + 1;
            project.setConsecutiveFailures(failures);
            project.setLastErrorCategory(result.errorCategory() != null ? result.errorCategory().name() : "UNEXPECTED_ERROR");
            project.setLastErrorMessage(result.errorMessage());

            if (result.errorCategory() == ErrorCategory.AUTHENTICATION_FAILED || result.errorCategory() == ErrorCategory.PERMISSION_DENIED) {
                project.setStatus(ProjectStatus.AUTHENTICATION_ERROR);
            } else if (result.errorCategory() == ErrorCategory.PROJECT_PAUSED) {
                project.setStatus(ProjectStatus.SUPABASE_PAUSED_CONFIRMED);
            } else {
                project.setStatus(ProjectStatus.CONNECTION_FAILED);
            }
            alertService.checkAndTriggerFailureAlert(project, failures, result.errorMessage());
        }

        projectRepository.save(project);
        log.info("Manual Run Now completed for project {} [{}]: status={}, latency={}ms",
                project.getName(), project.getProjectRef(), result.status(), result.latencyMs());

        return toDto(savedExecution);
    }

    @Transactional(readOnly = true)
    public Page<ExecutionDto> getProjectExecutions(UserPrincipal principal, UUID projectId, Pageable pageable) {
        // Verify ownership
        projectRepository.findByIdAndUserId(projectId, principal.id())
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));

        return executionRepository.findByProjectIdOrderByExecutedAtDesc(projectId, pageable)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Page<ExecutionDto> getFilteredExecutions(UserPrincipal principal, UUID projectId, ExecutionStatus status, Pageable pageable) {
        return executionRepository.findFilteredExecutions(principal.id(), projectId, status, pageable)
                .map(this::toDto);
    }

    private ExecutionDto toDto(HeartbeatExecution e) {
        return new ExecutionDto(
                e.getId(),
                e.getProject().getId(),
                e.getProject().getName(),
                e.getProject().getProjectRef(),
                e.getTriggerType(),
                e.getStatus(),
                e.getHttpStatusCode(),
                e.getLatencyMs(),
                e.getErrorCategory(),
                e.getErrorDetails(),
                e.getResponsePayloadSummary(),
                e.getExecutedAt()
        );
    }
}
