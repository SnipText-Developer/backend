#!/bin/bash
# EC2 최초 부팅 때 한 번 실행된다 (Amazon Linux 2023 ARM).
# 도커·compose 설치, 스왑 2GB, /opt/sniptext 생성까지만 한다. 앱 실행은 deploy.sh(#92)가 맡는다.
# 로그: /var/log/cloud-init-output.log
set -euxo pipefail

# ---------- 도커 ----------
dnf install -y docker
systemctl enable --now docker

# compose 플러그인 (AL2023 저장소에 없어 릴리스 파일을 받는다. 버전·체크섬 고정)
COMPOSE_VERSION="v5.6.1"
COMPOSE_SHA256="42cfa75f51ad0ccf290bad37899e0038a8c10a0069e85233ecc1d53e3ff1347d"
PLUGIN_DIR=/usr/local/lib/docker/cli-plugins
mkdir -p "$PLUGIN_DIR"
curl -fsSL -o "$PLUGIN_DIR/docker-compose" \
  "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-aarch64"
echo "${COMPOSE_SHA256}  $PLUGIN_DIR/docker-compose" | sha256sum -c -
chmod 755 "$PLUGIN_DIR/docker-compose"
docker compose version

# ---------- 스왑 2GB ----------
# 메모리 2GB에 앱·Caddy가 함께 뜬다. 스왑이 계속 쓰이면 인스턴스 크기를 올린다
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi
echo 'vm.swappiness=10' > /etc/sysctl.d/90-swappiness.conf
sysctl -p /etc/sysctl.d/90-swappiness.conf

# ---------- 앱 디렉터리 ----------
# deploy.sh가 compose 파일·Caddyfile·.env(권한 600)를 이곳에 둔다
install -d -m 750 -o root -g root /opt/sniptext
