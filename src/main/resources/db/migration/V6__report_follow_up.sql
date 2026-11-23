CREATE TABLE report_follow_up (
    id BIGSERIAL PRIMARY KEY,
    record_id BIGINT NOT NULL,
    revision INT NOT NULL,
    due_date DATE NOT NULL,
    instructions TEXT NOT NULL,
    patient_result TEXT,
    submitted_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    UNIQUE (record_id, revision),
    FOREIGN KEY (record_id, revision) REFERENCES report_publication(record_id, revision),
    CHECK ((patient_result IS NULL) = (submitted_at IS NULL)),
    CHECK (reviewed_at IS NULL OR submitted_at IS NOT NULL)
);
