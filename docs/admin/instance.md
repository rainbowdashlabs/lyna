---
sidebar_position: 12
title: Instance settings
---

The instance area holds what belongs to your whole Lyna installation rather than to one guild. Only operators can open it.

## Getting there

Click **Instance area →** on the right of the tab strip. Operators see that link in every guild. The instance area has three tabs: **System**, **Appearance** and **Operators**.

If you are not an operator, these pages show **Operator access required.**

## System

**System** shows facts about the running instance:

- **Version**: the Lyna version you run.
- **Guilds**: how many guilds the bot is in. This is `0` when no bot is connected.
- **Discord sign-in**: **Configured** when people can sign in with Discord, **Not configured** otherwise. Discord sign-in needs a client id, a secret and a redirect URL. See [Setting up Discord](../setup/discord.md).

Nothing here can be changed.

## Appearance

**Appearance** sets which colour themes people can use on your site.

- **Default theme**: the theme an account's **Appearance** page starts on when its owner never picked one.
- **Enabled themes (comma-separated)**: the themes people may pick on their account. Use the theme ids, such as `lyna, midnight`. Lyna ships `transistor`, `lyna`, `midnight` and `forest`. Empty means every theme is available.
- **Allow user theme**: when ticked, people can pick a theme on their account. When it is off, their account says **Your operator has fixed the theme and the corner style for everyone.** They can still choose between light and dark mode.

Click **Save** to store your changes.

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
