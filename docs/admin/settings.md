---
sidebar_position: 12
title: Settings
---

The **Settings** tab holds a few guild-wide values: how far a license can be shared, the limits for trials, and who else may administer the guild.

## The fields

### License sharee cap

How many people one license of this guild can be shared with. Pending invites count towards the cap, too. Set it to `0` for no limit. That holds on the website and for the bot's share command alike. The value also fills in the `{{ shares }}` placeholder in [purchase mails](./mailing.md#placeholders).

### Trial: minimum time on the server (minutes)

How long somebody must have been a member of your Discord server before they can start a [trial](./trial.md).

### Trial: minimum account age (minutes)

How old somebody's account must be before they can start a trial. Lyna checks the Discord account when the person is a member of your server. Otherwise it checks their Lyna account.

### Admin role (optional)

Members of this role administer this guild in the admin area, just like people with **Manage Server**. Use it when you would rather not hand out a server-wide permission. Leave it empty so only **Manage Server** counts.

With a bot connected, you pick the role by name. Without one, you type the role id. The role only lets people in while a bot is connected, because Lyna needs the bot to see who holds it.

See [Admin area](./index.md#who-gets-in) for everyone else who gets in.

## Saving

Click **Save**. You see **Saved.** when it worked, or the reason it did not. Lyna refuses negative numbers, and an admin role that is not a valid role id.
