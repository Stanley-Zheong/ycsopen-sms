# syntax=docker/dockerfile:1
FROM node:20-alpine AS build
WORKDIR /workspace/web

COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web ./
ARG BUILD_COMMIT=unknown
RUN VITE_BUILD_COMMIT="${BUILD_COMMIT}" npm run build

FROM nginx:1.27-alpine
ARG BUILD_COMMIT=unknown
ENV BUILD_COMMIT=${BUILD_COMMIT}
LABEL org.opencontainers.image.revision=${BUILD_COMMIT}

COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/web/dist /usr/share/nginx/html

EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=6 \
  CMD wget -qO- http://127.0.0.1/login >/dev/null || exit 1
