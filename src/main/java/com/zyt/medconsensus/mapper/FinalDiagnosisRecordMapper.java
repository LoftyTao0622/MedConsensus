package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.FinalDiagnosisRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface FinalDiagnosisRecordMapper extends JpaRepository<FinalDiagnosisRecord, Long> {

    Optional<FinalDiagnosisRecord> findByUserIdAndSessionId(Long userId, String sessionId);

    Optional<FinalDiagnosisRecord> findByIdAndUserId(Long id, Long userId);

    Optional<FinalDiagnosisRecord> findByIdAndPatientAccountIdAndPublishedToPatientTrue(
            Long id,
            Long patientAccountId
    );

    void deleteByUserIdAndSessionId(Long userId, String sessionId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update FinalDiagnosisRecord record
            set record.publishedToPatient = true,
                record.publishedAt = :publishedAt,
                record.version = record.version + 1
            where record.id = :recordId
              and record.userId = :doctorId
              and record.publishedToPatient = false
            """)
    int publishIfUnpublished(
            @Param("recordId") Long recordId,
            @Param("doctorId") Long doctorId,
            @Param("publishedAt") OffsetDateTime publishedAt
    );

    List<FinalDiagnosisRecord> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<FinalDiagnosisRecord> findByPatientAccountIdAndPublishedToPatientTrueOrderByPublishedAtDesc(Long patientAccountId);
}
