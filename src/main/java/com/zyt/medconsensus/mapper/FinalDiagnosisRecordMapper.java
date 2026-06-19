package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.FinalDiagnosisRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinalDiagnosisRecordMapper extends JpaRepository<FinalDiagnosisRecord, Long> {

    Optional<FinalDiagnosisRecord> findByUserIdAndSessionId(Long userId, String sessionId);

    Optional<FinalDiagnosisRecord> findByIdAndUserId(Long id, Long userId);

    Optional<FinalDiagnosisRecord> findByIdAndPatientAccountIdAndPublishedToPatientTrue(
            Long id,
            Long patientAccountId
    );

    void deleteByUserIdAndSessionId(Long userId, String sessionId);

    List<FinalDiagnosisRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<FinalDiagnosisRecord> findByPatientAccountIdAndPublishedToPatientTrueOrderByPublishedAtDesc(Long patientAccountId);
}
