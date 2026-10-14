package io.wakeharbor.control.repository;

import io.wakeharbor.control.domain.ProjectCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectCredentialsRepository extends JpaRepository<ProjectCredentials, UUID> {
    Optional<ProjectCredentials> findByProjectId(UUID projectId);
}
