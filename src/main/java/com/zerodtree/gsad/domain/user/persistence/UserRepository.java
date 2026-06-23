package com.zerodtree.gsad.domain.user.persistence;

import com.zerodtree.gsad.domain.user.model.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByLinuxUsername(String linuxUsername);

    boolean existsByStudentId(String studentId);

    @Query("""
            SELECT u FROM User u
            WHERE (:status IS NULL OR u.status = :status)
              AND (:cohort IS NULL OR :cohort = '' OR u.cohort = :cohort)
            ORDER BY u.updatedAt DESC
            """)
    Page<User> findFiltered(
            @Param("status") UserStatus status,
            @Param("cohort") String cohort,
            Pageable pageable);
}
