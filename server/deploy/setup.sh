#!/usr/bin/env bash
# 새 서버(Ubuntu 24.04, Lightsail/EC2) 처음 한 번 준비. 서버에서 sudo로 실행한다.
#   sudo bash setup.sh <도메인>     예) sudo bash setup.sh 3-35-10-20.sslip.io
# 하는 일: Java 21·ffmpeg·한글 글꼴·Caddy(HTTPS) 설치, 스왑 1GB, 서비스 계정·폴더, systemd 서비스, 환경 파일 틀.
# 여러 번 실행해도 안전하다(이미 있으면 건너뜀). 환경 파일(/etc/randomdrive.env)은 덮어쓰지 않는다.
set -euo pipefail

DOMAIN="${1:?usage: sudo bash setup.sh <domain>}"
HERE="$(cd "$(dirname "$0")" && pwd)"

echo "== packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q openjdk-21-jre-headless ffmpeg fonts-noto-cjk caddy curl

echo "== swap (1GB 메모리 서버에서 영상 만들 때 여유)"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== user, dirs"
id randomdrive >/dev/null 2>&1 || useradd --system --home /var/lib/randomdrive --shell /usr/sbin/nologin randomdrive
install -d -o randomdrive -g randomdrive -m 750 /var/lib/randomdrive /var/lib/randomdrive/recaps
install -d -o root -g root -m 755 /opt/randomdrive

echo "== env file"
if [ ! -f /etc/randomdrive.env ]; then
  sed "s|__DOMAIN__|${DOMAIN}|" "$HERE/randomdrive.env.example" > /etc/randomdrive.env
  echo "   /etc/randomdrive.env 생성됨 — VWORLD_API_KEY를 채워 넣을 것"
fi
chown root:randomdrive /etc/randomdrive.env
chmod 640 /etc/randomdrive.env

echo "== systemd"
install -m 644 "$HERE/randomdrive.service" /etc/systemd/system/randomdrive.service
systemctl daemon-reload
systemctl enable randomdrive >/dev/null

echo "== caddy (HTTPS 자동 발급)"
sed "s|__DOMAIN__|${DOMAIN}|" "$HERE/Caddyfile" > /etc/caddy/Caddyfile
systemctl reload caddy || systemctl restart caddy

echo "== done. 다음: jar를 /opt/randomdrive/server.jar에 올리고 sudo systemctl restart randomdrive"
