package io.wakeharbor.control.repository;

import io.wakeharbor.control.domain.SupabaseProject;
import io.wakeharbor.control.domain.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupabaseProjectRepository extends JpaRepository<SupabaseProject, UUID> {

    List<SupabaseProject> findByUserId(UUID userId);

    Optional<SupabaseProject> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndProjectRef(UUID userId, String projectRef);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, ProjectStatus status);

    long countByUserIdAndEnabled(UUID userId, boolean enabled);

    @Query("SELECT p FROM SupabaseProject p WHERE p.user.id = :userId AND " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.projectRef) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<SupabaseProject> findFiltered(@Param("userId") UUID userId,
                                      @Param("status") ProjectStatus status,
                                      @Param("search") String search,
                                      Pageable pageable);
}
