package com.zerodtree.gsad.domain.settings.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginRateLimitSettingRepository extends JpaRepository<LoginRateLimitSetting, Short> {
}
