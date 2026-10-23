---
title: First sign-in
sidebar_position: 2
---

How you get into the admin area of a new instance, and who else can.

## Make yourself an operator

Operators can always open the admin area, for every server and for the instance settings. Lyna knows
them by their Discord user id.

1. Put your Discord user id into `baseSettings.botOwner` and restart:

   ```yaml
   baseSettings:
     botOwner:
       - 123456789012345678
   ```

2. Open your Lyna address, sign up, and link your Discord account. Signing in with Discord does both
   at once.
3. Open **Admin** in the top bar.

An account without a linked Discord account is never an operator, whatever its address.

:::tip
Operators in `botOwner` can't be removed from the web app. That keeps the instance from locking you
out. Add further operators on the [instance page](../admin/instance.md#operators).
:::

## Who else gets in

With a bot connected, anybody whose Discord account has **Manage Server** on your server can
administer that server. So can holders of the server's admin role, if you set one under
[settings](../admin/settings.md#admin-role-optional). They see that server's pages, not the
instance settings.

Without a bot, Lyna can't ask Discord who has which permission, so only operators get in.

## Next

- [Connect your Nexus](./nexus.md) and create your first [product](../admin/products.md).
- Tour the [admin area](../admin/index.md).
