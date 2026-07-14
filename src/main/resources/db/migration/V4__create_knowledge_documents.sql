CREATE TABLE IF NOT EXISTS knowledge_documents (
    source VARCHAR(255) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    ingested_at TIMESTAMP NOT NULL
);