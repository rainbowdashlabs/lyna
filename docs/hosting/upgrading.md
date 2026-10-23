---
title: Upgrading
sidebar_position: 5
---

Upgrading is a pull and a restart. Lyna upgrades its database by itself on start.

## Before you upgrade

Back up three things:

- The database, for example with `pg_dump`.
- `config/`, which holds `config.yaml` and `token.key`.
- `data/`, which holds uploaded product icons.

Database changes only go forward. To go back to an older version, you restore the backup.

## With Docker Compose

```bash
docker compose pull
docker compose up -d
docker compose logs -f backend
```

Keep the backend and the frontend on the same version. Both images get the same tags.

## What happens on start

1. Lyna adds settings the new version knows to `config.yaml` and logs which ones. Read that line;
   a new feature may need you to fill one in. See [after an upgrade](../configuration/index.md#after-an-upgrade).
2. Lyna applies the database changes the new version brings.
3. The bot connects and the API starts.

If the backend fails to start, its log says why. Fix the cause and start it again; nothing is
applied twice.
