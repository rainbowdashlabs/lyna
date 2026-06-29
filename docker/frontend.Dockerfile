FROM node:24-alpine AS build

WORKDIR /build

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ .
RUN NODE_OPTIONS="--max-old-space-size=8192" npm run build

FROM node:24-alpine AS runtime

WORKDIR /app
COPY --from=build /build/.output ./

EXPOSE 3000

ENV NODE_ENV=production
ENV NITRO_PORT=3000
ENV NITRO_HOST=0.0.0.0

CMD ["node", "server/index.mjs"]
