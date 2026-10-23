---
title: Ko-fi
sidebar_position: 5
---

Ko-fi tells Lyna about every sale through a webhook. A sale of a shop item that belongs to a
product becomes a license for the buyer.

## 1. Point Ko-fi at Lyna

1. In your Ko-fi settings, open the **API** page with the webhook settings.
2. Set the webhook URL to your address with `/api/v1/kofi` at the end:

   ```text
   https://lyna.example.com/api/v1/kofi
   ```

3. Copy the **Verification Token** shown there.

## 2. Give Lyna the token

```bash
KOFI_VERIFICATIONTOKEN=the-token-from-ko-fi
```

Lyna refuses every call that doesn't carry this token. Until you set it, Lyna uses a random one, so
nothing is accepted by accident.

## 3. Connect shop items to products

Lyna only acts on shop orders. Each shop item carries a link code, and the code tells Lyna which
product was bought. Set the codes in the admin area on the [Ko-fi page](../admin/kofi.md) or on a
[product's General tab](../admin/products.md#ko-fi-shop-items).

Donations and subscriptions are logged, but grant nothing.
