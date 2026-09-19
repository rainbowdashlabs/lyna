-- The catalogue the end-to-end stories browse.
--
-- Applied after the backend has migrated the schema, because that is what creates the tables. Every
-- statement is idempotent so a rerun against a stack that is already up changes nothing.
--
-- Three products, one for each answer a tile can give: free, premium with somewhere to buy it, and
-- premium with nowhere.

INSERT INTO public.product (guild_id, name, url, role, free)
SELECT 4242, 'E2E Freebie', 'https://example.invalid/freebie', 1, TRUE
WHERE NOT EXISTS (SELECT 1 FROM public.product WHERE name = 'E2E Freebie');

INSERT INTO public.product (guild_id, name, role, free)
SELECT 4242, 'E2E Premium', 2, FALSE
WHERE NOT EXISTS (SELECT 1 FROM public.product WHERE name = 'E2E Premium');

UPDATE public.product
SET description = E'# What it does\n\nA **freebie** for the stories.\n\n- one\n- two\n\n<img src=x onerror="window.__xss=1">'
WHERE name = 'E2E Freebie';

INSERT INTO public.product (guild_id, name, role, free)
SELECT 4242, 'E2E Unsellable', 3, FALSE
WHERE NOT EXISTS (SELECT 1 FROM public.product WHERE name = 'E2E Unsellable');

INSERT INTO public.kofi_products (link_code, product_id)
SELECT 'e2eshop', id FROM public.product WHERE name = 'E2E Premium'
ON CONFLICT (link_code) DO NOTHING;

-- An account that already holds a license, so the stories can walk the entitled path without an
-- OAuth round trip. Its password is the suite's own and hashed the way the application hashes one;
-- nothing outside this stack ever sees these rows.
--
-- The licence hangs off the account, not the Discord id. The identity row is here anyway, because a
-- holder with one is the case the role-granting views answer for.

-- The username is set here as well as the handle. The application keeps the two in step whenever a
-- link is made through it, but these rows go straight into the table and miss that.
INSERT INTO public.account (id, password_hash, username)
SELECT 999000, '$2a$12$Y07xJ9n/YRmyONFQMdyq8uTeNmFH1utmmqrbAQmDqmMQ43QvaRuPi', 'entitled'
WHERE NOT EXISTS (SELECT 1 FROM public.account WHERE id = 999000);

-- An account holds its addresses in their own table. This one is proved, because the stories sign in
-- with it and a claimed address names nobody.
INSERT INTO public.account_email (account_id, email, verified_at, is_primary)
SELECT 999000, 'entitled@example.invalid', now(), TRUE
WHERE NOT EXISTS (SELECT 1 FROM public.account_email WHERE account_id = 999000);

INSERT INTO public.account_identity (provider, external_id, account_id, verified_via, handle)
SELECT 'discord', '4242424242', 999000, 'oauth', 'entitled'
WHERE NOT EXISTS (SELECT 1 FROM public.account_identity
                  WHERE provider = 'discord' AND external_id = '4242424242');

INSERT INTO public.license (product_id, user_identifier, key)
SELECT id, 'entitled@example.invalid', 'E2E-LICENSE-KEY'
FROM public.product
WHERE name = 'E2E Premium'
  AND NOT EXISTS (SELECT 1 FROM public.license WHERE key = 'E2E-LICENSE-KEY');

INSERT INTO public.user_license (account_id, license_id)
SELECT 999000, id FROM public.license WHERE key = 'E2E-LICENSE-KEY'
ON CONFLICT (license_id) DO NOTHING;

INSERT INTO public.license_access (license_id, release_type)
SELECT id, 'STABLE' FROM public.license WHERE key = 'E2E-LICENSE-KEY'
ON CONFLICT (license_id, release_type) DO NOTHING;

-- Something to actually download. The coordinates match what the Nexus stub serves, so the wizard
-- can be walked from release type through to the file itself.

INSERT INTO public.download_type (guild_id, name, description, release_type)
SELECT 4242, 'Jar', 'The plain jar', 'STABLE'
WHERE NOT EXISTS (SELECT 1 FROM public.download_type WHERE guild_id = 4242 AND name = 'Jar');

INSERT INTO public.download (product_id, type_id, repository, group_id, artifact_id)
SELECT p.id, t.id, 'releases', 'de.chojo', 'e2e-plugin'
FROM public.product p, public.download_type t
WHERE p.name = 'E2E Freebie'
  AND t.guild_id = 4242 AND t.name = 'Jar'
  AND NOT EXISTS (SELECT 1 FROM public.download d WHERE d.product_id = p.id AND d.type_id = t.id);

-- The premium product carries the same jars, so its versions can be listed to somebody who may not
-- download them.

INSERT INTO public.download (product_id, type_id, repository, group_id, artifact_id)
SELECT p.id, t.id, 'releases', 'de.chojo', 'e2e-plugin'
FROM public.product p, public.download_type t
WHERE p.name = 'E2E Premium'
  AND t.guild_id = 4242 AND t.name = 'Jar'
  AND NOT EXISTS (SELECT 1 FROM public.download d WHERE d.product_id = p.id AND d.type_id = t.id);
