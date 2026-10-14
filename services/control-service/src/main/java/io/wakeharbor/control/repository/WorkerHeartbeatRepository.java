package io.wakeharbor.control.repository;

import io.wakeharbor.control.domain.WorkerHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkerHeartbeatRepository extends JpaRepository<WorkerHeartbeat, UUID> {

    Optional<WorkerHeartbeat> findByWorkerId(String workerId);

    @Query("SELECT w FROM WorkerHeartbeat w WHERE w.status = 'ONLINE' AND w.lastHeartbeatAt < :threshold")
    List<WorkerHeartbeat> findStaleWorkers(@Param("threshold") Instant threshold);

    List<WorkerHeartbeat> findAllByOrderByLastHeartbeatAtDesc();
}
