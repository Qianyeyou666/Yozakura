#!/usr/bin/env bash
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root: sudo bash install-all.sh" >&2
  exit 1
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

bash "$ROOT_DIR/openclaw/install-openclaw.sh"
bash "$ROOT_DIR/website/install-yozakura-club.sh"

OPENCLAW_ENV=/etc/openclaw/openclaw.env
CLUB_ENV=/etc/yozakura-club/yozakura-club.env
if ! grep -q '^OPENCLAW_ADMIN_TOKEN=' "$OPENCLAW_ENV" || grep -q '^OPENCLAW_ADMIN_TOKEN=REPLACE_' "$OPENCLAW_ENV"; then
  ADMIN_TOKEN="$(openssl rand -hex 32)"
  if grep -q '^OPENCLAW_ADMIN_TOKEN=' "$OPENCLAW_ENV"; then
    sed -i "s|^OPENCLAW_ADMIN_TOKEN=.*|OPENCLAW_ADMIN_TOKEN=${ADMIN_TOKEN}|" "$OPENCLAW_ENV"
  else
    printf 'OPENCLAW_ADMIN_TOKEN=%s\n' "$ADMIN_TOKEN" >> "$OPENCLAW_ENV"
  fi
  printf '%s\n' "$ADMIN_TOKEN" > /root/yozakura-openclaw-admin-token.txt
  chmod 0600 /root/yozakura-openclaw-admin-token.txt
  echo "Generated OpenClaw admin token: /root/yozakura-openclaw-admin-token.txt"
fi

if ! grep -q '^OPENCLAW_JWT_SECRET=' "$OPENCLAW_ENV" || grep -q '^OPENCLAW_JWT_SECRET=REPLACE_' "$OPENCLAW_ENV"; then
  JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n' | tr '+/' '-_')"
  if grep -q '^OPENCLAW_JWT_SECRET=' "$OPENCLAW_ENV"; then
    sed -i "s|^OPENCLAW_JWT_SECRET=.*|OPENCLAW_JWT_SECRET=${JWT_SECRET}|" "$OPENCLAW_ENV"
  else
    printf 'OPENCLAW_JWT_SECRET=%s\n' "$JWT_SECRET" >> "$OPENCLAW_ENV"
  fi
fi

if grep -q '^OPENCLAW_SERVICE_SECRET=' "$OPENCLAW_ENV" && ! grep -q '^OPENCLAW_SERVICE_SECRET=REPLACE_' "$OPENCLAW_ENV"; then
  SERVICE_SECRET="$(grep '^OPENCLAW_SERVICE_SECRET=' "$OPENCLAW_ENV" | tail -n 1 | cut -d= -f2-)"
else
  SERVICE_SECRET="$(openssl rand -base64 48 | tr -d '\n' | tr '+/' '-_')"
  if grep -q '^OPENCLAW_SERVICE_SECRET=' "$OPENCLAW_ENV"; then
    sed -i "s|^OPENCLAW_SERVICE_SECRET=.*|OPENCLAW_SERVICE_SECRET=${SERVICE_SECRET}|" "$OPENCLAW_ENV"
  else
    printf 'OPENCLAW_SERVICE_SECRET=%s\n' "$SERVICE_SECRET" >> "$OPENCLAW_ENV"
  fi
fi

if grep -q '^YOZAKURA_VERIFY_SERVICE_SECRET=' "$CLUB_ENV"; then
  sed -i "s|^YOZAKURA_VERIFY_SERVICE_SECRET=.*|YOZAKURA_VERIFY_SERVICE_SECRET=${SERVICE_SECRET}|" "$CLUB_ENV"
else
  printf 'YOZAKURA_VERIFY_SERVICE_SECRET=%s\n' "$SERVICE_SECRET" >> "$CLUB_ENV"
fi
if grep -q '^YOZAKURA_VERIFY_REDEEM_URL=' "$CLUB_ENV"; then
  sed -i 's|^YOZAKURA_VERIFY_REDEEM_URL=.*|YOZAKURA_VERIFY_REDEEM_URL=http://127.0.0.1:8080/internal/api/accounts/redeem|' "$CLUB_ENV"
else
  printf '%s\n' 'YOZAKURA_VERIFY_REDEEM_URL=http://127.0.0.1:8080/internal/api/accounts/redeem' >> "$CLUB_ENV"
fi
if grep -q '^YOZAKURA_VERIFY_PROFILE_URL=' "$CLUB_ENV"; then
  sed -i 's|^YOZAKURA_VERIFY_PROFILE_URL=.*|YOZAKURA_VERIFY_PROFILE_URL=http://127.0.0.1:8080/internal/api/accounts/profile|' "$CLUB_ENV"
else
  printf '%s\n' 'YOZAKURA_VERIFY_PROFILE_URL=http://127.0.0.1:8080/internal/api/accounts/profile' >> "$CLUB_ENV"
fi
chmod 0600 "$OPENCLAW_ENV" "$CLUB_ENV"

systemctl restart openclaw
systemctl restart yozakura-club

echo "Services started on loopback:"
echo "  OpenClaw:     http://127.0.0.1:8080"
echo "  Yozakura Club: http://127.0.0.1:4173"
echo "Next: configure HTTPS using Caddyfile.yozakura.example."
