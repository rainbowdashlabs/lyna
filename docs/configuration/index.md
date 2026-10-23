---
title: How configuration works
sidebar_position: 1
---

Lyna reads one YAML file, `config/config.yaml`. Every setting in it can also come from an environment
variable or a JVM system property, so secrets never have to be written into the file.

## The config directory

Lyna looks for a directory called `config` in its working directory. In the Docker image that is
`/app/config`. The directory holds:

| File | What it is |
|---|---|
| `config.yaml` | All settings. See the [reference](./reference.md). |
| `token.key` | The key stored Discord tokens are encrypted with. Made on first start. See [secrets](./secrets.md#the-token-key). |
| `log4j2.xml` | The logging setup. Written on first start if missing. See [logging](./logging.md). |

Mount the whole directory, not single files. Lyna writes into it.

## The first start

When `config.yaml` is missing, Lyna writes one with every setting at its default. It then stops
and names the settings it can't run without: your public address, `api.hostname` and `api.url`.
Fill in the file and start it again. The [reference](./reference.md) marks every setting you have
to set.

That first write is the only time Lyna writes the whole file. It never saves your settings back
later, so values that came from the environment never end up on disk.

## After an upgrade

A newer version can know settings your file does not have yet. On start, Lyna adds those keys with
their defaults and logs which ones it added:

```text
Added 2 setting(s) this version understands to config.yaml: github.token, github.apiUrl
```

It only adds keys. It never changes or removes a value that is already in the file.

:::tip
Read that log line after every upgrade. A new key at its default is how a new feature arrives
switched off or half set up.
:::

### Coming from `config.json`

Older versions read `config/config.json`. If Lyna finds that file and no `config.yaml`, it converts
the JSON to YAML once and reads only the YAML from then on. The old file stays where it is. You can
delete it when you like.

## Overrides from outside the file

Each setting has an environment variable and a system property. Both win over the file. If both are
set, the environment variable wins.

The names follow one pattern. The section has a short prefix, and the setting's name is added to
it:

| Kind | Pattern | Example for `database.password` |
|---|---|---|
| Environment variable | `PREFIX_SETTING`, all upper case | `DB_PASSWORD` |
| System property | `prefix.setting`, setting in its own case | `-Ddb.password=…` |

The prefixes are not always the section name. `baseSettings` uses `BOT`, `database` uses `DB` and
`mailing` uses `MAIL`. The [reference](./reference.md) lists the exact name of every setting.

A list is given as one comma-separated value:

```bash
BOT_BOTOWNER=123456789012345678,234567890123456789
```

A value that can't be converted, such as text for a number, is skipped with a warning in the log, and
the file's value is used.

:::note
Not everything can be overridden. The `host`, `port` and `ssl` settings of `mailing.smtp` and
`mailing.imap`, and `mailing.properties`, are only read from the file.
:::
