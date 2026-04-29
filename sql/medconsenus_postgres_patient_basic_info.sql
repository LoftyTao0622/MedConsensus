CREATE TABLE IF NOT EXISTS patient_basic_info (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    age INT NOT NULL CHECK (age >= 0),
    weight NUMERIC(5,2) NOT NULL CHECK (weight >= 0),
    phone VARCHAR(20),
    gender VARCHAR(16),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE FUNCTION set_patient_basic_info_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_patient_basic_info_updated_at ON patient_basic_info;

CREATE TRIGGER trg_patient_basic_info_updated_at
BEFORE UPDATE ON patient_basic_info
FOR EACH ROW
EXECUTE FUNCTION set_patient_basic_info_updated_at();
