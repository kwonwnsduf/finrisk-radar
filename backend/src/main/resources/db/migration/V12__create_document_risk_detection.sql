CREATE TABLE documents (
 id BIGSERIAL PRIMARY KEY,
 document_type VARCHAR(30) NOT NULL,
 source_type VARCHAR(40) NOT NULL,
 source_name VARCHAR(200),
 title VARCHAR(1000) NOT NULL,
 content TEXT NOT NULL,
 summary TEXT,
 source_url VARCHAR(2000),
 external_id VARCHAR(500) NOT NULL,
 published_at TIMESTAMP,
 fetched_at TIMESTAMP NOT NULL,
 raw_s3_path VARCHAR(1000),
 content_hash VARCHAR(64) NOT NULL,
 canonical_url_hash VARCHAR(64),
 language VARCHAR(10) NOT NULL DEFAULT 'ko',
 content_version INTEGER NOT NULL DEFAULT 1,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_documents_source_external UNIQUE(source_type, external_id)
);
CREATE INDEX idx_documents_published ON documents(published_at DESC, id DESC);
CREATE INDEX idx_documents_content_hash ON documents(content_hash);
CREATE INDEX idx_documents_url_hash ON documents(canonical_url_hash);

CREATE TABLE document_collection_jobs (
 job_id UUID PRIMARY KEY,
 requested_by_user_id BIGINT REFERENCES app_users(id),
 asset_id BIGINT NOT NULL REFERENCES assets(id),
 source_type VARCHAR(40) NOT NULL,
 from_date DATE NOT NULL,
 to_date DATE NOT NULL,
 status VARCHAR(20) NOT NULL,
 requested_at TIMESTAMP NOT NULL,
 started_at TIMESTAMP,
 completed_at TIMESTAMP,
 document_count INTEGER NOT NULL DEFAULT 0,
 failure_code VARCHAR(100),
 failure_message VARCHAR(1000),
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT ck_document_job_dates CHECK(to_date >= from_date),
 CONSTRAINT ck_document_job_status CHECK(status IN ('REQUESTED','RUNNING','COMPLETED','FAILED'))
);
CREATE UNIQUE INDEX uq_document_active_job ON document_collection_jobs(asset_id, source_type)
 WHERE status IN ('REQUESTED','RUNNING');
CREATE INDEX idx_document_jobs_requested ON document_collection_jobs(requested_at DESC);

CREATE TABLE asset_aliases (
 id BIGSERIAL PRIMARY KEY,
 asset_id BIGINT NOT NULL REFERENCES assets(id),
 alias VARCHAR(300) NOT NULL,
 normalized_alias VARCHAR(300) NOT NULL,
 alias_type VARCHAR(30) NOT NULL,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_asset_alias UNIQUE(asset_id, normalized_alias)
);
CREATE INDEX idx_asset_alias_normalized ON asset_aliases(normalized_alias);

CREATE TABLE document_asset_mappings (
 id BIGSERIAL PRIMARY KEY,
 document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
 asset_id BIGINT NOT NULL REFERENCES assets(id),
 match_method VARCHAR(30) NOT NULL,
 matched_value VARCHAR(300) NOT NULL,
 confidence NUMERIC(5,4) NOT NULL,
 is_primary BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_document_asset_mapping UNIQUE(document_id, asset_id),
 CONSTRAINT ck_document_asset_confidence CHECK(confidence BETWEEN 0 AND 1)
);
CREATE INDEX idx_document_asset_asset ON document_asset_mappings(asset_id, document_id);

CREATE TABLE credit_event_candidates (
 id BIGSERIAL PRIMARY KEY,
 asset_id BIGINT NOT NULL REFERENCES assets(id),
 event_type VARCHAR(50) NOT NULL,
 event_date DATE NOT NULL,
 severity VARCHAR(20) NOT NULL,
 confidence NUMERIC(5,4) NOT NULL,
 incident_key VARCHAR(200) NOT NULL,
 representative_match_id BIGINT,
 status VARCHAR(30) NOT NULL,
 reviewed_by_user_id BIGINT REFERENCES app_users(id),
 reviewed_at TIMESTAMP,
 review_note VARCHAR(1000),
 approved_credit_event_id BIGINT REFERENCES credit_events(id),
 recalculation_status VARCHAR(30) NOT NULL DEFAULT 'NOT_REQUESTED',
 recalculation_job_id UUID REFERENCES risk_calculation_jobs(job_id),
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_credit_event_candidate_incident UNIQUE(asset_id, event_type, incident_key),
 CONSTRAINT ck_candidate_confidence CHECK(confidence BETWEEN 0 AND 1),
 CONSTRAINT ck_candidate_status CHECK(status IN ('PENDING_REVIEW','APPROVED','REJECTED'))
);
CREATE INDEX idx_candidates_review ON credit_event_candidates(status, confidence DESC, event_date DESC);

CREATE TABLE document_risk_matches (
 id BIGSERIAL PRIMARY KEY,
 document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
 asset_id BIGINT NOT NULL REFERENCES assets(id),
 keyword_code VARCHAR(80) NOT NULL,
 event_type VARCHAR(50) NOT NULL,
 sentence_index INTEGER NOT NULL,
 sentence_text TEXT NOT NULL,
 match_start_offset INTEGER NOT NULL,
 match_end_offset INTEGER NOT NULL,
 matched_text VARCHAR(300) NOT NULL,
 assertion_type VARCHAR(20) NOT NULL,
 source_reliability NUMERIC(5,4) NOT NULL,
 asset_match_confidence NUMERIC(5,4) NOT NULL,
 confidence NUMERIC(5,4) NOT NULL,
 extracted_amount NUMERIC(24,2),
 extracted_currency VARCHAR(10),
 amount_original_text VARCHAR(100),
 evidence JSONB NOT NULL,
 analyzer_version VARCHAR(100) NOT NULL,
 candidate_id BIGINT REFERENCES credit_event_candidates(id),
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_document_risk_match UNIQUE(document_id, asset_id, keyword_code, sentence_index, match_start_offset),
 CONSTRAINT ck_document_match_offsets CHECK(match_start_offset >= 0 AND match_end_offset > match_start_offset)
);
CREATE INDEX idx_document_matches_document ON document_risk_matches(document_id, sentence_index);
CREATE INDEX idx_document_matches_asset ON document_risk_matches(asset_id, created_at DESC);
CREATE INDEX idx_document_matches_candidate ON document_risk_matches(candidate_id);
ALTER TABLE credit_event_candidates ADD CONSTRAINT fk_candidate_representative_match
 FOREIGN KEY(representative_match_id) REFERENCES document_risk_matches(id);
