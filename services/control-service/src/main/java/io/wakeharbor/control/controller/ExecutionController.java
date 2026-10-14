package io.wakeharbor.control.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.wakeharbor.control.domain.enums.ExecutionStatus;
import io.wakeharbor.control.dto.common.ApiResponse;
import io.wakeharbor.control.dto.common.PageResponse;
import io.wakeharbor.control.dto.execution.ExecutionDto;
import io.wakeharbor.control.security.UserPrincipal;
import io.wakeharbor.control.service.ExecutionService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/executions")
@Tag(name = "Executions", description = "Queryable historical heartbeat execution records")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @GetMapping
    @Operation(summary = "Search and filter global execution records")
    public ResponseEntity<ApiResponse<PageResponse<ExecutionDto>>> getExecutions(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) ExecutionStatus status,
            @PageableDefault(size = 20, sort = "executedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = executionService.getFilteredExecutions(principal, projectId, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }
}
