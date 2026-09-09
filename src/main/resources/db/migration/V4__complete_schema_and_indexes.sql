-- Explicit schema for entities that were previously created by Hibernate update.
CREATE TABLE IF NOT EXISTS doctor_basic_info (
    id BIGSERIAL PRIMARY KEY, username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL, phone VARCHAR(20) NOT NULL UNIQUE,
    department VARCHAR(100), title VARCHAR(100), invite_code VARCHAR(20) UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS patient_basic_info (
    id BIGSERIAL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
    doctor_id BIGINT REFERENCES doctor_basic_info(id) ON DELETE CASCADE,
    patient_account_id BIGINT, patient_name VARCHAR(100) NOT NULL,
    gender VARCHAR(10) NOT NULL, age INT CHECK (age >= 0), weight DECIMAL(5,2),
    phone VARCHAR(20), chief_complaint TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS final_diagnosis_record (
    id BIGSERIAL PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL, patient_account_id BIGINT, patient_record_id BIGINT,
    session_id VARCHAR(64) NOT NULL, chief_complaint TEXT, ai_conclusion TEXT,
    doctor_opinion TEXT, final_conclusion TEXT, risk_level VARCHAR(32),
    confidence DOUBLE PRECISION, review_status VARCHAR(32) NOT NULL,
    treatment_keywords TEXT, treatment_source VARCHAR(32), treatment_advice TEXT,
    published_to_patient BOOLEAN NOT NULL DEFAULT FALSE, published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_final_diagnosis_record_user_session UNIQUE (user_id, session_id)
);

CREATE TABLE IF NOT EXISTS disease_medicine (
    id BIGSERIAL PRIMARY KEY, disease_name VARCHAR(255) NOT NULL,
    medicine_name VARCHAR(255) NOT NULL, medicine_effect TEXT, dosage_usage TEXT,
    contraindication TEXT, create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_disease_medicine_name UNIQUE (disease_name, medicine_name)
);

CREATE TABLE IF NOT EXISTS patient_account (
    id BIGSERIAL PRIMARY KEY,
    patient_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL UNIQUE,
    gender VARCHAR(10), age INT, weight DECIMAL(5,2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS doctor_patient_relation (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    doctor_id BIGINT NOT NULL REFERENCES doctor_basic_info(id) ON DELETE CASCADE,
    patient_account_id BIGINT NOT NULL REFERENCES patient_account(id) ON DELETE CASCADE,
    patient_record_id BIGINT REFERENCES patient_basic_info(id) ON DELETE SET NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_doctor_patient_relation_doctor_patient UNIQUE (doctor_id, patient_account_id)
);

CREATE TABLE IF NOT EXISTS patient_consultation (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    patient_account_id BIGINT NOT NULL REFERENCES patient_account(id) ON DELETE CASCADE,
    doctor_id BIGINT NOT NULL REFERENCES doctor_basic_info(id) ON DELETE CASCADE,
    patient_record_id BIGINT NOT NULL REFERENCES patient_basic_info(id) ON DELETE CASCADE,
    session_id VARCHAR(80) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    evidence_status VARCHAR(32) NOT NULL DEFAULT 'NONE',
    evidence_file_name VARCHAR(255), evidence_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE patient_basic_info ADD COLUMN IF NOT EXISTS patient_account_id BIGINT REFERENCES patient_account(id) ON DELETE SET NULL;
ALTER TABLE patient_basic_info ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE doctor_basic_info ADD COLUMN IF NOT EXISTS invite_code VARCHAR(20);
CREATE UNIQUE INDEX IF NOT EXISTS uk_doctor_basic_info_invite_code ON doctor_basic_info (invite_code) WHERE invite_code IS NOT NULL;
ALTER TABLE final_diagnosis_record ADD COLUMN IF NOT EXISTS patient_account_id BIGINT REFERENCES patient_account(id) ON DELETE SET NULL;
ALTER TABLE final_diagnosis_record ADD COLUMN IF NOT EXISTS patient_record_id BIGINT REFERENCES patient_basic_info(id) ON DELETE SET NULL;
ALTER TABLE final_diagnosis_record ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE final_diagnosis_record ADD COLUMN IF NOT EXISTS published_to_patient BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE final_diagnosis_record ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_patient_basic_info_account') THEN
        ALTER TABLE patient_basic_info ADD CONSTRAINT fk_patient_basic_info_account
            FOREIGN KEY (patient_account_id) REFERENCES patient_account(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_final_record_account') THEN
        ALTER TABLE final_diagnosis_record ADD CONSTRAINT fk_final_record_account
            FOREIGN KEY (patient_account_id) REFERENCES patient_account(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_final_record_patient') THEN
        ALTER TABLE final_diagnosis_record ADD CONSTRAINT fk_final_record_patient
            FOREIGN KEY (patient_record_id) REFERENCES patient_basic_info(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_patient_basic_info_doctor_update ON patient_basic_info (doctor_id, update_time DESC);
CREATE INDEX IF NOT EXISTS idx_patient_basic_info_account ON patient_basic_info (patient_account_id);
CREATE INDEX IF NOT EXISTS idx_relation_doctor_updated ON doctor_patient_relation (doctor_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_relation_patient_updated ON doctor_patient_relation (patient_account_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_consultation_doctor_updated ON patient_consultation (doctor_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_consultation_patient_updated ON patient_consultation (patient_account_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_final_record_user_created ON final_diagnosis_record (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_final_record_session ON final_diagnosis_record (session_id);
