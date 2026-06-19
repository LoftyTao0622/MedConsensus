package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.PatientAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientAccountMapper extends JpaRepository<PatientAccount, Long> {

    Optional<PatientAccount> findByPhone(String phone);

    boolean existsByPhone(String phone);
}
