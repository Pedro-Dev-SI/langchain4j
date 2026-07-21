ALTER TABLE reservation
ADD COLUMN customer_id UUID NOT NULL;

ALTER TABLE reservation
ADD CONSTRAINT fk_reservation_customer
    FOREIGN KEY (customer_id)
    REFERENCES customer (id);

CREATE INDEX idx_reservation_customer_id
ON reservation (customer_id);
