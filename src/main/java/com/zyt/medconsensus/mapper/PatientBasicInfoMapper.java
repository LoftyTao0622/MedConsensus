package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.PatientBasicInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientBasicInfoMapper extends JpaRepository<PatientBasicInfo, Long> {

    List<PatientBasicInfo> findByDoctorIdOrderByUpdateTimeDesc(Long doctorId);

    Optional<PatientBasicInfo> findByIdAndDoctorId(Long id, Long doctorId);

    Optional<PatientBasicInfo> findByDoctorIdAndPatientAccountId(Long doctorId, Long patientAccountId);

    boolean existsByIdAndDoctorId(Long id, Long doctorId);
}
