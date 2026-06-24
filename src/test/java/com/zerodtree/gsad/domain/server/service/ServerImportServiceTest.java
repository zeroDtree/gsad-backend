package com.zerodtree.gsad.domain.server.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerImportServiceTest {

    @Mock
    private ServerRepository serverRepository;

    @InjectMocks
    private ServerImportService serverImportService;

    @Test
    void importCsv_createsServerWithOptionalFields() {
        String csv = """
                server_id,ssh_host,resource_level
                gpu-node-01,10.0.0.11,H100
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        when(serverRepository.existsByServerId("gpu-node-01")).thenReturn(false);
        when(serverRepository.save(any(Server.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isEqualTo(1);
        assertThat(response.skipped()).isZero();
        assertThat(response.errors()).isEmpty();

        ArgumentCaptor<Server> captor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).save(captor.capture());
        Server saved = captor.getValue();
        assertThat(saved.getServerId()).isEqualTo("gpu-node-01");
        assertThat(saved.getSshHost()).isEqualTo("10.0.0.11");
        assertThat(saved.getResourceLevel()).isEqualTo("H100");
        assertThat(saved.getStatus()).isEqualTo(ServerStatus.OFFLINE);
        assertThat(saved.getMetricsJson()).isEqualTo("{}");
    }

    @Test
    void importCsv_createsServerWithoutResourceLevel() {
        String csv = """
                server_id
                gpu-node-02
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        when(serverRepository.existsByServerId("gpu-node-02")).thenReturn(false);
        when(serverRepository.save(any(Server.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isEqualTo(1);
        ArgumentCaptor<Server> captor = ArgumentCaptor.forClass(Server.class);
        verify(serverRepository).save(captor.capture());
        assertThat(captor.getValue().getResourceLevel()).isNull();
    }

    @Test
    void importCsv_ignoresAgentPskColumn() {
        String csv = """
                server_id,agent_psk
                gpu-node-03,abc123deadbeef
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        when(serverRepository.existsByServerId("gpu-node-03")).thenReturn(false);
        when(serverRepository.save(any(Server.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isEqualTo(1);
        assertThat(response.errors()).isEmpty();
    }

    @Test
    void importCsv_skipsExistingServerId() {
        String csv = """
                server_id
                gpu-node-01
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        when(serverRepository.existsByServerId("gpu-node-01")).thenReturn(true);

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isZero();
        assertThat(response.skipped()).isEqualTo(1);
    }

    @Test
    void importCsv_rejectsDuplicateServerIdInCsv() {
        String csv = """
                server_id
                gpu-node-01
                gpu-node-01
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        when(serverRepository.existsByServerId("gpu-node-01")).thenReturn(false);
        when(serverRepository.save(any(Server.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isEqualTo(1);
        assertThat(response.errors()).hasSize(1);
        assertThat(response.errors().getFirst().reason()).isEqualTo("duplicate server_id in CSV");
    }

    @Test
    void importCsv_rejectsInvalidServerId() {
        String csv = """
                server_id
                -bad-id
                """;
        MockMultipartFile file = new MockMultipartFile("file", "servers.csv", "text/csv", csv.getBytes());

        ServerImportResponse response = serverImportService.importCsv(file);

        assertThat(response.created()).isZero();
        assertThat(response.errors()).hasSize(1);
    }
}
