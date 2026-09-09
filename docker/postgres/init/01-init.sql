-- Bootstrap only the separate vector database. Business schema is managed exclusively by Flyway.
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
