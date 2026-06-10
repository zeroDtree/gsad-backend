package com.zerodtree.gsad.domain.application.service;

import com.zerodtree.gsad.domain.application.api.ApplicationVO;
import com.zerodtree.gsad.domain.application.model.AuditStatus;
import com.zerodtree.gsad.domain.application.persistence.Application;

public final class ApplicationMapper {

    private ApplicationMapper() {}

    public static ApplicationVO toVo(Application application) {
        return new ApplicationVO(
                application.getId(),
                application.getServerId(),
                application.getResourceLevel(),
                application.getPurpose(),
                application.getRequestedDays(),
                application.getRequestedStartAt(),
                application.getExpireAt(),
                application.getAuditStatus().name(),
                application.getComment(),
                application.getServerIp(),
                application.getSshUsername(),
                isCredentialsReady(application) ? application.getInitialPassword() : null,
                isCredentialsReady(application),
                Boolean.TRUE.equals(application.getPasswordDelivered()),
                application.getCreatedAt(),
                application.getUpdatedAt());
    }

    private static boolean isCredentialsReady(Application application) {
        return application.getAuditStatus() == AuditStatus.ACTIVE
                && application.getServerIp() != null
                && !application.getServerIp().isBlank();
    }
}
