---
sidebar_position: 10
title: Time channels
---

The **Time channels** tab puts a clock in a channel name. For example, a channel called `Developer Time: 14:30` lets your community see what time it is for a developer.

## How it works

The bot renames each time channel every quarter of an hour, on the quarter. That is as often as Discord allows. The time is shown to the minute, in 24-hour format, such as `09:45`.

This needs a bot. Without one, you see **The bot renames these channels, and none is connected, so no new one can be set up.** The list still shows the channels you set up earlier, but nothing gets renamed.

## Set up a channel

Under **Show the time on a channel**:

1. Pick a channel in **Voice channel or category**. Text channels do not work, because Discord rewrites their names into lowercase words joined by hyphens.
2. Type a zone in **Time zone**, such as `Europe/Berlin`. Lyna suggests matching zones once you have typed two letters.
3. Optionally set the **Name**. Write `{time}` where the time goes. If you leave it empty, Lyna uses `Developer Time: {time}`.
4. Click **Set up**.

The bot renames the channel right away. If you set up a channel that already shows the time, its zone and name are replaced.

Lyna refuses the setup when the zone is unknown, when the name has no `{time}` in it, or when the channel is not a voice channel or category the bot can see.

## The list

Each channel shows what its name says right now, followed by the channel's current name and its zone. If you have none yet, you see **No channel shows the time.**

Click **Stop** to remove a channel. Lyna asks first: click **Confirm** to stop it, or **Cancel** to keep it. The bot stops renaming the channel, and the channel keeps the name it has at that moment.
