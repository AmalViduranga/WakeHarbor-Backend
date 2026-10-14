package io.wakeharbor.control.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.wakeharbor.control.domain.enums.ProjectStatus;
import io.wakeharbor.control.dto.common.ApiResponse;
import io.wakeharbor.control.dto.common.PageResponse;
import io.wakeharbor.control.dto.execution.ExecutionDto;
import io.wakeharbor.control.dto.project.*;
import io.wakeharbor.control.security.UserPrincipal;
import io.wakeharbor.control.service.ExecutionService;
import io.wakeharbor.control.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@Tag(name = "Projects", description = "Endpoints for Supabase project registration, configuration, testing, and lifecycle management")
public class ProjectController {

    private final ProjectService projectService;
    private final ExecutionService executionService;

    public ProjectController(ProjectService projectService, ExecutionService executionService) {
        this.projectService = projectService;
        this.executionService = executionService;
    }

    @GetMapping
    @Operation(summary = "List Supabase projects with optional status filtering and search")
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> getProjects(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = projectService.getProjects(principal, status, search, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    @PostMapping
    @Operation(summary = "Register a new Supabase project", description = "Validates URL, encrypts publishable key, and provisions initial heartbeat schedule")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse response = projectService.createProject(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Project registered successfully", response));
    }

    @PostMapping("/validate-credentials")
    @Operation(summary = "Test credentials preview", description = "Executes an immediate live test against the Supabase RPC function prior to adding the project")
    public ResponseEntity<ApiResponse<ValidateCredentialsResponse>> validateCredentials(
            @Valid @RequestBody ValidateCredentialsRequest request) {
        ValidateCredentialsResponse response = projectService.validateCredentials(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project details by ID")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        ProjectResponse response = projectService.getProject(principal, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update project metadata and interval")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectRequest request) {
        ProjectResponse response = projectService.updateProject(principal, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Project updated successfully", response));
    }

    @PostMapping("/{id}/rotate-key")
    @Operation(summary = "Rotate project publishable API key")
    public ResponseEntity<ApiResponse<ProjectResponse>> rotateKey(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RotateKeyRequest request) {
        ProjectResponse response = projectService.rotateKey(principal, id, request);
        return ResponseEntity.ok(ApiResponse.ok("API key updated successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete project and associated schedules/history")
    public ResponseEntity<ApiResponse<Void>> deleteProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        projectService.deleteProject(principal, id);
        return ResponseEntity.ok(ApiResponse.ok("Project deleted successfully", null));
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "Pause heartbeat monitoring for project")
    public ResponseEntity<ApiResponse<ProjectResponse>> pauseProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        ProjectResponse response = projectService.pauseProject(principal, id);
        return ResponseEntity.ok(ApiResponse.ok("Monitoring paused", response));
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Resume heartbeat monitoring for project")
    public ResponseEntity<ApiResponse<ProjectResponse>> resumeProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        ProjectResponse response = projectService.resumeProject(principal, id);
        return ResponseEntity.ok(ApiResponse.ok("Monitoring resumed", response));
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate connection using saved credentials")
    public ResponseEntity<ApiResponse<ValidateCredentialsResponse>> validateExistingProject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        ValidateCredentialsResponse response = projectService.validateExistingProject(principal, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/run")
    @Operation(summary = "Run Now: Execute immediate manual heartbeat")
    public ResponseEntity<ApiResponse<ExecutionDto>> runNow(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        ExecutionDto execution = executionService.runNow(principal, id);
        return ResponseEntity.ok(ApiResponse.ok("Heartbeat executed successfully", execution));
    }

    @GetMapping("/{id}/executions")
    @Operation(summary = "Get execution history for a specific project")
    public ResponseEntity<ApiResponse<PageResponse<ExecutionDto>>> getProjectExecutions(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PageableDefault(size = 10, sort = "executedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = executionService.getProjectExecutions(principal, id, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }
}
