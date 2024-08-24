#!/bin/sh

set -e

if [ "${1#-}" != "$1" ]; then
  set -- java "$@" -jar bot.jar
else
  set -- java -jar bot.jar "$@"
fi

exec "$@"
