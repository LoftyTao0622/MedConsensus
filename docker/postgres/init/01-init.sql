SELECT 'CREATE DATABASE vector_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'vector_db')\gexec

\connect vector_db

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS medical_embedding (
    id BIGSERIAL PRIMARY KEY,
    source_file TEXT NOT NULL,
    source_index INT NOT NULL,
    title TEXT,
    metadata JSONB,
    content_hash TEXT NOT NULL UNIQUE,
    chunk_text TEXT NOT NULL,
    embedding VECTOR(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS medical_embedding_source_idx
ON medical_embedding (source_file, source_index);

\connect medconsenus

CREATE TABLE IF NOT EXISTS doctor_basic_info (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20) NOT NULL UNIQUE,
    department VARCHAR(100),
    title VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION set_doctor_basic_info_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_doctor_basic_info_updated_at ON doctor_basic_info;

CREATE TRIGGER trg_doctor_basic_info_updated_at
BEFORE UPDATE ON doctor_basic_info
FOR EACH ROW
EXECUTE FUNCTION set_doctor_basic_info_updated_at();

CREATE TABLE IF NOT EXISTS patient_basic_info (
    id BIGSERIAL PRIMARY KEY,
    doctor_id BIGINT NOT NULL REFERENCES doctor_basic_info(id) ON DELETE CASCADE,
    patient_name VARCHAR(100) NOT NULL,
    gender VARCHAR(10) NOT NULL,
    age INT CHECK (age >= 0),
    weight DECIMAL(5,2),
    phone VARCHAR(20),
    chief_complaint TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION set_patient_basic_info_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_patient_basic_info_update_time ON patient_basic_info;

CREATE TRIGGER trg_patient_basic_info_update_time
BEFORE UPDATE ON patient_basic_info
FOR EACH ROW
EXECUTE FUNCTION set_patient_basic_info_update_time();

CREATE TABLE IF NOT EXISTS final_diagnosis_record (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    chief_complaint TEXT,
    ai_conclusion TEXT,
    doctor_opinion TEXT,
    final_conclusion TEXT,
    risk_level VARCHAR(32),
    confidence DOUBLE PRECISION,
    review_status VARCHAR(32) NOT NULL,
    treatment_keywords TEXT,
    treatment_source VARCHAR(32),
    treatment_advice TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS final_diagnosis_record_user_created_idx
ON final_diagnosis_record (user_id, created_at DESC);

CREATE OR REPLACE FUNCTION set_final_diagnosis_record_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_final_diagnosis_record_updated_at ON final_diagnosis_record;

CREATE TRIGGER trg_final_diagnosis_record_updated_at
BEFORE UPDATE ON final_diagnosis_record
FOR EACH ROW
EXECUTE FUNCTION set_final_diagnosis_record_updated_at();

CREATE TABLE IF NOT EXISTS disease_medicine (
    id BIGSERIAL PRIMARY KEY,
    disease_name VARCHAR(255) NOT NULL,
    medicine_name VARCHAR(255) NOT NULL,
    medicine_effect TEXT,
    dosage_usage TEXT,
    contraindication TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_disease_medicine_name UNIQUE (disease_name, medicine_name)
);

CREATE OR REPLACE FUNCTION set_disease_medicine_update_time()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_disease_medicine_update_time ON disease_medicine;

CREATE TRIGGER trg_disease_medicine_update_time
BEFORE UPDATE ON disease_medicine
FOR EACH ROW
EXECUTE FUNCTION set_disease_medicine_update_time();

INSERT INTO disease_medicine (
    disease_name,
    medicine_name,
    medicine_effect,
    dosage_usage,
    contraindication
) VALUES
(
    '口腔溃疡',
    '西地碘含片',
    '用于口腔、咽喉局部感染及口腔溃疡辅助治疗。',
    '按药品说明书或医嘱含服，儿童需由医生评估后使用。',
    '对碘制剂过敏者禁用，甲状腺疾病患者慎用。'
),
(
    '口腔溃疡',
    '康复新液',
    '促进创面修复，可用于口腔溃疡的局部辅助治疗。',
    '可含漱或局部使用，具体剂量按说明书或医嘱执行。',
    '过敏体质者慎用，使用后不适应停止并就医。'
),
(
    '小儿肥胖症',
    '奥利司他胶囊',
    '抑制脂肪吸收，适用于部分肥胖患者的体重管理。',
    '儿童和青少年使用需严格由医生评估，通常不作为低龄儿童首选。',
    '慢性吸收不良综合征、胆汁淤积及对本品过敏者禁用。'
),
(
    '小儿肥胖症',
    '二甲双胍片',
    '改善胰岛素抵抗，适用于合并糖代谢异常时的医生评估用药。',
    '需结合血糖、肝肾功能和年龄由医生决定剂量。',
    '严重肾功能不全、代谢性酸中毒、严重感染或缺氧状态禁用。'
)
ON CONFLICT (disease_name, medicine_name) DO UPDATE SET
    medicine_effect = EXCLUDED.medicine_effect,
    dosage_usage = EXCLUDED.dosage_usage,
    contraindication = EXCLUDED.contraindication,
    update_time = CURRENT_TIMESTAMP;
