package com.zyt.medconsensus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "final_diagnosis_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_final_diagnosis_record_user_session",
                columnNames = {"user_id", "session_id"}
        )
)
public class FinalDiagnosisRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "patient_account_id")
    private Long patientAccountId;

    @Column(name = "patient_record_id")
    private Long patientRecordId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "chief_complaint", columnDefinition = "TEXT")
    private String chiefComplaint;

    @Column(name = "ai_conclusion", columnDefinition = "TEXT")
    private String aiConclusion;

    @Column(name = "doctor_opinion", columnDefinition = "TEXT")
    private String doctorOpinion;

    @Column(name = "final_conclusion", columnDefinition = "TEXT")
    private String finalConclusion;

    @Column(name = "risk_level", length = 32)
    private String riskLevel;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "review_status", nullable = false, length = 32)
    private String reviewStatus;

    @Column(name = "treatment_keywords", columnDefinition = "TEXT")
    private String treatmentKeywords;

    @Column(name = "treatment_source", length = 32)
    private String treatmentSource;

    @Column(name = "treatment_advice", columnDefinition = "TEXT")
    private String treatmentAdvice;

    @Column(name = "published_to_patient", nullable = false)
    private boolean publishedToPatient;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getPatientAccountId() {
        return patientAccountId;
    }

    public void setPatientAccountId(Long patientAccountId) {
        this.patientAccountId = patientAccountId;
    }

    public Long getPatientRecordId() {
        return patientRecordId;
    }

    public void setPatientRecordId(Long patientRecordId) {
        this.patientRecordId = patientRecordId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getChiefComplaint() {
        return chiefComplaint;
    }

    public void setChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
    }

    public String getAiConclusion() {
        return aiConclusion;
    }

    public void setAiConclusion(String aiConclusion) {
        this.aiConclusion = aiConclusion;
    }

    public String getDoctorOpinion() {
        return doctorOpinion;
    }

    public void setDoctorOpinion(String doctorOpinion) {
        this.doctorOpinion = doctorOpinion;
    }

    public String getFinalConclusion() {
        return finalConclusion;
    }

    public void setFinalConclusion(String finalConclusion) {
        this.finalConclusion = finalConclusion;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getTreatmentKeywords() {
        return treatmentKeywords;
    }

    public void setTreatmentKeywords(String treatmentKeywords) {
        this.treatmentKeywords = treatmentKeywords;
    }

    public String getTreatmentSource() {
        return treatmentSource;
    }

    public void setTreatmentSource(String treatmentSource) {
        this.treatmentSource = treatmentSource;
    }

    public String getTreatmentAdvice() {
        return treatmentAdvice;
    }

    public void setTreatmentAdvice(String treatmentAdvice) {
        this.treatmentAdvice = treatmentAdvice;
    }

    public boolean isPublishedToPatient() {
        return publishedToPatient;
    }

    public void setPublishedToPatient(boolean publishedToPatient) {
        this.publishedToPatient = publishedToPatient;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(OffsetDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
