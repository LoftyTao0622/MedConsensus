DO $$
BEGIN
    IF to_regclass('public.final_diagnosis_record') IS NOT NULL THEN
        ALTER TABLE final_diagnosis_record
            DROP CONSTRAINT IF EXISTS final_diagnosis_record_session_id_key;
    END IF;
END
$$;
