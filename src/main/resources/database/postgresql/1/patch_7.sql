create table if not exists lyna.kofi_products
(
    link_code  text    not null
        constraint kofi_products_pk
            primary key,
    product_id integer not null
        constraint kofi_products_product_id_fk
            references lyna.product
);

create table IF NOT EXISTS lyna.kofi_sales
(
    id               serial    not null,
    transaction_id   uuid      not null,
    transaction_type text      not null,
    purchase_time    timestamp not null,
    amount           numeric   not null,
    currency         text      not null,
    from_name        text      not null,
    email            text      not null,
    link_code        TEXT,
    raw              jsonb     not null
);
