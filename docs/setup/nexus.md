---
title: Nexus
sidebar_position: 3
---

Lyna hands out builds from a Sonatype Nexus. It downloads them with its own Nexus account, so the
repositories can stay private.

## 1. A Nexus user for Lyna

Create a user in your Nexus that can browse and read the repositories you want to serve. It needs
nothing else.

## 2. Tell Lyna where the Nexus is

```yaml
nexus:
  host: "nexus.example.com"
  username: "lyna"
  password: ""
```

- `host` is the host name only, without `https://`. Lyna talks to it over HTTPS.
- Give the password as `NEXUS_PASSWORD`.

Restart the backend after a change.

## 3. Connect products to builds

Which repository, group and artifact belongs to which product is set in the admin area, not in the
config:

1. Create a [download type](../admin/download-types.md) for each kind of build, for example stable
   releases and dev builds.
2. On a [product's Downloads tab](../admin/products.md#downloads), add a download per type and point
   it at the repository and artifact.

## Who may download

A signed-in person may download a product's builds when they hold a license for it, or have a
role that grants access. Free products are open to everyone. The details are on the
[Access tab](../admin/products.md#access) and the [download types](../admin/download-types.md) page.
