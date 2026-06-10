package com.zerodtree.gsad.domain.application.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class LinuxUsernameResolver {

    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-z_][a-z0-9_-]{0,31}$");

    /**
     * Derives a Linux username from email local-part until CSV mapping is wired.
     */
    public String resolveFromEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Invalid email for username resolution");
        }
        String local = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
        String sanitized = local.replaceAll("[^a-z0-9_-]", "_");
        if (sanitized.isEmpty() || !Character.isLetter(sanitized.charAt(0)) && sanitized.charAt(0) != '_') {
            sanitized = "u_" + sanitized;
        }
        if (sanitized.length() > 32) {
            sanitized = sanitized.substring(0, 32);
        }
        if (!VALID_USERNAME.matcher(sanitized).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Cannot derive valid Linux username from email");
        }
        return sanitized;
    }
}
