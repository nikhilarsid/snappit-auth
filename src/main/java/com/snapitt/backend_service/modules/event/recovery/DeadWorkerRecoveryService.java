package com.snapitt.backend_service.modules.event.recovery;

import com.snapitt.backend_service.modules.event.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadWorkerRecoveryService {

    private final EventService eventService;

    @Value("${snapitt.event.stale-lock-threshold-ms:300000}")
    private long staleLockThresholdMs;

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

    public long getStaleLockThresholdMs() {
        return staleLockThresholdMs;
    }

    public long triggerRecovery() {
        return eventService.recoverStaleLockedEvents(staleLockThresholdMs);
    }
}
