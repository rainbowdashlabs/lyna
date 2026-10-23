---
title: Mail
sidebar_position: 4
---

With a mailbox, Lyna reads PayPal receipts and turns them into licenses, and it sends mail: the
license to a buyer, address checks and password resets.

Mail is optional and off until you turn it on with `enabled: true`.

:::note
Without mail, nobody can confirm an address or reset a password by mail.
:::

## 1. The mailbox

Use a mailbox of its own for Lyna. It looks at every unread mail in the inbox.

## 2. The settings

```yaml
mailing:
  enabled: true
  user: "lyna@example.com"
  password: ""
  imap:
    host: "imap.example.com"
    port: 993
    ssl: true
  smtp:
    host: "smtp.example.com"
    port: 465
    ssl: true
  pollSeconds: 300
  senderName: "Lyna"
```

- `user` and `password` are used for both IMAP and SMTP. Give the password as `MAIL_PASSWORD`.
- The ports above are the defaults, for servers reached over TLS.
- `properties` takes any other [Jakarta Mail property](https://jakarta.ee/specifications/mail/),
  for example `mail.smtp.starttls.enable: "true"` for a server on port `587`.

The `imap`, `smtp` and `properties` settings can only be set in the file, not from the environment.

## 3. Receipts from PayPal

Lyna reads a mail as a receipt when it comes from an address in `paypalSenders` or in
`originMail`. Everything else is ignored.

`paypalSenders` holds `service@paypal.de` and `service@paypal.com`. If your PayPal account sends
receipts from another address, add it:

```yaml
mailing:
  paypalSenders:
    - service@paypal.de
    - service@paypal.com
    - service@paypal.co.uk
```

If your PayPal receipts go to another mailbox, forward them to Lyna's and name that mailbox in
`originMail`:

```yaml
mailing:
  originMail:
    - shop@example.com
```

A forwarded mail is only accepted from an address in that list. With the list empty, every forwarded
mail is refused, and the log says why.

Which product a receipt is for is set per product in the admin area, under
[mailing](../admin/mailing.md).

:::warning
Leave `skipVerify` off. It accepts a receipt from any sender, so anybody could send Lyna a fake one.
:::

## Checking that it works

Watch the backend log after a start. Every `pollSeconds` it checks the mailbox, and each new mail it
reads is logged with its sender. Errors reaching the mail server are logged with the
`NOTIFY_ADMIN` marker, so they also reach a [Discord log channel](../configuration/logging.md#errors-in-a-discord-channel)
if you set one up.
