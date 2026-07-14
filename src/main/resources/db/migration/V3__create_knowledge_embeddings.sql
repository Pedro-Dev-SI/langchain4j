CREATE TABLE IF NOT EXISTS knowledge_embeddings (
    embedding_id UUID PRIMARY KEY,
    embedding vector(768),
    text TEXT NULL,
    metadata JSON NULL
);

CREATE INDEX IF NOT EXISTS knowledge_embeddings_embedding_ivfflat_index
ON knowledge_embeddings
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);