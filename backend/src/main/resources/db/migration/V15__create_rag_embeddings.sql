CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE documents ADD COLUMN content_scope VARCHAR(20);
UPDATE documents SET content_scope = CASE
  WHEN source_type = 'NAVER_NEWS' THEN 'SNIPPET'
  WHEN source_type = 'OPEN_DART' AND length(trim(content)) >= 500 THEN 'FULL_TEXT'
  WHEN source_type = 'OPEN_DART' AND length(trim(content)) >= 100 THEN 'PARTIAL'
  ELSE 'UNUSABLE'
END;
ALTER TABLE documents ALTER COLUMN content_scope SET NOT NULL;
ALTER TABLE documents ADD CONSTRAINT ck_documents_content_scope
  CHECK (content_scope IN ('FULL_TEXT', 'PARTIAL', 'SNIPPET', 'UNUSABLE'));

CREATE TABLE document_embedding_jobs (
  job_id UUID PRIMARY KEY,
  document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  content_version INTEGER NOT NULL,
  source_content_hash VARCHAR(64) NOT NULL,
  embedding_model VARCHAR(100) NOT NULL,
  embedding_dimensions INTEGER NOT NULL CHECK(embedding_dimensions = 1536),
  status VARCHAR(20) NOT NULL CHECK(status IN ('REQUESTED','PROCESSING','COMPLETED','FAILED','SKIPPED')),
  active BOOLEAN NOT NULL DEFAULT FALSE,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  chunk_count INTEGER NOT NULL DEFAULT 0,
  requested_at TIMESTAMP NOT NULL,
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  failed_at TIMESTAMP,
  failure_code VARCHAR(100),
  failure_message VARCHAR(1000),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT uq_document_embedding_generation
    UNIQUE(document_id, content_version, embedding_model, embedding_dimensions)
);
CREATE UNIQUE INDEX uq_document_embedding_active
  ON document_embedding_jobs(document_id) WHERE active = TRUE;
CREATE INDEX idx_document_embedding_jobs_status
  ON document_embedding_jobs(status, requested_at);

CREATE TABLE document_chunks (
  id BIGSERIAL PRIMARY KEY,
  job_id UUID NOT NULL REFERENCES document_embedding_jobs(job_id) ON DELETE CASCADE,
  document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
  chunk_index INTEGER NOT NULL CHECK(chunk_index >= 0),
  sentence_start_index INTEGER NOT NULL,
  sentence_end_index INTEGER NOT NULL,
  content TEXT NOT NULL CHECK(length(trim(content)) > 0),
  content_hash VARCHAR(64) NOT NULL,
  embedding vector(1536) NOT NULL,
  embedding_model VARCHAR(100) NOT NULL,
  embedding_dimensions INTEGER NOT NULL CHECK(embedding_dimensions = 1536),
  content_version INTEGER NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT uq_document_chunks_job_index UNIQUE(job_id, chunk_index),
  CONSTRAINT ck_document_chunks_sentence_range
    CHECK(sentence_start_index >= 0 AND sentence_end_index >= sentence_start_index)
);
CREATE INDEX idx_document_chunks_document ON document_chunks(document_id, chunk_index);
