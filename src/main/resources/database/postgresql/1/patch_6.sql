alter table lyna.mail_products
    drop constraint mail_products_platform_id_fkk;

alter table lyna.mail_products
    drop column platform_id;

alter table lyna.license
    drop constraint keys_platform_platform_id_id_fk;

DROP VIEW lyna.user_product_access;
DROP VIEW lyna.user_license_all;
drop view lyna.user_platforms;
drop view lyna.user_products;
drop view lyna.user_products_all;
drop view lyna.guild_license;
drop view lyna.user_guild_license;
drop view lyna.user_guild_sub_license;


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

CREATE VIEW lyna.guild_license AS
SELECT product_id, user_identifier, l.id, key, guild_id
FROM lyna.license l
         LEFT JOIN lyna.product p ON l.product_id = p.id;

CREATE VIEW lyna.user_guild_license AS
SELECT product_id, user_identifier, l.id, key, user_id, guild_id
FROM lyna.license l
         LEFT JOIN lyna.user_license u ON l.id = u.license_id
         LEFT JOIN lyna.product p ON l.product_id = p.id;

CREATE VIEW lyna.user_guild_sub_license AS
SELECT product_id, user_identifier, l.id, key, user_id, guild_id
FROM lyna.license l
         LEFT JOIN lyna.user_sub_license u ON l.id = u.license_id
         LEFT JOIN lyna.product p ON l.product_id = p.id;


CREATE VIEW lyna.user_license_all AS
	SELECT
		guild_id,
		user_id,
		product_id,
		license_id,
		user_identifier,
		key
	FROM
		(
			SELECT
				license_id,
				user_id
			FROM
				lyna.user_license
			UNION
			SELECT
				license_id,
				user_id
			FROM
				lyna.user_sub_license
		) a
			LEFT JOIN lyna.license l
			ON a.license_id = l.id
			LEFT JOIN lyna.product p
			ON l.product_id = p.id;

CREATE VIEW lyna.user_product_access AS
	SELECT
		user_id,
		release_type,
		product_id
	FROM
		lyna.user_license_all l
			LEFT JOIN lyna.license_access a
			ON l.license_id = a.license_id;


alter table lyna.license
    drop column platform_id;

DROP TABLE lyna.platform;

