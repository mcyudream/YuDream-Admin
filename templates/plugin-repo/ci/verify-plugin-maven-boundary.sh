#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT_DIR"

fail() {
  echo "[verify-plugin-maven-boundary] $1" >&2
  exit 1
}

search_tree() {
  pattern=$1
  shift
  if command -v rg >/dev/null 2>&1; then
    rg -n --no-messages "$pattern" "$@" >/dev/null 2>&1
  else
    grep -R -n -E -- "$pattern" "$@" >/dev/null 2>&1
  fi
}

search_poms() {
  pattern=$1
  if command -v rg >/dev/null 2>&1; then
    rg -n --no-messages "$pattern" pom.xml yudream-plugins -g 'pom.xml' >/dev/null 2>&1
  else
    if find yudream-plugins -name pom.xml -type f -exec grep -n -E -- "$pattern" {} + >/dev/null 2>&1; then
      return 0
    fi
    grep -n -E -- "$pattern" pom.xml >/dev/null 2>&1
  fi
}

check_spi_dependency_version() {
  pom_file=$1
  if ! awk '
    /<dependency>/ { in_dep=1; group=""; artifact=""; version=""; next }
    in_dep && /<groupId>/ {
      line=$0
      sub(/^.*<groupId>/, "", line)
      sub(/<\/groupId>.*$/, "", line)
      group=line
    }
    in_dep && /<artifactId>/ {
      line=$0
      sub(/^.*<artifactId>/, "", line)
      sub(/<\/artifactId>.*$/, "", line)
      artifact=line
    }
    in_dep && /<version>/ {
      line=$0
      sub(/^.*<version>/, "", line)
      sub(/<\/version>.*$/, "", line)
      version=line
    }
    /<\/dependency>/ {
      if (group == "online.yudream.base" && artifact == "yudream-plugin-spi" && version != "") {
        exit 10
      }
      in_dep=0
      group=""
      artifact=""
      version=""
    }
  ' "$pom_file"; then
    fail "plugin module must not pin yudream-plugin-spi version locally: $pom_file"
  fi
}

echo "[verify-plugin-maven-boundary] checking committed pom registry leakage"
if search_poms '<repositories>|<pluginRepositories>|<systemPath>|<scope>system</scope>'; then
  fail "plugin repo pom files must not hardcode repositories, pluginRepositories, or system-scope paths"
fi

echo "[verify-plugin-maven-boundary] checking forbidden host-module dependencies"
if search_poms '<artifactId>(yudream-domain|yudream-application|yudream-infrastructure|yudream-interfaces|yudream-bootstrap)</artifactId>'; then
  fail "plugin modules must not depend on host core implementation artifacts"
fi

echo "[verify-plugin-maven-boundary] checking forbidden host-module imports"
if search_tree 'online\.yudream\.base\.(domain|application|infra|interfaces|bootstrap)' yudream-plugins; then
  fail "plugin source must not import host core implementation packages directly"
fi

echo "[verify-plugin-maven-boundary] checking module parents and SPI contract usage"
for pom_file in yudream-plugins/*/pom.xml; do
  [ -f "$pom_file" ] || continue
  grep -q '<groupId>online.yudream.plugins</groupId>' "$pom_file" || fail "plugin module parent groupId must stay online.yudream.plugins: $pom_file"
  grep -q '<artifactId>yudream-admin-plugins</artifactId>' "$pom_file" || fail "plugin module parent artifactId must stay yudream-admin-plugins: $pom_file"
  grep -q '<relativePath>../../pom.xml</relativePath>' "$pom_file" || fail "plugin module parent relativePath must stay ../../pom.xml: $pom_file"
  grep -q '<artifactId>yudream-plugin-spi</artifactId>' "$pom_file" || fail "plugin module must depend on yudream-plugin-spi: $pom_file"
  check_spi_dependency_version "$pom_file"
done

echo "[verify-plugin-maven-boundary] checking SPI frontend asset wiring"
for pom_file in yudream-plugins/*/pom.xml; do
  [ -f "$pom_file" ] || continue
  if ! grep -q '<directory>../../yudream-frontend/packages/plugin-' "$pom_file"; then
    fail "plugin module frontend assets must come from standalone repo frontend workspace: $pom_file"
  fi
done

echo "[verify-plugin-maven-boundary] OK"
