package com.zerodtree.gsad.domain.application.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.application.api.ApplicationVO;
import com.zerodtree.gsad.domain.application.api.CreateApplicationRequest;
import com.zerodtree.gsad.domain.application.model.AuditStatus;
import com.zerodtree.gsad.domain.application.persistence.Application;
import com.zerodtree.gsad.domain.application.persistence.ApplicationRepository;
import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.service.ServerService;
import com.zerodtree.gsad.domain.user.persistence.User;
import com.zerodtree.gsad.domain.user.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ServerService serverService;

    @Mock
    private LinuxUsernameResolver linuxUsernameResolver;

    @Mock
    private ApplicationPasswordGenerator applicationPasswordGenerator;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ApplicationService applicationService;

    @Test
    void create_setsApprovedWithoutLeaseFields() {
        User user = new User();
        user.setId(1L);
        user.setEmail("dev@example.com");

        Server server = new Server();
        server.setServerId("gpu-001");
        server.setResourceLevel("H100");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(serverService.requireByServerId("gpu-001")).thenReturn(server);
        when(linuxUsernameResolver.resolveFromEmail("dev@example.com")).thenReturn("dev");
        when(applicationPasswordGenerator.resolvePassword(null)).thenReturn("generated-pass");
        when(applicationRepository.save(any(Application.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApplicationVO vo = applicationService.create(1L, null, new CreateApplicationRequest("gpu-001", null));

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());
        Application saved = captor.getValue();

        assertThat(saved.getAuditStatus()).isEqualTo(AuditStatus.APPROVED);
        assertThat(saved.getServerId()).isEqualTo("gpu-001");
        assertThat(vo.auditStatus()).isEqualTo("APPROVED");
    }

    @Test
    void revokeApproved_becomesCancelled() {
        Application application = activeApplication(AuditStatus.APPROVED);
        when(applicationRepository.findByIdAndUserId("app-1", 1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(application)).thenReturn(application);

        ApplicationVO vo = applicationService.revoke(1L, "app-1");

        assertThat(application.getAuditStatus()).isEqualTo(AuditStatus.CANCELLED);
        assertThat(vo.auditStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void revokeActive_becomesRevokingAndClearsCredentials() {
        Application application = activeApplication(AuditStatus.ACTIVE);
        application.setInitialPassword("secret");
        application.setSshPasswordPlain("plain");
        when(applicationRepository.findByIdAndUserId("app-1", 1L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(application)).thenReturn(application);

        ApplicationVO vo = applicationService.revoke(1L, "app-1");

        assertThat(application.getAuditStatus()).isEqualTo(AuditStatus.REVOKING);
        assertThat(application.getInitialPassword()).isNull();
        assertThat(application.getSshPasswordPlain()).isNull();
        assertThat(vo.auditStatus()).isEqualTo("REVOKING");
    }

    @Test
    void revokeRevoking_isIdempotent() {
        Application application = activeApplication(AuditStatus.REVOKING);
        when(applicationRepository.findByIdAndUserId("app-1", 1L)).thenReturn(Optional.of(application));

        ApplicationVO vo = applicationService.revoke(1L, "app-1");

        assertThat(vo.auditStatus()).isEqualTo("REVOKING");
    }

    @Test
    void revokeRevoked_throwsConflict() {
        Application application = activeApplication(AuditStatus.REVOKED);
        when(applicationRepository.findByIdAndUserId("app-1", 1L)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> applicationService.revoke(1L, "app-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.STATE_CONFLICT);
    }

    @Test
    void revoke_wrongUser_notFound() {
        when(applicationRepository.findByIdAndUserId("app-1", 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.revoke(1L, "app-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static Application activeApplication(AuditStatus status) {
        Application application = new Application();
        application.setId("app-1");
        application.setUserId(1L);
        application.setUserEmail("dev@example.com");
        application.setServerId("gpu-001");
        application.setResourceLevel("H100");
        application.setAuditStatus(status);
        application.setSshUsername("dev");
        return application;
    }
}
