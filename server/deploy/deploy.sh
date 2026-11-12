#!/usr/bin/env bash
# 로컬에서 실행: jar를 빌드해 서버에 올리고 재시작한 뒤 상태를 확인한다.
#   server/deploy/deploy.sh <ssh 대상> <도메인> [ssh 키 파일]
#   예) server/deploy/deploy.sh ubuntu@3.35.10.20 3-35-10-20.sslip.io ~/.ssh/lightsail-seoul.pem
# 처음이면 --setup을 붙여 서버 준비(setup.sh)까지 한다:
#   server/deploy/deploy.sh ubuntu@3.35.10.20 3-35-10-20.sslip.io ~/.ssh/key.pem --setup
# 트립은 H2 파일 DB에 남으므로 배포(재시작)해도 공유 링크가 유지된다. 생성 중이던 리캡은 재시작 후 다시 만든다.
set -euo pipefail

TARGET="${1:?usage: deploy.sh <user@host> <domain> [key] [--setup]}"
DOMAIN="${2:?domain required}"
KEY="${3:-}"
SETUP="${4:-}"
if [ "$KEY" = "--setup" ]; then SETUP="--setup"; KEY=""; fi

SSH_OPTS=(-o StrictHostKeyChecking=accept-new)
[ -n "$KEY" ] && SSH_OPTS+=(-i "$KEY")

SERVER_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$SERVER_DIR"

echo "== build"
./gradlew -q bootJar
JAR="$(ls build/libs/*-SNAPSHOT.jar | grep -v plain | head -1)"

if [ "$SETUP" = "--setup" ]; then
  echo "== setup ($TARGET)"
  scp "${SSH_OPTS[@]}" -q deploy/setup.sh deploy/randomdrive.service deploy/Caddyfile deploy/randomdrive.env.example "$TARGET:/tmp/"
  ssh "${SSH_OPTS[@]}" "$TARGET" "mkdir -p /tmp/rd-deploy && mv /tmp/setup.sh /tmp/randomdrive.service /tmp/Caddyfile /tmp/randomdrive.env.example /tmp/rd-deploy/ && sudo bash /tmp/rd-deploy/setup.sh '$DOMAIN'"
fi

echo "== upload $JAR"
scp "${SSH_OPTS[@]}" -q "$JAR" "$TARGET:/tmp/server.jar"
ssh "${SSH_OPTS[@]}" "$TARGET" "sudo install -m 644 /tmp/server.jar /opt/randomdrive/server.jar && sudo systemctl restart randomdrive"

echo "== health"
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "https://$DOMAIN/r/AAAAAAAAAAAAAAAAAAAAAA" || true)
  if [ "$code" = "404" ]; then echo "   up: https://$DOMAIN (공유 페이지 404 안내 = 정상)"; exit 0; fi
  sleep 3
done
echo "   서버 응답 없음. 로그: ssh $TARGET 'sudo journalctl -u randomdrive -n 80 --no-pager'"
exit 1
