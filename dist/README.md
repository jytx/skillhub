# dist/

构建产物占位目录。`build-push-images.sh` 每次构建完成后会把最终镜像 tag
（如 `2026.09.16-5`）写入 `dist/latest-tag`，`release-upgrade.sh` 在升级前
会读取该文件并校验私库确实存在对应 tag，自动同步到 `.env-release` 的
`SKILLHUB_VERSION`，避免「打了镜像但版本变量没跟上」导致容器仍跑旧版。

目录本身需要存在，但 `latest-tag` 文件本身不纳入版本控制——见 `.gitignore`。
