package com.zerodtree.gsad.domain.user.persistence;

import com.zerodtree.gsad.domain.user.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByLinuxUsername(String linuxUsername);

    boolean existsByStudentId(String studentId);

    boolean existsByLinuxUsernameAndIdNot(String linuxUsername, Long id);

    boolean existsByStudentIdAndIdNot(String studentId, Long id);

    @Query("""
            SELECT u FROM User u
            WHERE (:status IS NULL OR u.status = :status)
              AND (:cohort IS NULL OR :cohort = '' OR u.cohort = :cohort)
              AND (:roleFilter IS NULL OR :roleFilter = 'all'
                   OR (:roleFilter = 'admin' AND CONCAT(',', u.roles, ',') LIKE '%,admin,%')
                   OR (:roleFilter = 'user' AND (u.roles IS NULL OR u.roles = ''
                        OR CONCAT(',', u.roles, ',') NOT LIKE '%,admin,%')))
            ORDER BY u.updatedAt DESC
            """)
    Page<User> findFiltered(
            @Param("status") UserStatus status,
            @Param("cohort") String cohort,
            @Param("roleFilter") String roleFilter,
            Pageable pageable);

    @Query("""
            SELECT LOWER(u.email) FROM User u
            WHERE u.email IS NOT NULL AND TRIM(u.email) <> ''
            """)
    List<String> findAllEmails();
}
