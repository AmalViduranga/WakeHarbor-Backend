package io.wakeharbor.control.service;

import io.wakeharbor.control.repository.ApplicationSettingRepository;
import io.wakeharbor.control.repository.HeartbeatExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Periodically prunes historical heartbeat execution records based on the retention_days setting.
 */
@Service
public class LogRetentionService {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionService.class);

    private final HeartbeatExecutionRepository executionRepository;
    private final ApplicationSettingRepository settingRepository;

    public LogRetentionService(
            HeartbeatExecutionRepository executionRepository,
            ApplicationSettingRepository settingRepository) {
        this.executionRepository = executionRepository;
        this.settingRepository = settingRepository;
    }

    @Scheduled(cron = "${wakeharbor.scheduling.retention-cleanup-cron:0 0 2 * * ?}")
    @Transactional
    public void cleanupOldExecutionLogs() {
        int retentionDays = 30;
        try {
            var setting = settingRepository.findById("retention_days");
            if (setting.isPresent()) {
                retentionDays = Integer.parseInt(setting.get().getValue());
            }
        } catch (Exception e) {
            log.warn("Could not parse retention_days setting, defaulting to 30: {}", e.getMessage());
        }

        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = executionRepository.deleteExecutionsOlderThan(cutoff);
        log.info("Retention cleanup executed: pruned {} execution logs older than {} days (cutoff: {})",
                deleted, retentionDays, cutoff);
    }
}
