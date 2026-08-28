#!/usr/bin/env bash
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root: sudo bash website/install-yozakura-club.sh" >&2
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="${YOZAKURA_CLUB_APP_DIR:-/opt/yozakura-club}"
CONFIG_DIR="${YOZAKURA_CLUB_CONFIG_DIR:-/etc/yozakura-club}"
DATA_DIR="${YOZAKURA_CLUB_DATA_DIR:-/var/lib/yozakura-club}"
RELEASE_DIR="${YOZAKURA_RELEASE_DIR:-/var/lib/yozakura-releases}"
APP_USER="${YOZAKURA_CLUB_USER:-yozakura-club}"

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ca-certificates curl openssl

if ! command -v node >/dev/null 2>&1 || [ "$(node -p 'Number(process.versions.node.split(".")[0])')" -lt 20 ]; then
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends nodejs
fi

if ! id "$APP_USER" >/dev/null 2>&1; then
  useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin "$APP_USER"
fi

install -d -m 0755 "$APP_DIR" "$APP_DIR/public" "$CONFIG_DIR"
install -d -m 0700 -o "$APP_USER" -g "$APP_USER" "$DATA_DIR" "$RELEASE_DIR"
install -m 0644 "$SCRIPT_DIR/server.js" "$APP_DIR/server.js"
install -m 0644 "$SCRIPT_DIR/package.json" "$APP_DIR/package.json"
cp -a "$SCRIPT_DIR/public/." "$APP_DIR/public/"
install -m 0644 "$SCRIPT_DIR/yozakura-club.service" /etc/systemd/system/yozakura-club.service

if [ ! -f "$CONFIG_DIR/yozakura-club.env" ]; then
  install -m 0600 "$SCRIPT_DIR/yozakura-club.env.example" "$CONFIG_DIR/yozakura-club.env"
  echo "Created $CONFIG_DIR/yozakura-club.env"
fi

chown -R "$APP_USER:$APP_USER" "$APP_DIR" "$DATA_DIR" "$RELEASE_DIR"
systemctl daemon-reload
systemctl enable yozakura-club

echo "Yozakura Club installed."
echo "Config:  $CONFIG_DIR/yozakura-club.env"
echo "Release: $RELEASE_DIR/yozakura-client.zip"
echo "Start:   sudo systemctl restart yozakura-club"
echo "Status:  sudo systemctl status yozakura-club --no-pager"
