---
title: Requirements
sidebar_position: 1
---

What you need before you start, and what you can add later.

## What a Lyna instance is made of

| Part | What it does |
|---|---|
| Backend | The Discord bot and the HTTP API, in one Java process. Listens on port `8888`. |
| Frontend | The web app. Serves the pages and passes every `/api/` request on to the backend. Listens on port `3000`. |
| PostgreSQL | Holds everything. Lyna sets up its tables itself. |

Only the frontend faces the internet. The backend and the database stay on an internal network.

## You need

- **Docker with Compose**, or Java 25 and Node.js 24 to [run from source](./from-source.md).
- **PostgreSQL.** The test suite runs on PostgreSQL 17.
- **A domain and HTTPS.** Discord sign-in, sign-in cookies and webhooks need a public `https://`
  address. A [reverse proxy](./reverse-proxy.md) in front of the frontend does that.

## You can add

Each of these is optional and can be set up later.

| Service | What it gives you | Set up on |
|---|---|---|
| A Discord application with a bot | Roles from licenses, slash commands, Discord sign-in, release posts | [Discord](../setup/discord.md) |
| A Sonatype Nexus | Downloads of your builds | [Nexus](../setup/nexus.md) |
| A mailbox | Licenses from PayPal receipts, mails to buyers, address checks | [Mail](../setup/mail.md) |
| Ko-fi | Licenses from Ko-fi purchases | [Ko-fi](../setup/kofi.md) |
| GitHub | READMEs on product pages, release posts in Discord | [GitHub](../setup/github.md) |

Without a bot, Lyna still runs: the web app, accounts, licenses and downloads all work. Only the
Discord side is missing, and only operators can open the admin area.
