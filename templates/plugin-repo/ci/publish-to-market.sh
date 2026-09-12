#!/usr/bin/env sh
# 将构建好的插件 JAR 发布到自托管 YuDream 插件市场源。
#
# 默认发布本次选择的全部最终 JAR（tag 流水线为 release/plugins.txt；
# 本地未设置 PLUGIN_RELEASE_ONLY 时处理 dist/plugins 或 target 下全部包）。
# 也可显式传入 JAR 路径：publish-to-market.sh dist/plugins/foo-1.0.0.jar
#
# 环境变量：
#   YUDREAM_MARKET_URL            宿主根地址，如 https://yudream.example.com
#   YUDREAM_MARKET_API_KEY        具备 platform:plugin-market-source:upload 的 API Key
#   YUDREAM_MARKET_RELEASE_NOTES  可选，覆盖 store.json / 单行发布说明
#   YUDREAM_MARKET_CATEGORY       可选，内置分类清单值
#   YUDREAM_MARKET_TAGS           可选，逗号分隔标签
#   YUDREAM_MARKET_METADATA       可选，JSON 对象，与 store.json 的 license/compatibility 合并
#   DRY_RUN                       非空时只打印将要发布的条目，不请求
#
# 元数据优先从 JAR 内 plugin.yml 与 store.json 读取；{code}@{version} 不可覆盖。
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"
. "$ROOT_DIR/ci/lib/plugin-jar-selection.sh"

if [ -n "${CI_COMMIT_TAG:-}" ] && [ "${PLUGIN_RELEASE_ONLY:-}" != "1" ]; then
  PLUGIN_RELEASE_ONLY=1
  export PLUGIN_RELEASE_ONLY
fi

fail() {
  echo "[publish-to-market] $1" >&2
  exit 1
}

select_python() {
  if command -v python3 >/dev/null 2>&1 && python3 -c 'import json' >/dev/null 2>&1; then
    MARKET_PYTHON=python3
  elif command -v python >/dev/null 2>&1 && python -c 'import json' >/dev/null 2>&1; then
    MARKET_PYTHON=python
  else
    fail "python3 or python is required"
  fi
}

jar_yaml_value() {
  key=$1
  jar_path=$2
  unzip -p "$jar_path" plugin.yml 2>/dev/null \
    | sed -n "s/^${key}:[[:space:]]*//p" \
    | head -n 1 \
    | tr -d '\r' \
    | sed 's/[[:space:]]*#.*$//; s/^"//; s/"$//; s/^'"'"'//; s/'"'"'$//'
}

DRY_RUN="${DRY_RUN:-}"
if [ -z "$DRY_RUN" ]; then
  : "${YUDREAM_MARKET_URL:?YUDREAM_MARKET_URL is required}"
  : "${YUDREAM_MARKET_API_KEY:?YUDREAM_MARKET_API_KEY is required}"
  command -v curl >/dev/null 2>&1 || fail "curl is required"
fi
command -v unzip >/dev/null 2>&1 || fail "unzip is required to read plugin.yml / store.json"
select_python

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT INT TERM
JAR_LIST="$TMP_DIR/jars.txt"
: > "$JAR_LIST"

if [ "$#" -gt 0 ]; then
  for jar_path in "$@"; do
    [ -f "$jar_path" ] || fail "plugin jar not found: $jar_path"
    printf '%s\n' "$jar_path" >> "$JAR_LIST"
  done
else
  write_final_plugin_jars "$ROOT_DIR" "$JAR_LIST" || fail "no plugin jars found under dist/plugins or yudream-plugins/*/target"
fi

MARKET_HOST=$(printf '%s' "${YUDREAM_MARKET_URL:-https://yudream.example.com}" | sed 's#[/[:space:]]*$##')
PUBLISH_URL="$MARKET_HOST/api/platform/plugin-market-source/publications"
published=0

while IFS= read -r jar_path; do
  [ -n "$jar_path" ] || continue
  plugin_code=$(jar_yaml_value name "$jar_path")
  plugin_version=$(jar_yaml_value version "$jar_path")
  [ -n "$plugin_code" ] || fail "JAR plugin.yml name is required: $jar_path"
  [ -n "$plugin_version" ] || fail "JAR plugin.yml version is required: $jar_path"

  store_json="{}"
  if unzip -l "$jar_path" 2>/dev/null | grep -Eq '(^|[[:space:]])store\.json$'; then
    store_json=$(unzip -p "$jar_path" store.json 2>/dev/null || printf '%s' '{}')
  fi

  fields_file="$TMP_DIR/fields-$plugin_code-$plugin_version.json"
  "$MARKET_PYTHON" - "$store_json" "$fields_file" <<'PY'
import json
import os
import sys

raw_store, fields_file = sys.argv[1:3]
try:
    store = json.loads(raw_store) if raw_store.strip() else {}
except json.JSONDecodeError as error:
    raise SystemExit(f"invalid store.json: {error}")
if not isinstance(store, dict):
    raise SystemExit("store.json must be an object")

def one_line(value):
    text = " ".join(str(value).split())
    if any(ord(char) < 32 or ord(char) == 127 for char in text):
        raise SystemExit("releaseNotes must not contain control characters")
    return text

notes = os.environ.get("YUDREAM_MARKET_RELEASE_NOTES") or store.get("releaseNotes") or ""
if notes:
    notes = one_line(notes)
category = os.environ.get("YUDREAM_MARKET_CATEGORY") or store.get("category") or ""
tags = os.environ.get("YUDREAM_MARKET_TAGS")
if not tags and isinstance(store.get("tags"), list):
    tags = ",".join(str(item) for item in store["tags"] if str(item).strip())
elif not tags:
    tags = store.get("tags") or ""
tags = ",".join(part.strip() for part in str(tags).replace("，", ",").split(",") if part.strip())

metadata = {}
if isinstance(store.get("license"), str) and store["license"].strip():
    metadata["license"] = store["license"].strip()
if isinstance(store.get("compatibility"), dict):
    metadata["compatibility"] = store["compatibility"]
override = os.environ.get("YUDREAM_MARKET_METADATA") or ""
if override.strip():
    try:
        extra = json.loads(override)
    except json.JSONDecodeError as error:
        raise SystemExit(f"YUDREAM_MARKET_METADATA is not valid JSON: {error}")
    if not isinstance(extra, dict):
        raise SystemExit("YUDREAM_MARKET_METADATA must be a JSON object")
    metadata.update(extra)

with open(fields_file, "w", encoding="utf-8") as handle:
    json.dump({
        "notes": notes,
        "category": str(category),
        "tags": tags,
        "metadata": json.dumps(metadata, ensure_ascii=False, separators=(",", ":")),
    }, handle, ensure_ascii=False)
PY

  notes=$("$MARKET_PYTHON" -c 'import json,sys; print(json.load(open(sys.argv[1],encoding="utf-8"))["notes"])' "$fields_file")
  category=$("$MARKET_PYTHON" -c 'import json,sys; print(json.load(open(sys.argv[1],encoding="utf-8"))["category"])' "$fields_file")
  tags=$("$MARKET_PYTHON" -c 'import json,sys; print(json.load(open(sys.argv[1],encoding="utf-8"))["tags"])' "$fields_file")
  metadata=$("$MARKET_PYTHON" -c 'import json,sys; print(json.load(open(sys.argv[1],encoding="utf-8"))["metadata"])' "$fields_file")

  echo "[publish-to-market] $plugin_code@$plugin_version <- $jar_path"

  if [ -n "$DRY_RUN" ]; then
    echo "[publish-to-market] dry-run POST $PUBLISH_URL"
    [ -z "$notes" ] || echo "[publish-to-market] releaseNotes=$notes"
    [ -z "$category" ] || echo "[publish-to-market] category=$category"
    [ -z "$tags" ] || echo "[publish-to-market] tags=$tags"
    [ "$metadata" = "{}" ] || echo "[publish-to-market] metadata=$metadata"
    published=$((published + 1))
    continue
  fi

  response_file="$TMP_DIR/response-$plugin_code-$plugin_version.json"
  set -- curl --fail-with-body -sS -X POST "$PUBLISH_URL" \
    -H "X-API-Key: $YUDREAM_MARKET_API_KEY" \
    -H "Accept: application/json" \
    -F "file=@$jar_path"
  [ -z "$notes" ] || set -- "$@" -F "releaseNotes=$notes"
  [ -z "$category" ] || set -- "$@" -F "category=$category"
  [ -z "$tags" ] || set -- "$@" -F "tags=$tags"
  [ "$metadata" = "{}" ] || set -- "$@" -F "metadata=$metadata"
  if ! "$@" -o "$response_file"; then
    echo "[publish-to-market] upload failed for $plugin_code@$plugin_version" >&2
    cat "$response_file" >&2 || true
    fail "market publish HTTP error: $plugin_code@$plugin_version"
  fi
  "$MARKET_PYTHON" - "$response_file" "$plugin_code" "$plugin_version" <<'PY'
import json
import sys

path, code, version = sys.argv[1:4]
try:
    payload = json.load(open(path, encoding="utf-8"))
except json.JSONDecodeError as error:
    raise SystemExit(f"market publish returned non-JSON: {error}")
if not isinstance(payload, dict):
    raise SystemExit("market publish returned an unexpected payload")
status_code = payload.get("code")
if status_code not in (200, "200", None):
    message = payload.get("message") or path
    raise SystemExit(f"market publish rejected {code}@{version}: {message}")
data = payload.get("data") if "data" in payload else payload
if isinstance(data, dict):
    actual_code = data.get("code") or code
    actual_version = data.get("pluginVersion") or version
    status = data.get("status") or "UNKNOWN"
    print(f"[publish-to-market] {actual_code}@{actual_version} status={status}")
else:
    print(f"[publish-to-market] {code}@{version} accepted")
PY
  published=$((published + 1))
done < "$JAR_LIST"

[ "$published" -gt 0 ] || fail "no plugin jars were published"
echo "[publish-to-market] published $published plugin(s) to $MARKET_HOST"
