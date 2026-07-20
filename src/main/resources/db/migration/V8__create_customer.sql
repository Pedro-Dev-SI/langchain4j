CREATE TABLE customer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    document VARCHAR(30) NOT NULL UNIQUE,
    email VARCHAR(160),
    phone VARCHAR(30),
    type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_customer_document
ON customer (document);
