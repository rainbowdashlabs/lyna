---
title: Docker Compose
sidebar_position: 2
---

The quickest way to run Lyna: the published images, one Compose file, and a config directory.

## The images

| Image | What it is |
|---|---|
| `ghcr.io/rainbowdashlabs/lyna-backend` | The bot and the API. |
| `ghcr.io/rainbowdashlabs/lyna-frontend` | The web app. |

:::tip
`latest` is the newest release. To control when you upgrade, pin a version instead: `v2` follows
every 2.x release, `v2.1.0` stays put. `dev` is the newest build of `main` and not meant for
production.
:::

## 1. Make a directory

```text
lyna/
  compose.yaml
  .env
  config/
    config.yaml
    log4j2.xml
```

`data/` and `logs/` are created on the first start.

## 2. Write `compose.yaml`

```yaml
services:
  postgres:
    image: postgres:17
    restart: unless-stopped
    environment:
      POSTGRES_USER: lyna
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: lyna
    volumes:
      - db:/var/lib/postgresql/data

  backend:
    image: ghcr.io/rainbowdashlabs/lyna-backend:latest
    restart: unless-stopped
    depends_on:
      - postgres
    env_file: .env
    environment:
      DB_HOST: postgres
      DB_DATABASE: lyna
      DB_USER: lyna
    volumes:
      - ./config:/app/config
      - ./data:/app/data
      - ./logs:/app/logs

  frontend:
    image: ghcr.io/rainbowdashlabs/lyna-frontend:latest
    restart: unless-stopped
    depends_on:
      - backend
    environment:
      NUXT_BACKEND_URL: http://backend:8888
    ports:
      - 127.0.0.1:3000:3000

volumes:
  db:
```

What matters in it:

- Only the frontend publishes a port, and only on `127.0.0.1`. Your
  [reverse proxy](./reverse-proxy.md) reaches it there.
- `NUXT_BACKEND_URL` tells the frontend where the backend is. It is read on every request, so one
  image works anywhere.
- `config/` holds the settings and the token key. `data/` holds uploaded product icons. Both must
  survive a new container.
- The backend waits for the database on start and retries until it answers.

## 3. Write `.env`

The secrets, kept out of `config.yaml`. See [secrets](../configuration/secrets.md).

```text
DB_PASSWORD=a-long-random-password
AUTH_JWTSECRET=a-long-random-secret
BOT_TOKEN=your-bot-token
DISCORD_OAUTH_CLIENTSECRET=your-client-secret
```

## 4. Write `config/config.yaml`

At least these, with your own address:

```yaml
api:
  hostname: "lyna.example.com"
  url: "https://lyna.example.com"
links:
  frontend: "https://lyna.example.com"
baseSettings:
  botOwner:
    - 123456789012345678
  botGuild: 234567890123456789
```

`botOwner` is your own Discord user id. It makes you an operator. Every other setting can stay at its
default for now; the [reference](../configuration/reference.md) has all of them. If you have no bot
yet, set `botEnabled: false` under `baseSettings` and leave out `BOT_TOKEN`.

On the first start Lyna also writes a [logging setup](../configuration/logging.md) to
`config/log4j2.xml`.

:::tip
You can also start once without a `config.yaml`. Lyna writes one with every setting at its default,
then stops and tells you which settings to fill in.
:::

## 5. Start it

```bash
docker compose up -d
docker compose logs -f backend
```

On the first start the backend creates its tables, makes `config/token.key`, and connects to Discord.
Then set up your [reverse proxy](./reverse-proxy.md) and open your address.

## Next

- [Set up Discord](../setup/discord.md) for the bot and Discord sign-in.
- [Sign in for the first time](../setup/first-sign-in.md).
