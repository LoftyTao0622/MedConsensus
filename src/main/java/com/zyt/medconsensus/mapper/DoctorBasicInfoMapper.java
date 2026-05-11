package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.DoctorBasicInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorBasicInfoMapper extends JpaRepository<DoctorBasicInfo, Long> {

    Optional<DoctorBasicInfo> findByPhone(String phone);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);
}
