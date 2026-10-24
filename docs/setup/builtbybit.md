---
title: BuiltByBit
sidebar_position: 6
---

BuiltByBit talks to Lyna in two ways. A **purchase webhook** tells Lyna about every sale, and a
**license key placeholder** asks Lyna for the buyer's key whenever they download the resource.
BuiltByBit writes that key into the jar they get. Both arrive at the same license, so it doesn't
matter which comes first.

Neither needs BuiltByBit Ultimate.

## 1. Choose two secrets

Make up two long random values, one for each call:

```bash
BUILTBYBIT_WEBHOOKSECRET=$(openssl rand -hex 32)
BUILTBYBIT_KEYSECRET=$(openssl rand -hex 32)
```

Until you set them, Lyna uses random ones, so nothing is accepted by accident.

## 2. Add the purchase webhook

1. In your BuiltByBit creator dashboard, create a webhook with the **Purchase** trigger, limited to
   the resources Lyna sells.
2. Set its URL to your address with `/api/v1/builtbybit/` and the webhook secret at the end:

   ```text
   https://lyna.example.com/api/v1/builtbybit/<webhook secret>
   ```

3. Set the body to exactly this:

   ```json
   { "username": "{username}", "user_id": "{user_id}", "user_url": "{user_url}", "resource_title": "{resource_title}", "resource_id": "{resource_id}", "resource_url": "{resource_url}", "addon_id": "{addon_id}", "addon_title": "{addon_title}", "bundle_id": "{bundle_id}", "bundle_title": "{bundle_title}", "renewal": "{renewal}", "list_price": "{list_price}", "final_price": "{final_price}", "purchase_date": "{purchase_date}" }
   ```

Bundles are not supported. A bundle purchase is logged, but no license is issued.

## 3. Add the license key placeholder

1. On the resource, create a custom placeholder of the type **External license key** and name it
   `%%__LYNA_LICENSE__%%`.
2. Set its URL to your address with `/api/v1/builtbybit/key` at the end:

   ```text
   https://lyna.example.com/api/v1/builtbybit/key
   ```

3. Set its secret to the key secret.
4. Build the plugin with the placeholder in its build data: publishdata's
   `buildData { lynaLicense() }` writes `lynaLicense=%%__LYNA_LICENSE__%%`, and eldo-util's
   `LynaLicense` reads it back. Upload a new version afterwards: BuiltByBit only fills placeholders in
   versions published after the placeholder was created.

Lyna fills the same placeholder when it serves a download itself, with the downloader's own license
key, so one build works for both.

If Lyna can't answer, BuiltByBit writes "Unable to acquire a license key automatically, please
contact the creator directly." instead of a key.

## 4. Connect resources to products

Lyna only issues licenses for resources it knows. Set the BuiltByBit resource id, and the add-on id
for an add-on, on the [product's General tab](../admin/products.md#main-settings). The
[BuiltByBit tab](../admin/builtbybit.md) lists every call BuiltByBit made and what came of it.

## Optional: the API

With BuiltByBit Ultimate you can give Lyna an API token. Lyna then asks BuiltByBit about every
purchase and every key request before issuing a license, so a leaked secret alone can't issue one.

```bash
BUILTBYBIT_APITOKEN=the-token-from-builtbybit
```
