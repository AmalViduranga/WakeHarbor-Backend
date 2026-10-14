package io.wakeharbor.control.repository;

import io.wakeharbor.control.domain.ExecutionJob;
import io.wakeharbor.control.domain.enums.JobStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, UUID> {

    Optional<ExecutionJob> findByIdAndLeaseToken(UUID id, String leaseToken);

    boolean existsByProjectIdAndStatusIn(UUID projectId, List<JobStatus> statuses);

    /**
     * Atomically selects and locks due jobs using PostgreSQL's FOR UPDATE SKIP LOCKED.
     * Prevents race conditions across multiple distributed workers.
     */
    @Query(value = "SELECT * FROM execution_jobs j " +
                   "WHERE j.status IN ('PENDING', 'RETRY') " +
                   "AND j.scheduled_for <= :now " +
                   "ORDER BY j.scheduled_for ASC " +
                   "LIMIT :limit " +
                   "FOR UPDATE SKIP LOCKED", 
           nativeQuery = true)
    List<ExecutionJob> claimDueJobsForUpdateSkipLocked(@Param("now") Instant now, @Param("limit") int limit);

    /**
     * Finds jobs where lease has expired without completion (worker crash recovery).
     */
    @Query("SELECT j FROM ExecutionJob j WHERE j.status = 'CLAIMED' AND j.leaseExpiresAt < :now")
    List<ExecutionJob> findExpiredLeases(@Param("now") Instant now);

    List<ExecutionJob> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    @Query("SELECT j FROM ExecutionJob j WHERE j.status IN ('PENDING', 'CLAIMED', 'EXECUTING') " +
           "ORDER BY j.scheduledFor ASC")
    List<ExecutionJob> findUpcomingJobs(Pageable pageable);
}
