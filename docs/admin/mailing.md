---
sidebar_position: 8
title: Mailing
---

The **Mailing** tab holds the mail a buyer gets with their license key. You build each mail from blocks, and Lyna renders it into the same layout as every other mail it sends.

Lyna needs a working mail setup to send anything. See [Setting up mail](../setup/mail.md).

## How the mails fit together

- **The guild default** is the mail every product sends unless it has its own. Lyna ships one, and you can replace it with yours.
- **A product's mail** belongs to one product. It either sends the guild default or has its own blocks.
- **Discord and links** are shared by every purchase mail of the guild.

A product needs a mail, even an empty one that sends the default, before it can be sold. Lyna finds the product for a payment receipt by the mail's name, and a [Ko-fi](./kofi.md) sale issues no license for a product without a mail.

## The overview

At the top you see the **Guild default** card. It says **The default Lyna ships. Edit it to make it your own.** or **Your own default.** Click **Edit** to open it.

Below it is the **Discord and links** form. More on that further down.

If some products have no mail yet, you see **No mail yet, so no receipt can be matched to them:** followed by their names. Click **Add a mail for each, sending the default** to give each of them an empty mail named after the product. They then send the guild default.

Last comes one entry per product that has a mail. Each shows the product, the mail's name, and what it sends:

- **sends the guild default**
- **its own mail**
- **written before the editor**: an older HTML mail, kept as it was

Click **Edit** to open one.

## Discord and links

- **Discord invite**: where buyers join to redeem their key. It fills in the `{{ discordUrl }}` placeholder.
- **Links**: shown in a row at the end of every purchase mail that has a links block. Click **Add link**, then fill in **Label** and **Address**. Click **Remove** to drop a row.

Click **Save**. Every address has to start with `http://` or `https://`, and each link needs a label. You can have up to 10 links. Empty rows are dropped when you save.

## The editor

The editor looks the same for the guild default and for a product's mail.

### Blocks

Add blocks with the buttons below the list:

- **Add heading**: a heading. Tick **Centred** to centre it.
- **Add text**: a paragraph. You can tick **Centred** and **Italics**.
- **Add license key**: the buyer's key. You set the **Label above the key**. Lyna fills in the key when it sends the mail.
- **Add button**: a button with **Button text** and an **Address**.
- **Add list**: **Items**, one line per item.
- **Add image**: an **Image address**, and the **Text when the image does not load**.
- **Add links**: the guild's links from the **Discord and links** form, in a row.
- **Add divider**: a line across the mail.

Use the arrow buttons to move a block up or down, and **Remove** to delete it.

In headings and text you can use placeholders. You write a link as `[label](https://…)` or `[label]({{ wikiUrl }})`.

A button or image address has to be an `http` address or a placeholder such as `{{ reviewUrl }}`. If it is neither, the block is left out of the mail.

An older mail opens as a single **HTML** block. That block is sent exactly as written.

### Placeholders

The **Placeholders** section lists what each placeholder stands for in this mail. A placeholder shown as **unset** has no value, and a link to it is left out of the mail.

These are the placeholders you can use:

- `{{ name }}`: the buyer's name
- `{{ key }}`: the license key
- `{{ product }}`: the product's name
- `{{ downloadUrl }}` and `{{ productUrl }}`: the product's project URL
- `{{ productPage }}`: the product's page on your Lyna site
- `{{ productIcon }}`: the product's icon
- `{{ wikiUrl }}` and `{{ reviewUrl }}`: the product's wiki and review links
- `{{ discordUrl }}`: the guild's Discord invite
- `{{ shares }}`: how many people a license can be shared with

### Save and preview

- **Save** stores the mail. For a product, saving an empty editor hands the product back to the guild default.
- **Preview** shows what the mail looks like right now, including changes you have not saved. An empty product mail previews the guild default.
- **Back to the list** closes the editor. Changes you did not save are lost.

The guild default cannot be saved empty. It needs at least one block.

### Start from another mail

For a product, **Load guild default** fills the editor with the guild default. For the guild default, **Load shipped default** fills it with the one Lyna ships. If the editor already holds blocks, the button first asks **Replace the current blocks?**. Click it again to confirm. Nothing is saved until you click **Save**.

### Go back to a default

- **Use guild default** shows on a product that has its own mail. It drops the product's own mail at once, and the product sends the guild default again.
- **Reset to shipped default** shows on a guild default you wrote yourself. It goes back to the default Lyna ships at once.

### Send yourself one

Under **Send yourself one**, enter an **Address** and click **Send**. Lyna mails the saved version to that address, with stand-in values for the buyer and the key. This is the only real check of what a mail client makes of it.

:::tip
The test mail is the saved mail, not what is in the editor. Click **Save** before you send one.
:::

## Mails for one product

You can also create a product's mail and send a license by hand from the product itself. See [Products](./products.md).
