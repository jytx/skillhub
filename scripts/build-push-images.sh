#!/usr/bin/env bash
#
# 构建并推送 skillhub 镜像到私有 Harbor 仓库（在 x86_64 服务器上运行）。
#
# server / web 从源码构建；Dockerfile 内基础镜像已指向 DaoCloud 国内加速源，
# Maven 依赖通过 BuildKit 缓存挂载跨构建复用。
# scanner 跟随上游镜像不单独构建：以私库 latest 为基准源，打上本次 tag 推回，
# 保证三个镜像版本号始终一致（compose 的 SKILLHUB_VERSION 同时作用于三者）。
#
# 用法:
#   ./scripts/build-push-images.sh [镜像tag] [all|server|web|scanner]
#   tag 传 auto 或省略时按「日期-当天序号」规则自动生成（查私库当天已有序号递增）
# 环境变量:
#   REGISTRY_PREFIX  私库前缀，默认 dockerhub.nobiliachina.com/common
#
set -euo pipefail

TAG="${1:-auto}"
TARGET="${2:-all}"
REGISTRY_PREFIX="${REGISTRY_PREFIX:-dockerhub.nobiliachina.com/common}"
SCANNER_UPSTREAM="ghcr.io/iflytek/skillhub-scanner:latest"

# 按「YYYY.MM.DD-序号」规则自动生成 tag：
# 以私库 server 镜像当天已推送的序号为基准递增，取第一个未占用的序号
generate_tag() {
  local today seq candidate
  today="$(date +%Y.%m.%d)"
  for seq in $(seq 1 99); do
    candidate="$today-$seq"
    if ! docker manifest inspect "$REGISTRY_PREFIX/skillhub-server:$candidate" >/dev/null 2>&1; then
      echo "$candidate"
      return
    fi
  done
  echo "!!! 当天构建序号已到 99，请显式指定 tag" >&2
  exit 1
}

if [[ "$TAG" == "auto" ]]; then
  TAG="$(generate_tag)"
  echo ">>> 自动生成 tag: $TAG"
fi

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

# 同步 scanner：私库 latest 为基准源；缺失时回落到本地/上游 ghcr 镜像并补齐 latest
sync_scanner() {
  local full_ref="$REGISTRY_PREFIX/skillhub-scanner:$TAG"
  local latest_ref="$REGISTRY_PREFIX/skillhub-scanner:latest"
  local source_ref

  if docker pull "$latest_ref" >/dev/null 2>&1; then
    source_ref="$latest_ref"
  elif docker image inspect "$SCANNER_UPSTREAM" >/dev/null 2>&1; then
    source_ref="$SCANNER_UPSTREAM"
  elif docker pull "$SCANNER_UPSTREAM" >/dev/null 2>&1; then
    source_ref="$SCANNER_UPSTREAM"
  else
    echo "!!! 找不到可用的 scanner 源镜像（私库 latest 与 $SCANNER_UPSTREAM 均不可用）" >&2
    exit 1
  fi

  echo ">>> 同步 scanner（源: $source_ref）..."
  docker tag "$source_ref" "$full_ref"
  docker push "$full_ref"
  # 基准源若来自 ghcr，顺带把私库 latest 补齐，后续同步即可完全走私库
  if [[ "$source_ref" == "$SCANNER_UPSTREAM" ]]; then
    docker tag "$source_ref" "$latest_ref"
    docker push "$latest_ref"
  fi
  echo ">>> 完成 $full_ref"
}

if [[ "$TARGET" == "all" || "$TARGET" == "server" ]]; then
  build_and_push server server
fi

if [[ "$TARGET" == "all" || "$TARGET" == "web" ]]; then
  build_and_push web web
fi

if [[ "$TARGET" == "all" || "$TARGET" == "scanner" ]]; then
  sync_scanner
fi
