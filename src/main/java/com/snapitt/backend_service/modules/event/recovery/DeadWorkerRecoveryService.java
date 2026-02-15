package com.snapitt.backend_service.modules.event.recovery;

import com.snapitt.backend_service.modules.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Monitors and recovers events locked by dead workers.
 * 
 * When a worker claims an event, it sets:
 * - status: processing
 * - lockedBy: workerId
 * - lockedAt: timestamp
 * 
 * If the worker crashes or hangs, this service detects the stale lock
 * and releases it back to pending status so another worker can retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeadWorkerRecoveryService {

    private final EventService eventService;

    /**
     * Default stale threshold: 5 minutes (300,000 milliseconds).
     * If an event is locked for longer than this, the worker is considered dead.
     */
    @Value("${snapitt.event.stale-lock-threshold-ms:300000}")
    private long staleLockThresholdMs;

    /**
     * Run recovery every 1 minute (60,000 milliseconds).
     * Adjust via: snapitt.event.recovery-interval-ms property
     */
    @Scheduled(fixedRateString = "${snapitt.event.recovery-interval-ms:60000}")
    public void recoverStaleLockedEvents() {
        try {
            long recoveredCount = eventService.recoverStaleLockedEvents(staleLockThresholdMs);
            
            if (recoveredCount > 0) {
                log.info("Dead Worker Recovery: Recovered {} stale locked events", recoveredCount);
            }
        } catch (Exception e) {
            log.error("Error during dead worker recovery", e);
        }
    }

    /**
     * Get current stale threshold in milliseconds.
     */
    public long getStaleLockThresholdMs() {
        return staleLockThresholdMs;
    }

    /**
     * Manually trigger recovery (useful for testing or admin operations).
     */
    public long triggerRecovery() {
        return eventService.recoverStaleLockedEvents(staleLockThresholdMs);
    }
}
