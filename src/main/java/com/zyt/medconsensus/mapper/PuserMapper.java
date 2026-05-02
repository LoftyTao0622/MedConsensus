package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.Puser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PuserMapper extends JpaRepository<Puser, Long> {

    Optional<Puser> findByUsername(String username);

    Optional<Puser> findByPhone(String phone);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);
}
