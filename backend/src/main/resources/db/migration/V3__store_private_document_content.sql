CREATE TABLE document_contents (
    storage_key VARCHAR(128) PRIMARY KEY,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT document_content_size_check CHECK (octet_length(content) BETWEEN 5 AND 10485760)
);
