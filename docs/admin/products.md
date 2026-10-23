---
sidebar_position: 2
title: Products
---

A product is something people can get from your guild: a Discord role, downloads from Nexus, and a page on the storefront. This page covers the product list and everything you can change about one product.

Open it from **Products** in the admin tab strip. Guild admins and instance operators see and can change the same things here. See [the admin area](./index.md) for who counts as which.

## The product list

The list shows every product of the guild. Each row has the product's icon, its name, a **Free** or **Premium** badge and its id (for example `#12`). Add-ons sit indented under the product they belong to and carry an **Add-on** badge.

Click a row to open that product's editor.

## Creating a product

1. Click **New product**.
2. Fill in the form:
   - **Name**: what the product is called everywhere. A name can only be used once per guild, and case does not matter.
   - **Role**: the Discord role Lyna gives to someone who holds a license for this product.
   - **Project URL (optional)**: the project's home page. Use a GitHub repository address if you want the product page to show its README.
   - **Free product**: anyone can download it, without signing in and without a license.
   - **Trial available**: people can take a trial of it. See [Trial](./trial.md).
3. Click **Create**.

Name and role are required. When a bot is connected, the role must exist in the guild.

A new product has no downloads yet. Add them on its **Downloads** tab.

## The product editor

The editor has six tabs: **General**, **Page**, **Downloads**, **Access**, **Mailing** and **Announcements**. The tab you are on is kept in the address, so you can link to a tab and a reload stays on it.

## General

The **General** tab holds the icon, the main settings, the Ko-fi shop items and the delete button.

### Icon

Pick an image file to upload it as the product's icon. Lyna accepts PNG, JPEG and WebP up to 4 MB and resizes it to the sizes the pages use. Other formats, such as GIF, are refused.

**Remove icon** takes an uploaded icon away again.

:::warning
The admin area does not show an icon you uploaded, so the preview and **Remove icon** may be missing even when the storefront shows the icon. The **Icon address** field is not saved either.
:::

### Main settings

These fields are saved together when you click **Save**. If one of them is refused, nothing is changed.

- **Name**: the product's name.
- **Role**: the Discord role that goes with the product.
- **Belongs to**: makes this product an add-on of another product. Choose **Nothing, it stands on its own** to undo that. An add-on is shown with its product on its page and in every list. It does not change who may download it.
- **Project URL**: the project's home page, or its GitHub repository.
- **Wiki URL**: shown on the product page and linked from the purchase mail.
- **Review URL**: where the purchase mail asks a buyer to leave a review.
- **UpdateButler id**: only for plugins built against the old UpdateButler. Leave it empty if the product never had one. See [Updaters](./updaters.md).
- **Trial available**: whether people can take a trial.
- **Free to everyone**: whether anyone can download the product without a license. When you change it, the form warns **This changes who may download it.**

Some rules the server checks when you save:

- The wiki and review addresses must start with `http://` or `https://`.
- An UpdateButler id is a positive number, and only one product can carry each id.
- Add-ons are one level deep. A product that has add-ons cannot become an add-on, so **Belongs to** is locked for it. You also cannot pick an add-on as the parent.

### Ko-fi shop items

Here you link Ko-fi shop items to the product. Enter the item's **Link code**, the part after `ko-fi.com/s/`, and click **Add**. A Ko-fi order for that item issues a license for this product. **Remove** unlinks a code.

A product can have several codes. The storefront's buy button points at the first one in alphabetical order. See [Ko-fi](./kofi.md) for the whole Ko-fi setup.

:::note
A Ko-fi order only issues a license when the product has a mailing. See [Mailing](#mailing).
:::

### Deleting a product

The **Delete product** box at the bottom removes the product with its licenses, downloads, mailing, icon and everything else attached to it. This cannot be undone.

Type the product's name exactly as it is written into **Type the product's name to confirm**. Then **Delete product** becomes active. After deleting, you land back on the product list.

## Page

The **Page** tab decides what the product's public page says. You have two choices: text you write yourself, or the README of the project's GitHub repository.

### Your own text

Write the page in **Markdown**. The **Preview** next to it shows how it will look. Click **Save page** to publish it.

A page can be up to 8000 characters long. Saving an empty page removes your text.

### The GitHub README

Turn on **Show the GitHub README as the page** to show the repository's README instead of your text. The switch saves as soon as you flip it.

- It only works when the **Project URL** on the **General** tab is a GitHub repository, like `https://github.com/owner/repo`. Otherwise the switch is greyed out.
- Lyna fetches the README from GitHub and keeps it for an hour, so changes take up to an hour to show.
- Links in the README that point to its releases page or to plugin hosts lead to this product's download on Lyna instead.
- If you have written no text and the project URL is a GitHub repository, the page shows the README anyway.
- If GitHub has never answered for the repository, your own text is shown.

When the page shows the README, the tab tells you so and shows it, with a link to **Open it on GitHub**.

## Downloads

The **Downloads** tab lists what the product offers to download. Each download is one [download type](./download-types.md) plus the place in Nexus where its builds live.

### Adding a download

Under **Add a download**:

1. Choose a **Download type**. Only types this product does not offer yet are listed. A product has at most one download per type.
2. Fill in where the builds are in Nexus: **Repository**, **Group id**, **Artifact id** and, if needed, **Classifier**. See [Download types](./download-types.md#nexus-coordinates) for what each field means.
3. Click **Add download**.

Repository, group id and artifact id are required. If every download type is in use already, the tab tells you to make another under **Download types**.

### Checking and changing a download

Each download shows its type, its release type and the newest version Nexus holds for it, for example **Nexus: 1.4.2**. If you see **Nexus holds nothing for these coordinates yet**, Nexus has no build there or could not be reached. Lyna accepts coordinates with nothing in them yet, so you can set up a download before its first release.

Change the coordinates and click **Save**. **Remove** takes the download off the product. To change a download's type, remove it and add it again with the other type.

## Access

The **Access** tab lists the Discord roles that may download this product, each for one release type. This is how you open downloads to a role, for example giving your testers the `DEV` builds.

To add one, pick a **Role** and a **Release type** and click **Grant**. **Revoke** removes a grant. A role that should reach two release types needs two grants.

When a bot is connected, the role must exist in the guild, and the list shows role names. Without a bot, roles are shown by id.

See [how access is decided](./download-types.md#who-may-download-what) for how roles and licenses work together.

## Mailing

A mailing is the mail a buyer gets with their license key. Ko-fi orders and payment receipts only issue a license for a product that has one.

If the product has none, enter **Product name as the mail says it** and click **Create mailing**. A product has at most one mailing. Once it exists, follow **Write it on the mailing page** to write the mail itself. See [Mailing](./mailing.md).

### Sending a license by mail

Use **Send a license by mail** for someone who bought the product somewhere Lyna does not see:

1. Enter their **Email address** and the **Name of the recipient**. The mail greets them by that name.
2. Under **Reaches**, tick the release types the license should reach. It starts on `STABLE` only, which is what a purchase gives.
3. Click **Issue license and send**.

Lyna issues a license against that address, sends the product's mail with the key, and gives the license straight to any account that has already proved that address. If the address already has a license for this product, the request is refused with **A license already exists for this address**.

Sending needs working mail. See [Mail setup](../setup/mail.md).

## Announcements

The **Announcements** tab connects the product to its GitHub repository through a webhook. Lyna can then post releases and repository activity in Discord.

### Setting up the webhook

1. Click **Set up webhook**. Lyna shows a **Payload URL** and a **Secret**.
2. On GitHub, open the repository's **Settings**, then **Webhooks**, then **Add webhook**.
3. Paste the payload URL and the secret. Set the content type to `application/json`.
4. Under events, choose **Releases**, **Pushes**, **Pull requests** and **Issues**.

The payload URL is built from your configured API URL, so GitHub must be able to reach it. See [the API settings](../configuration/reference.md#api) and [GitHub setup](../setup/github.md).

Lyna ignores every delivery that is not signed with the secret.

### Release channel

Choose the **Release channel** and click **Save channel**. Published releases and pre-releases are posted there, with a button that leads to the product's page on Lyna. Drafts, edits and deletions are not posted.

Leave the channel empty to post nothing. Every release event still updates the release notes on the product's page, whether it is posted or not.

When a bot is connected, the channel must be one in this guild that the bot can post in.

### Repository activity

Under **Repository activity**, pick an **Activity channel** and tick what to post:

- **Pushes**: only pushes to the default branch.
- **Pull request opened**
- **Pull request merged**
- **Issue opened**
- **Issue closed**

Click **Save activity**. Without an activity channel nothing is posted. Activity posts carry no button and mention nobody. Keep this channel apart from the release channel, which is for your buyers.

### New address or removing the webhook

- **New address and secret** replaces both. The old address stops working, so update the webhook on GitHub right away.
- **Remove webhook** removes the webhook from the product. Deliveries to its address are no longer accepted.
