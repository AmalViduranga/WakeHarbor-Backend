package io.wakeharbor.control.service;

import io.wakeharbor.control.domain.enums.ExecutionStatus;
import io.wakeharbor.control.domain.enums.ProjectStatus;
import io.wakeharbor.control.dto.execution.DashboardSummaryResponse;
import io.wakeharbor.control.dto.execution.ExecutionDto;
import io.wakeharbor.control.repository.ExecutionJobRepository;
import io.wakeharbor.control.repository.HeartbeatExecutionRepository;
import io.wakeharbor.control.repository.SupabaseProjectRepository;
import io.wakeharbor.control.security.UserPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DashboardService {

    private final SupabaseProjectRepository projectRepository;
    private final HeartbeatExecutionRepository executionRepository;
    private final ExecutionJobRepository jobRepository;

    public DashboardService(
            SupabaseProjectRepository projectRepository,
            HeartbeatExecutionRepository executionRepository,
            ExecutionJobRepository jobRepository) {
        this.projectRepository = projectRepository;
        this.executionRepository = executionRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(UserPrincipal principal) {
        Instant since24h = Instant.now().minus(24, ChronoUnit.HOURS);

        long total = projectRepository.countByUserId(principal.id());
        long healthy = projectRepository.countByUserIdAndStatus(principal.id(), ProjectStatus.HEALTHY);
        long paused = projectRepository.countByUserIdAndStatus(principal.id(), ProjectStatus.PAUSED_BY_USER);
        long failed = projectRepository.countByUserIdAndStatus(principal.id(), ProjectStatus.CONNECTION_FAILED)
                + projectRepository.countByUserIdAndStatus(principal.id(), ProjectStatus.AUTHENTICATION_ERROR)
                + projectRepository.countByUserIdAndStatus(principal.id(), ProjectStatus.SUPABASE_PAUSED_CONFIRMED)
                + projectRepository.countByUserIdAndStatus(principal.id(), ProjectStatus.DEGRADED);

        long exec24h = executionRepository.countByProjectUserIdAndExecutedAtAfter(principal.id(), since24h);
        long success24h = executionRepository.countByProjectUserIdAndStatusAndExecutedAtAfter(
                principal.id(), ExecutionStatus.SUCCESS, since24h
        );

        double successRate = (exec24h > 0) ? ((double) success24h / exec24h) * 100.0 : 100.0;
        Double avgLatency = executionRepository.getAverageLatencySince(principal.id(), since24h);

        List<ExecutionDto> recent = executionRepository.findTop10ByProjectUserIdOrderByExecutedAtDesc(principal.id())
                .stream()
                .map(e -> new ExecutionDto(
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
                ))
                .toList();

        List<DashboardSummaryResponse.UpcomingJobDto> upcoming = jobRepository.findUpcomingJobs(PageRequest.of(0, 5))
                .stream()
                .map(j -> new DashboardSummaryResponse.UpcomingJobDto(
                        j.getId(),
                        j.getProject().getId(),
                        j.getProject().getName(),
                        j.getProject().getProjectRef(),
                        j.getScheduledFor().toString()
                ))
                .toList();

        return new DashboardSummaryResponse(
                total,
                healthy,
                failed,
                paused,
                exec24h,
                Math.round(successRate * 10.0) / 10.0,
                avgLatency != null ? Math.round(avgLatency * 10.0) / 10.0 : 0.0,
                recent,
                upcoming
        );
    }
}
