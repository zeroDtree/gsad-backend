package com.zerodtree.gsad.domain.server.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServerRepository extends JpaRepository<Server, Long> {

    Optional<Server> findByServerId(String serverId);

    List<Server> findAllByOrderByServerIdAsc();
}
