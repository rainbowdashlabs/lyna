create table lyna.mail_products
(
    id          SERIAL
        constraint mail_products_pk
            primary key,
    product_id  integer not null
        constraint mail_products_product_id_fk
            references lyna.product
            on delete cascade,
    platform_id integer not null
        constraint mail_products_platform_id_fk
            references lyna.platform,
    name        text    not null,
    mail_text   text    not null
);

