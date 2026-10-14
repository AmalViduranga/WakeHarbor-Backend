package io.wakeharbor.control.repository;

import io.wakeharbor.control.domain.HeartbeatExecution;
import io.wakeharbor.control.domain.enums.ExecutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface HeartbeatExecutionRepository extends JpaRepository<HeartbeatExecution, UUID> {

    Page<HeartbeatExecution> findByProjectIdOrderByExecutedAtDesc(UUID projectId, Pageable pageable);

    @Query("SELECT e FROM HeartbeatExecution e WHERE e.project.user.id = :userId " +
           "AND (:projectId IS NULL OR e.project.id = :projectId) " +
           "AND (:status IS NULL OR e.status = :status) " +
           "ORDER BY e.executedAt DESC")
    Page<HeartbeatExecution> findFilteredExecutions(@Param("userId") UUID userId,
                                                   @Param("projectId") UUID projectId,
                                                   @Param("status") ExecutionStatus status,
                                                   Pageable pageable);

    List<HeartbeatExecution> findTop10ByProjectUserIdOrderByExecutedAtDesc(UUID userId);

    long countByProjectUserIdAndStatus(UUID userId, ExecutionStatus status);

    long countByProjectUserIdAndExecutedAtAfter(UUID userId, Instant after);

    long countByProjectUserIdAndStatusAndExecutedAtAfter(UUID userId, ExecutionStatus status, Instant after);

    @Query("SELECT AVG(e.latencyMs) FROM HeartbeatExecution e WHERE e.project.user.id = :userId AND e.status = 'SUCCESS' AND e.executedAt >= :after")
    Double getAverageLatencySince(@Param("userId") UUID userId, @Param("after") Instant after);

    @Modifying
    @Query("DELETE FROM HeartbeatExecution e WHERE e.executedAt < :cutoff")
    int deleteExecutionsOlderThan(@Param("cutoff") Instant cutoff);
}
