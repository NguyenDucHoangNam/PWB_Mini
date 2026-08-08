#!/bin/sh
# One-time certificate bootstrap. Run once on the VPS, from the repository root:
#
#   ./nginx/init-letsencrypt.sh you@example.com
#
# Renewal afterwards is automatic — the certbot service in docker-compose.prod.yml retries every
# 12 hours and nginx reloads every 6 — so this script is not part of normal deploys.
#
# It exists because of a bootstrap deadlock: nginx refuses to start when the files named by
# ssl_certificate are missing, but certbot's webroot challenge needs a running nginx to serve
# /.well-known/acme-challenge/. The way out is to hand nginx a throwaway self-signed certificate,
# start it, obtain the real one through the now-reachable challenge path, and reload.

set -eu

EMAIL="${1:-}"
DOMAIN="producerworkbench.online"
API_DOMAIN="api.producerworkbench.online"
COMPOSE="docker compose -f docker-compose.prod.yml --env-file .env.prod"

# Set to 1 to use Let's Encrypt's staging environment. The certificate it issues is not trusted by
# browsers, but the rate limits are far looser — 5 failures per hour against production locks the
# domain out for the rest of the week, which is a bad thing to discover the day before a demo.
STAGING="${STAGING:-0}"

if [ -z "$EMAIL" ]; then
    echo "Usage: $0 <email>   (used by Let's Encrypt for expiry warnings)" >&2
    exit 1
fi

CERT_PATH="/etc/letsencrypt/live/$DOMAIN"

echo "==> Creating a throwaway certificate so nginx can start"
$COMPOSE run --rm --entrypoint "\
  sh -c 'mkdir -p $CERT_PATH && \
         openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
           -keyout $CERT_PATH/privkey.pem \
           -out    $CERT_PATH/fullchain.pem \
           -subj \"/CN=localhost\"'" certbot

echo "==> Starting nginx"
$COMPOSE up -d nginx
# nginx binds and loads its configuration in well under this; the wait only avoids racing the
# challenge request against a container that has not opened port 80 yet.
sleep 5

echo "==> Discarding the throwaway certificate"
$COMPOSE run --rm --entrypoint "rm -rf /etc/letsencrypt/live/$DOMAIN /etc/letsencrypt/archive/$DOMAIN /etc/letsencrypt/renewal/$DOMAIN.conf" certbot

echo "==> Requesting the real certificate for $DOMAIN and $API_DOMAIN"
STAGING_ARG=""
if [ "$STAGING" != "0" ]; then
    STAGING_ARG="--staging"
    echo "    (staging mode: the result will NOT be trusted by browsers)"
fi

# Both names go into a single certificate, which is why both server blocks in nginx.conf point at
# the same files. Requesting them separately would mean two renewal schedules for no benefit.
$COMPOSE run --rm --entrypoint "\
  certbot certonly --webroot -w /var/www/certbot \
    $STAGING_ARG \
    -d $DOMAIN -d $API_DOMAIN \
    --email $EMAIL \
    --agree-tos --no-eff-email \
    --non-interactive" certbot

echo "==> Reloading nginx with the real certificate"
$COMPOSE exec nginx nginx -s reload

echo
echo "Done. Verify with:"
echo "  curl -I https://$DOMAIN"
echo "  curl -I https://$API_DOMAIN"
