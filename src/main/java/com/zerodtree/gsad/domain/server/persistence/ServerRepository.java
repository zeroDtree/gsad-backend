package com.zerodtree.gsad.domain.server.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServerRepository extends JpaRepository<Server, Long> {

    Optional<Server> findByServerId(String serverId);

    boolean existsByServerId(String serverId);

    boolean existsByServerIdAndIdNot(String serverId, Long id);

    List<Server> findAllByOrderByServerIdAsc();

    Page<Server> findAllByOrderByServerIdAsc(Pageable pageable);
}
