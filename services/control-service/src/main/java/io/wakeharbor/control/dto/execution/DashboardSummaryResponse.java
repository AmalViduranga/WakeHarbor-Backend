package io.wakeharbor.control.dto.execution;

import java.util.List;
import java.util.UUID;

public record DashboardSummaryResponse(
        long totalProjects,
        long healthyProjects,
        long failedProjects,
        long pausedProjects,
        long executions24h,
        double successRate24h,
        Double averageLatencyMs,
        List<ExecutionDto> recentExecutions,
        List<UpcomingJobDto> upcomingJobs
) {
    public record UpcomingJobDto(
            UUID jobId,
            UUID projectId,
            String projectName,
            String projectRef,
            String scheduledFor
    ) {}
}
