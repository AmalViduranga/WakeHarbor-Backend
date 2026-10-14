package io.wakeharbor.control.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding window rate limiter for login protection.
 * Throttles after 5 failed attempts within 15 minutes.
 */
@Service
public class RateLimiterService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_SECONDS = 900; // 15 minutes

    private record AttemptRecord(int count, Instant firstAttemptAt, Instant lockedUntil) {}

    private final Map<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public boolean isAllowed(String key) {
        cleanOldEntries();
        AttemptRecord record = attempts.get(key);
        if (record == null) {
            return true;
        }
        if (record.lockedUntil() != null) {
            return Instant.now().isAfter(record.lockedUntil());
        }
        return record.count() < MAX_ATTEMPTS;
    }

    public void recordFailure(String key) {
        Instant now = Instant.now();
        attempts.compute(key, (k, old) -> {
            if (old == null || now.isAfter(old.firstAttemptAt().plusSeconds(LOCKOUT_SECONDS))) {
                return new AttemptRecord(1, now, null);
            }
            int newCount = old.count() + 1;
            Instant lockedUntil = (newCount >= MAX_ATTEMPTS) ? now.plusSeconds(LOCKOUT_SECONDS) : null;
            return new AttemptRecord(newCount, old.firstAttemptAt(), lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private void cleanOldEntries() {
        Instant threshold = Instant.now().minusSeconds(LOCKOUT_SECONDS * 2);
        attempts.entrySet().removeIf(entry -> entry.getValue().firstAttemptAt().isBefore(threshold));
    }
}
