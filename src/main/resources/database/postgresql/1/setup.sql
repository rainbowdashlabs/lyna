CREATE TABLE lyra.platform
(
    id       SERIAL NOT NULL
        CONSTRAINT platform_pk
            PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    name     TEXT   NOT NULL,
    url      TEXT
);

CREATE UNIQUE INDEX platform_guild_id_lower_name_uindex
    ON lyra.platform (guild_id, LOWER(name));

CREATE UNIQUE INDEX platform_id_guild_id_uindex
    ON lyra.platform (id, guild_id);

CREATE TABLE lyra.product
(
    id       SERIAL NOT NULL
        CONSTRAINT product_pk
            PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    name     TEXT   NOT NULL,
    url      TEXT,
    role     BIGINT
);

CREATE UNIQUE INDEX product_guild_id_lower_name_uindex
    ON lyra.product (guild_id, LOWER(name));

CREATE UNIQUE INDEX product_id_guild_id_uindex
    ON lyra.product (id, guild_id);

CREATE TABLE lyra.license
(
    product_id      INTEGER NOT NULL
        CONSTRAINT keys_product_product_id_id_fk
            REFERENCES lyra.product
            ON DELETE CASCADE,
    platform_id     INTEGER NOT NULL
        CONSTRAINT keys_platform_platform_id_id_fk
            REFERENCES lyra.platform
            ON DELETE CASCADE,
    user_identifier TEXT    NOT NULL,
    id              SERIAL  NOT NULL
        CONSTRAINT license_pk
            PRIMARY KEY,
    key             TEXT    NOT NULL
);

CREATE UNIQUE INDEX keys_key_uindex
    ON lyra.license (key);


CREATE TABLE lyra.user_license
(
    user_id    BIGINT  NOT NULL,
    license_id INTEGER NOT NULL
        CONSTRAINT user_license_pk
            PRIMARY KEY
        CONSTRAINT user_license_license_license_id_id_fk
            REFERENCES lyra.license
            ON DELETE CASCADE
);

CREATE INDEX user_license_user_id_index
    ON lyra.user_license (user_id);

CREATE TABLE lyra.user_sub_license
(
    user_id    BIGINT  NOT NULL,
    license_id INTEGER NOT NULL
        CONSTRAINT user_sub_license_license_license_id_id_fk
            REFERENCES lyra.license (id)
            ON DELETE CASCADE,
    CONSTRAINT user_sub_license_pk
        PRIMARY KEY (user_id, license_id)
);

CREATE INDEX user_sub_license_license_id_index
    ON lyra.user_sub_license (license_id);

CREATE INDEX user_sub_license_user_id_index
    ON lyra.user_sub_license (user_id);
