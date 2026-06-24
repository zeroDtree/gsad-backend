package com.zerodtree.gsad.security;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AgentServerGuard {

    public void assertBodyServerId(HttpServletRequest request, String bodyServerId) {
        Object authenticated = request.getAttribute(AgentAuthAttributes.SERVER_ID);
        if (!(authenticated instanceof String agentServerId) || !StringUtils.hasText(agentServerId)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Agent not authenticated");
        }
        if (!agentServerId.equals(bodyServerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "serverId does not match authenticated agent");
        }
    }
}
