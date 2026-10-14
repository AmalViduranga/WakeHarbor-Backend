package io.wakeharbor.control.service;

import io.wakeharbor.control.domain.*;
import io.wakeharbor.control.domain.enums.ProjectStatus;
import io.wakeharbor.control.dto.project.*;
import io.wakeharbor.control.repository.*;
import io.wakeharbor.control.security.EncryptionService;
import io.wakeharbor.control.security.SsrfValidator;
import io.wakeharbor.control.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final SupabaseProjectRepository projectRepository;
    private final ProjectCredentialsRepository credentialsRepository;
    private final HeartbeatScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;
    private final SsrfValidator ssrfValidator;
    private final EncryptionService encryptionService;
    private final SupabaseVerificationService verificationService;

    public ProjectService(
            SupabaseProjectRepository projectRepository,
            ProjectCredentialsRepository credentialsRepository,
            HeartbeatScheduleRepository scheduleRepository,
            UserRepository userRepository,
            AuditEventRepository auditEventRepository,
            SsrfValidator ssrfValidator,
            EncryptionService encryptionService,
            SupabaseVerificationService verificationService) {
        this.projectRepository = projectRepository;
        this.credentialsRepository = credentialsRepository;
        this.scheduleRepository = scheduleRepository;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.ssrfValidator = ssrfValidator;
        this.encryptionService = encryptionService;
        this.verificationService = verificationService;
    }

    @Transactional
    public ProjectResponse createProject(UserPrincipal principal, CreateProjectRequest request) {
        SsrfValidator.ValidatedSupabaseTarget target = ssrfValidator.validateUrl(request.supabaseUrl());

        if (projectRepository.existsByUserIdAndProjectRef(principal.id(), target.projectRef())) {
            throw new IllegalArgumentException("A project with reference '" + target.projectRef() + "' is already connected.");
        }

        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        int interval = (request.intervalHours() != null) ? request.intervalHours() : 12;

        // Encrypt credentials
        EncryptionService.EncryptedResult encryptedKey = encryptionService.encrypt(request.publishableKey().trim());

        SupabaseProject project = SupabaseProject.builder()
                .user(user)
                .name(request.name().trim())
                .projectRef(target.projectRef())
                .supabaseUrl(target.canonicalBaseUrl())
                .heartbeatMethod("RPC_WAKEHARBOR_HEARTBEAT")
                .status(ProjectStatus.PENDING_VERIFICATION)
                .enabled(true)
                .notes(request.notes())
                .build();

        SupabaseProject savedProject = projectRepository.save(project);

        ProjectCredentials credentials = ProjectCredentials.builder()
                .project(savedProject)
                .encryptedApiKey(encryptedKey.cipherTextBase64())
                .initializationVector(encryptedKey.ivHex())
                .authTag(encryptedKey.authTagHex())
                .keyPreview(encryptedKey.preview())
                .keyType(request.publishableKey().startsWith("sb_publishable_") ? "PUBLISHABLE" : "LEGACY_ANON")
                .keyVersion(1)
                .build();

        credentialsRepository.save(credentials);

        // Schedule next run
        Instant nextRun = Instant.now().plus(interval, ChronoUnit.HOURS);
        HeartbeatSchedule schedule = HeartbeatSchedule.builder()
                .project(savedProject)
                .intervalHours(interval)
                .nextRunAt(nextRun)
                .active(true)
                .build();

        scheduleRepository.save(schedule);

        auditEventRepository.save(AuditEvent.builder()
                .user(user)
                .project(savedProject)
                .action("CREATE_PROJECT")
                .entityType("SUPABASE_PROJECT")
                .entityId(savedProject.getId().toString())
                .details("Created Supabase project: " + savedProject.getName() + " (" + savedProject.getProjectRef() + ")")
                .build());

        log.info("Project created: {} [{}] for user: {}", savedProject.getName(), savedProject.getProjectRef(), user.getEmail());

        return toResponse(savedProject, credentials, schedule);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> getProjects(UserPrincipal principal, ProjectStatus status, String search, Pageable pageable) {
        Page<SupabaseProject> page = projectRepository.findFiltered(principal.id(), status, search, pageable);
        return page.map(p -> toResponse(p, p.getCredentials(), p.getSchedule()));
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UserPrincipal principal, UUID projectId) {
        SupabaseProject project = findUserProject(principal, projectId);
        return toResponse(project, project.getCredentials(), project.getSchedule());
    }

    @Transactional
    public ProjectResponse updateProject(UserPrincipal principal, UUID projectId, UpdateProjectRequest request) {
        SupabaseProject project = findUserProject(principal, projectId);

        project.setName(request.name().trim());
        project.setNotes(request.notes());

        if (request.intervalHours() != null && project.getSchedule() != null) {
            project.getSchedule().setIntervalHours(request.intervalHours());
            scheduleRepository.save(project.getSchedule());
        }

        SupabaseProject updated = projectRepository.save(project);

        auditEventRepository.save(AuditEvent.builder()
                .user(project.getUser())
                .project(project)
                .action("UPDATE_PROJECT")
                .entityType("SUPABASE_PROJECT")
                .entityId(project.getId().toString())
                .details("Updated settings for project: " + project.getName())
                .build());

        return toResponse(updated, updated.getCredentials(), updated.getSchedule());
    }

    @Transactional
    public ProjectResponse rotateKey(UserPrincipal principal, UUID projectId, RotateKeyRequest request) {
        SupabaseProject project = findUserProject(principal, projectId);
        EncryptionService.EncryptedResult encryptedKey = encryptionService.encrypt(request.publishableKey().trim());

        ProjectCredentials credentials = project.getCredentials();
        if (credentials == null) {
            credentials = ProjectCredentials.builder().project(project).build();
        }

        credentials.setEncryptedApiKey(encryptedKey.cipherTextBase64());
        credentials.setInitializationVector(encryptedKey.ivHex());
        credentials.setAuthTag(encryptedKey.authTagHex());
        credentials.setKeyPreview(encryptedKey.preview());
        credentials.setKeyType(request.publishableKey().startsWith("sb_publishable_") ? "PUBLISHABLE" : "LEGACY_ANON");
        credentials.setKeyVersion(credentials.getKeyVersion() + 1);

        credentialsRepository.save(credentials);

        auditEventRepository.save(AuditEvent.builder()
                .user(project.getUser())
                .project(project)
                .action("ROTATE_KEY")
                .entityType("PROJECT_CREDENTIALS")
                .entityId(project.getId().toString())
                .details("Rotated API key for project: " + project.getName())
                .build());

        log.info("API key rotated for project: {}", project.getProjectRef());
        return toResponse(project, credentials, project.getSchedule());
    }

    @Transactional
    public void deleteProject(UserPrincipal principal, UUID projectId) {
        SupabaseProject project = findUserProject(principal, projectId);
        String projectName = project.getName();
        String ref = project.getProjectRef();

        projectRepository.delete(project);

        auditEventRepository.save(AuditEvent.builder()
                .user(project.getUser())
                .action("DELETE_PROJECT")
                .entityType("SUPABASE_PROJECT")
                .entityId(projectId.toString())
                .details("Deleted project: " + projectName + " (" + ref + ")")
                .build());

        log.info("Project deleted: {} [{}]", projectName, ref);
    }

    @Transactional
    public ProjectResponse pauseProject(UserPrincipal principal, UUID projectId) {
        SupabaseProject project = findUserProject(principal, projectId);
        project.setEnabled(false);
        project.setStatus(ProjectStatus.PAUSED_BY_USER);

        if (project.getSchedule() != null) {
            project.getSchedule().setActive(false);
            scheduleRepository.save(project.getSchedule());
        }

        SupabaseProject updated = projectRepository.save(project);
        return toResponse(updated, updated.getCredentials(), updated.getSchedule());
    }

    @Transactional
    public ProjectResponse resumeProject(UserPrincipal principal, UUID projectId) {
        SupabaseProject project = findUserProject(principal, projectId);
        project.setEnabled(true);
        project.setStatus(ProjectStatus.HEALTHY);

        if (project.getSchedule() != null) {
            project.getSchedule().setActive(true);
            project.getSchedule().setNextRunAt(Instant.now().plus(project.getSchedule().getIntervalHours(), ChronoUnit.HOURS));
            scheduleRepository.save(project.getSchedule());
        }

        SupabaseProject updated = projectRepository.save(project);
        return toResponse(updated, updated.getCredentials(), updated.getSchedule());
    }

    public ValidateCredentialsResponse validateCredentials(ValidateCredentialsRequest request) {
        return verificationService.validate(request.supabaseUrl(), request.publishableKey());
    }

    @Transactional
    public ValidateCredentialsResponse validateExistingProject(UserPrincipal principal, UUID projectId) {
        SupabaseProject project = findUserProject(principal, projectId);
        ProjectCredentials creds = project.getCredentials();
        if (creds == null) {
            throw new IllegalStateException("Credentials missing for project");
        }

        String rawKey = encryptionService.decrypt(
                creds.getEncryptedApiKey(),
                creds.getInitializationVector(),
                creds.getAuthTag()
        );

        ValidateCredentialsResponse response = verificationService.validate(project.getSupabaseUrl(), rawKey);

        // Update project status based on test result
        if (response.valid()) {
            project.setStatus(ProjectStatus.HEALTHY);
            project.setLastSuccessfulAt(Instant.now());
            project.setConsecutiveFailures(0);
        } else {
            if ("AUTHENTICATION_FAILED".equals(response.errorCategory())) {
                project.setStatus(ProjectStatus.AUTHENTICATION_ERROR);
            } else if ("PROJECT_PAUSED".equals(response.errorCategory())) {
                project.setStatus(ProjectStatus.SUPABASE_PAUSED_CONFIRMED);
            } else {
                project.setStatus(ProjectStatus.CONNECTION_FAILED);
            }
            project.setConsecutiveFailures(project.getConsecutiveFailures() + 1);
        }

        project.setLastAttemptedAt(Instant.now());
        project.setLastLatencyMs(response.latencyMs());
        project.setLastErrorCategory(response.errorCategory());
        project.setLastErrorMessage(response.message());
        projectRepository.save(project);

        return response;
    }

    private SupabaseProject findUserProject(UserPrincipal principal, UUID projectId) {
        return projectRepository.findByIdAndUserId(projectId, principal.id())
                .orElseThrow(() -> new IllegalArgumentException("Project not found with id: " + projectId));
    }

    private ProjectResponse toResponse(SupabaseProject p, ProjectCredentials c, HeartbeatSchedule s) {
        return new ProjectResponse(
                p.getId(),
                p.getName(),
                p.getProjectRef(),
                p.getSupabaseUrl(),
                c != null ? c.getKeyPreview() : "N/A",
                p.getHeartbeatMethod(),
                p.getStatus(),
                p.isEnabled(),
                s != null ? s.getIntervalHours() : 12,
                s != null ? s.getNextRunAt() : null,
                p.getLastAttemptedAt(),
                p.getLastSuccessfulAt(),
                p.getLastLatencyMs(),
                p.getLastErrorCategory(),
                p.getLastErrorMessage(),
                p.getConsecutiveFailures(),
                p.getNotes(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
