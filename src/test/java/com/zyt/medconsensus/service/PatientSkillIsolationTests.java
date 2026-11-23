package com.zyt.medconsensus.service;

import com.zyt.medconsensus.dto.ConsultationRequest;
import com.zyt.medconsensus.entity.PatientBasicInfo;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class PatientSkillIsolationTests {
    @Test
    void sameNameAndAccountCannotShareMemoriesAcrossPatientRecords(@TempDir Path directory) {
        PatientSkillService service = new PatientSkillService(directory.toString());
        PatientBasicInfo patient = new PatientBasicInfo();
        patient.setId(13L);
        patient.setPatientAccountId(31L);
        patient.setPatientName("同名患者");
        patient.setChiefComplaint("档案十三的主诉");
        service.recordPatientProfile(patient, true);

        ConsultationRequest request = new ConsultationRequest();
        request.setPatientName(patient.getPatientName());
        request.setPatientAccountId(31L);
        assertThat(service.loadSkill(request)).isEmpty();
        request.setPatientRecordId(14L);
        assertThat(service.loadSkill(request)).isEmpty();
        request.setPatientRecordId(13L);
        assertThat(service.loadSkill(request)).contains("档案十三的主诉");
    }
}
