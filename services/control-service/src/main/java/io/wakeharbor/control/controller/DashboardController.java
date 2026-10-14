package io.wakeharbor.control.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.wakeharbor.control.dto.common.ApiResponse;
import io.wakeharbor.control.dto.execution.DashboardSummaryResponse;
import io.wakeharbor.control.security.UserPrincipal;
import io.wakeharbor.control.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Real-time aggregated health statistics and monitoring trends")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get aggregated dashboard statistics and recent activity")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary(@AuthenticationPrincipal UserPrincipal principal) {
        DashboardSummaryResponse summary = dashboardService.getSummary(principal);
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }
}
