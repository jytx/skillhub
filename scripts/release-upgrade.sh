#!/usr/bin/env bash
#
# Upgrade the SkillHub single-host release stack (compose.release.yml):
# pull latest images, recreate containers, verify health. Data volumes
# (postgres_data / redis_data / skillhub_storage) are preserved.
#
# Usage:
#   scripts/release-upgrade.sh
#
# Requires .env.release to exist (run scripts/release-up.sh once, or create
# it manually). Image registry is controlled by SKILLHUB_*_IMAGE variables
# in .env.release — point them at a mirror if GHCR is slow from your host.
#
# Override paths via env if needed:
#   COMPOSE_FILE=... ENV_FILE=... scripts/release-upgrade.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/compose.release.yml}"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.release}"

# --- docker compose (v2 preferred, v1 fallback) -----------------------------
if docker compose version >/dev/null 2>&1; then
  DC=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  DC=(docker-compose)
else
  echo "ERROR: docker compose (v2) or docker-compose (v1) not found." >&2
  exit 1
fi

[[ -f "$COMPOSE_FILE" ]] || { echo "ERROR: compose file not found: $COMPOSE_FILE" >&2; exit 1; }
[[ -f "$ENV_FILE" ]] || { echo "ERROR: $ENV_FILE not found. Run scripts/release-up.sh first." >&2; exit 1; }

cd "$ROOT_DIR"

get_env_value() {  # key -> value (last occurrence); empty + rc 0 if missing
  grep "^${1}=" "$ENV_FILE" 2>/dev/null | tail -n1 | cut -d= -f2- || true
}

running_digest() {  # service -> image digest currently used by its container
  local image
  image="$("${DC[@]}" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps -aq "$1" \
    | head -n1 \
    | xargs -r docker inspect -f '{{.Config.Image}}' 2>/dev/null)" || true
  [[ -n "$image" ]] || return 0
  docker images --digests --no-trunc --format '{{.Repository}}:{{.Tag}} {{.Digest}}' \
    | grep -F "$image" | head -n1 || true
}

# --- 自动跟随最新构建产物（dist/latest-tag） --------------------------------
# 若 $ROOT_DIR/dist/latest-tag 存在（由 build-push-images.sh 写入）且与
# $ENV_FILE 的 SKILLHUB_VERSION 不一致，先用 docker manifest inspect 校验
# 私库确实存在该 tag（直接询问注册表，不依赖本地镜像缓存），通过后自动同步，
# 避免"打了镜像但版本变量没跟上"导致容器仍跑旧版。
# 注意：本段必须在 get_env_value 定义之后执行。
TAG_FILE="${TAG_FILE:-$ROOT_DIR/dist/latest-tag}"
if [[ -f "$TAG_FILE" ]]; then
  pinned_tag="$(tr -d '[:space:]' < "$TAG_FILE")"
  current_tag="$(get_env_value SKILLHUB_VERSION)"
  if [[ -n "$pinned_tag" && "$pinned_tag" != "$current_tag" ]]; then
    if docker manifest inspect "dockerhub.nobiliachina.com/common/skillhub-server:$pinned_tag" >/dev/null 2>&1; then
      sed -i.bak -E "s|^SKILLHUB_VERSION=.*|SKILLHUB_VERSION=$pinned_tag|" "$ENV_FILE"
      rm -f "$ENV_FILE.bak"
      echo "==> Pinned SKILLHUB_VERSION=$pinned_tag from $TAG_FILE (synced to $ENV_FILE)"
    else
      echo "WARN: tag $pinned_tag not present in registry; keep current SKILLHUB_VERSION." >&2
    fi
  fi
fi

# --- snapshot before upgrade -------------------------------------------------
BEFORE="$(for svc in server web skill-scanner; do
  printf '%s %s\n' "$svc" "$(running_digest "$svc" | awk '{print $2}')"
done)"
echo "==> Images in use before upgrade:"
echo "$BEFORE" | sed 's/^/    /'

# --- pull + recreate ---------------------------------------------------------
echo "==> Pulling latest images (registry per SKILLHUB_*_IMAGE in $ENV_FILE)..."
"${DC[@]}" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" pull

echo "==> Recreating containers..."
"${DC[@]}" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d

# --- verify ------------------------------------------------------------------
web_port="$(get_env_value WEB_PORT)"; web_port="${web_port:-1080}"
api_port="$(get_env_value API_PORT)"; api_port="${api_port:-1081}"

echo "==> Waiting for endpoints (up to ~90s)..."
ready=0
for _ in $(seq 1 30); do
  if curl -fsS -o /dev/null --max-time 3 "http://127.0.0.1:${api_port}/actuator/health" \
     && curl -fsS -o /dev/null --max-time 3 "http://127.0.0.1:${web_port}/"; then
    ready=1; break
  fi
  sleep 3
done

echo
"${DC[@]}" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps
echo

AFTER="$(for svc in server web skill-scanner; do
  printf '%s %s\n' "$svc" "$(running_digest "$svc" | awk '{print $2}')"
done)"

changed=0
while read -r line; do
  svc="${line%% *}"
  if ! grep -qF "$line" <<<"$AFTER"; then
    echo "UPGRADED: $svc now ${line#* }"
    changed=1
  fi
done <<<"$BEFORE"
[[ "$changed" -eq 1 ]] || echo "INFO: images unchanged (already latest)."

if [[ "$ready" -eq 1 ]]; then
  echo "OK: SkillHub upgraded and healthy."
else
  echo "WARN: containers recreated but endpoints not ready yet." >&2
  echo "      Inspect logs: ${DC[*]} --env-file $ENV_FILE -f $COMPOSE_FILE logs -f server" >&2
  exit 1
fi
echo "Web UI      : http://localhost:${web_port}"
echo "Backend API : http://localhost:${api_port}"
echo "Tip: reclaim old images with 'docker image prune -f'."
