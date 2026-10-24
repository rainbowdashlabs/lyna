---
sidebar_position: 8
title: BuiltByBit
---

The **BuiltByBit** tab shows what to paste into BuiltByBit and every call BuiltByBit made to Lyna.

## Before you start

Set the two BuiltByBit secrets in the instance configuration. [Setting up BuiltByBit](../setup/builtbybit.md) walks through the whole setup.

Then give each product you sell there its BuiltByBit resource id on the product's [General tab](./products.md#main-settings). Lyna only issues licenses for resources it knows.

## How a sale becomes a license

BuiltByBit calls Lyna twice for a sale:

- The **purchase webhook** reports the sale. Lyna issues a license for the product to the buyer's BuiltByBit member id.
- The **license key placeholder** asks for the buyer's key whenever they download the resource. BuiltByBit writes the key into the jar.

Both end at the same license, whichever comes first. Downloading again returns the same key. If you took away the license's access, a new download doesn't give it back.

The buyer redeems the key in Lyna like any other key. BuiltByBit never tells Lyna an email address, so no mail is sent.

Bundles are not supported. A bundle purchase is logged as **Bundle, not issued**, and you issue its licenses by hand on the [Licenses](./licenses.md) tab.

## Setting it up

Under **Setting it up** you find:

- **Purchase webhook URL**: the address for the purchase webhook. Replace `<BUILTBYBIT_WEBHOOKSECRET>` with the webhook secret.
- **Purchase webhook body**: the body the webhook has to send. Paste it as it is.
- **License key placeholder URL**: the address for the placeholder. Give the placeholder the key secret.

The last line says whether a BuiltByBit API token is configured. With one, Lyna asks BuiltByBit about every purchase and download before issuing a license.

## Recent calls

The list shows the newest 200 calls BuiltByBit made, newest first. Calls to the wrong address or with the wrong secret are refused and not listed. Each call shows when it arrived, whether it was a purchase or a key request, and what Lyna did:

- **Issued**: the buyer has a license now.
- **Unreadable**: the body was not what Lyna expects. Check the webhook body.
- **Unknown resource**: no product is sold as that resource. Set the resource id on the product.
- **Bundle, not issued**: a bundle was bought.
- **Denied by BuiltByBit**: the API says the member holds no license for it.
- **BuiltByBit unreachable**: the API is configured but didn't answer, so nothing was issued.

Click a call to see the body it arrived with.
