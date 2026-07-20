CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE car (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id BIGINT,
    model VARCHAR(255) NOT NULL,
    plate VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    CONSTRAINT fk_car_rental_category
        FOREIGN KEY (category_id)
        REFERENCES rental_category (id)
);

CREATE INDEX idx_car_category_id
ON car (category_id);
