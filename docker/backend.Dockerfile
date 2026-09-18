FROM gradle:jdk25-alpine AS build

WORKDIR /home/gradle

COPY . .

RUN gradle clean installDist --no-daemon -x test -x javadocJar -x sourcesJar

FROM eclipse-temurin:25-alpine AS runtime

WORKDIR /app

COPY --from=build /home/gradle/build/install/lyna/ ./

RUN mkdir -p config

ENV JAVA_OPTS="-Dlog4j.configurationFile=config/log4j2.xml"

ENTRYPOINT ["./bin/lyna"]
