DO $$
BEGIN
    IF to_regclass('public.patient_basic_info') IS NOT NULL THEN
        ALTER TABLE patient_basic_info ADD COLUMN IF NOT EXISTS patient_account_id BIGINT;
        ALTER TABLE patient_basic_info ADD COLUMN IF NOT EXISTS version BIGINT;
        UPDATE patient_basic_info SET version = 0 WHERE version IS NULL;
        ALTER TABLE patient_basic_info ALTER COLUMN version SET DEFAULT 0;
        ALTER TABLE patient_basic_info ALTER COLUMN version SET NOT NULL;

        CREATE UNIQUE INDEX IF NOT EXISTS uk_patient_basic_info_doctor_patient_account
            ON patient_basic_info (doctor_id, patient_account_id)
            WHERE patient_account_id IS NOT NULL;
    END IF;

    IF to_regclass('public.doctor_patient_relation') IS NOT NULL THEN
        ALTER TABLE doctor_patient_relation ADD COLUMN IF NOT EXISTS version BIGINT;
        UPDATE doctor_patient_relation SET version = 0 WHERE version IS NULL;
        ALTER TABLE doctor_patient_relation ALTER COLUMN version SET DEFAULT 0;
        ALTER TABLE doctor_patient_relation ALTER COLUMN version SET NOT NULL;

        CREATE UNIQUE INDEX IF NOT EXISTS uk_doctor_patient_relation_doctor_patient
            ON doctor_patient_relation (doctor_id, patient_account_id);
    END IF;

    IF to_regclass('public.patient_consultation') IS NOT NULL THEN
        ALTER TABLE patient_consultation ADD COLUMN IF NOT EXISTS version BIGINT;
        UPDATE patient_consultation SET version = 0 WHERE version IS NULL;
        ALTER TABLE patient_consultation ALTER COLUMN version SET DEFAULT 0;
        ALTER TABLE patient_consultation ALTER COLUMN version SET NOT NULL;

        CREATE UNIQUE INDEX IF NOT EXISTS uk_patient_consultation_session
            ON patient_consultation (session_id);
    END IF;

    IF to_regclass('public.final_diagnosis_record') IS NOT NULL THEN
        ALTER TABLE final_diagnosis_record ADD COLUMN IF NOT EXISTS version BIGINT;
        UPDATE final_diagnosis_record SET version = 0 WHERE version IS NULL;
        ALTER TABLE final_diagnosis_record ALTER COLUMN version SET DEFAULT 0;
        ALTER TABLE final_diagnosis_record ALTER COLUMN version SET NOT NULL;

        CREATE UNIQUE INDEX IF NOT EXISTS uk_final_diagnosis_record_user_session
            ON final_diagnosis_record (user_id, session_id);
    END IF;
END
$$;
