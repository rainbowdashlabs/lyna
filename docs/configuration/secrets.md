---
title: Secrets
sidebar_position: 3
---

Some settings are passwords and keys. Keep them out of `config.yaml` and give them to Lyna through
environment variables instead. Lyna never writes an environment value back into the file.

## Which settings are secrets

| Setting | Environment variable |
|---|---|
| `baseSettings.token` | `BOT_TOKEN` |
| `database.password` | `DB_PASSWORD` |
| `auth.jwtSecret` | `AUTH_JWTSECRET` |
| `auth.tokenKey` | `AUTH_TOKENKEY` |
| `discord.oauth.clientSecret` | `DISCORD_OAUTH_CLIENTSECRET` |
| `nexus.password` | `NEXUS_PASSWORD` |
| `mailing.password` | `MAIL_PASSWORD` |
| `kofi.verificationToken` | `KOFI_VERIFICATIONTOKEN` |
| `license.baseSeed` | `LICENSE_BASESEED` |
| `github.token` | `GITHUB_TOKEN` |
| `dashboard.gatewaySecret` | `DASHBOARD_GATEWAYSECRET` |

Leave the value in the file empty (`""`). The environment variable wins over it.

## Making the secrets

`auth.jwtSecret` is yours to make up. Use a long random value:

```bash
openssl rand -base64 48
```

The rest come from the services they belong to: Discord, your Nexus, your mail provider and Ko-fi.

## With Docker Compose

Put the values into an `.env` file beside your `compose.yaml` and hand them to the backend:

```text
BOT_TOKEN=...
DB_PASSWORD=...
AUTH_JWTSECRET=...
DISCORD_OAUTH_CLIENTSECRET=...
NEXUS_PASSWORD=...
```

```yaml
services:
  backend:
    env_file: .env
```

Keep `.env` out of version control and readable only by you. The full file is on
[Docker Compose](../hosting/docker-compose.md).

## The token key

When somebody signs in with Discord, Lyna stores their Discord tokens encrypted. The key for that is
found in this order:

1. `auth.tokenKey` or `AUTH_TOKENKEY`, as base64.
2. The file `config/token.key`.
3. Neither exists: Lyna makes a new key, writes it to `config/token.key` and logs where.

Back up `config/token.key` with your database. A database dump alone can't decrypt the tokens, which
is the point.

If the key is lost, the stored Discord tokens can't be read. Nothing else is lost: each person's
tokens are stored again the next time they sign in with Discord. If the key file is there but can't
be read, Lyna refuses to start rather than make a new key over it.

To keep the key out of files entirely, make one and pass it as `AUTH_TOKENKEY`:

```bash
openssl rand -base64 32
```

## Changing a secret

| Secret | What happens when it changes |
|---|---|
| `auth.jwtSecret` | Everybody is signed out. |
| `auth.tokenKey` | Stored Discord tokens can't be read until each person signs in with Discord again. |
| `license.baseSeed` | New license keys come from the new seed. Keep it once licenses exist. |
| Anything else | Takes effect on the next start. |
