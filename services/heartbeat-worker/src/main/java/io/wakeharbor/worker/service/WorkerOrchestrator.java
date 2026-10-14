package io.wakeharbor.worker.service;

import io.wakeharbor.worker.client.ControlServiceClient;
import io.wakeharbor.worker.dto.*;
import io.wakeharbor.worker.executor.SupabaseHeartbeatExecutor;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WorkerOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(WorkerOrchestrator.class);

    private final ControlServiceClient controlClient;
    private final SupabaseHeartbeatExecutor executor;
    private final String workerId;
    private final int maxConcurrency;
    private final int batchSize;
    private final String hostname;

    private final ExecutorService threadPool;
    private final AtomicInteger activeJobs = new AtomicInteger(0);
    private final AtomicLong completedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);

    public WorkerOrchestrator(
            ControlServiceClient controlClient,
            SupabaseHeartbeatExecutor executor,
            @Value("${wakeharbor.worker.id:worker-standalone-1}") String workerId,
            @Value("${wakeharbor.worker.max-concurrency:5}") int maxConcurrency,
            @Value("${wakeharbor.worker.batch-size:10}") int batchSize) {
        this.controlClient = controlClient;
        this.executor = executor;
        this.workerId = workerId;
        this.maxConcurrency = maxConcurrency;
        this.batchSize = batchSize;
        this.threadPool = Executors.newFixedThreadPool(maxConcurrency);

        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}
        this.hostname = host;

        log.info("WorkerOrchestrator initialized [workerId={}, maxConcurrency={}, hostname={}]",
                workerId, maxConcurrency, hostname);
    }

    /**
     * Polls Control Service for due work items and executes them concurrently.
     */
    @Scheduled(fixedDelayString = "${wakeharbor.worker.poll-interval-ms:15000}")
    public void pollAndExecute() {
        int availableSlots = maxConcurrency - activeJobs.get();
        if (availableSlots <= 0) {
            log.debug("Worker is currently at maximum concurrency ({} active jobs)", activeJobs.get());
            return;
        }

        int claimSize = Math.min(availableSlots, batchSize);
        ClaimJobsRequest claimRequest = new ClaimJobsRequest(workerId, hostname, claimSize);

        List<ClaimedJobDto> jobs = controlClient.claimJobs(claimRequest);
        if (jobs.isEmpty()) {
            return;
        }

        log.info("Claimed {} jobs from Control Service. Executing...", jobs.size());

        for (ClaimedJobDto job : jobs) {
            activeJobs.incrementAndGet();
            threadPool.submit(() -> processJob(job));
        }
    }

    private void processJob(ClaimedJobDto job) {
        try {
            log.info("Executing heartbeat for job {} [project: {} - {}]", job.jobId(), job.projectRef(), job.rpcEndpoint());

            SupabaseHeartbeatExecutor.ExecutionOutcome outcome = executor.execute(job.rpcEndpoint(), job.apiKey());

            SubmitJobResultRequest resultRequest = new SubmitJobResultRequest(
                    job.jobId(),
                    job.leaseToken(),
                    workerId,
                    outcome.status(),
                    outcome.httpStatusCode(),
                    outcome.latencyMs(),
                    outcome.errorCategory(),
                    outcome.errorDetails(),
                    outcome.responsePayloadSummary()
            );

            controlClient.submitResult(resultRequest);

            if ("SUCCESS".equals(outcome.status())) {
                completedCount.incrementAndGet();
            } else {
                failedCount.incrementAndGet();
            }

            log.info("Finished job {} [status={}, latency={}ms]", job.jobId(), outcome.status(), outcome.latencyMs());
        } catch (Exception e) {
            log.error("Fatal error processing job {}: {}", job.jobId(), e.getMessage());
            failedCount.incrementAndGet();
        } finally {
            activeJobs.decrementAndGet();
        }
    }

    /**
     * Periodically transmits health telemetry to Control Service.
     */
    @Scheduled(fixedDelayString = "${wakeharbor.worker.heartbeat-interval-ms:30000}")
    public void transmitHealth() {
        WorkerHeartbeatRequest request = new WorkerHeartbeatRequest(
                workerId,
                hostname,
                activeJobs.get(),
                completedCount.get(),
                failedCount.get()
        );
        controlClient.sendHeartbeat(request);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down worker thread pool gracefully...");
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(15, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
