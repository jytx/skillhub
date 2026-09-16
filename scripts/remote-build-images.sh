#!/usr/bin/env bash
#
# 本地一键打包：同步源码到构建服务器 → 远程构建并推送镜像到私库。
# 在本地 Mac 上运行（不在服务器上跑），Mac 上无需 Java/Node/Docker 构建环境。
#
# 用法:
#   ./scripts/remote-build-images.sh <镜像tag> [all|server|web]
# 环境变量:
#   REMOTE_HOST  构建服务器，默认 root@47.102.42.81
#   REMOTE_DIR   服务器构建目录，默认 /opt/skillhub-build
#   SSH_KEY      ssh 私钥，默认 ~/.ssh/id_rsa_legion
#
set -euo pipefail

TAG="${1:?用法: $0 <镜像tag> [all|server|web]}"
TARGET="${2:-all}"
REMOTE_HOST="${REMOTE_HOST:-root@47.102.42.81}"
REMOTE_DIR="${REMOTE_DIR:-/opt/skillhub-build}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_rsa_legion}"

# 项目根目录（脚本位于 <项目根>/scripts/）
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SSH_CMD=(ssh -i "$SSH_KEY" -o BatchMode=yes)

echo ">>> [1/2] 同步源码到 $REMOTE_HOST:$REMOTE_DIR ..."
rsync -az --delete -e "${SSH_CMD[*]}" \
  --exclude='.git' --exclude='node_modules' --exclude='target' --exclude='dist' \
  --exclude='.codegraph' --exclude='logs' --exclude='.venv' \
  "$PROJECT_ROOT/" "$REMOTE_HOST:$REMOTE_DIR/"

echo ">>> [2/2] 远程构建并推送 (tag=$TAG target=$TARGET) ..."
"${SSH_CMD[@]}" "$REMOTE_HOST" "cd $REMOTE_DIR && ./scripts/build-push-images.sh '$TAG' '$TARGET'"
