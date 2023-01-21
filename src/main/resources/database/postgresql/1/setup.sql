CREATE TABLE lyna.platform
(
    id       SERIAL
        CONSTRAINT platform_pk
            PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    name     TEXT   NOT NULL,
    url      TEXT
);

CREATE UNIQUE INDEX platform_guild_id_lower_name_uindex
    ON lyna.platform (guild_id, LOWER(name));

CREATE UNIQUE INDEX platform_id_guild_id_uindex
    ON lyna.platform (id, guild_id);

CREATE TABLE lyna.product
(
    id       SERIAL
        CONSTRAINT product_pk
            PRIMARY KEY,
    guild_id BIGINT NOT NULL,
    name     TEXT   NOT NULL,
    url      TEXT,
    role     BIGINT NOT NULL
);

CREATE UNIQUE INDEX product_guild_id_lower_name_uindex
    ON lyna.product (guild_id, LOWER(name));

CREATE UNIQUE INDEX product_id_guild_id_uindex
    ON lyna.product (id, guild_id);

CREATE TABLE lyna.license
(
    product_id      INTEGER NOT NULL
        CONSTRAINT keys_product_product_id_id_fk
            REFERENCES lyna.product
            ON DELETE CASCADE,
    platform_id     INTEGER NOT NULL
        CONSTRAINT keys_platform_platform_id_id_fk
            REFERENCES lyna.platform
            ON DELETE CASCADE,
    user_identifier TEXT    NOT NULL,
    id              SERIAL  NOT NULL
        CONSTRAINT license_pk
            PRIMARY KEY,
    key             TEXT    NOT NULL
);

CREATE UNIQUE INDEX keys_key_uindex
    ON lyna.license (key);

CREATE TABLE lyna.user_license
(
    user_id    BIGINT  NOT NULL,
    license_id INTEGER NOT NULL
        CONSTRAINT user_license_pk
            PRIMARY KEY
        CONSTRAINT user_license_license_license_id_id_fk
            REFERENCES lyna.license
            ON DELETE CASCADE
);

CREATE INDEX user_license_user_id_index
    ON lyna.user_license (user_id);

CREATE TABLE lyna.user_sub_license
(
    user_id    BIGINT  NOT NULL,
    license_id INTEGER NOT NULL
        CONSTRAINT user_sub_license_license_license_id_id_fk
            REFERENCES lyna.license
            ON DELETE CASCADE,
    CONSTRAINT user_sub_license_pk
        PRIMARY KEY (user_id, license_id)
);

CREATE INDEX user_sub_license_license_id_index
    ON lyna.user_sub_license (license_id);

CREATE INDEX user_sub_license_user_id_index
    ON lyna.user_sub_license (user_id);

CREATE TABLE lyna.license_settings
(
    guild_id BIGINT            NOT NULL
        CONSTRAINT license_settings_pk
            PRIMARY KEY,
    shares   INTEGER DEFAULT 0 NOT NULL
);

CREATE VIEW lyna.user_products AS
SELECT guild_id, user_id, p.id, p.name, url, role
FROM lyna.user_license u
         LEFT JOIN lyna.license l ON u.license_id = l.id
         LEFT JOIN lyna.product p ON l.product_id = p.id;

CREATE VIEW lyna.user_products_all AS
SELECT DISTINCT (p.name), guild_id, user_id, p.id, url, role
FROM (SELECT user_id, license_id
      FROM lyna.user_license
      UNION
      SELECT user_id, license_id
      FROM lyna.user_sub_license) u
         LEFT JOIN lyna.license l ON u.license_id = l.id
         LEFT JOIN lyna.product p ON l.product_id = p.id;

CREATE VIEW lyna.user_platforms AS
SELECT guild_id, user_id, p.id, p.name, url
FROM lyna.user_license u
         LEFT JOIN lyna.license l ON u.license_id = l.id
         LEFT JOIN lyna.platform p ON l.platform_id = p.id;

CREATE VIEW lyna.guild_license AS
SELECT product_id, platform_id, user_identifier, l.id, key, guild_id
FROM lyna.license l
         LEFT JOIN lyna.product p ON l.product_id = p.id;

CREATE VIEW lyna.user_guild_license AS
SELECT product_id, platform_id, user_identifier, l.id, key, user_id, guild_id
FROM lyna.license l
         LEFT JOIN lyna.user_license u ON l.id = u.license_id
         LEFT JOIN lyna.product p ON l.product_id = p.id;

CREATE VIEW lyna.user_guild_sub_license AS
SELECT product_id, platform_id, user_identifier, l.id, key, user_id, guild_id
FROM lyna.license l
         LEFT JOIN lyna.user_sub_license u ON l.id = u.license_id
         LEFT JOIN lyna.product p ON l.product_id = p.id;
