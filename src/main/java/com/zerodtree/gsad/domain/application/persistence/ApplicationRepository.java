package com.zerodtree.gsad.domain.application.persistence;

import com.zerodtree.gsad.domain.application.model.AuditStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, String> {

    Optional<Application> findByIdempotencyKeyAndUserId(String idempotencyKey, Long userId);

    Page<Application> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    Page<Application> findByUserIdAndAuditStatusOrderByUpdatedAtDesc(
            Long userId, AuditStatus auditStatus, Pageable pageable);

    List<Application> findByAuditStatusAndServerId(AuditStatus auditStatus, String serverId);

    Optional<Application> findByIdAndUserId(String id, Long userId);

    List<Application> findByUserId(Long userId);

    long countByUserIdAndAuditStatusIn(Long userId, Collection<AuditStatus> statuses);

    void deleteByUserId(Long userId);
}
