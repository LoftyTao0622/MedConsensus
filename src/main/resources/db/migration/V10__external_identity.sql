-- 医生和患者各有独立主键空间，两侧外键确保关联仅指向相应角色的现有账户。
CREATE TABLE external_identity (
    provider VARCHAR(64) NOT NULL,
    app_id VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL,
    doctor_id BIGINT REFERENCES doctor_basic_info(id) ON DELETE CASCADE,
    patient_id BIGINT REFERENCES patient_account(id) ON DELETE CASCADE,
    local_id BIGINT GENERATED ALWAYS AS (COALESCE(doctor_id, patient_id)) STORED NOT NULL,
    PRIMARY KEY (provider, app_id, subject, role),
    UNIQUE (provider, app_id, local_id, role),
    CHECK ((role = 'DOCTOR' AND doctor_id IS NOT NULL AND patient_id IS NULL)
        OR (role = 'PATIENT' AND patient_id IS NOT NULL AND doctor_id IS NULL))
);
