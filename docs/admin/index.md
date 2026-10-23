---
sidebar_position: 1
title: Admin area
---

The admin area is where you run a guild in Lyna from the web: its products, licenses, mails and settings. Operators also find the settings for the whole instance here.

## Who gets in

You need to be signed in. If you are not, Lyna sends you to sign in first and brings you back afterwards.

Lyna names admins by their Discord account. An account that never linked Discord administers nothing, not even as an operator. Link it on your account page first.

Who may administer a guild depends on whether a bot is connected:

- **With a bot**, you administer a guild when you have **Manage Server** on it, or when you hold the guild's admin role. A guild sets that role on its [Settings](./settings.md) page.
- **Without a bot**, there is no membership to check. Only operators get in.
- **Operators** always get in, to every guild.

An operator is named by Discord user id in one of two places:

- `baseSettings.botOwner` in the configuration file. See [the configuration reference](../configuration/reference.md#basesettings). These ids cannot be removed from the web, so they are always a way back in.
- The **Operators** page of the instance area. See [Instance settings](./instance.md#operators).

## Getting there

Open `/admin` on your Lyna site. You also find an **Admin** link on the storefront and in your account area, but only once your account can administer something.

What happens next depends on how many guilds you administer:

- **None.** You see **No admin access** and a short reason. Either your account has no Discord linked yet, or your Discord account cannot administer any guild here.
- **One.** Lyna takes you straight to that guild's **Products** tab.
- **More than one.** You see **Select a guild**. Each guild is listed with your role there, `operator` or `guild admin`. Click one to open it.

With a bot connected, an operator sees every guild the bot is in. Without a bot, an operator sees the guild set in `baseSettings.botGuild` and every guild that has products. These are shown by id, as `Guild <id>`, because there is no bot to ask for their names.

## Switching guilds

Below the tab strip, the **Administering** dropdown shows the guild you are working on. Pick another guild there to switch. Lyna keeps you on the same tab. Guilds you reach as an operator are marked `(operator)`.

## The tabs

Each guild has these tabs:

- **Products**: create and edit the products you sell or give away. See [Products](./products.md).
- **Download types**: the kinds of build a guild offers, such as stable or dev builds. See [Download types](./download-types.md).
- **Licenses**: find, issue and manage licenses. See [Licenses](./licenses.md).
- **Updaters**: requests for more updaters, and allowances you raised. See [Updaters](./updaters.md).
- **Registrations**: look up what a Discord user owns or has shared with them. See [Registrations](./registrations.md).
- **Trial**: which products offer a trial, and the limits that apply. See [Trial](./trial.md).
- **Ko-fi**: map Ko-fi shop items to products. See [Ko-fi](./kofi.md).
- **Mailing**: the mail a buyer gets with their license key. See [Mailing](./mailing.md).
- **Time channels**: channels whose name shows the time. See [Time channels](./time-channels.md).
- **Settings**: share cap, trial limits and the admin role. See [Settings](./settings.md).

On the right of the tab strip you find **Your account** and **Storefront**. Operators also see **Instance area →**, which opens the [instance settings](./instance.md).

## The status bar

The bar at the bottom tells operators about the instance:

- **bot connected** or **no bot**
- how many guilds the bot is in
- the Lyna version

Guild admins who are not operators only see **guild admin** there.
