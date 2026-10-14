package io.wakeharbor.control.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.wakeharbor.control.domain.ApplicationSetting;
import io.wakeharbor.control.domain.WorkerHeartbeat;
import io.wakeharbor.control.dto.common.ApiResponse;
import io.wakeharbor.control.repository.ApplicationSettingRepository;
import io.wakeharbor.control.service.WorkerMonitorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
@Tag(name = "Settings", description = "System configuration, application settings, and background worker status")
public class SettingsController {

    private final ApplicationSettingRepository settingRepository;
    private final WorkerMonitorService workerMonitorService;

    public SettingsController(ApplicationSettingRepository settingRepository, WorkerMonitorService workerMonitorService) {
        this.settingRepository = settingRepository;
        this.workerMonitorService = workerMonitorService;
    }

    @GetMapping
    @Operation(summary = "Get all application settings")
    public ResponseEntity<ApiResponse<List<ApplicationSetting>>> getSettings() {
        List<ApplicationSetting> settings = settingRepository.findAll();
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PutMapping("/{key}")
    @Operation(summary = "Update an application setting value")
    public ResponseEntity<ApiResponse<ApplicationSetting>> updateSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value == null) {
            throw new IllegalArgumentException("Value is required");
        }

        ApplicationSetting setting = settingRepository.findById(key)
                .orElse(ApplicationSetting.builder().key(key).build());

        setting.setValue(value);
        ApplicationSetting saved = settingRepository.save(setting);
        return ResponseEntity.ok(ApiResponse.ok("Setting updated", saved));
    }

    @GetMapping("/workers")
    @Operation(summary = "Get background worker nodes health status")
    public ResponseEntity<ApiResponse<List<WorkerHeartbeat>>> getWorkers() {
        List<WorkerHeartbeat> workers = workerMonitorService.getActiveWorkers();
        return ResponseEntity.ok(ApiResponse.ok(workers));
    }
}
