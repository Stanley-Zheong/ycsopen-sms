#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
cd "$REPO_ROOT"

BASE_REF="${REVIEW_BASE_REF:-}"
while [[ "$#" -gt 0 ]]; do
  case "$1" in
    --base)
      if [[ "$#" -lt 2 || -z "$2" ]]; then
        echo "ERROR: --base requires a git ref" >&2
        exit 2
      fi
      BASE_REF="$2"
      shift 2
      ;;
    *)
      echo "ERROR: unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [[ -z "$BASE_REF" ]]; then
  BASE_REF="$(git symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>/dev/null || true)"
fi
if [[ -z "$BASE_REF" ]] && git rev-parse --verify origin/main^{commit} >/dev/null 2>&1; then
  BASE_REF="origin/main"
fi
if [[ -z "$BASE_REF" ]] && git rev-parse --verify HEAD^ >/dev/null 2>&1; then
  BASE_REF="HEAD^"
fi
if [[ -z "$BASE_REF" ]] || ! git rev-parse --verify "$BASE_REF^{commit}" >/dev/null 2>&1; then
  echo "ERROR: cannot resolve review base; pass --base <ref>" >&2
  exit 2
fi
if ! git merge-base "$BASE_REF" HEAD >/dev/null 2>&1; then
  echo "ERROR: review base has no merge base with HEAD: $BASE_REF" >&2
  exit 2
fi

echo "[precheck] git diff whitespace base=$BASE_REF"
git diff --check "$BASE_REF...HEAD"
git diff --check
git diff --cached --check

echo "[precheck] repository-specific source scan"
if ! command -v rg >/dev/null 2>&1; then
  echo "ERROR: rg is required for repository source checks" >&2
  exit 2
fi
if [[ ! -d core/src ]]; then
  echo "ERROR: expected source directory is missing: core/src" >&2
  exit 2
fi
if rg -n '\bSystem\.(out|err)\.print|\.printStackTrace\s*\(' core/src --glob '*.java'; then
  echo "ERROR: direct stdout/stderr or printStackTrace found in Java source" >&2
  exit 1
else
  scan_status=$?
  if [[ "$scan_status" -ne 1 ]]; then
    echo "ERROR: rg source scan failed with exit $scan_status" >&2
    exit "$scan_status"
  fi
fi

echo "[precheck] PASS"
