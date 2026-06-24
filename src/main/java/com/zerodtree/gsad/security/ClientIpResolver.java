package com.zerodtree.gsad.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    /**
     * Returns the client IP. In production, {@code server.forward-headers-strategy=framework}
     * and Tomcat RemoteIpValve resolve the real client from trusted proxy headers.
     */
    public String resolve(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
