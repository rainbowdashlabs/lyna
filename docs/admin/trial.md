---
sidebar_position: 5
title: Trial
---

A trial lets someone try a paid product once before they buy it. The **Trial** page shows the limits that decide who may take one.

Open it from **Trial** in the admin tab strip.

## What a trial gives

A trial is one download of the newest `STABLE` build of the product. Each person can take it once per product. Lyna records the trial against their account and their Discord account, so linking Discord later does not open a second trial.

People take the trial on the product's page on the web. The `/trial` bot command sends them there.

A product offers a trial when **Trial available** is ticked on its **General** tab. Free products never offer one, since anyone can download them anyway. See [Products](./products.md#main-settings).

## Active limits

The **Active limits** box shows two values:

- **Per server cooldown**: how long someone must have been a member of your Discord server.
- **Per account cooldown**: how old their account must be.

Despite the names, these are not waiting times between trials. They are minimum ages. Which one counts depends on the person:

- **If the bot sees their linked Discord account in your server**, both apply: they must have been in the server for the server time, and their Discord account must be at least the account time old.
- **Otherwise**, Lyna checks their Lyna account instead. It must be at least the account time old, and it needs a proved email address that is not from a throwaway mail provider.

Someone who does not meet a limit yet is told how long they still have to wait.

You change both values on the [Settings](./settings.md) page, as **Trial server time (minutes)** and **Trial account time (minutes)**.

## Products

Below the limits, the page lists products with their name and id.

:::warning
This list shows every product of the guild, not only the ones that offer a trial. Check **Trial available** on each product to see which do.
:::
