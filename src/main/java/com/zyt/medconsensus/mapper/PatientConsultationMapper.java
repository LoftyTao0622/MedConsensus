package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.PatientConsultation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PatientConsultationMapper extends JpaRepository<PatientConsultation, Long> {

    Optional<PatientConsultation> findByIdAndPatientAccountId(Long id, Long patientAccountId);

    Optional<PatientConsultation> findByIdAndDoctorId(Long id, Long doctorId);

    Optional<PatientConsultation> findByDoctorIdAndSessionId(Long doctorId, String sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update PatientConsultation consultation
            set consultation.status = :nextStatus,
                consultation.version = consultation.version + 1
            where consultation.id = :consultationId
              and consultation.patientAccountId = :patientAccountId
              and consultation.status = :expectedStatus
            """)
    int transitionPatientStatus(
            @Param("consultationId") Long consultationId,
            @Param("patientAccountId") Long patientAccountId,
            @Param("expectedStatus") String expectedStatus,
            @Param("nextStatus") String nextStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update PatientConsultation consultation
            set consultation.evidenceStatus = :nextEvidenceStatus,
                consultation.status = :nextStatus,
                consultation.version = consultation.version + 1
            where consultation.id = :consultationId
              and consultation.doctorId = :doctorId
              and consultation.evidenceStatus = :expectedEvidenceStatus
            """)
    int transitionEvidenceStatus(
            @Param("consultationId") Long consultationId,
            @Param("doctorId") Long doctorId,
            @Param("expectedEvidenceStatus") String expectedEvidenceStatus,
            @Param("nextEvidenceStatus") String nextEvidenceStatus,
            @Param("nextStatus") String nextStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update PatientConsultation consultation
            set consultation.status = :nextStatus,
                consultation.version = consultation.version + 1
            where consultation.doctorId = :doctorId
              and consultation.sessionId = :sessionId
              and consultation.status <> :nextStatus
            """)
    int transitionSessionStatus(
            @Param("doctorId") Long doctorId,
            @Param("sessionId") String sessionId,
            @Param("nextStatus") String nextStatus
    );

    List<PatientConsultation> findByPatientAccountIdOrderByUpdatedAtDesc(Long patientAccountId);

    List<PatientConsultation> findByDoctorIdOrderByUpdatedAtDesc(Long doctorId);
}
