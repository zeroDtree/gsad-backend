package com.zerodtree.gsad.domain.settings.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "t_login_rate_limit_setting")
@Getter
@Setter
@NoArgsConstructor
public class LoginRateLimitSetting {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(nullable = false)
    private Short id = SINGLETON_ID;

    @Column(name = "window_minutes", nullable = false)
    private int windowMinutes = 15;

    @Column(name = "max_attempts_per_email", nullable = false)
    private int maxAttemptsPerEmail = 5;

    @Column(name = "max_attempts_per_ip", nullable = false)
    private int maxAttemptsPerIp = 30;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
