package com.zerodtree.gsad.domain.settings.service;

import com.zerodtree.gsad.common.BusinessException;
import com.zerodtree.gsad.common.ErrorCode;
import com.zerodtree.gsad.domain.settings.api.LoginRateLimitSettingsResponse;
import com.zerodtree.gsad.domain.settings.api.UpdateLoginRateLimitSettingsRequest;
import com.zerodtree.gsad.domain.settings.persistence.LoginRateLimitSetting;
import com.zerodtree.gsad.domain.settings.persistence.LoginRateLimitSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginRateLimitSettingsService {

    private final LoginRateLimitSettingRepository repository;

    @Transactional(readOnly = true)
    public LoginRateLimitSettingsResponse get() {
        return toResponse(requireRow());
    }

    @Transactional
    public LoginRateLimitSettingsResponse update(UpdateLoginRateLimitSettingsRequest request) {
        LoginRateLimitSetting row = requireRow();
        row.setWindowMinutes(request.windowMinutes());
        row.setMaxAttemptsPerEmail(request.maxAttemptsPerEmail());
        row.setMaxAttemptsPerIp(request.maxAttemptsPerIp());
        repository.save(row);
        return toResponse(row);
    }

    private LoginRateLimitSetting requireRow() {
        return repository.findById(LoginRateLimitSetting.SINGLETON_ID)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_ERROR, "Login rate limit settings are missing"));
    }

    private static LoginRateLimitSettingsResponse toResponse(LoginRateLimitSetting row) {
        return new LoginRateLimitSettingsResponse(
                row.getWindowMinutes(),
                row.getMaxAttemptsPerEmail(),
                row.getMaxAttemptsPerIp());
    }
}
