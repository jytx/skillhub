#!/usr/bin/env bash
#
# 本地一键远程回滚：回滚到指定历史版本。
# 版本号必须提供（参数或交互输入），未提供则拒绝执行，绝不猜默认值。
#
# 用法:
#   ./scripts/remote-rollback.sh <版本号>     # 例如 ./scripts/remote-rollback.sh 2026.09.16-3
#   ./scripts/remote-rollback.sh              # 无参数时交互式提示输入版本号
# 环境变量:
#   REMOTE_HOST  目标服务器，默认 root@47.102.42.81
#   REMOTE_DIR   服务器上部署目录，默认 /app/skillhub
#   SSH_KEY      ssh 私钥，默认 ~/.ssh/id_rsa_legion
#   REGISTRY_PREFIX  私库前缀，默认 dockerhub.nobiliachina.com/common
#
set -euo pipefail

REMOTE_HOST="${REMOTE_HOST:-root@47.102.42.81}"
REMOTE_DIR="${REMOTE_DIR:-/app/skillhub}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_rsa_legion}"
REGISTRY_PREFIX="${REGISTRY_PREFIX:-dockerhub.nobiliachina.com/common}"

TAG="${1:-}"
if [[ -z "$TAG" ]]; then
  read -r -p "请输入要回滚到的版本号（如 2026.09.16-3）: " TAG
fi
if [[ -z "$TAG" ]]; then
  echo "!!! 未输入版本号，已取消回滚（回滚操作绝不猜测默认版本）" >&2
  exit 1
fi

echo ">>> 校验私库中是否存在 ${REGISTRY_PREFIX}/skillhub-server:${TAG} ..."
ssh -i "$SSH_KEY" -o BatchMode=yes "$REMOTE_HOST" \
  "docker manifest inspect ${REGISTRY_PREFIX}/skillhub-server:${TAG}" >/dev/null 2>&1 \
  || { echo "!!! 私库中不存在版本 ${TAG}，已取消回滚" >&2; exit 1; }

echo ">>> 回滚 ${REMOTE_HOST}:${REMOTE_DIR} 到 ${TAG} ..."
ssh -i "$SSH_KEY" -o BatchMode=yes "$REMOTE_HOST" 'bash -s' -- "$REMOTE_DIR" "$TAG" <<'REMOTE_EOF'
set -euo pipefail
REMOTE_DIR="$1"
TAG="$2"
cd "$REMOTE_DIR"

# 版本指向三处同步：latest-tag 必须先改，否则升级脚本会把它再同步回新版本
echo "$TAG" > dist/latest-tag
sed -i.bak -E "s|^SKILLHUB_VERSION=.*|SKILLHUB_VERSION=$TAG|" .env.release
rm -f .env.release.bak
echo ">>> 版本已指向 $TAG，开始拉取旧镜像并重建容器..."
bash scripts/release-upgrade.sh
REMOTE_EOF

echo ">>> 回滚完成。当前版本: $TAG（如需再次前滚，重新构建或手写 dist/latest-tag 后跑 upgrade）"
