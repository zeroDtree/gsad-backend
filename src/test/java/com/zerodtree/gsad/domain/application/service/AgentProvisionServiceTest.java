package com.zerodtree.gsad.domain.application.service;

import com.zerodtree.gsad.domain.application.model.AuditStatus;
import com.zerodtree.gsad.domain.application.persistence.Application;
import com.zerodtree.gsad.domain.application.persistence.ApplicationRepository;
import com.zerodtree.gsad.domain.server.api.internal.RevokeCompleteRequest;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentProvisionServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ServerRepository serverRepository;

    @InjectMocks
    private AgentProvisionService agentProvisionService;

    @Test
    void findPendingRevokes_returnsRevokingApplications() {
        Application application = new Application();
        application.setId("app-1");
        application.setSshUsername("dev");
        application.setAuditStatus(AuditStatus.REVOKING);

        when(applicationRepository.findByAuditStatusAndServerId(AuditStatus.REVOKING, "gpu-001"))
                .thenReturn(List.of(application));

        assertThat(agentProvisionService.findPendingRevokes("gpu-001"))
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.applicationId()).isEqualTo("app-1");
                    assertThat(task.linuxUsername()).isEqualTo("dev");
                });
    }

    @Test
    void completeRevoke_success_setsRevoked() {
        Application application = new Application();
        application.setId("app-1");
        application.setServerId("gpu-001");
        application.setAuditStatus(AuditStatus.REVOKING);
        application.setInitialPassword("secret");

        when(applicationRepository.findById("app-1")).thenReturn(Optional.of(application));

        agentProvisionService.completeRevoke(
                new RevokeCompleteRequest("app-1", "gpu-001", true, null));

        assertThat(application.getAuditStatus()).isEqualTo(AuditStatus.REVOKED);
        assertThat(application.getInitialPassword()).isNull();
    }
}
