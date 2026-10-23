---
sidebar_position: 12
title: Instance settings
---

The instance area holds what belongs to your whole Lyna installation rather than to one guild. Only operators can open it.

## Getting there

Click **Instance area →** on the right of the tab strip. Operators always see that link. An operator with no guild to administer lands in the instance area straight from `/admin`. The instance area has three tabs: **System**, **Appearance** and **Operators**.

If you are not an operator, these pages show **Operator access required.**

## System

**System** shows facts about the running instance:

- **Version**: the Lyna version you run.
- **Guilds**: how many guilds the bot is in. This is `0` when no bot is connected.
- **Discord sign-in**: **Configured** when people can sign in with Discord, **Not configured** otherwise. Discord sign-in needs a client id, a secret and a redirect URL. See [Setting up Discord](../setup/discord.md).

Nothing here can be changed.

## Appearance

**Appearance** sets which colour themes people see on your site.

Lyna ships four themes. Their ids are `transistor`, `lyna`, `midnight` and `forest`.

- **Default theme**: the theme everyone sees who has not picked one. That covers visitors who are not signed in, and accounts whose owner never picked a theme. It also covers someone whose pick is no longer allowed.
- **Enabled themes (comma-separated)**: the themes people may pick on their account. Use the theme ids, such as `lyna, midnight`. Empty means every theme is available. Lyna refuses an id it does not know, and names the known ones. When you list themes here, the default theme has to be one of them.
- **Allow user theme**: when ticked, people can pick a theme on their account. When it is off, everybody sees the default theme. The form says **Off shows everybody the default theme.** An account's **Appearance** page then says **Your operator has set one theme for everyone. Dark mode is still yours to choose.**
- **Custom theme colors (JSON)**: your own colours, laid over whichever theme is shown. Leave it empty to use the themes as they are.

Click **Save** to store your changes. You see **Saved.**, or the reason Lyna refused them.

The site keeps these settings for about a minute, so a change can take that long to show everywhere.

### Custom theme colors

Write a JSON object. Every value is a hex colour, in the form `#rgb` or `#rrggbb`. You only list the colours you want to change.

- **Page tokens** go at the top level: `bgLight`, `bgLightAccent`, `bgDark` and `bgDarkAccent`. They apply in both light and dark mode.
- **Mode tokens** go under `light` or `dark`: `primary`, `primaryAccent`, `secondary`, `secondaryAccent`, `info`, `infoAccent`, `success` and `error`.

For example:

```json
{
  "bgDark": "#101418",
  "light": {"primary": "#e92063"},
  "dark": {"primary": "#ff5c8a", "secondary": "#4ab"}
}
```

Lyna refuses anything else when you save: text that is not JSON, a key it does not know, or a value that is not a hex colour. The message names the key that is wrong.

## Operators

An operator reaches every guild's admin area and the instance area. This page lists every operator, by Discord id:

- Ids from `baseSettings.botOwner` in the configuration file are marked **From the config**. You cannot remove them here; the row says **Edit the config** instead. They are the way back in if a grant goes wrong. See [the configuration reference](../configuration/reference.md#basesettings).
- Ids added on this page show who granted them under **Granted by**, and when under **Granted**.

### Add an operator

Under **Grant the instance**, enter a **Discord user id** and click **Grant**. The id is a number of 5 to 20 digits. It appears in the list right away.

The person needs a Lyna account linked to that Discord account. Until they link one, the grant does nothing for them.

Lyna refuses an id that is already in the configuration file.

### Remove an operator

Click **Withdraw** on their row and confirm. They lose every guild's admin area and the instance area. They keep any guild they can still administer as a guild admin, for example through **Manage Server**.

Lyna will not remove the last operator when the configuration file names none. Add another operator first, so the instance never ends up with nobody who can run it.
