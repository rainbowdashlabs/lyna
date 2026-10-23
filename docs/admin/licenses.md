---
sidebar_position: 4
title: Licenses
---

A license gives one person a product: its role in Discord and its downloads. The **Licenses** page lists every license of your guild and lets you issue, adjust and delete them.

Open it from **Licenses** in the admin tab strip.

## The license list

Each row shows the product, then the license id, its identifier, its holder and how many people it is shared with. For example: `#42 · buyer@example.com · owner alex (123456789012345678) · 1 sharees`.

- The **identifier** says who the license was issued for. Usually it is the buyer's email address or an order id.
- The **owner** is the account that holds the license. Lyna names it by its username. If it has none, it uses one of the account's email addresses, and otherwise `account` and its number, such as `account 17`. When the account has linked Discord, its Discord id follows in brackets.
- A license nobody holds yet shows **unclaimed**. A license held by an account without Discord is not unclaimed. It shows its holder like any other.
- The **sharees** count includes people it is shared with and invites nobody has answered yet.

Use the search box to filter the list by product, identifier, id or owner. You can search for the owner by name or by Discord id.

## Issuing a license

1. Click **Issue license**.
2. Choose the **Product**.
3. Enter the **Identifier (mail / order id)**. Use the buyer's email address if you have it.
4. Under **Reaches**, tick the release types the license should reach. It starts on `STABLE` only, which is what a purchase gives.
5. Click **Issue**.

Lyna shows the new key under **License issued**. Copy it and give it to the person. They redeem it in their account on the web or with the bot.

A product can only have one license per identifier. Issuing a second one for the same identifier is refused.

If you untick every release type, the license reaches nothing until you change it.

If the identifier is an email address that an account has already proved, Lyna gives the license to that account right away. Only a proved address counts. An address someone merely added to their account does not. If nobody has proved the address yet, the license stays unclaimed, and the account that proves the address later picks it up.

:::note
Issuing here sends no mail. To mail the key as well, use **Send a license by mail** on the product's [Mailing tab](./products.md#mailing) instead.
:::

## Changing what a license reaches

Click **Manage** on a license. The row opens and shows a switch for each release type under **Reaches**. Each switch saves as soon as you flip it.

People the license is shared with reach the same release types. See [who may download what](./download-types.md#who-may-download-what).

**Close** folds the row again.

## Deleting a license

Click **Manage**, then **Delete license**, then **Really delete** to confirm.

Deleting a license:

- ends every share of it,
- takes the product's role back from its holder and its sharees, unless something else still gives them that role,
- removes the license and its key for good.

## Sharing and transferring

You cannot share or transfer a license from the admin area. The holder does that themselves, in their account on the web or with the `/registrations` bot commands. The list shows you the result in the **sharees** count and the **owner**.

You set how many people one license may be shared with under **License sharee cap** on the [Settings](./settings.md) page.

To see which licenses one person holds or shares, use [Registrations](./registrations.md).

## Where a license came from

Every license records its source. The admin area does not show it, but it explains where a license in your list came from:

- `KOFI`: issued by a Ko-fi order for a linked shop item. See [Ko-fi](./kofi.md).
- `MAIL`: issued from a payment receipt Lyna read out of your mailbox. See [Mailing](./mailing.md).
- `MANUAL`: issued by an admin, on this page, from a product's **Mailing** tab, or with a bot command.

The source does not change who the license belongs to. When someone proves an email address in their account, they collect every unclaimed license issued against that address, whatever its source.
