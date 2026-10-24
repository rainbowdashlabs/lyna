---
sidebar_position: 10
title: Server keys
---

The **Server keys** tab is where you decide how many upgrade tokens and product keys an account may hold for one of your products, and where you revoke product keys. Most accounts never need you here. You only act when somebody asks for more.

The tabs **Upgrade tokens** and **Product keys** at the top pick which kind of key the lists below are about.

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

## Product keys

A product can hand out keys of its own, such as the key a server connects to the product's dashboard with. You set up these key types on the product's [Server keys tab](./products.md#server-keys). Your customers make keys on the product's page, where each key type has its own section. Creating one shows the key once, in the product's command.

- A buyer may hold the key type's **Keys per buyer** and ask for more like for upgrade tokens. An account that only took the product's trial holds the type's **Keys per trial** and cannot ask for more.
- The server trades its key for a new one on the key type's schedule. The old key still answers for ten minutes, so a server that lost the answer gets the new one. After that, using the old key is refused and noted on the key, like a reused upgrade token.
- A key nobody uses for the key type's expiry time expires. Its owner reissues it on the product's page.
- Every refresh and every verification checks again whether the account has the product or its trial.
- A key type's gateway is answered about its own keys only. A key of another key type, of this product or another, is unknown to it.

**Product keys held** lists the keys people hold for this guild's products: the account, the product and key type, the key's name, how many keys of that type the account holds, and when the key was last used and expires. Click **Revoke** and then **Really revoke** to end a key. The server is refused at its next verification.
