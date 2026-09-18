FROM gradle:jdk25-alpine AS build

# What the build stamps into the version, so a running instance can say which commit it is. A build
# that is not CI leaves them unset and the version says only what it is.
ARG GITHUB_ACTIONS=false
ARG GITHUB_REF_TYPE=null
ARG GITHUB_REF_NAME=null
ARG GITHUB_SHA=null
ENV GITHUB_ACTIONS=$GITHUB_ACTIONS
ENV GITHUB_REF_TYPE=$GITHUB_REF_TYPE
ENV GITHUB_REF_NAME=$GITHUB_REF_NAME
ENV GITHUB_SHA=$GITHUB_SHA

WORKDIR /home/gradle

COPY . .

RUN gradle clean installDist --no-daemon -x test -x javadocJar -x sourcesJar

FROM eclipse-temurin:25-alpine AS runtime

WORKDIR /app

COPY --from=build /home/gradle/build/install/lyna/ ./

RUN mkdir -p config

ENV JAVA_OPTS="-Dlog4j.configurationFile=config/log4j2.xml"

ENTRYPOINT ["./bin/lyna"]
