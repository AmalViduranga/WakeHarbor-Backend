package io.wakeharbor.control.service;

import io.wakeharbor.control.domain.WorkerHeartbeat;
import io.wakeharbor.control.dto.worker.WorkerHeartbeatRequest;
import io.wakeharbor.control.repository.WorkerHeartbeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class WorkerMonitorService {

    private static final Logger log = LoggerFactory.getLogger(WorkerMonitorService.class);

    private final WorkerHeartbeatRepository workerRepository;
    private final AlertService alertService;
    private final int livenessTimeoutSeconds;

    public WorkerMonitorService(
            WorkerHeartbeatRepository workerRepository,
            AlertService alertService,
            @Value("${wakeharbor.worker.liveness-timeout-seconds:180}") int livenessTimeoutSeconds) {
        this.workerRepository = workerRepository;
        this.alertService = alertService;
        this.livenessTimeoutSeconds = livenessTimeoutSeconds;
    }

    @Transactional
    public void recordHeartbeat(WorkerHeartbeatRequest request) {
        Instant now = Instant.now();
        WorkerHeartbeat worker = workerRepository.findByWorkerId(request.workerId())
                .orElseGet(() -> WorkerHeartbeat.builder()
                        .workerId(request.workerId())
                        .build());

        worker.setHostname(request.hostname());
        worker.setCurrentLoad(request.currentLoad());
        worker.setCompletedJobsCount(request.completedJobsCount());
        worker.setFailedJobsCount(request.failedJobsCount());
        worker.setLastHeartbeatAt(now);
        worker.setStatus("ONLINE");

        workerRepository.save(worker);
        log.debug("Worker heartbeat received: {} (load: {})", request.workerId(), request.currentLoad());
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void checkStaleWorkers() {
        Instant threshold = Instant.now().minus(livenessTimeoutSeconds, ChronoUnit.SECONDS);
        List<WorkerHeartbeat> staleWorkers = workerRepository.findStaleWorkers(threshold);

        for (WorkerHeartbeat worker : staleWorkers) {
            log.warn("Heartbeat worker {} missed heartbeat deadline (last seen: {}). Marking OFFLINE.",
                    worker.getWorkerId(), worker.getLastHeartbeatAt());

            worker.setStatus("OFFLINE");
            workerRepository.save(worker);
            alertService.sendWorkerOfflineAlert(worker.getWorkerId(), worker.getLastHeartbeatAt());
        }
    }

    @Transactional(readOnly = true)
    public List<WorkerHeartbeat> getActiveWorkers() {
        return workerRepository.findAllByOrderByLastHeartbeatAtDesc();
    }
}
