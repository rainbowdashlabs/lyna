---
title: GitHub
sidebar_position: 6
---

Lyna uses GitHub in two ways: it shows a project's README on its product page, and it posts releases
and repository activity into Discord.

## READMEs on product pages

Nothing to set up. A product whose page is its GitHub README fetches it from GitHub and keeps it for
an hour.

Without a token, GitHub allows 60 requests an hour from your server's address. That is enough for
most instances. If you serve many products, give Lyna a token:

```bash
GITHUB_TOKEN=github_pat_...
```

A fine-grained token with read access to public repositories is enough.

## Release posts and activity

Each product gets its own webhook. Set it up on the product's
[Announcements tab](../admin/products.md#announcements). It shows you a payload URL and a secret to
enter on GitHub.

The payload URL is built from `api.url`, so set that to your public address first. See
[reverse proxy](../hosting/reverse-proxy.md#match-your-address-in-the-config).

Posts go into Discord, so they need the bot. See [Discord](./discord.md).
