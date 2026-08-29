package com.zerodtree.gsad.domain.server.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.application.persistence.ApplicationRepository;
import com.zerodtree.gsad.domain.server.api.AdminServerVO;
import com.zerodtree.gsad.domain.server.api.CreateAdminServerRequest;
import com.zerodtree.gsad.domain.server.api.UpdateAdminServerRequest;
import com.zerodtree.gsad.domain.server.model.ServerStatus;
import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServerServiceTest {

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private ApplicationRepository applicationRepository;

    @InjectMocks
    private AdminServerService adminServerService;

    @Test
    void create_persistsServerAndPsk() {
        when(serverRepository.existsByServerId("gpu-node-01")).thenReturn(false);
        when(serverRepository.save(any(Server.class))).thenAnswer(invocation -> {
            Server server = invocation.getArgument(0);
            server.setId(7L);
            return server;
        });

        AdminServerVO vo = adminServerService.create(
                new CreateAdminServerRequest("gpu-node-01", "0123456789abcdef"));

        assertThat(vo.id()).isEqualTo(7L);
        assertThat(vo.serverId()).isEqualTo("gpu-node-01");
        assertThat(vo.agentPsk()).isEqualTo("0123456789abcdef");
        assertThat(vo.status()).isEqualTo(ServerStatus.OFFLINE.name());
    }

    @Test
    void update_renamesServerAndCascadesApplications() {
        Server server = new Server();
        server.setId(3L);
        server.setServerId("old-id");
        server.setAgentPsk("0123456789abcdef");
        server.setStatus(ServerStatus.OFFLINE);

        when(serverRepository.findById(3L)).thenReturn(Optional.of(server));
        when(serverRepository.existsByServerIdAndIdNot("new-id", 3L)).thenReturn(false);
        when(serverRepository.save(any(Server.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminServerVO vo = adminServerService.update(3L, new UpdateAdminServerRequest("new-id", null));

        assertThat(vo.serverId()).isEqualTo("new-id");
        assertThat(vo.agentPsk()).isEqualTo("0123456789abcdef");
        verify(applicationRepository).updateServerId("old-id", "new-id");
    }

    @Test
    void update_rejectsServerIdCollision() {
        Server server = new Server();
        server.setId(3L);
        server.setServerId("old-id");
        server.setAgentPsk("0123456789abcdef");

        when(serverRepository.findById(3L)).thenReturn(Optional.of(server));
        when(serverRepository.existsByServerIdAndIdNot("taken-id", 3L)).thenReturn(true);

        assertThatThrownBy(() -> adminServerService.update(3L, new UpdateAdminServerRequest("taken-id", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.STATE_CONFLICT);
        verify(applicationRepository, never()).updateServerId(any(), any());
    }
}
