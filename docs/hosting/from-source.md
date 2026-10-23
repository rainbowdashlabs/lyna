---
title: Run from source
sidebar_position: 4
---

Build and run Lyna without Docker. You need Java 25, Node.js 24 and Git.

## The backend

Build it:

```bash
git clone https://github.com/rainbowdashlabs/lyna.git
cd lyna
./gradlew installDist
```

That lays out a runnable copy in `build/install/lyna/`, with the start script `bin/lyna`. Copy that
directory to where Lyna should live.

Lyna reads `config/` from its working directory, so start it from the directory that holds `config/`:

```bash
cd /opt/lyna
export JAVA_OPTS="-Dlog4j.configurationFile=config/log4j2.xml"
./bin/lyna
```

`JAVA_OPTS` tells Log4j where your [logging file](../configuration/logging.md) is. Give the secrets
as environment variables, see [secrets](../configuration/secrets.md). Set `api.port` to `8888` and
`api.host` to the address the frontend reaches it on.

## The frontend

```bash
cd frontend
npm ci
NODE_OPTIONS="--max-old-space-size=8192" npm run build
```

The build ends up in `frontend/.output/`. Start it with the backend's address:

```bash
NUXT_BACKEND_URL=http://127.0.0.1:8888 NITRO_PORT=3000 node .output/server/index.mjs
```

Then put a [reverse proxy](./reverse-proxy.md) in front of port `3000`.

## Keeping it running

Run both as services of your system, for example with systemd, and restart them after an upgrade.
To upgrade, pull the new code, build both again, and restart. See [upgrading](./upgrading.md).
