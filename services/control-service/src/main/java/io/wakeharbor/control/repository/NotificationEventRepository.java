package io.wakeharbor.control.repository;

import io.wakeharbor.control.domain.NotificationEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationEventRepository extends JpaRepository<NotificationEvent, UUID> {

    Page<NotificationEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT e FROM NotificationEvent e WHERE e.project.id = :projectId " +
           "AND e.eventType = :eventType AND e.createdAt >= :since " +
           "ORDER BY e.createdAt DESC LIMIT 1")
    Optional<NotificationEvent> findRecentEvent(@Param("projectId") UUID projectId,
                                                @Param("eventType") String eventType,
                                                @Param("since") Instant since);
}
