package com.zerodtree.gsad.domain.application.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.common.PageResult;
import com.zerodtree.gsad.domain.application.api.ApplicationVO;
import com.zerodtree.gsad.domain.application.api.CreateApplicationRequest;
import com.zerodtree.gsad.domain.application.model.AuditStatus;
import com.zerodtree.gsad.domain.application.persistence.Application;
import com.zerodtree.gsad.domain.application.persistence.ApplicationRepository;
import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.service.ServerService;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private static final String IDEMPOTENCY_REDIS_PREFIX = "idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ServerService serverService;
    private final LinuxUsernameResolver linuxUsernameResolver;
    private final ApplicationPasswordGenerator applicationPasswordGenerator;
    private final RedisTemplate<String, String> redisTemplate;

    @Transactional
    public ApplicationVO create(Long userId, String idempotencyKey, CreateApplicationRequest request) {
        if (StringUtils.hasText(idempotencyKey)) {
            Optional<ApplicationVO> existing = findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Not authenticated"));
        Server server = serverService.requireByServerId(request.serverId());

        Application application = new Application();
        application.setId(generateApplicationId());
        application.setUserId(user.getId());
        application.setUserEmail(user.getEmail());
        application.setServerId(server.getServerId());
        application.setResourceLevel(server.getResourceLevel());
        application.setAuditStatus(AuditStatus.APPROVED);
        application.setSshUsername(linuxUsernameResolver.resolve(user));
        application.setSshPasswordPlain(applicationPasswordGenerator.resolvePassword(request.sshPassword()));
        if (StringUtils.hasText(idempotencyKey)) {
            application.setIdempotencyKey(idempotencyKey);
        }

        applicationRepository.save(application);
        if (StringUtils.hasText(idempotencyKey)) {
            redisTemplate.opsForValue().set(
                    IDEMPOTENCY_REDIS_PREFIX + idempotencyKey,
                    application.getId(),
                    IDEMPOTENCY_TTL);
        }

        return ApplicationMapper.toVo(application);
    }

    @Transactional
    public ApplicationVO revoke(Long userId, String applicationId) {
        Application application = applicationRepository.findByIdAndUserId(applicationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Application not found"));
        revokeApplication(application);
        applicationRepository.save(application);
        return ApplicationMapper.toVo(application);
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        for (Application application : applicationRepository.findByUserId(userId)) {
            if (canRevoke(application.getAuditStatus())) {
                revokeApplication(application);
                applicationRepository.save(application);
            }
        }
    }

    void revokeApplication(Application application) {
        AuditStatus status = application.getAuditStatus();
        switch (status) {
            case APPROVED -> {
                application.setAuditStatus(AuditStatus.CANCELLED);
                application.setComment(null);
                application.setSshPasswordPlain(null);
            }
            case ACTIVE, FAILED_REVOKE -> {
                application.setAuditStatus(AuditStatus.REVOKING);
                application.setComment(null);
                application.setInitialPassword(null);
                application.setSshPasswordPlain(null);
            }
            case REVOKING -> {
                // idempotent
            }
            case REVOKED, CANCELLED, FAILED_GRANT ->
                    throw new BusinessException(ErrorCode.STATE_CONFLICT, "Application cannot be revoked");
            default -> throw new BusinessException(ErrorCode.STATE_CONFLICT, "Application cannot be revoked");
        }
    }

    private static boolean canRevoke(AuditStatus status) {
        return status == AuditStatus.APPROVED
                || status == AuditStatus.ACTIVE
                || status == AuditStatus.FAILED_REVOKE;
    }

    @Transactional(readOnly = true)
    public PageResult<ApplicationVO> listMine(Long userId, String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);

        Page<Application> resultPage;
        if (StringUtils.hasText(status) && !"all".equalsIgnoreCase(status)) {
            AuditStatus auditStatus = AuditStatus.valueOf(status);
            resultPage = applicationRepository.findByUserIdAndAuditStatusOrderByUpdatedAtDesc(
                    userId, auditStatus, pageable);
        } else {
            resultPage = applicationRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable);
        }

        return PageResult.of(
                resultPage.getContent().stream().map(ApplicationMapper::toVo).toList(),
                resultPage.getTotalElements(),
                safePage,
                safeSize);
    }

    private Optional<ApplicationVO> findByIdempotencyKey(String idempotencyKey) {
        String cachedId = redisTemplate.opsForValue().get(IDEMPOTENCY_REDIS_PREFIX + idempotencyKey);
        if (StringUtils.hasText(cachedId)) {
            return applicationRepository.findById(cachedId).map(ApplicationMapper::toVo);
        }
        return applicationRepository.findByIdempotencyKey(idempotencyKey).map(ApplicationMapper::toVo);
    }

    private static String generateApplicationId() {
        return "app-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
