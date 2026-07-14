create table rental_category (
    id bigserial primary key,
    code varchar(40) not null unique,
    name varchar(120) not null,
    daily_base_price numeric(10, 2) not null,
    insurance_rate numeric(5, 4) not null,
    active boolean not null default true
);

insert into rental_category (code, name, daily_base_price, insurance_rate, active)
values
    ('economico', 'Economico', 150.00, 0.0500, true),
    ('suv', 'SUV', 280.00, 0.0800, true),
    ('premium', 'Premium', 420.00, 0.1200, true);
