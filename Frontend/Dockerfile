# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Dependencies
# ---------------------------------------------------------------------------
FROM node:22-alpine AS deps
WORKDIR /app

# The repository pins no packageManager field, so the version is pinned here instead of letting
# corepack pick whatever the base image ships with.
RUN npm install -g pnpm@11

# pnpm-workspace.yaml carries the allowBuilds list, which decides whether sharp compiles. Without
# it here, the install runs with different settings than a developer's machine.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app
RUN npm install -g pnpm@11

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Every NEXT_PUBLIC_* value is inlined into the client bundle during `next build`, so these are
# build arguments rather than container environment variables. Changing any of them means
# rebuilding this image — restarting the container will keep serving the old values.
#
# next.config.ts also derives the Content-Security-Policy from them: the backend origin lands in
# connect-src and the storage origins in media-src. A value missing here does not degrade
# gracefully — the browser blocks the request outright.
ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_SITE_URL
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID
ARG NEXT_PUBLIC_TURNSTILE_SITE_KEY
ARG NEXT_PUBLIC_STORAGE_BUCKET_NAME
ARG NEXT_PUBLIC_STORAGE_REGION
ARG NEXT_PUBLIC_STORAGE_PUBLIC_URL_PREFIX
ARG NEXT_PUBLIC_STORAGE_ENDPOINT
ARG NEXT_PUBLIC_AUTH_OTP_EXPIRY_SECONDS
ARG NEXT_PUBLIC_AUTH_OTP_RESEND_COOLDOWN_SECONDS

ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL \
    NEXT_PUBLIC_SITE_URL=$NEXT_PUBLIC_SITE_URL \
    NEXT_PUBLIC_GOOGLE_CLIENT_ID=$NEXT_PUBLIC_GOOGLE_CLIENT_ID \
    NEXT_PUBLIC_TURNSTILE_SITE_KEY=$NEXT_PUBLIC_TURNSTILE_SITE_KEY \
    NEXT_PUBLIC_STORAGE_BUCKET_NAME=$NEXT_PUBLIC_STORAGE_BUCKET_NAME \
    NEXT_PUBLIC_STORAGE_REGION=$NEXT_PUBLIC_STORAGE_REGION \
    NEXT_PUBLIC_STORAGE_PUBLIC_URL_PREFIX=$NEXT_PUBLIC_STORAGE_PUBLIC_URL_PREFIX \
    NEXT_PUBLIC_STORAGE_ENDPOINT=$NEXT_PUBLIC_STORAGE_ENDPOINT \
    NEXT_PUBLIC_AUTH_OTP_EXPIRY_SECONDS=$NEXT_PUBLIC_AUTH_OTP_EXPIRY_SECONDS \
    NEXT_PUBLIC_AUTH_OTP_RESEND_COOLDOWN_SECONDS=$NEXT_PUBLIC_AUTH_OTP_RESEND_COOLDOWN_SECONDS \
    NEXT_TELEMETRY_DISABLED=1 \
    NODE_ENV=production

RUN pnpm build

# ---------------------------------------------------------------------------
# Runtime
# ---------------------------------------------------------------------------
FROM node:22-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

RUN addgroup -S nextjs && adduser -S nextjs -G nextjs

# `output: "standalone"` produces a server bundle carrying only the dependencies actually reached,
# but it deliberately leaves out two directories that Next serves rather than imports. They have to
# be copied separately or every static asset and image 404s.
COPY --from=build --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nextjs /app/.next/static ./.next/static
COPY --from=build --chown=nextjs:nextjs /app/public ./public

USER nextjs
EXPOSE 3000

# Caddy sits in front and terminates TLS; this server only ever sees plain HTTP on the internal
# network. Node is invoked directly because the standalone output is a plain server.js, not a
# `next start` project.
CMD ["node", "server.js"]
