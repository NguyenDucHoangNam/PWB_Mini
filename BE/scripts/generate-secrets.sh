#!/usr/bin/env bash
set -euo pipefail

ENV_FILE=".env"

if [ -f "$ENV_FILE" ]; then
    echo "$ENV_FILE already exists. Refusing to overwrite." >&2
    exit 1
fi

cp .env.example "$ENV_FILE"

replace_env() {
    local key="$1"
    local value="$2"
    if grep -q "^${key}=" "$ENV_FILE"; then
        sed -i.bak "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
        rm -f "$ENV_FILE.bak"
    else
        echo "${key}=${value}" >> "$ENV_FILE"
    fi
}

replace_env "JWT_SECRET" "$(openssl rand -hex 32)"
replace_env "JWT_REFRESH_SECRET" "$(openssl rand -hex 32)"
replace_env "AUDIO_AES_MASTER_KEY" "$(openssl rand -hex 16)"
replace_env "IAM_OUTBOX_ENCRYPTION_KEY" "$(openssl rand -base64 32)"
replace_env "AUDIO_STREAM_COOKIE_SECRET" "$(openssl rand -base64 32)"
replace_env "AUDIO_IP_HASH_SALT" "$(openssl rand -base64 32)"
replace_env "REDIS_PASSWORD" "$(openssl rand -base64 24 | tr -d '\n')"
replace_env "MINIO_ACCESS_KEY" "pwb-minio-admin"
replace_env "MINIO_SECRET_KEY" "$(openssl rand -base64 32)"
replace_env "SEED_ADMIN_PASSWORD" "$(openssl rand -base64 18 | tr -d '\n')"
replace_env "SEED_USER_PASSWORD" "$(openssl rand -base64 18 | tr -d '\n')"
replace_env "SEED_ARTIST_PASSWORD" "$(openssl rand -base64 18 | tr -d '\n')"

echo "Generated $ENV_FILE with random secrets."