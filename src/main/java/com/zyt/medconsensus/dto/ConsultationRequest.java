package com.zyt.medconsensus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ConsultationRequest {

    @Size(max = 80)
    private String sessionId;

    @NotBlank
    @Size(max = 4000)
    private String message;

    @Size(max = 80)
    private String patientName;

    @Size(max = 20)
    private String patientPhone;

    @Size(max = 16)
    private String patientGender;

    @Size(max = 20)
    private String patientAge;

    @Size(max = 20)
    private String patientWeight;

    @Size(max = 1000)
    private String chiefComplaint;

    @Size(max = 12000)
    private String medicalEvidence;

    @Size(max = 255)
    private String medicalEvidenceFileName;

    private boolean medicalEvidenceConfirmed;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getPatientPhone() {
        return patientPhone;
    }

    public void setPatientPhone(String patientPhone) {
        this.patientPhone = patientPhone;
    }

    public String getPatientGender() {
        return patientGender;
    }

    public void setPatientGender(String patientGender) {
        this.patientGender = patientGender;
    }

    public String getPatientAge() {
        return patientAge;
    }

    public void setPatientAge(String patientAge) {
        this.patientAge = patientAge;
    }

    public String getPatientWeight() {
        return patientWeight;
    }

    public void setPatientWeight(String patientWeight) {
        this.patientWeight = patientWeight;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public void setChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
    }

    public String getMedicalEvidence() {
        return medicalEvidence;
    }

    public void setMedicalEvidence(String medicalEvidence) {
        this.medicalEvidence = medicalEvidence;
    }

    public String getMedicalEvidenceFileName() {
        return medicalEvidenceFileName;
    }

    public void setMedicalEvidenceFileName(String medicalEvidenceFileName) {
        this.medicalEvidenceFileName = medicalEvidenceFileName;
    }

    public boolean isMedicalEvidenceConfirmed() {
        return medicalEvidenceConfirmed;
    }

    public void setMedicalEvidenceConfirmed(boolean medicalEvidenceConfirmed) {
        this.medicalEvidenceConfirmed = medicalEvidenceConfirmed;
    }
}
