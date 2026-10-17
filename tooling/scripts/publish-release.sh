#!/usr/bin/env bash
# 通用 GitHub Release 一键发布包装脚本（跨平台：Git Bash / Linux / macOS）
#
# 用法（在仓库根目录）：
#   bash tooling/scripts/publish-release.sh <资产路径> <说明md路径> [资产显示名]
#
# 示例（BempDiff 风格）：
#   bash tooling/scripts/publish-release.sh \
#       release/BempDiff-0.1.2026100901-setup.exe \
#       release-notes-2026100901.md \
#       BempDiff-0.1.2026100901-setup.exe
#
# 说明：
#   - token 由 publish-release.py 自动从 git 凭据库读取，无需手动传入。
#   - 仓库坐标 / 标签 / 目标分支缺省自动推断，也可用环境变量覆盖：
#       GH_REPO=owner/name  GH_TAG=1.2.3  GH_TARGET=master
#       GH_DRAFT=1  GH_PRERELEASE=1
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY="${PYTHON:-python3}"

ASSET="${1:?用法: publish-release.sh <资产路径> <说明md路径> [资产显示名]}"
NOTES="${2:?缺少说明md路径}"
NAME="${3:-$(basename "$ASSET")}"

# 让 Python 用 UTF-8 读写（Windows 控制台默认 GBK 会炸中文说明）
export PYTHONIOENCODING=utf-8

exec "$PY" "$SCRIPT_DIR/publish-release.py" "$ASSET" "$NOTES" "$NAME"
