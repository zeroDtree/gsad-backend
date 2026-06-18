package com.zerodtree.gsad.domain.application.persistence;

import com.zerodtree.gsad.domain.application.model.AuditStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "t_application")
@Getter
@Setter
@NoArgsConstructor
public class Application {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "server_id", nullable = false)
    private String serverId;

    @Column(name = "resource_level", nullable = false)
    private String resourceLevel;

    @Column(nullable = false, length = 500)
    private String purpose;

    @Column(name = "requested_days", nullable = false)
    private Integer requestedDays;

    @Column(name = "requested_start_at", nullable = false)
    private Instant requestedStartAt;

    @Column(name = "expire_at")
    private Instant expireAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "audit_status", nullable = false)
    private AuditStatus auditStatus = AuditStatus.APPROVED;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "server_ip")
    private String serverIp;

    @Column(name = "ssh_username")
    private String sshUsername;

    @Column(name = "initial_password")
    private String initialPassword;

    @Column(name = "ssh_password_plain")
    private String sshPasswordPlain;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
