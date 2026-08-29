package com.zerodtree.gsad.domain.server.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.domain.server.api.ServerImportResponse;
import com.zerodtree.gsad.domain.server.model.ServerStatus;
import com.zerodtree.gsad.domain.server.persistence.Server;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerImportServiceTest {

    private static final String VALID_PSK = "0123456789abcdef";

    @Mock
    private ServerRepository serverRepository;

    @InjectMocks
    private ServerImportService serverImportService;

    @Test
    void importCsv_createsServerWithPsk() {
        String csv = """
                server_id,agent_psk
                gpu-node-01,%s
                """.formatted(VALID_PSK);
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        when(serverRepository.findByServerId("gpu-node-01")).thenReturn(Optional.empty());
        when(serverRepository.save(any(Server.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isEqualTo(1);
        assertThat(response.updated()).isZero();
        assertThat(response.errors()).isEmpty();

        ArgumentCaptor<Server> captor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).save(captor.capture());
        Server saved = captor.getValue();
        assertThat(saved.getServerId()).isEqualTo("gpu-node-01");
        assertThat(saved.getAgentPsk()).isEqualTo(VALID_PSK);
        assertThat(saved.getStatus()).isEqualTo(ServerStatus.OFFLINE);
        assertThat(saved.getMetricsJson()).isEqualTo("{}");
    }

    @Test
    void importCsv_overwritesExistingPskOnly() {
        String csv = """
                server_id,agent_psk
                gpu-node-01,new-psk-at-least16
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        Server existing = new Server();
        existing.setServerId("gpu-node-01");
        existing.setAgentPsk("old-psk-at-least16");
        existing.setSshHost("10.0.0.11");
        existing.setResourceLevel("H100");
        existing.setStatus(ServerStatus.ONLINE);
        existing.setMetricsJson("{\"ok\":true}");

        when(serverRepository.findByServerId("gpu-node-01")).thenReturn(Optional.of(existing));
        when(serverRepository.save(any(Server.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isZero();
        assertThat(response.updated()).isEqualTo(1);

        ArgumentCaptor<Server> captor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).save(captor.capture());
        Server saved = captor.getValue();
        assertThat(saved.getAgentPsk()).isEqualTo("new-psk-at-least16");
        assertThat(saved.getSshHost()).isEqualTo("10.0.0.11");
        assertThat(saved.getResourceLevel()).isEqualTo("H100");
        assertThat(saved.getStatus()).isEqualTo(ServerStatus.ONLINE);
        assertThat(saved.getMetricsJson()).isEqualTo("{\"ok\":true}");
    }

    @Test
    void importCsv_lastRowWinsForDuplicateServerId() {
        String csv = """
                server_id,agent_psk
                gpu-node-01,first-psk-at-least
                gpu-node-01,last-psk-value-16x
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        when(serverRepository.findByServerId("gpu-node-01")).thenReturn(Optional.empty());
        when(serverRepository.save(any(Server.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isEqualTo(1);
        assertThat(response.updated()).isZero();
        assertThat(response.errors()).isEmpty();
        verify(serverRepository, times(1)).save(any(Server.class));

        ArgumentCaptor<Server> captor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).save(captor.capture());
        assertThat(captor.getValue().getAgentPsk()).isEqualTo("last-psk-value-16x");
    }

    @Test
    void importCsv_acceptsAgentPskHeaderAlias() {
        String csv = """
                server_id,AGENT_PSK
                gpu-node-03,0123456789abcdef
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        when(serverRepository.findByServerId("gpu-node-03")).thenReturn(Optional.empty());
        when(serverRepository.save(any(Server.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isEqualTo(1);
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void importCsv_rejectsMissingPskColumn() {
        String csv = """
                server_id
                gpu-node-01
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        assertThatThrownBy(() -> serverImportService.importCsv(file))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("agent_psk");
    }

    @Test
    void importCsv_rejectsShortPsk() {
        String csv = """
                server_id,agent_psk
                gpu-node-01,short
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isZero();
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().getFirst().reason()).contains("agent_psk");
    }

    @Test
    void importCsv_rejectsInvalidServerId() {
        String csv = """
                server_id,agent_psk
                -bad-id,0123456789abcdef
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isZero();
        assertThat(response.errors()).hasSize(1);
    }
}
