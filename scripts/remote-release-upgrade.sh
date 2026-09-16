#!/usr/bin/env bash
#
# 本地一键远程升级：在 Mac 上触发服务器端的 release-upgrade.sh。
# 升级自动跟随 dist/latest-tag（由 build-push-images.sh 构建时写入），
# 无需手动指定版本号。
#
# 用法:
#   ./scripts/remote-release-upgrade.sh
# 环境变量:
#   REMOTE_HOST  目标服务器，默认 root@47.102.42.81
#   REMOTE_DIR   服务器上部署目录，默认 /app/skillhub
#   SSH_KEY      ssh 私钥，默认 ~/.ssh/id_rsa_legion
#
set -euo pipefail

REMOTE_HOST="${REMOTE_HOST:-root@47.102.42.81}"
REMOTE_DIR="${REMOTE_DIR:-/app/skillhub}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_rsa_legion}"

echo ">>> 远程升级 ${REMOTE_HOST}:${REMOTE_DIR}（自动跟随 dist/latest-tag）..."
ssh -i "$SSH_KEY" -o BatchMode=yes "$REMOTE_HOST" "cd $REMOTE_DIR && bash scripts/release-upgrade.sh"
