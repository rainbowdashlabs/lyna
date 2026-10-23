---
title: Lyna
sidebar_position: 0
---

Lyna sells and hands out your software from your own Discord server. People buy a product on Ko-fi
or PayPal, get a license key, and redeem it. The license gives them a Discord role and access to
downloads from your Sonatype Nexus. It can also be shared with others or transferred to another
account.

Lyna is a Discord bot and a web app in one. The web app has a storefront with a page for each
product, an account area for your buyers, and an admin area for you.

These pages are for the people who run a Lyna instance.

## What it does

- **Licenses**: issued by hand, from Ko-fi sales or from PayPal receipts in a mailbox. Each grants a
  role and sticks: someone who leaves and rejoins gets their roles back.
- **Downloads**: builds served from a Nexus, also from private repositories, per release type. Plugins
  check for updates and download through Lyna.
- **Product pages**: your own text or the project's GitHub README, with versions and a download
  wizard.
- **Announcements**: releases and repository activity from GitHub, posted into Discord.
- **Mail**: the license key to the buyer, from templates you edit in the admin area.

## Where to start

1. Check the [requirements](./hosting/requirements.md).
2. Run Lyna with [Docker Compose](./hosting/docker-compose.md) and a
   [reverse proxy](./hosting/reverse-proxy.md).
3. Set up [Discord](./setup/discord.md) and [sign in for the first time](./setup/first-sign-in.md).
4. Connect your [Nexus](./setup/nexus.md) and create your first [product](./admin/products.md).

Every setting is listed in the [configuration reference](./configuration/reference.md).

## Source

Lyna is open source under the AGPL-3.0 on
[GitHub](https://github.com/rainbowdashlabs/lyna).
