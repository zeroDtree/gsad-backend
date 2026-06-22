package com.zerodtree.gsad.domain.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByLinuxUsername(String linuxUsername);

    boolean existsByStudentId(String studentId);
}
