package com.zerodtree.gsad.security;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentServerGuardTest {

    private final AgentServerGuard guard = new AgentServerGuard();

    @Test
    void assertBodyServerId_matchingServerId_passes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AgentAuthAttributes.SERVER_ID, "gpu-01");

        assertThatCode(() -> guard.assertBodyServerId(request, "gpu-01"))
                .doesNotThrowAnyException();
    }

    @Test
    void assertBodyServerId_mismatch_throwsForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AgentAuthAttributes.SERVER_ID, "gpu-01");

        assertThatThrownBy(() -> guard.assertBodyServerId(request, "gpu-02"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }
}
