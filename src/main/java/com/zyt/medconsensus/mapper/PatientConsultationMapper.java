package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.PatientConsultation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientConsultationMapper extends JpaRepository<PatientConsultation, Long> {

    Optional<PatientConsultation> findByIdAndPatientAccountId(Long id, Long patientAccountId);

    Optional<PatientConsultation> findByIdAndDoctorId(Long id, Long doctorId);

    Optional<PatientConsultation> findByDoctorIdAndSessionId(Long doctorId, String sessionId);

    List<PatientConsultation> findByPatientAccountIdOrderByUpdatedAtDesc(Long patientAccountId);

    List<PatientConsultation> findByDoctorIdOrderByUpdatedAtDesc(Long doctorId);
}
