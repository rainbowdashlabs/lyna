---
sidebar_position: 7
title: Ko-fi
---

The **Ko-fi** tab connects items in your Ko-fi shop to products in Lyna. When somebody buys a mapped item, Lyna issues them a license and mails them the key, as long as the product has a mail.

## Before you start

Ko-fi has to send its webhooks to Lyna first. [Setting up Ko-fi](../setup/kofi.md) shows you how.

Give each product you map a mail, too. The mail is how the buyer gets their key. Open the [Mailing](./mailing.md) tab and check that the product is not listed under products without a mail.

A sale for a product without a mail still issues the license. Only the mail is skipped, and Lyna logs a warning with the `NOTIFY_ADMIN` marker, so it can [reach a Discord channel](../configuration/logging.md#errors-in-a-discord-channel). The license still goes to the account that proved the address the buyer paid from, either right away or once they prove it.

## How a sale becomes a license

When a Ko-fi shop order arrives, Lyna looks at each item in it. It takes the item's direct-link code and looks for a mapping with that code. If it finds one, it issues a license for the mapped product to the buyer's email address. If an account has already proved that address, the license goes to that account right away. Then Lyna sends the product's mail.

Items without a mapping are skipped. Donations and other payments that are not shop orders never issue a license.

## Add a mapping

Under **Add or update mapping**:

1. Enter the item's Ko-fi direct-link code in **Link code**.
2. Pick the product in **Product**.
3. Click **Save**.

You see **Mapping saved.** and the mapping shows up in the list below.

A code can point at one product only. If you save a code that is already mapped in this guild, it now points at the product you picked. A code that another guild already uses is refused with **This Ko-fi code belongs to another guild**.

## The list

Each mapping shows its link code, the product it points to, and the product's id. The list is sorted by product name. If you have none yet, you see **No Ko-fi mappings yet.**

Click **Remove** to delete a mapping. Lyna asks first: click **Confirm** to remove it, or **Cancel** to keep it. Licenses already issued through it stay as they are.
