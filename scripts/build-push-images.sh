#!/usr/bin/env bash
#
# 构建并推送 skillhub 镜像到私有 Harbor 仓库（在 x86_64 服务器上运行）。
#
# Dockerfile 内基础镜像已指向 DaoCloud 国内加速源，无需访问 docker.io；
# Maven 依赖通过 BuildKit 缓存挂载跨构建复用，首次构建后速度会显著提升。
#
# 用法:
#   ./scripts/build-push-images.sh <镜像tag> [all|server|web]
# 环境变量:
#   REGISTRY_PREFIX  私库前缀，默认 dockerhub.nobiliachina.com/common
#
set -euo pipefail

TAG="${1:?用法: $0 <镜像tag> [all|server|web]}"
TARGET="${2:-all}"
REGISTRY_PREFIX="${REGISTRY_PREFIX:-dockerhub.nobiliachina.com/common}"

# RUN --mount 语法依赖 BuildKit；老版本 Docker 需显式开启
export DOCKER_BUILDKIT=1

build_and_push() {
  local name="$1"
  local dir="$2"
  local full_ref="$REGISTRY_PREFIX/skillhub-$name:$TAG"

  echo ">>> 构建 $full_ref ..."
  docker build -f "$dir/Dockerfile" -t "$full_ref" "$dir/"
  echo ">>> 推送 $full_ref ..."
  docker push "$full_ref"
  echo ">>> 完成 $full_ref"
}

if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
  build_and_push server server
fi

if [[ "$TARGET" == "all" || "$TARGET" == "web" ]]; then
  build_and_push web web
fi
