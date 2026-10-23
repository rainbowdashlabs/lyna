---
sidebar_position: 9
title: Updaters
---

The **Updaters** tab is where you decide how many updaters an account may run for one of your products. Most accounts never need you here. You only act when somebody asks for more.

## What an updater is

An updater runs on a customer's server and fetches new builds of a product on its own. Your customers set them up on the **Updaters** page of their account.

Each updater holds an upgrade token. It trades the token for a download, and every trade gives it a new token. So one token is good for one download. A copied token works once at most, and using it locks out the real updater, which its owner then notices.

A few rules:

- Tokens belong to an account and a product, not to a license.
- Every trade checks again whether the account may download the product. A license, a share or a role all count. When the account loses access, its updaters stop working. You do not have to revoke anything.
- Free products have no updaters. Their download links are public.

## The allowance

Every account may run ten updaters per product. That is the default, and you cannot change it.

When an account needs more, its owner sends a request from their account page. They say how many more they need, between 1 and 100, and why. An account can have one open request per product.

## Requests for more

**Requests for more** lists the open requests for this guild's products, oldest first. Each one shows:

- the account
- the product
- how many more it asks for, such as `+5`
- the reason it gave
- when it was sent

Click **Approve** to add what they asked for to that account's allowance for that product. Click **Decline** to turn the request down. Either way the request leaves the list.

If nobody is waiting, you see **Nobody is waiting for more updaters.**

## Raised allowances

**Raised allowances** lists every account that may run more than the default on one of your products. Each row shows the account, the product, and how many updaters it runs now, such as `12 in use`.

The **Extra** field is how many updaters the account may run on top of the default ten. Change it and click **Save**.

- Set it to `0` to put the account back on the default. It then leaves this list.
- Lowering it removes no updaters. An account above its allowance keeps the ones it has, but cannot add more.

If no account has more than the default, you see **Every account is on the default of ten.**
