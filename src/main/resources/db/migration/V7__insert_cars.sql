INSERT INTO car (category_id, model, plate, status)
VALUES
    ((SELECT id FROM rental_category WHERE code = 'economico'), 'Fiat Argo', 'ECO1A01', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'economico'), 'Hyundai HB20', 'ECO2B02', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'economico'), 'Chevrolet Onix', 'ECO3C03', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'economico'), 'Volkswagen Polo', 'ECO4D04', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'economico'), 'Renault Kwid', 'ECO5E05', 'DISPONIVEL'),

    ((SELECT id FROM rental_category WHERE code = 'suv'), 'Jeep Compass', 'SUV1A01', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'suv'), 'Toyota Corolla Cross', 'SUV2B02', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'suv'), 'Volkswagen T-Cross', 'SUV3C03', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'suv'), 'Hyundai Creta', 'SUV4D04', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'suv'), 'Honda HR-V', 'SUV5E05', 'DISPONIVEL'),

    ((SELECT id FROM rental_category WHERE code = 'premium'), 'BMW 320i', 'PRE1A01', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'premium'), 'Mercedes-Benz C 200', 'PRE2B02', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'premium'), 'Audi A4', 'PRE3C03', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'premium'), 'Volvo XC60', 'PRE4D04', 'DISPONIVEL'),
    ((SELECT id FROM rental_category WHERE code = 'premium'), 'Lexus NX 350h', 'PRE5E05', 'DISPONIVEL');
