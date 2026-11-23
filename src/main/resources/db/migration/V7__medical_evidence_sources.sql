CREATE TABLE medical_evidence_source (
    id VARCHAR(36) PRIMARY KEY,
    doctor_id BIGINT NOT NULL,
    patient_account_id BIGINT NULL,
    session_id VARCHAR(80) NULL,
    file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    file_data BYTEA NOT NULL,
    recognition_scope VARCHAR(255) NOT NULL,
    citations_json TEXT NOT NULL,
    original_text TEXT NOT NULL,
    reviewed_text TEXT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_evidence_doctor_session ON medical_evidence_source(doctor_id, session_id);
CREATE INDEX idx_evidence_patient_session ON medical_evidence_source(patient_account_id, session_id);

ALTER TABLE patient_consultation ADD COLUMN evidence_source_id VARCHAR(36) REFERENCES medical_evidence_source(id);
