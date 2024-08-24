FROM gradle:jdk21-alpine as build

COPY src src
COPY settings.gradle.kts build.gradle.kts ./
RUN gradle clean build --no-daemon

FROM eclipse-temurin:21-alpine as runtime

WORKDIR /app

COPY --from=build /home/gradle/build/libs/lyna-*-all.jar bot.jar

COPY docker/docker-entrypoint.sh .

ENTRYPOINT ["sh", "docker-entrypoint.sh"]
CMD ["-Dbot.config=config/config.json", "-Dlog4j.configurationFile=config/log4j2.xml"]
