package io.wakeharbor.control.repository;

import io.wakeharbor.control.domain.HeartbeatSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HeartbeatScheduleRepository extends JpaRepository<HeartbeatSchedule, UUID> {

    Optional<HeartbeatSchedule> findByProjectId(UUID projectId);

    @Query("SELECT s FROM HeartbeatSchedule s JOIN FETCH s.project p " +
           "WHERE s.active = true AND p.enabled = true AND s.nextRunAt <= :cutoff")
    List<HeartbeatSchedule> findDueSchedules(@Param("cutoff") Instant cutoff);
}
