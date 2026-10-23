---
sidebar_position: 3
title: Download types
---

A download type is a kind of build your guild offers, such as "Plugin" or "Dev build". Each product's downloads name one, and the type's release type decides who may download it.

Open it from **Download types** in the admin tab strip.

## Release types

Every download type has one of three release types:

- `STABLE`: regular releases. This is what a purchase gives.
- `DEV`: development builds.
- `SNAPSHOT`: snapshot builds.

You pick the release type when you create a download type, and it can never change. Changing it would move every download of that type, and every license that reaches it, to another track.

Each release type is granted on its own. A license or role that reaches `DEV` does not reach `STABLE` unless you grant that too.

:::note
Update checks work the other way round. A build installed from `DEV` is told about newer `STABLE` and `DEV` builds, and a `SNAPSHOT` build about all three. A `STABLE` build only hears of `STABLE` builds.
:::

## Creating a download type

Under **New download type**:

1. Enter a **Name**. Names are unique within the guild, and case does not matter.
2. Enter a **Description**. It is shown next to the download, for example when someone takes a trial.
3. Choose the **Release type**.
4. Click **Create**.

Then add the type to a product on that product's **Downloads** tab. See [Products](./products.md#downloads).

## Changing and deleting a type

Each type in the list shows its **Name** and **Description** as fields you can edit, and its release type as a fixed badge. Change the text and click **Save**.

Below a type you see which products offer it, for example **Offered by MyPlugin, MyAddon**.

**Delete** removes a type. A type that a product still offers cannot be deleted. Lyna tells you which products use it, and you remove those downloads first.

## Nexus coordinates

A product's download tells Lyna where its builds are in your Nexus. You fill in these fields on the product's **Downloads** tab:

- **Repository**: the name of the Nexus repository that holds the builds, for example `maven-releases`.
- **Group id**: the Maven group id of the artifact, for example `de.example`.
- **Artifact id**: the Maven artifact id, for example `my-plugin`.
- **Classifier**: optional. Fill it in when the file you want carries a Maven classifier, such as `all`. Leave it empty otherwise.

Repository, group id and artifact id are required. After saving, the download shows the newest version Nexus holds for these coordinates. See [Nexus setup](../setup/nexus.md) and [the Nexus settings](../configuration/reference.md#nexus) for connecting Lyna to Nexus.

## Who may download what

Lyna decides access per product and per release type.

**Free products.** If a product is free, anyone may download every release type of it. No sign-in and no license are needed.

**Everything else.** For a product that is not free, a person may download the release types they reach in any of these ways. They are added together.

- **A license they hold.** Each license reaches the release types ticked on it. New licenses reach `STABLE` unless someone chose otherwise. You change this on the [Licenses](./licenses.md) page.
- **A license shared with them.** It reaches the same release types as for its holder.
- **A role they wear.** The product's **Access** tab grants a Discord role one release type. On the web, Lyna uses the roles of the person's linked Discord account.

If none of these applies, the person cannot download the product.

**Trials.** A trial is separate from all of this. It gives one download of the newest `STABLE` build. See [Trial](./trial.md).

:::tip
The product's own **Role** is not a download grant by itself. Lyna gives that role to license holders. If you want a role to open downloads, grant it on the product's **Access** tab.
:::
