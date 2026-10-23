#!/bin/sh
# ci/publish-host-images.sh —— 宿主 backend/frontend 镜像构建 + 双仓（Harbor/阿里云 ACR）发布脚本。
#
# 仅依赖 sh（POSIX/busybox ash）+ jq + docker CLI；不假定 python/node/skopeo。
# CI 侧（docker:cli 镜像）需 `apk add --no-cache jq`（先切阿里云 apk 镜像，官方源国内不可达）；作业必须保留
# resource_group: yudreamadmin-images（外部串行化，防多 tag 流水线竞争可变标签）。
#
# 输入（环境变量，无命令行参数）：
#   必填：
#     HOST_BUILD_VERSION        发布版本（GitLab 作业层取 $CI_COMMIT_TAG 赋值），严格格式：
#                               正式 vX.Y.Z；候选 vX.Y.Z-rc.N；开发 dX.Y.Z（禁前导零；
#                               后缀重发形态 vX.Y.Z-N 已废弃，不再进入正式通道）
#     HOST_BUILD_COMMIT         完整 commit（缺省回落 CI_COMMIT_SHA）
#   可选：
#     HOST_BUILD_COMMIT_SHORT   短 commit（缺省回落 CI_COMMIT_SHORT_SHA / 取前 8 位）
#     HARBOR_REGISTRY / HARBOR_PROJECT           默认 registry.yudream.online / yda
#     DOCKER_REGISTRY_USER / DOCKER_REGISTRY_PASSWORD   Harbor robot 凭据（必填）
#     ALIYUN_PUSH_ENABLED       默认 true；正式通道必须为 true（双仓强制）
#     ALIYUN_REGISTRY / ALIYUN_REGISTRY_PROJECT  默认 ACR 个人版域名 / yudream
#     ALIYUN_REGISTRY_USER / ALIYUN_REGISTRY_PASSWORD   阿里云凭据（启用双仓时必填）
#     ALLOW_LATEST_MIGRATION    =1 时才允许覆盖「无 version 标签/非正式通道标签」的历史 latest
#                               （迁移需人工明确批准，脚本不自动猜测）
#     RELEASE_EVIDENCE_FILE     发布证据 JSON 路径，默认 <BUILD_CONTEXT>/dist/images/release-evidence.json
#     BUILD_CONTEXT / BACKEND_DOCKERFILE / FRONTEND_DOCKERFILE / BACKEND_IMAGE_NAME / FRONTEND_IMAGE_NAME
#
# 行为要点：
#   - 正式(v*)才允许晋升 latest；rc/d* 一律不碰 latest（修复旧脚本 d* 推 latest 的问题）。
#   - 不可变标签（精确版本、短 SHA）推送前先检查远端：已存在但 revision 不同 → 拒绝覆盖；
#     同 commit → 复用远端制品重试（不重建，杜绝构建漂移）；任何内容（摘要）不一致 → 拒绝。
#   - 防倒退：晋升 latest 前读取两仓 latest 的 OCI version 标签；网络/鉴权错误一律 fail closed，
#     只有明确 not found 视为空；历史 latest 无 version 标签或为非正式版本 → fail closed，
#     需人工批准迁移（ALLOW_LATEST_MIGRATION=1）。
#   - 正式通道必须双仓都完成验证（镜像 ID + 摘要证据一致）才写发布证据 JSON 并晋升 latest；
#     partial 的 latest 更新只允许写 status=partial 的证据，不宣告 complete，进程以非 0 退出。
#   - OCI 标签：org.opencontainers.image.version=HOST_BUILD_VERSION、
#               org.opencontainers.image.revision=完整 commit。
set -eu

LOG_PREFIX='[publish-host-images]'
log()  { printf '%s %s\n' "$LOG_PREFIX" "$*"; }
warn() { printf '%s [警告] %s\n' "$LOG_PREFIX" "$*" >&2; }
die()  { printf '%s [失败] %s\n' "$LOG_PREFIX" "$*" >&2; exit 1; }

# 捕获命令合并输出：LAST_OUT=输出，LAST_RC=退出码；函数返回值即命令退出码（供 if 使用）
cap_out() {
  LAST_RC=0
  LAST_OUT=$("$@" 2>&1) || LAST_RC=$?
  return "$LAST_RC"
}

extract_digest() {
  # 从 docker push/pull 输出提取 `digest: sha256:...` 证据（取最后一次出现）
  printf '%s' "$1" | sed -n 's/.*[Dd]igest: \(sha256:[0-9a-fA-F]\{64\}\).*/\1/p' | tail -n 1
}

# 拉取结果分类：OK / ABSENT（明确 not found）/ FATAL（网络、鉴权等一切非明确缺失 → fail closed）
classify_pull() {
  if [ "$1" -eq 0 ]; then
    printf 'OK'
  elif printf '%s' "$2" | grep -qiE 'manifest unknown|not found|no such|repository does not exist'; then
    printf 'ABSENT'
  else
    printf 'FATAL'
  fi
}

parse_rank() {
  # 版本标签 → {channel, triple, rebuild}；无法识别 → channel=invalid
  # 严格 semver（禁前导零）；后缀重发形态 vX.Y.Z-N 已废弃，不再识别为正式通道
  jq -Rn --arg v "$1" '
    if   ($v|test("^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")) then
      {channel:"stable", triple:($v|.[1:]), rebuild:"0"}
    elif ($v|test("^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)-rc\\.[1-9][0-9]*$")) then
      ($v|.[1:]|split("-")) as $p | {channel:"rc", triple:$p[0], rebuild:"0"}
    elif ($v|test("^d(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")) then
      {channel:"dev", triple:($v|.[1:]), rebuild:"0"}
    else {channel:"invalid", triple:"0.0.0", rebuild:"0"} end'
}

rank_cmp() {
  # 比较 {triple,rebuild} 两个 rank：1 新于 / 0 相同 / -1 旧于
  jq -n --arg a "$1" --arg b "$2" '
    ($a|fromjson) as $x | ($b|fromjson) as $y |
    ($x.triple|split(".")|map(tonumber)) as $xt | ($y.triple|split(".")|map(tonumber)) as $yt |
    ($x.rebuild|tonumber) as $xn | ($y.rebuild|tonumber) as $yn |
    if $xt > $yt then 1 elif $xt < $yt then -1
    elif $xn > $yn then 1 elif $xn < $yn then -1 else 0 end'
}

# ---------- 1. 输入与依赖 ----------
require_cmds() {
  for c in docker jq; do
    command -v "$c" >/dev/null 2>&1 || die "缺少依赖命令: $c（CI docker:cli 作业请先 apk add --no-cache jq）"
  done
}

BUILD_CONTEXT=${BUILD_CONTEXT:-$(cd "$(dirname "$0")/.." && pwd)}
[ -d "$BUILD_CONTEXT" ] || die "构建上下文不存在: $BUILD_CONTEXT"

[ -n "${HOST_BUILD_VERSION:-}" ] || die '缺少 HOST_BUILD_VERSION（CI 作业层应赋值为 $CI_COMMIT_TAG）'
COMMIT_FULL=${HOST_BUILD_COMMIT:-${CI_COMMIT_SHA:-}}
[ -n "$COMMIT_FULL" ] || die '缺少 HOST_BUILD_COMMIT（或 CI_COMMIT_SHA）'
COMMIT_SHORT=${HOST_BUILD_COMMIT_SHORT:-${CI_COMMIT_SHORT_SHA:-}}
if [ -z "$COMMIT_SHORT" ]; then
  COMMIT_SHORT=$(printf '%s' "$COMMIT_FULL" | cut -c1-8)
fi

HARBOR_HOST=${HARBOR_REGISTRY:-registry.yudream.online}
HARBOR_PROJECT=${HARBOR_PROJECT:-yda}
BACKEND_NAME=${BACKEND_IMAGE_NAME:-backend}
FRONTEND_NAME=${FRONTEND_IMAGE_NAME:-frontend}
BACKEND_DOCKERFILE=${BACKEND_DOCKERFILE:-Dockerfile.backend}
FRONTEND_DOCKERFILE=${FRONTEND_DOCKERFILE:-Dockerfile.frontend}
ALIYUN_PUSH_ENABLED=${ALIYUN_PUSH_ENABLED:-true}
if [ "$ALIYUN_PUSH_ENABLED" = "true" ]; then ALIYUN_ON=1; else ALIYUN_ON=0; fi
ALIYUN_HOST=${ALIYUN_REGISTRY:-crpi-bavv47zo78j1v6cv.cn-hangzhou.personal.cr.aliyuncs.com}
ALIYUN_PROJECT=${ALIYUN_REGISTRY_PROJECT:-yudream}
RELEASE_EVIDENCE_FILE=${RELEASE_EVIDENCE_FILE:-dist/images/release-evidence.json}

# ---------- 2. 版本通道严格分类（不猜测） ----------
CLS_JSON=$(jq -Rn --arg v "$HOST_BUILD_VERSION" '
  if   ($v|test("^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")) then {ok:true, channel:"stable", num:($v|.[1:])}
  elif ($v|test("^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)-rc\\.[1-9][0-9]*$")) then {ok:true, channel:"rc", num:($v|.[1:])}
  elif ($v|test("^d(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")) then {ok:true, channel:"dev", num:($v|.[1:])}
  else {ok:false, channel:"invalid", num:""} end') || die 'HOST_BUILD_VERSION 分类失败'
[ "$(printf '%s' "$CLS_JSON" | jq -r .ok)" = "true" ] || \
  die "无法识别的 HOST_BUILD_VERSION='$HOST_BUILD_VERSION'；仅支持：正式 vX.Y.Z、候选 vX.Y.Z-rc.N、开发 dX.Y.Z（严格 semver，禁前导零；后缀重发形态已废弃）"
CHANNEL=$(printf '%s' "$CLS_JSON" | jq -r .channel)
VER_NUM=$(printf '%s' "$CLS_JSON" | jq -r .num)

# 不可变「精确版本标签」：正式=去 v 前缀；候选含 -rc.N；开发加 -dev 后缀避免与正式版本号互占
case "$CHANNEL" in
  stable) VER_TAG=$VER_NUM ;;
  rc)     VER_TAG=$VER_NUM ;;
  dev)    VER_TAG="$VER_NUM-dev" ;;
esac
CUR_RANK=$(parse_rank "$HOST_BUILD_VERSION")

log "版本=$HOST_BUILD_VERSION 通道=$CHANNEL 版本标签=$VER_TAG commit=$COMMIT_SHORT（$COMMIT_FULL）"

if [ "$CHANNEL" = "stable" ] && [ "$ALIYUN_ON" != "1" ]; then
  die '正式通道要求双仓（Harbor + 阿里云）启用：ALIYUN_PUSH_ENABLED=false 时禁止发布正式版本'
fi

slot_enabled() {
  case "$1" in
    harbor) return 0 ;;
    aliyun) [ "$ALIYUN_ON" = "1" ] ;;
    *) return 1 ;;
  esac
}
slot_server() {
  case "$1" in
    harbor) printf '%s' "$HARBOR_HOST" ;;
    aliyun) printf '%s' "$ALIYUN_HOST" ;;
  esac
}
slot_project() {
  case "$1" in
    harbor) printf '%s' "$HARBOR_PROJECT" ;;
    aliyun) printf '%s' "$ALIYUN_PROJECT" ;;
  esac
}
slot_ref() { # slot image tag
  printf '%s/%s/%s:%s' "$(slot_server "$1")" "$(slot_project "$1")" "$2" "$3"
}
tagkey_tag() {
  case "$1" in
    version) printf '%s' "$VER_TAG" ;;
    sha)     printf '%s' "$COMMIT_SHORT" ;;
    latest)  printf 'latest' ;;
  esac
}

# ---------- 3. 临时工作区 ----------
WORK=$(mktemp -d "${TMPDIR:-/tmp}/yd-publish-host-images.XXXXXX") || die '无法创建临时目录'
cleanup() { rm -rf "$WORK"; }
trap cleanup EXIT
REC_DIR="$WORK/rec"        # 拉取到的远端已有标签记录
PUSH_DIR="$WORK/pushed"    # 本次推送记录
STATE_DIR="$WORK/state"
IMG_DIR="$WORK/img"
LATEST_DIR="$WORK/latest"
mkdir -p "$REC_DIR" "$PUSH_DIR" "$STATE_DIR" "$IMG_DIR" "$LATEST_DIR"

WARNINGS_TXT="$WORK/warnings.txt"
: > "$WARNINGS_TXT"
add_warning() { printf '%s\n' "$1" >> "$WARNINGS_TXT"; }

# ---------- 4. 登录（先于一切远端读取，鉴权问题尽早失败） ----------
login_slot() { # slot
  case "$1" in
    harbor) srv=$HARBOR_HOST; user=${DOCKER_REGISTRY_USER:-}; pass=${DOCKER_REGISTRY_PASSWORD:-} ;;
    aliyun) srv=$ALIYUN_HOST; user=${ALIYUN_REGISTRY_USER:-}; pass=${ALIYUN_REGISTRY_PASSWORD:-} ;;
  esac
  if [ -z "$user" ] || [ -z "$pass" ]; then
    die "镜像仓库 $1（$srv）缺少凭据（Harbor: DOCKER_REGISTRY_USER/DOCKER_REGISTRY_PASSWORD；阿里云: ALIYUN_REGISTRY_USER/ALIYUN_REGISTRY_PASSWORD）"
  fi
  # 密码必须经 stdin 传入：CI 作业 stdin 无数据，--password-stdin 读空后会回退交互式登录并在 non-TTY 下报错
  if ! LAST_OUT=$(printf '%s\n' "$pass" | docker login "$srv" -u "$user" --password-stdin 2>&1); then
    die "docker login $srv（$1）失败：$(printf '%s' "$LAST_OUT" | tail -n 1)"
  fi
  log "已登录 $1（$srv）"
}

# ---------- 5. 前置检查：远端不可变标签占用情况（先查后建，避免覆盖/漂移） ----------
record_pull() { # slot image tagkey → 记录已存在标签的内容；不存在返回 1
  lp_ref=$(slot_ref "$1" "$2" "$(tagkey_tag "$3")")
  cap_out docker pull "$lp_ref"
  lp_cls=$(classify_pull "$LAST_RC" "$LAST_OUT")
  case "$lp_cls" in
    ABSENT) return 1 ;;
    FATAL) die "远端标签检查失败（fail-closed，仅明确 not found 视为不存在）: $lp_ref : $(printf '%s' "$LAST_OUT" | tail -n 1)" ;;
  esac
  lp_digest=$(extract_digest "$LAST_OUT")
  [ -n "$lp_digest" ] || die "拉取输出缺少摘要证据: $lp_ref"
  lp_labels=$(docker image inspect --format '{{json .Config.Labels}}' "$lp_ref") || \
    die "无法读取镜像标签: $lp_ref"
  lp_rev=$(printf '%s' "$lp_labels" | jq -r '.["org.opencontainers.image.revision"] // ""')
  lp_ver=$(printf '%s' "$lp_labels" | jq -r '.["org.opencontainers.image.version"] // ""')
  lp_id=$(docker image inspect --format '{{.Id}}' "$lp_ref") || die "无法读取镜像 ID: $lp_ref"
  if [ -z "$lp_rev" ]; then
    die "不可变标签已存在但缺少 revision（OCI revision）标签，无法确认属于本 commit，拒绝处理（需人工确认后删除遗留标签）: $lp_ref"
  fi
  if [ "$lp_rev" != "$COMMIT_FULL" ]; then
    die "不可变标签已被不同 commit 占用，禁止覆盖: $lp_ref 现有 revision=$lp_rev 当前=$COMMIT_FULL"
  fi
  jq -n --arg ref "$lp_ref" --arg digest "$lp_digest" --arg revision "$lp_rev" \
        --arg versionLabel "$lp_ver" --arg imageId "$lp_id" \
        '{ref:$ref,digest:$digest,revision:$revision,versionLabel:$versionLabel,imageId:$imageId}' \
    > "$REC_DIR/$1.$2.$3.json"
  log "远端已存在（同 commit，复用）: $lp_ref digest=$lp_digest"
}

check_digest_consistency() { # image —— 已有标签之间摘要必须一致（同 commit 内容漂移防护）
  cd_ref_digest=""
  for f in "$REC_DIR"/*."$1".*.json; do
    [ -f "$f" ] || continue
    cd_d=$(jq -r .digest "$f")
    if [ -z "$cd_ref_digest" ]; then
      cd_ref_digest=$cd_d
    elif [ "$cd_d" != "$cd_ref_digest" ]; then
      die "已有不可变标签内容（摘要）不一致，拒绝发布: $1 — $f digest=$cd_d 基准=$cd_ref_digest"
    fi
  done
}

# ---------- 6. 上游制品检查（仅真实构建时） ----------
require_build_input() {
  case "$1" in
    backend)
      set -- "$BUILD_CONTEXT"/yudream-bootstrap/target/yudream-bootstrap-*.jar
      [ -f "$1" ] || die "缺少上游后端构建产物: yudream-bootstrap/target/yudream-bootstrap-*.jar（build:backend 阶段）"
      [ -f "$BUILD_CONTEXT/yudream-frontend/apps/core-arco-design-vue/dist/index.html" ] || \
        die '缺少前端 index.html（backend 镜像 SEO 壳依赖，build:frontend-main 阶段）'
      ;;
    frontend)
      [ -d "$BUILD_CONTEXT/yudream-frontend/apps/core-arco-design-vue/dist" ] || \
        die '缺少前端构建产物: yudream-frontend/apps/core-arco-design-vue/dist（build:frontend-main 阶段）'
      ;;
  esac
}

# ---------- 7. 制品来源选择：远端复用 > 本地同 commit 复用 > 新构建 ----------
select_source() { # image
  ss_src='' ; ss_how='' ; ss_id=''
  for key in harbor.version harbor.sha aliyun.version aliyun.sha; do
    ss_slot=${key%%.*} ; ss_tk=${key#*.}
    ss_f="$REC_DIR/$ss_slot.$1.$ss_tk.json"
    [ -f "$ss_f" ] || continue
    ss_src=$(jq -r .ref "$ss_f")
    ss_id=$(jq -r .imageId "$ss_f")
    ss_how="reuse:$ss_src"
    break
  done
  if [ -z "$ss_src" ]; then
    # 无远端制品：本地已有同 commit 镜像则复用（重试场景），否则构建
    ss_cand=$(slot_ref harbor "$1" "$(tagkey_tag version)")
    if cap_out docker image inspect --format '{{json .Config.Labels}}' "$ss_cand"; then
      ss_rev=$(printf '%s' "$LAST_OUT" | jq -r '.["org.opencontainers.image.revision"] // ""')
      if [ "$ss_rev" = "$COMMIT_FULL" ]; then
        ss_src=$ss_cand
        ss_id=$(docker image inspect --format '{{.Id}}' "$ss_cand") || die "无法读取镜像 ID: $ss_cand"
        ss_how="reuse-local:$ss_cand"
      fi
    fi
  fi
  if [ -z "$ss_src" ]; then
    require_build_input "$1"
    case "$1" in
      backend)  ss_df=$BACKEND_DOCKERFILE ;;
      frontend) ss_df=$FRONTEND_DOCKERFILE ;;
    esac
    ss_cand=$(slot_ref harbor "$1" "$(tagkey_tag version)")
    log "构建 $1 镜像（$ss_df，labels: version=$HOST_BUILD_VERSION revision=${COMMIT_FULL}）"
    if ! cap_out docker build -f "$ss_df" \
        --label "org.opencontainers.image.version=$HOST_BUILD_VERSION" \
        --label "org.opencontainers.image.revision=$COMMIT_FULL" \
        -t "$ss_cand" "$BUILD_CONTEXT"; then
      die "docker build 失败: $1 : $(printf '%s' "$LAST_OUT" | tail -n 5)"
    fi
    ss_src=$ss_cand
    ss_id=$(docker image inspect --format '{{.Id}}' "$ss_cand") || die "无法读取镜像 ID: $ss_cand"
    ss_how="build:$ss_cand"
    ss_rev=$(docker image inspect --format '{{json .Config.Labels}}' "$ss_cand" | \
             jq -r '.["org.opencontainers.image.revision"] // ""')
    [ "$ss_rev" = "$COMMIT_FULL" ] || die "新构建镜像 revision 标签异常: $ss_cand（$ss_rev）"
  fi
  jq -n --arg source "$ss_how" --arg sourceRef "$ss_src" --arg imageId "$ss_id" \
        '{source:$source, sourceRef:$sourceRef, imageId:$imageId}' > "$STATE_DIR/$1.meta.json"
  log "$1 制品来源: $ss_how imageId=$ss_id"
}

# ---------- 8. 推送缺失标签（已有同内容标签跳过，保证幂等重试） ----------
ensure_slot_tag() { # slot image tagkey
  if [ -f "$REC_DIR/$1.$2.$3.json" ]; then
    return 0   # 远端已有同内容标签，不重复推送
  fi
  et_src=$(jq -r .sourceRef "$STATE_DIR/$2.meta.json")
  et_tag=$(tagkey_tag "$3")
  et_dst=$(slot_ref "$1" "$2" "$et_tag")
  docker tag "$et_src" "$et_dst"
  if ! cap_out docker push "$et_dst"; then
    die "推送失败: $et_dst : $(printf '%s' "$LAST_OUT" | tail -n 3)"
  fi
  et_digest=$(extract_digest "$LAST_OUT")
  [ -n "$et_digest" ] || die "推送输出缺少摘要证据（fail-closed）: $et_dst"
  et_id=$(docker image inspect --format '{{.Id}}' "$et_dst") || die "无法读取镜像 ID: $et_dst"
  jq -n --arg ref "$et_dst" --arg digest "$et_digest" --arg imageId "$et_id" \
        '{ref:$ref,digest:$digest,imageId:$imageId,action:"pushed"}' > "$PUSH_DIR/$1.$2.$3.json"
  log "已推送: $et_dst digest=$et_digest"
}

verify_cross_slot() { # image tagkey —— 双仓同标签摘要必须一致
  vc_digest=''
  for vc_slot in harbor aliyun; do
    slot_enabled "$vc_slot" || continue
    vc_f="$PUSH_DIR/$vc_slot.$1.$2.json"
    [ -f "$vc_f" ] || vc_f="$REC_DIR/$vc_slot.$1.$2.json"
    [ -f "$vc_f" ] || die "内部错误：缺少标签记录 $vc_slot/$1/$2"
    vc_d=$(jq -r .digest "$vc_f")
    if [ -z "$vc_digest" ]; then
      vc_digest=$vc_d
    elif [ "$vc_d" != "$vc_digest" ]; then
      die "双仓摘要不一致（内容校验失败）: $1 $(tagkey_tag "$2") — $vc_slot=$vc_d 基准=$vc_digest"
    fi
  done
}

# ---------- 9. latest 防倒退检查（fail closed） ----------
migration_gate() { # mg_msg → 0=放行（需显式批准） 1=阻止
  if [ "${ALLOW_LATEST_MIGRATION:-0}" = "1" ]; then
    warn "$1 —— 已按人工批准放行（ALLOW_LATEST_MIGRATION=1）"
    add_warning "$1（已人工批准迁移 ALLOW_LATEST_MIGRATION=1）"
    return 0
  fi
  LATEST_BLOCK_ERR="$1；迁移需人工明确批准：确认后设置 ALLOW_LATEST_MIGRATION=1 重跑（脚本不自动猜测）"
  return 1
}

latest_precheck() { # slot image → 0=可晋升；1=阻止（LATEST_BLOCK_ERR）
  lp_ref=$(slot_ref "$1" "$2" latest)
  cap_out docker pull "$lp_ref"
  lp_cls=$(classify_pull "$LAST_RC" "$LAST_OUT")
  case "$lp_cls" in
    ABSENT)
      jq -n '{attempted:true, promoted:false, previousVersionLabel:"", previousDigest:""}' \
        > "$LATEST_DIR/$1.$2.precheck.json"
      return 0 ;;
    FATAL)
      LATEST_BLOCK_ERR="远端 latest 版本读取失败（网络/鉴权错误 fail-closed，不视为空）: $lp_ref : $(printf '%s' "$LAST_OUT" | tail -n 1)"
      return 1 ;;
  esac
  lp_digest=$(extract_digest "$LAST_OUT")
  lp_labels=$(docker image inspect --format '{{json .Config.Labels}}' "$lp_ref") || {
    LATEST_BLOCK_ERR="无法读取 latest 标签: $lp_ref"; return 1; }
  lp_ver=$(printf '%s' "$lp_labels" | jq -r '.["org.opencontainers.image.version"] // ""')
  if [ -z "$lp_ver" ]; then
    if ! migration_gate "远端 latest 无 OCI version 标签（历史遗留），防倒退检查无法判定: $lp_ref"; then
      return 1
    fi
  else
    lp_rank=$(parse_rank "$lp_ver")
    if [ "$(printf '%s' "$lp_rank" | jq -r .channel)" != "stable" ]; then
      if ! migration_gate "远端 latest 版本标签 '$lp_ver' 非正式通道版本，无法比较新旧: $lp_ref"; then
        return 1
      fi
    else
      lp_cmp=$(rank_cmp "$CUR_RANK" "$lp_rank")
      if [ "$lp_cmp" = "-1" ]; then
        LATEST_BLOCK_ERR="拒绝 latest 倒退: $lp_ref 当前为 '$lp_ver'，本次 '$HOST_BUILD_VERSION' 版本更低"
        return 1
      fi
    fi
  fi
  jq -n --arg v "$lp_ver" --arg d "$lp_digest" \
        '{attempted:true, promoted:false, previousVersionLabel:$v, previousDigest:$d}' \
    > "$LATEST_DIR/$1.$2.precheck.json"
  return 0
}

latest_promote() { # slot image
  lo_src=$(jq -r .sourceRef "$STATE_DIR/$2.meta.json")
  lo_ref=$(slot_ref "$1" "$2" latest)
  docker tag "$lo_src" "$lo_ref"
  if ! cap_out docker push "$lo_ref"; then
    jq -n --arg e "$(printf '%s' "$LAST_OUT" | tail -n 1)" \
          '{promoted:false, error:("latest 推送失败: " + $e)}' > "$LATEST_DIR/$1.$2.json"
    LATEST_OK=0
    LATEST_NOTE="$LATEST_NOTE;$1/$2 latest 推送失败"
    return 1
  fi
  lo_digest=$(extract_digest "$LAST_OUT")
  [ -n "$lo_digest" ] || {
    jq -n '{promoted:false, error:"latest 推送输出缺少摘要证据"}' > "$LATEST_DIR/$1.$2.json"
    LATEST_OK=0
    LATEST_NOTE="$LATEST_NOTE;$1/$2 latest 推送缺少摘要证据"
    return 1
  }
  lo_prev=$(jq -r '.previousVersionLabel // ""' "$LATEST_DIR/$1.$2.precheck.json")
  jq -n --arg d "$lo_digest" --arg p "$lo_prev" \
        '{promoted:true, digest:$d, previousVersionLabel:$p}' > "$LATEST_DIR/$1.$2.json"
  log "已晋升 latest: $lo_ref digest=$lo_digest"
}

# ---------- 10. 主流程 ----------
log '==> 阶段 1/5：登录镜像仓库'
for sl in harbor aliyun; do
  slot_enabled "$sl" || continue
  login_slot "$sl"
done

log '==> 阶段 2/5：检查远端不可变标签占用（版本/短 SHA）'
for sl in harbor aliyun; do
  slot_enabled "$sl" || continue
  for im in "$BACKEND_NAME" "$FRONTEND_NAME"; do
    for tk in version sha; do
      record_pull "$sl" "$im" "$tk" || true
    done
  done
done
check_digest_consistency "$BACKEND_NAME"
check_digest_consistency "$FRONTEND_NAME"

log '==> 阶段 3/5：确定制品来源（优先复用远端/本地同 commit 制品，避免重建漂移）'
select_source "$BACKEND_NAME"
select_source "$FRONTEND_NAME"

log '==> 阶段 4/5：推送缺失标签并做双仓内容验证'
for sl in harbor aliyun; do
  slot_enabled "$sl" || continue
  for im in "$BACKEND_NAME" "$FRONTEND_NAME"; do
    for tk in version sha; do
      ensure_slot_tag "$sl" "$im" "$tk"
    done
  done
done
for im in "$BACKEND_NAME" "$FRONTEND_NAME"; do
  for tk in version sha; do
    verify_cross_slot "$im" "$tk"
  done
done
log '双仓不可变标签验证通过（镜像 ID + 摘要一致）'

log '==> 阶段 5/5：发布证据与 latest 晋升'
LATEST_ATTEMPTED=false
LATEST_OK=1
LATEST_NOTE=''
if [ "$CHANNEL" = "stable" ]; then
  LATEST_ATTEMPTED=true
  blocked=''
  for sl in harbor aliyun; do
    slot_enabled "$sl" || continue
    for im in "$BACKEND_NAME" "$FRONTEND_NAME"; do
      LATEST_BLOCK_ERR=''
      if ! latest_precheck "$sl" "$im"; then
        blocked="$blocked $sl/$im"
        jq -n --arg e "$LATEST_BLOCK_ERR" '{attempted:true, promoted:false, error:$e}' \
          > "$LATEST_DIR/$sl.$im.json"
        LATEST_OK=0
        LATEST_NOTE="$LATEST_NOTE;$sl/$im ${LATEST_BLOCK_ERR}"
      fi
    done
  done
  if [ -z "$blocked" ]; then
    # 防倒退检查全部通过后才允许晋升；任一推送失败都只记 partial，不宣告 complete
    for sl in harbor aliyun; do
      slot_enabled "$sl" || continue
      for im in "$BACKEND_NAME" "$FRONTEND_NAME"; do
        latest_promote "$sl" "$im" || true
      done
    done
  fi
else
  for sl in harbor aliyun; do
    jq -n --arg c "$CHANNEL" '{attempted:false, promoted:false, reason:("通道 " + $c + " 不晋升 latest")}' \
      > "$LATEST_DIR/$sl.json"
    add_warning "通道 $CHANNEL 不晋升 latest（仅正式 vX.Y.Z 晋升）"
  done
fi

# ---------- 11. 汇总各单元 JSON（证据与结果，仅双仓验证完成后执行到这里） ----------
for im in "$BACKEND_NAME" "$FRONTEND_NAME"; do
  for sl in harbor aliyun; do
    is_out="$IMG_DIR/$im.$sl.json"
    if ! slot_enabled "$sl"; then
      printf '{"enabled":false}' > "$is_out"
      continue
    fi
    is_ref=$(slot_ref "$sl" "$im" "$(tagkey_tag version)")
    is_v_tag=$(tagkey_tag version) ; is_s_tag=$(tagkey_tag sha)
    is_vd=$(jq -r .digest "$( [ -f "$PUSH_DIR/$sl.$im.version.json" ] && printf '%s' "$PUSH_DIR/$sl.$im.version.json" || printf '%s' "$REC_DIR/$sl.$im.version.json" )")
    is_sd=$(jq -r .digest "$( [ -f "$PUSH_DIR/$sl.$im.sha.json" ] && printf '%s' "$PUSH_DIR/$sl.$im.sha.json" || printf '%s' "$REC_DIR/$sl.$im.sha.json" )")
    is_va=$([ -f "$PUSH_DIR/$sl.$im.version.json" ] && printf pushed || printf reused)
    is_sa=$([ -f "$PUSH_DIR/$sl.$im.sha.json" ] && printf pushed || printf reused)
    jq -n --arg ref "$is_ref" --arg vt "$is_v_tag" --arg vd "$is_vd" --arg va "$is_va" \
          --arg st "$is_s_tag" --arg sd "$is_sd" --arg sa "$is_sa" \
          '{enabled:true, ref:$ref,
            tags:{version:{tag:$vt, digest:$vd, action:$va},
                  sha:{tag:$st, digest:$sd, action:$sa}}}' > "$is_out"
  done
done

if [ "$CHANNEL" = "stable" ]; then
  for sl in harbor aliyun; do
    slot_enabled "$sl" || { printf '{"attempted":false}' > "$LATEST_DIR/$sl.agg.json"; continue; }
    lb=$(jq -c '. + {image:"'"$BACKEND_NAME"'"}' "$LATEST_DIR/$sl.$BACKEND_NAME.json" 2>/dev/null || \
         jq -c '{promoted:false, error:"防倒退检查未通过", image:"'"$BACKEND_NAME"'"}' "$LATEST_DIR/$sl.$BACKEND_NAME.precheck.json")
    lf=$(jq -c '. + {image:"'"$FRONTEND_NAME"'"}' "$LATEST_DIR/$sl.$FRONTEND_NAME.json" 2>/dev/null || \
         jq -c '{promoted:false, error:"防倒退检查未通过", image:"'"$FRONTEND_NAME"'"}' "$LATEST_DIR/$sl.$FRONTEND_NAME.precheck.json")
    jq -n --argjson b "$lb" --argjson f "$lf" \
          '{attempted:true, promoted:($b.promoted and $f.promoted), images:{backend:$b, frontend:$f}}' \
      > "$LATEST_DIR/$sl.agg.json"
  done
else
  for sl in harbor aliyun; do
    slot_enabled "$sl" || { printf '{"attempted":false}' > "$LATEST_DIR/$sl.agg.json"; continue; }
    jq -c '. + {promoted:false}' "$LATEST_DIR/$sl.json" > "$LATEST_DIR/$sl.agg.json"
  done
fi

if [ "$LATEST_ATTEMPTED" != "true" ]; then
  # rc/dev 通道本就不晋升 latest，发布语义上是完整成功
  STATUS=complete
elif [ "$LATEST_OK" = "1" ]; then
  STATUS=complete
else
  STATUS=partial
fi

NOW_ISO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EVIDENCE_DIR=$(dirname "$RELEASE_EVIDENCE_FILE")
mkdir -p "$EVIDENCE_DIR"
EVIDENCE_TMP="$RELEASE_EVIDENCE_FILE.tmp.$$"

WARNINGS_JSON=$(jq -R . < "$WARNINGS_TXT" | jq -s -c .)
jq -n \
  --arg status "$STATUS" \
  --arg now "$NOW_ISO" \
  --arg hv "$HOST_BUILD_VERSION" \
  --arg vt "$VER_TAG" \
  --arg ch "$CHANNEL" \
  --arg cs "$COMMIT_SHORT" \
  --arg cf "$COMMIT_FULL" \
  --arg la "$LATEST_ATTEMPTED" \
  --arg lc "$LATEST_OK" \
  --arg ln "$LATEST_NOTE" \
  --arg warnings "$WARNINGS_JSON" \
  --rawfile bmeta "$STATE_DIR/$BACKEND_NAME.meta.json" \
  --rawfile fmeta "$STATE_DIR/$FRONTEND_NAME.meta.json" \
  --rawfile hb "$IMG_DIR/$BACKEND_NAME.harbor.json" \
  --rawfile ab "$IMG_DIR/$BACKEND_NAME.aliyun.json" \
  --rawfile hf "$IMG_DIR/$FRONTEND_NAME.harbor.json" \
  --rawfile af "$IMG_DIR/$FRONTEND_NAME.aliyun.json" \
  --rawfile lhb "$LATEST_DIR/harbor.agg.json" \
  --rawfile lab "$LATEST_DIR/aliyun.agg.json" \
  '{
    kind: "yudream-host-images-release-evidence",
    script: "ci/publish-host-images.sh",
    generatedAt: $now,
    status: $status,
    hostVersion: $hv,
    versionTag: $vt,
    channel: $ch,
    commit: {short: $cs, full: $cf},
    images: {
      backend:  ($bmeta | fromjson | . + {registries: {harbor: ($hb|fromjson), aliyun: ($ab|fromjson)}}),
      frontend: ($fmeta | fromjson | . + {registries: {harbor: ($hf|fromjson), aliyun: ($af|fromjson)}})
    },
    latest: {
      attempted: ($la == "true"),
      complete: ($lc == "1"),
      note: $ln,
      registries: {harbor: ($lhb|fromjson), aliyun: ($lab|fromjson)}
    },
    warnings: ($warnings | fromjson)
  }' > "$EVIDENCE_TMP" || die '发布证据 JSON 生成失败'
mv "$EVIDENCE_TMP" "$RELEASE_EVIDENCE_FILE"
log "发布证据已写入: $RELEASE_EVIDENCE_FILE（status=$STATUS）"

if [ "$STATUS" = "complete" ]; then
  log "发布完成: $HOST_BUILD_VERSION（$CHANNEL）"
  exit 0
fi
die "发布未完成（status=partial，详见发布证据）: $LATEST_NOTE"
