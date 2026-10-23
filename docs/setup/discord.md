---
title: Discord
sidebar_position: 1
---

One Discord application gives Lyna two things: the bot, and signing in with Discord on the web app.
You can use either one alone.

## 1. Create the application

1. Open the [Discord Developer Portal](https://discord.com/developers/applications) and click
   **New Application**.
2. Under **OAuth2**, copy the **Client ID** and reset and copy the **Client Secret**.

## 2. The bot

1. Under **Bot**, reset and copy the **Token**.
2. On the same page, under **Privileged Gateway Intents**, turn on **Server Members Intent**. Lyna
   needs it to give roles back to members who rejoin.
3. Give the token to Lyna as `BOT_TOKEN` and keep `baseSettings.botEnabled` on.
4. Set `baseSettings.botGuild` to your server's id. Lyna serves one server, and registers its slash
   commands there.

To copy an id, turn on **Developer Mode** in Discord under **Settings > Advanced**, then right-click
the server, a channel or a user and pick **Copy ID**.

### Inviting the bot

Under **OAuth2 > URL Generator**, tick the scopes `bot` and `applications.commands`, then the
permissions below. Open the link and pick your server.

| Permission | Needed for |
|---|---|
| Manage Roles | Giving and taking the roles that come with licenses |
| Send Messages, Embed Links | Release posts and repository activity in channels |
| Send Messages in Threads, Create Public Threads | Posts into threads and forums |
| Manage Channels | [Time channels](../admin/time-channels.md), which rename a channel |

In **Server Settings > Roles**, drag the bot's role above every role it should hand out. Discord
doesn't let a bot give a role that sits above its own.

Put the same invite into `links.invite`, so `/info` shows your bot's invite and not another one.

### Without a bot

Set `baseSettings.botEnabled` to `false` and leave `BOT_TOKEN` out. The web app, accounts, licenses
and downloads keep working. There are no roles, no slash commands and no posts in Discord, and only
[operators](./first-sign-in.md) can open the admin area.

## 3. Signing in with Discord

1. Under **OAuth2 > Redirects**, add your address with `/api/auth/discord/callback` at the end:

   ```text
   https://lyna.example.com/api/auth/discord/callback
   ```

2. Fill in the three settings:

   ```yaml
   discord:
     oauth:
       clientId: "YOUR_CLIENT_ID"
       clientSecret: ""
       redirectUri: "https://lyna.example.com/api/auth/discord/callback"
   ```

3. Give the client secret as `DISCORD_OAUTH_CLIENTSECRET`.

`redirectUri` must match the redirect in the portal character for character. Set all three settings
or none: with any of them empty, Discord sign-in is off and the web app says so.

Linking Discord is also what makes someone an admin: who may administer a server is decided by their
Discord account. See [first sign-in](./first-sign-in.md).
