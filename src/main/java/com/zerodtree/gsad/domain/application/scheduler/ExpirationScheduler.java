package com.zerodtree.gsad.domain.application.scheduler;

import com.zerodtree.gsad.domain.application.model.AuditStatus;
import com.zerodtree.gsad.domain.application.persistence.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpirationScheduler {

    private final ApplicationRepository applicationRepository;

    /**
     * Expired ACTIVE applications are exposed via provision/pending pendingRevokes; no outbound revoke.
     */
    @Scheduled(fixedRate = 60_000)
    public void logExpiredActiveApplications() {
        long count = applicationRepository
                .findByAuditStatusAndExpireAtBefore(AuditStatus.ACTIVE, Instant.now())
                .size();
        if (count > 0) {
            log.debug("{} expired ACTIVE application(s) awaiting agent revoke", count);
        }
    }
}
