package com.zerodtree.gsad.domain.application.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.user.persistence.User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
public class LinuxUsernameResolver {

    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-z_][a-z0-9_-]{0,31}$");

    public String resolve(User user) {
        if (user == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "User is required for username resolution");
        }
        return validateAndReturn(user.getLinuxUsername());
    }

    public String validateAndReturn(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Linux username is required");
        }
        if (!VALID_USERNAME.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Invalid Linux username: " + username);
        }
        return username;
    }
}
