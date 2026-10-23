---
title: Logging
sidebar_position: 4
---

Lyna logs with Log4j 2. It reads its logging setup from `config/log4j2.xml`, which you provide.

:::warning
Neither the Docker image nor a release brings a `log4j2.xml`. Without one, Log4j only prints
errors to the console. Put the file below into your config directory before the first start.
:::

## Where the file is read from

The start script reads `JAVA_OPTS`. The Docker image sets it to:

```bash
JAVA_OPTS="-Dlog4j.configurationFile=config/log4j2.xml"
```

Running from a release, set the same yourself. See [from source](../hosting/from-source.md).

## A starting file

Logs to the console and to `logs/app.log`, and keeps ten days of old logs:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN" shutdownHook="disable" packages="de.chojo.logutil.appender">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="[%d{HH:mm:ss}][%p{length=3}][%c{1}] %m%n"/>
        </Console>
        <RollingFile name="File" fileName="logs/app.log" filePattern="logs/app-%d{yyyy-MM-dd}.log">
            <PatternLayout pattern="[%d{HH:mm:ss}][%p{length=3}][%15.15c{1}] %m%ex%n"/>
            <Policies>
                <OnStartupTriggeringPolicy/>
                <TimeBasedTriggeringPolicy/>
            </Policies>
            <DefaultRolloverStrategy max="10"/>
        </RollingFile>
    </Appenders>
    <Loggers>
        <Logger name="net.dv8tion.jda" level="INFO"/>
        <Logger name="org.eclipse.jetty" level="INFO"/>
        <Logger name="com.zaxxer.hikari" level="INFO"/>
        <Root level="INFO">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="File"/>
        </Root>
    </Loggers>
</Configuration>
```

`logs/` is relative to the working directory, `/app/logs` in the Docker image. Mount it if you want
to keep the files.

## Errors in a Discord channel

Errors an operator should see are logged with the marker `NOTIFY_ADMIN`: a failed mail check, a
receipt Lyna couldn't read, a failed database request, a crashed command. You can send exactly those
to a Discord channel through a webhook.

Create a webhook in the channel's settings under **Integrations**, then add an appender and a
reference to it:

```xml
<Appenders>
    <!-- the appenders from above -->
    <DiscordWebhook name="DiscordError" url="https://discord.com/api/webhooks/...">
        <MarkerFilter marker="NOTIFY_ADMIN" onMatch="ACCEPT" onMismatch="DENY"/>
    </DiscordWebhook>
</Appenders>
<Loggers>
    <Root level="INFO">
        <AppenderRef ref="Console"/>
        <AppenderRef ref="File"/>
        <AppenderRef ref="DiscordError"/>
    </Root>
</Loggers>
```

The `packages="de.chojo.logutil.appender"` attribute on `<Configuration>` is what makes
`DiscordWebhook` known. Keep it.

:::note
The webhook address is a secret too. Anybody who has it can post into that channel.
:::
