---
sidebar_position: 9
title: Server keys
---

The **Server keys** tab is where you decide how many upgrade tokens and dashboard keys an account may hold for one of your products, and where you revoke dashboard keys. Most accounts never need you here. You only act when somebody asks for more.

The tabs **Upgrade tokens** and **Dashboard keys** at the top pick which kind of key the lists below are about.

## What an updater is

An updater runs on a customer's server and fetches new builds of a product on its own. Your customers set them up on the **Server keys** page of their account.

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

If nobody is waiting, you see **Nobody is waiting for more keys of this kind.**

## Raised allowances

**Raised allowances** lists every account that may run more than the default on one of your products. Each row shows the account, the product, and how many updaters it runs now, such as `12 in use`.

The **Extra** field is how many updaters the account may run on top of the default ten. Change it and click **Save**.

- Set it to `0` to put the account back on the default. It then leaves this list.
- Lowering it removes no updaters. An account above its allowance keeps the ones it has, but cannot add more.

If no account has more than the default, you see **Every account is on the default.**

## Dashboard keys

A dashboard key lets a server connect to the hosted dashboard. Your customers make them on the **Server keys** page of their account, for every product whose [product settings](./products.md#dashboard-keys) hand them out. Creating one shows the command to paste on the server once.

- A buyer may hold the product's **Keys per buyer**, ten by default, and ask for more like for upgrade tokens. An account that only took the product's trial holds exactly one and cannot ask for more.
- The server trades its key for a new one every seven days by default. The old key still answers for ten minutes, so a server that lost the answer gets the new one. After that, using the old key is refused and noted on the key, like a reused upgrade token.
- A key nobody uses for thirty days by default expires. Its owner reissues it on the **Server keys** page.
- Every refresh and every verification checks again whether the account has the product or its trial.

**Dashboard keys held** lists the dashboard keys people hold for this guild's products: the account, the product, the key's name, how many keys that account holds for the product, and when the key was last used and expires. Click **Revoke** and then **Really revoke** to end a key. The server is refused at its next verification.
