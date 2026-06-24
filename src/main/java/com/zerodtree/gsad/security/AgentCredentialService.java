package com.zerodtree.gsad.security;

import com.zerodtree.gsad.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class AgentCredentialService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AgentProperties agentProperties;

    public String derivePsk(String serverId) {
        if (!StringUtils.hasText(serverId)) {
            throw new IllegalArgumentException("serverId is required");
        }
        String masterSecret = agentProperties.getMasterSecret();
        if (!StringUtils.hasText(masterSecret)) {
            throw new IllegalStateException("AGENT_MASTER_SECRET is not configured");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(masterSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(serverId.trim().getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to derive agent PSK", ex);
        }
    }

    public boolean matches(String serverId, String presentedPsk) {
        if (!StringUtils.hasText(serverId) || !StringUtils.hasText(presentedPsk)) {
            return false;
        }
        String expected = derivePsk(serverId);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presentedPsk.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
