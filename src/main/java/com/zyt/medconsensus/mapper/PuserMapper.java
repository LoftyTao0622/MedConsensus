package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.Puser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PuserMapper extends JpaRepository<Puser, Long> {

    Optional<Puser> findByUsername(String username);

    boolean existsByUsername(String username);
}
