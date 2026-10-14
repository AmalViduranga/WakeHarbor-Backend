package io.wakeharbor.control.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.wakeharbor.control.dto.alert.AlertDto;
import io.wakeharbor.control.dto.alert.NotificationPreferencesDto;
import io.wakeharbor.control.dto.alert.NotificationPreferencesUpdateRequest;
import io.wakeharbor.control.dto.common.ApiResponse;
import io.wakeharbor.control.dto.common.PageResponse;
import io.wakeharbor.control.security.UserPrincipal;
import io.wakeharbor.control.service.AlertService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
@Tag(name = "Alerts", description = "System alerts, notification history, and SMTP email settings")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    @Operation(summary = "List alert events with pagination")
    public ResponseEntity<ApiResponse<PageResponse<AlertDto>>> getAlerts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = alertService.getAlerts(pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(page)));
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get notification preferences and SMTP configuration status")
    public ResponseEntity<ApiResponse<NotificationPreferencesDto>> getPreferences(@AuthenticationPrincipal UserPrincipal principal) {
        NotificationPreferencesDto dto = alertService.getPreferences(principal);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update notification preferences")
    public ResponseEntity<ApiResponse<NotificationPreferencesDto>> updatePreferences(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody NotificationPreferencesUpdateRequest request) {
        NotificationPreferencesDto dto = alertService.updatePreferences(principal, request);
        return ResponseEntity.ok(ApiResponse.ok("Notification preferences updated", dto));
    }
}
