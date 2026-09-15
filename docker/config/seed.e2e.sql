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

INSERT INTO public.product (guild_id, name, role, free)
SELECT 4242, 'E2E Unsellable', 3, FALSE
WHERE NOT EXISTS (SELECT 1 FROM public.product WHERE name = 'E2E Unsellable');

INSERT INTO public.kofi_products (link_code, product_id)
SELECT 'e2eshop', id FROM public.product WHERE name = 'E2E Premium'
ON CONFLICT (link_code) DO NOTHING;

-- An account that already holds a license, so the stories can walk the entitled path without an
-- OAuth round trip. Its password is the suite's own and hashed the way the application hashes one;
-- nothing outside this stack ever sees these rows.

INSERT INTO public.account (id, email, password_hash)
SELECT 999000, 'entitled@example.invalid', '$2a$12$Y07xJ9n/YRmyONFQMdyq8uTeNmFH1utmmqrbAQmDqmMQ43QvaRuPi'
WHERE NOT EXISTS (SELECT 1 FROM public.account WHERE id = 999000);

INSERT INTO public.account_discord_link (account_id, discord_user_id, verified_via)
SELECT 999000, 4242424242, 'oauth'
WHERE NOT EXISTS (SELECT 1 FROM public.account_discord_link WHERE account_id = 999000);

INSERT INTO public.license (product_id, user_identifier, key)
SELECT id, 'entitled@example.invalid', 'E2E-LICENSE-KEY'
FROM public.product
WHERE name = 'E2E Premium'
  AND NOT EXISTS (SELECT 1 FROM public.license WHERE key = 'E2E-LICENSE-KEY');

INSERT INTO public.user_license (user_id, license_id)
SELECT 4242424242, id FROM public.license WHERE key = 'E2E-LICENSE-KEY'
ON CONFLICT (license_id) DO NOTHING;

INSERT INTO public.license_access (license_id, release_type)
SELECT id, 'STABLE' FROM public.license WHERE key = 'E2E-LICENSE-KEY'
ON CONFLICT (license_id, release_type) DO NOTHING;
