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
