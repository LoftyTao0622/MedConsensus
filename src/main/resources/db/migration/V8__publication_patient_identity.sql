-- 历史版可从原报告恢复稳定的档案标识；发布时的姓名无法重建，保留空值。
ALTER TABLE report_publication ADD COLUMN patient_record_id BIGINT;
ALTER TABLE report_publication ADD COLUMN patient_name TEXT;
UPDATE report_publication publication
SET patient_record_id = report.patient_record_id
FROM final_diagnosis_record report
WHERE report.id = publication.record_id AND report.patient_account_id = publication.patient_account_id;
