package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.DoctorPatientRelation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorPatientRelationMapper extends JpaRepository<DoctorPatientRelation, Long> {

    Optional<DoctorPatientRelation> findByDoctorIdAndPatientAccountId(Long doctorId, Long patientAccountId);

    Optional<DoctorPatientRelation> findByIdAndDoctorId(Long id, Long doctorId);

    Optional<DoctorPatientRelation> findByIdAndPatientAccountId(Long id, Long patientAccountId);

    List<DoctorPatientRelation> findByDoctorIdOrderByUpdatedAtDesc(Long doctorId);

    List<DoctorPatientRelation> findByPatientAccountIdOrderByUpdatedAtDesc(Long patientAccountId);
}
