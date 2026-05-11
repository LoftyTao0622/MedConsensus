package com.zyt.medconsensus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "disease_medicine")
public class DiseaseMedicine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disease_name", nullable = false)
    private String diseaseName;

    @Column(name = "medicine_name", nullable = false)
    private String medicineName;

    @Column(name = "medicine_effect", columnDefinition = "TEXT")
    private String medicineEffect;

    @Column(name = "dosage_usage", columnDefinition = "TEXT")
    private String dosageUsage;

    @Column(name = "contraindication", columnDefinition = "TEXT")
    private String contraindication;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDiseaseName() {
        return diseaseName;
    }

    public void setDiseaseName(String diseaseName) {
        this.diseaseName = diseaseName;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public String getMedicineEffect() {
        return medicineEffect;
    }

    public void setMedicineEffect(String medicineEffect) {
        this.medicineEffect = medicineEffect;
    }

    public String getDosageUsage() {
        return dosageUsage;
    }

    public void setDosageUsage(String dosageUsage) {
        this.dosageUsage = dosageUsage;
    }

    public String getContraindication() {
        return contraindication;
    }

    public void setContraindication(String contraindication) {
        this.contraindication = contraindication;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
