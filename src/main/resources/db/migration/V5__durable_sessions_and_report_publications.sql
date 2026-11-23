-- 保存完整的问诊轨迹；一次问诊提交的消息、诊断和摘要共同提交。
CREATE TABLE consultation_session (
    doctor_id BIGINT NOT NULL,
    session_id VARCHAR(80) NOT NULL,
    patient_record_id BIGINT REFERENCES patient_basic_info(id) ON DELETE SET NULL,
    title TEXT NOT NULL, status TEXT NOT NULL, chief_complaint TEXT, evidence_file_name TEXT,
    messages JSONB NOT NULL DEFAULT '[]', diagnosis JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, archived BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (doctor_id, session_id)
);
CREATE INDEX idx_consultation_session_list ON consultation_session(doctor_id, updated_at DESC) WHERE NOT archived;

-- 旧缓存过期时，仍可从已保存的问诊与报告还原入口。
INSERT INTO consultation_session(doctor_id, session_id, patient_record_id, title, status, chief_complaint, evidence_file_name, updated_at)
SELECT c.doctor_id, c.session_id, c.patient_record_id, p.patient_name, c.status, NULL, c.evidence_file_name, c.updated_at
FROM patient_consultation c JOIN patient_basic_info p ON p.id=c.patient_record_id;
INSERT INTO consultation_session(doctor_id, session_id, patient_record_id, title, status, chief_complaint, updated_at)
SELECT f.user_id, f.session_id, f.patient_record_id, COALESCE(p.patient_name, NULLIF(LEFT(f.chief_complaint,24),''),'历史会诊'),
       CASE WHEN f.review_status='IMPORTED' THEN '待医生定夺' ELSE '已形成最终结论' END, f.chief_complaint, f.updated_at
FROM final_diagnosis_record f LEFT JOIN patient_basic_info p ON p.id=f.patient_record_id
ON CONFLICT(doctor_id, session_id) DO UPDATE SET chief_complaint=EXCLUDED.chief_complaint;

-- 每次发布生成一条不可变快照，草稿内容与患者已收到的内容各自保存。
CREATE TABLE report_publication (
    record_id BIGINT NOT NULL REFERENCES final_diagnosis_record(id),
    revision INT NOT NULL CHECK (revision > 0), doctor_id BIGINT NOT NULL, patient_account_id BIGINT NOT NULL,
    session_id VARCHAR(80) NOT NULL, chief_complaint TEXT, ai_conclusion TEXT, doctor_opinion TEXT,
    final_conclusion TEXT, risk_level TEXT, confidence DOUBLE PRECISION, treatment_advice TEXT,
    published_at TIMESTAMPTZ NOT NULL, PRIMARY KEY(record_id, revision)
);
CREATE INDEX idx_report_publication_patient ON report_publication(patient_account_id, record_id, revision DESC);
INSERT INTO report_publication(record_id, revision, doctor_id, patient_account_id, session_id, chief_complaint,
                               ai_conclusion, doctor_opinion, final_conclusion, risk_level, confidence, treatment_advice, published_at)
SELECT id, 1, user_id, patient_account_id, session_id, chief_complaint, ai_conclusion, doctor_opinion, final_conclusion,
       risk_level, confidence, treatment_advice, COALESCE(published_at,updated_at)
FROM final_diagnosis_record WHERE published_to_patient AND patient_account_id IS NOT NULL;
