package com.zerodtree.gsad.domain.server.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.config.AgentProperties;
import com.zerodtree.gsad.domain.server.api.ServerReportRequest;
import com.zerodtree.gsad.domain.server.persistence.ServerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerServiceTest {

    @Mock
    private ServerRepository serverRepository;

    @Mock
    private ServerMetricsReader metricsReader;

    @Mock
    private AgentProperties agentProperties;

    @InjectMocks
    private ServerService serverService;

    @BeforeEach
    void allowRegistrationDisabled() {
        when(agentProperties.isAllowServerRegistration()).thenReturn(false);
    }

    @Test
    void reportMetrics_unknownServer_rejectsWhenRegistrationDisabled() {
        when(serverRepository.findByServerId("gpu-new")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serverService.reportMetrics(sampleRequest("gpu-new")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static ServerReportRequest sampleRequest(String serverId) {
        return new ServerReportRequest(
                serverId,
                "H100",
                Instant.now(),
                new ServerReportRequest.GpuSummaryBlock(1, 0.5, 1024),
                List.of(new ServerReportRequest.GpuRow(0, "GPU", 0.5, 1024, 8192)));
    }
}
