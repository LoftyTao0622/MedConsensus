package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.PatientBasicInfo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PatientBasicInfoMapper extends JpaRepository<PatientBasicInfo, Long> {

    List<PatientBasicInfo> findByDoctorIdOrderByUpdateTimeDesc(Long doctorId);

    Page<PatientBasicInfo> findByDoctorId(Long doctorId, Pageable pageable);

    long countByDoctorId(Long doctorId);

    Optional<PatientBasicInfo> findByIdAndDoctorId(Long id, Long doctorId);

    Optional<PatientBasicInfo> findByDoctorIdAndPatientAccountId(Long doctorId, Long patientAccountId);

    boolean existsByIdAndDoctorId(Long id, Long doctorId);
}
