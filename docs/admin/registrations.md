---
sidebar_position: 6
title: Registrations
---

The **Registrations** page shows which of your guild's licenses one person holds and which are shared with them. Use it when someone asks for help with their access.

Open it from **Registrations** in the admin tab strip.

## Looking someone up

1. Copy the person's Discord user id. In Discord, turn on developer mode, right-click the user and choose to copy their id.
2. Paste it into the **Discord user id** field.
3. Click **Look up**.

Lyna looks the person up by the Discord account linked to their Lyna account. Someone who never linked Discord has no licenses here, even if they hold some on the web.

## What you see

The top of the result shows the **Discord id:** and **Member name:** of the person. The id is exactly the one you looked up. For the member name, Lyna asks Discord when the bot has not cached the member. If the person is not in your server, or no bot is connected, the result says they are not in this guild.

Below that are two lists:

- **Owned**: the licenses this person holds. Each shows the product, the license id, its identifier and how many sharees it has.
- **Shared with**: licenses someone else holds and shares with this person. Each shows the product, the license id and the holder, such as `owner alex`. The holder is named the same way as on the [Licenses](./licenses.md#the-license-list) page.

Each heading shows how many licenses it lists. A list with nothing in it says **None.**

If the person holds nothing and nothing is shared with them, you see **No registration found.** instead.

Only licenses for products of the guild you are administering are shown.

## What to do next

The page is read only. To change a license you found here, search for its id or identifier on the [Licenses](./licenses.md) page and click **Manage** there.

Sharing and transferring are up to the license holder. See [Licenses](./licenses.md#sharing-and-transferring).
