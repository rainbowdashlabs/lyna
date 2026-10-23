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

- **Minimum time on the server:** how long someone must have been a member of your Discord server.
- **Minimum account age:** how old their account must be.

These are not waiting times between trials. Which one counts depends on the person:

- **If the bot sees their linked Discord account in your server**, both apply: they must have been in the server for the server time, and their Discord account must be at least the account time old.
- **Otherwise**, Lyna checks their Lyna account instead. It must be at least the account time old, and it needs a proved email address that is not from a throwaway mail provider.

Someone who does not meet a limit yet is told how long they still have to wait.

You change both values on the [Settings](./settings.md) page, as **Trial: minimum time on the server (minutes)** and **Trial: minimum account age (minutes)**.

## Products

Below the limits, the page lists the products that offer a trial, with their name and id. Products without **Trial available** are not listed. If no product offers a trial, you see **No products.**
