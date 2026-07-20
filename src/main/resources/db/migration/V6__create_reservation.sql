CREATE TABLE reservation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    car_id UUID NOT NULL,
    session_id UUID NOT NULL,
    start_date TIMESTAMP NOT NULL,
    end_date TIMESTAMP NOT NULL,
    CONSTRAINT fk_reservation_car
        FOREIGN KEY (car_id)
        REFERENCES car (id)
);

CREATE INDEX idx_reservation_car_id
ON reservation (car_id);

CREATE INDEX idx_reservation_session_id
ON reservation (session_id);
