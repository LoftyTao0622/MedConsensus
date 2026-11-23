ALTER TABLE medical_evidence_source
    ADD COLUMN expected_chart_id BIGINT REFERENCES patient_basic_info(id),
    ADD COLUMN expected_session_id VARCHAR(80),
    ADD COLUMN used_at TIMESTAMPTZ;
ALTER TABLE final_diagnosis_record ADD COLUMN evidence_cutoff TIMESTAMPTZ;

CREATE TABLE publication_evidence (
    record_id BIGINT NOT NULL,
    revision INTEGER NOT NULL,
    source_id VARCHAR(36) NOT NULL REFERENCES medical_evidence_source(id),
    PRIMARY KEY (record_id, revision, source_id),
    FOREIGN KEY (record_id, revision) REFERENCES report_publication(record_id, revision)
);
CREATE INDEX idx_publication_evidence_source ON publication_evidence(source_id);
