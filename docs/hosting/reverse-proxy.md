---
title: Reverse proxy
sidebar_position: 3
---

Put a reverse proxy in front of the frontend. It gives Lyna its `https://` address, and it is the only
thing that faces the internet.

## What to send where

Send **everything** to the frontend on port `3000`. The frontend passes the backend's paths on by
itself:

| Path | Goes to |
|---|---|
| `/api/...` | The backend: the web app's API, downloads, update checks, Ko-fi and GitHub webhooks, Discord sign-in. |
| `/check`, `/download`, `/debug/v1/submit` | The backend: old plugins built for UpdateButler still call these. |
| Everything else | The web app's pages. |

So you need one site with one upstream. Don't route `/api` to the backend yourself.

## Headers

Your proxy must pass on two headers. Most do by default.

- `X-Forwarded-Proto`: Lyna marks its sign-in cookie as secure when this says `https`.
- `X-Forwarded-For`: the visitor's address. Limits such as the wait between two debug uploads count
  per address. Without it, all visitors count as one.

## Upload size

Plugins upload debug reports of up to 16 MiB (see
[`debugReports.maxUploadBytes`](../configuration/reference.md#debugreports)). Let your proxy accept
bodies at least that large.

## Caddy

Caddy gets a certificate on its own and sets both headers:

```text
lyna.example.com {
    request_body {
        max_size 20MB
    }
    reverse_proxy 127.0.0.1:3000
}
```

## nginx

```text
server {
    listen 443 ssl;
    server_name lyna.example.com;

    ssl_certificate     /etc/letsencrypt/live/lyna.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/lyna.example.com/privkey.pem;

    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

## Match your address in the config

Your public address goes into three settings. They must all match what the proxy serves:

```yaml
api:
  hostname: "lyna.example.com"
  url: "https://lyna.example.com"
links:
  frontend: "https://lyna.example.com"
```

And, with Discord sign-in, `discord.oauth.redirectUri`. See [Discord](../setup/discord.md).
