---
title: Configuration reference
sidebar_position: 2
---

Every setting in `config/config.yaml`, by section. **Required** means Lyna doesn't run properly
until you set it. How the overrides work is on [How configuration works](./index.md).

## A starting file

A file for an instance with a bot, Discord sign-in and a Nexus, behind `https://lyna.example.com`.
The secrets are left empty here and come from the environment, see [secrets](./secrets.md).

```yaml
baseSettings:
  token: ""
  botEnabled: true
  botOwner:
    - 123456789012345678
  botGuild: 234567890123456789
database:
  host: "postgres"
  port: "5432"
  database: "lyna"
  schema: "lyna"
  user: "lyna"
  password: ""
  poolSize: 5
api:
  hostname: "lyna.example.com"
  url: "https://lyna.example.com"
links:
  frontend: "https://lyna.example.com"
  invite: "https://discord.com/oauth2/authorize?client_id=YOUR_CLIENT_ID&scope=bot&permissions=2415921152"
auth:
  jwtSecret: ""
  sessionDays: 30
  renewAfterHours: 24
  tokenKey: ""
discord:
  oauth:
    clientId: "YOUR_CLIENT_ID"
    clientSecret: ""
    redirectUri: "https://lyna.example.com/api/auth/discord/callback"
    apiUrl: "https://discord.com/api"
nexus:
  host: "nexus.example.com"
  username: "lyna"
  password: ""
mailing:
  enabled: false
```

## baseSettings

The Discord bot. Environment prefix `BOT`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `token` | `""` | `BOT_TOKEN` | The bot token. **Required** while `botEnabled` is on. |
| `botEnabled` | `true` | `BOT_BOTENABLED` | Whether Lyna connects to Discord at all. Off, only the web app and its API run. |
| `botOwner` | `[]` | `BOT_BOTOWNER` | Discord user ids of the operators. They can always use the admin area. See [first sign-in](../setup/first-sign-in.md). |
| `botGuild` | `0` | `BOT_BOTGUILD` | The id of the one guild the bot serves. Its slash commands are registered there. **Required** with a bot. |

## database

PostgreSQL. Environment prefix `DB`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `host` | `"localhost"` | `DB_HOST` | Database host. |
| `port` | `"5432"` | `DB_PORT` | Database port. |
| `database` | `"db"` | `DB_DATABASE` | Database name. |
| `schema` | `"lyna"` | `DB_SCHEMA` | Schema Lyna creates its tables in. It is created if missing. |
| `user` | `"user"` | `DB_USER` | Database user. |
| `password` | `"pw"` | `DB_PASSWORD` | Database password. **Required.** |
| `poolSize` | `5` | `DB_POOLSIZE` | Most connections Lyna keeps open. |

Lyna creates and upgrades its tables itself on every start. See [upgrading](../hosting/upgrading.md).

## api

The HTTP server the web app talks to. Environment prefix `API`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `hostname` | `""` | `API_HOSTNAME` | Your public host name, without `https://`. It goes into the Content-Security-Policy header. **Required**: Lyna doesn't start without it. |
| `url` | `""` | `API_URL` | Your public address, with `https://` and without a trailing slash. Download links, webhook addresses and the "Download on Lyna" button are built from it. **Required**: Lyna doesn't start without it. |
| `host` | `"0.0.0.0"` | `API_HOST` | Address to listen on. |
| `port` | `8888` | `API_PORT` | Port to listen on. The frontend expects `8888` unless you change `NUXT_BACKEND_URL`. |
| `allowedOrigins` | `[]` | `API_ALLOWEDORIGINS` | Browser origins allowed to call the API from another address. Not needed when the frontend serves the API under its own address, as it does by default. |
| `iconHosts` | `[]` | `API_ICONHOSTS` | Extra hosts that pages may load images from. |
| `staticUi` | `false` | `API_STATICUI` | Serves a UI bundled into the backend. Leave it off; the frontend image is the UI. |

## links

Addresses Lyna puts into mails and pages. Environment prefix `LINKS`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `frontend` | `"http://localhost:3000"` | `LINKS_FRONTEND` | Your public address. Links in mails (verify an address, reset a password, purchases) point here. **Required.** |
| `invite` | `""` | `LINKS_INVITE` | Your bot's invite link. `/info` shows it when set. |
| `website` | `""` | `LINKS_WEBSITE` | Your website. The web app and `/info` show it when set. |
| `support`, `tos`, `faq` | `""` | `LINKS_SUPPORT`, `LINKS_TOS`, `LINKS_FAQ` | Your support server, terms and FAQ. `/info` shows each one that is set. |

## auth

Sign-in sessions. Environment prefix `AUTH`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `jwtSecret` | `""` | `AUTH_JWTSECRET` | The secret sessions are signed with. **Required**: Lyna doesn't start without it. Changing it signs everybody out. |
| `sessionDays` | `30` | `AUTH_SESSIONDAYS` | Days a session lasts without being used. |
| `renewAfterHours` | `24` | `AUTH_RENEWAFTERHOURS` | Hours after which an active session is renewed, which moves its end along. |
| `tokenKey` | `""` | `AUTH_TOKENKEY` | Base64 key for stored Discord tokens. Empty uses `config/token.key`. See [secrets](./secrets.md#the-token-key). |

## discord

Signing in with Discord. Leave all three of `clientId`, `clientSecret` and `redirectUri` empty to turn
Discord sign-in off; set all three to turn it on. Environment prefix `DISCORD_OAUTH`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `oauth.clientId` | `""` | `DISCORD_OAUTH_CLIENTID` | Your Discord application's client id. |
| `oauth.clientSecret` | `""` | `DISCORD_OAUTH_CLIENTSECRET` | Your Discord application's client secret. |
| `oauth.redirectUri` | `""` | `DISCORD_OAUTH_REDIRECTURI` | `https://<your address>/api/auth/discord/callback`. It must match a redirect in the Discord application exactly. |
| `oauth.apiUrl` | `"https://discord.com/api"` | `DISCORD_OAUTH_APIURL` | Discord's API. Leave it. |

See [Discord](../setup/discord.md).

## nexus

The Sonatype Nexus downloads come from. Environment prefix `NEXUS`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `host` | `"eldonexus.de"` | `NEXUS_HOST` | Host name of your Nexus, without `https://`. Lyna reaches it over HTTPS. |
| `username` | `""` | `NEXUS_USERNAME` | A Nexus user that can read the repositories you serve. |
| `password` | `""` | `NEXUS_PASSWORD` | That user's password. |

See [Nexus](../setup/nexus.md).

## mailing

Reading payment receipts from a mailbox and sending mail. Environment prefix `MAIL`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `enabled` | `false` | `MAIL_ENABLED` | Whether Lyna connects to a mail server at all. |
| `user` | `""` | `MAIL_USER` | Mailbox login, used for IMAP and SMTP. |
| `password` | `""` | `MAIL_PASSWORD` | Mailbox password. |
| `imap.host`, `imap.port`, `imap.ssl` | `""`, `993`, `true` | file only | The IMAP server receipts are read from. |
| `smtp.host`, `smtp.port`, `smtp.ssl` | `""`, `465`, `true` | file only | The SMTP server mail is sent through. |
| `properties` | `{}` | file only | Extra Jakarta Mail properties, such as `mail.smtp.starttls.enable: "true"`. They win over the ones above. |
| `paypalSenders` | `["service@paypal.de", "service@paypal.com"]` | `MAIL_PAYPALSENDERS` | Addresses PayPal sends receipts from. Add the one of your PayPal account's country if it is missing. |
| `pollSeconds` | `300` | `MAIL_POLLSECONDS` | Seconds between two checks of the mailbox. |
| `originMail` | `[]` | `MAIL_ORIGINMAIL` | Addresses trusted to forward receipts to the mailbox. Empty trusts no forwarder. |
| `skipVerify` | `false` | `MAIL_SKIPVERIFY` | Accepts receipts from any sender. Only for testing: anybody could fake a receipt. |
| `senderName` | `"Lyna"` | `MAIL_SENDERNAME` | The name Lyna signs its mail with. |

See [mail](../setup/mail.md).

## kofi

Ko-fi webhooks. Environment prefix `KOFI`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `verificationToken` | random | `KOFI_VERIFICATIONTOKEN` | The token from your Ko-fi webhook settings. Calls without it are refused. |

See [Ko-fi](../setup/kofi.md).

## builtByBit

BuiltByBit's purchase webhook and license key placeholder. Environment prefix `BUILTBYBIT`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `webhookSecret` | random | `BUILTBYBIT_WEBHOOKSECRET` | The secret at the end of the purchase webhook's address. Calls to any other address are refused. |
| `keySecret` | random | `BUILTBYBIT_KEYSECRET` | The secret you give the license key placeholder. Requests that don't send it are refused. |
| `apiToken` | `""` | `BUILTBYBIT_APITOKEN` | Optional API token, which needs BuiltByBit Ultimate. With one, Lyna checks every purchase with BuiltByBit before issuing a license. |
| `apiUrl` | `"https://api.builtbybit.com"` | `BUILTBYBIT_APIURL` | BuiltByBit's API. Leave it. |
| `oauthClientId` | `""` | `BUILTBYBIT_OAUTHCLIENTID` | Client id from the API token's Details page, for signing in with BuiltByBit. Not used yet. |
| `oauthClientSecret` | `""` | `BUILTBYBIT_OAUTHCLIENTSECRET` | Client secret belonging to `oauthClientId`. Not used yet. |

See [BuiltByBit](../setup/builtbybit.md).

## license

Environment prefix `LICENSE`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `baseSeed` | random, 50 characters | `LICENSE_BASESEED` | Seed for new license keys. Written into the file on the first start. Keep it once licenses exist. |

## github

Reading from GitHub, for product pages that show a project's README. Environment prefix `GITHUB`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `token` | `""` | `GITHUB_TOKEN` | Optional GitHub token. Without one, GitHub allows 60 requests an hour, which cached READMEs rarely reach. |
| `apiUrl` | `"https://api.github.com"` | `GITHUB_APIURL` | GitHub's API. Leave it. |

## dashboard

The hosted dashboard gateway, which asks Lyna whether a server's dashboard key is good. Environment prefix `DASHBOARD`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `gatewaySecret` | `""` | `DASHBOARD_GATEWAYSECRET` | The secret the gateway sends to verify keys. Left empty, every verification is refused. Supply it from the environment. |

## downloads

Environment prefix `DOWNLOADS`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `skipJarSigning` | `false` | `DOWNLOADS_SKIPJARSIGNING` | Serves jars untouched instead of stamping who downloaded them and the key of their license (`%%__LYNA_LICENSE__%%`). Only for testing: the stamp is how you tell which copy went to whom. |

## storage

Environment prefix `STORAGE`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `directory` | `"data"` | `STORAGE_DIRECTORY` | Where uploaded files are kept, relative to the working directory unless absolute. Product icons go into `icons/` inside it. Put it on a volume. |

## debugReports

Debug reports that plugins upload. Environment prefix `DEBUGREPORTS`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `retentionDays` | `14` | `DEBUGREPORTS_RETENTIONDAYS` | Days a report is kept. |
| `maxUploadBytes` | `16777216` (16 MiB) | `DEBUGREPORTS_MAXUPLOADBYTES` | Largest upload accepted. This also raises the size limit for every other request. |
| `uploadIntervalSeconds` | `10` | `DEBUGREPORTS_UPLOADINTERVALSECONDS` | Seconds one address has to wait between two uploads. |

## demo

Environment prefix `DEMO`.

| Key | Default | Environment | What it does |
|---|---|---|---|
| `enabled` | `false` | `DEMO_ENABLED` | Seeds sample data and lets anybody sign in as a sample account without a password. |
| `resetIntervalMinutes` | `60` | `DEMO_RESETINTERVALMINUTES` | Minutes between two resets of the sample data. `0` or less never resets. |

:::danger
Never turn on demo mode on an instance with real data. Anybody can sign in without a password, and
the reset deletes what the demo made.
:::
