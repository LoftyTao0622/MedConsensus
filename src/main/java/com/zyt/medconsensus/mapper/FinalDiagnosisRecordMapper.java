package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.entity.FinalDiagnosisRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinalDiagnosisRecordMapper extends JpaRepository<FinalDiagnosisRecord, Long> {

    Optional<FinalDiagnosisRecord> findByUserIdAndSessionId(Long userId, String sessionId);

    void deleteByUserIdAndSessionId(Long userId, String sessionId);

    List<FinalDiagnosisRecord> findByUserIdOrderByCreatedAtDesc(Long userId);
}
