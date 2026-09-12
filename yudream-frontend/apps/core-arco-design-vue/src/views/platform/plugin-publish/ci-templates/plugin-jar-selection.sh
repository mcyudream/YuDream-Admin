#!/usr/bin/env sh

plugin_release_list_file() {
  printf '%s\n' "$1/release/plugins.txt"
}

fail_plugin_selection() {
  echo "[plugin-jar-selection] $1" >&2
  return 1
}

validate_plugin_release_list() {
  root_dir=$1
  list_file=$(plugin_release_list_file "$root_dir")

  [ -f "$list_file" ] || fail_plugin_selection "missing release module list: $list_file" || return 1
  awk '
    /^[[:space:]]*$/ { exit 1 }
    !/^[a-z0-9][a-z0-9-]*$/ { exit 1 }
    seen[$0]++ { exit 1 }
    END { if (NR == 0) exit 1 }
  ' "$list_file" || fail_plugin_selection "release module list must contain unique, non-empty module IDs" || return 1

  if [ -d "$root_dir/yudream-plugins" ]; then
    while IFS= read -r module; do
      module_dir="$root_dir/yudream-plugins/$module"
      [ -d "$module_dir" ] || fail_plugin_selection "release module does not exist: $module" || return 1
      [ -f "$module_dir/pom.xml" ] || fail_plugin_selection "release module is missing pom.xml: $module" || return 1
      grep -Fq "<artifactId>$module</artifactId>" "$module_dir/pom.xml" \
        || fail_plugin_selection "release module POM artifactId does not match: $module" || return 1
    done < "$list_file"
  fi
}

plugin_release_selection_enabled() {
  [ "${PLUGIN_RELEASE_ONLY:-}" = "1" ] || [ "${PLUGIN_RELEASE_MODULES+x}" = x ]
}

validate_plugin_release_override() {
  root_dir=$1
  override=$2

  [ -n "$override" ] || fail_plugin_selection "PLUGIN_RELEASE_MODULES must not be empty" || return 1
  printf '%s\n' "$override" | awk -F, '
    {
      for (i = 1; i <= NF; i++) {
        field = $i
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", field)
        if (field == "") exit 1
      }
    }
  ' || fail_plugin_selection "PLUGIN_RELEASE_MODULES must not contain empty comma-separated entries" || return 1

  tokens=$(printf '%s' "$override" | tr ',\t\r\n' '    ')
  set -f
  set -- $tokens
  set +f
  [ "$#" -gt 0 ] || fail_plugin_selection "PLUGIN_RELEASE_MODULES must contain module IDs" || return 1

  seen=' '
  for module in "$@"; do
    case "$module" in
      *[!a-z0-9-]*|'') fail_plugin_selection "invalid plugin module ID: $module" || return 1 ;;
    esac
    case "$seen" in
      *" $module "*) fail_plugin_selection "duplicate plugin module ID: $module" || return 1 ;;
    esac
    grep -Fx "$module" "$(plugin_release_list_file "$root_dir")" >/dev/null \
      || fail_plugin_selection "plugin module is not in release/plugins.txt: $module" || return 1
    seen="$seen$module "
    printf '%s\n' "$module"
  done
}

selected_plugin_modules() {
  root_dir=$1
  validate_plugin_release_list "$root_dir" || return 1

  if [ "${PLUGIN_RELEASE_MODULES+x}" = x ]; then
    validate_plugin_release_override "$root_dir" "$PLUGIN_RELEASE_MODULES" || return 1
  else
    cat "$(plugin_release_list_file "$root_dir")"
  fi
}

selected_plugin_modules_csv() {
  root_dir=$1
  selected_plugin_modules "$root_dir" | paste -sd, -
}

select_flat_plugin_jars() {
  root_dir=$1
  flat_dir="$root_dir/dist/plugins"

  [ -d "$flat_dir" ] || return 1

  if plugin_release_selection_enabled; then
    selected_plugin_modules "$root_dir" | while IFS= read -r module; do
      find "$flat_dir" -maxdepth 1 -type f -name "$module-*.jar" ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name 'original-*.jar' | sort | head -n 1
    done
  else
    find "$flat_dir" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name 'original-*.jar' | sort
  fi
}

select_target_plugin_jars() {
  root_dir=$1

  if plugin_release_selection_enabled; then
    module_dirs=$(selected_plugin_modules "$root_dir" | while IFS= read -r module; do printf '%s\n' "$root_dir/yudream-plugins/$module"; done)
  else
    module_dirs=$(find "$root_dir/yudream-plugins" -mindepth 1 -maxdepth 1 -type d | sort)
  fi

  printf '%s\n' "$module_dirs" | while IFS= read -r module_dir; do
    [ -n "$module_dir" ] || continue
    target_dir="$module_dir/target"
    [ -d "$target_dir" ] || continue

    shaded_jar=$(find "$target_dir" -maxdepth 1 -type f -name '*-shaded.jar' | sort | head -n 1)
    if [ -n "$shaded_jar" ]; then
      printf '%s\n' "$shaded_jar"
      continue
    fi

    plain_jar=$(find "$target_dir" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name 'original-*.jar' ! -name '*-shaded.jar' | sort | head -n 1)
    if [ -n "$plain_jar" ]; then
      printf '%s\n' "$plain_jar"
    fi
  done
}

select_final_plugin_jars() {
  root_dir=$1

  flat_jars=$(select_flat_plugin_jars "$root_dir" || true)
  if [ -n "$flat_jars" ]; then
    printf '%s\n' "$flat_jars"
    return 0
  fi

  select_target_plugin_jars "$root_dir"
}

write_final_plugin_jars() {
  root_dir=$1
  output_file=$2

  : > "$output_file"
  select_final_plugin_jars "$root_dir" >> "$output_file"
  [ -s "$output_file" ]
}

copy_final_plugin_jars() {
  root_dir=$1
  output_dir=$2

  tmp_file=$(mktemp "${TMPDIR:-/tmp}/yudream-plugin-jars-XXXXXX.txt")
  trap 'rm -f "$tmp_file"' EXIT INT TERM

  if ! write_final_plugin_jars "$root_dir" "$tmp_file"; then
    rm -f "$tmp_file"
    trap - EXIT INT TERM
    return 1
  fi

  while IFS= read -r jar_path; do
    cp "$jar_path" "$output_dir/$(basename "$jar_path")"
  done < "$tmp_file"

  rm -f "$tmp_file"
  trap - EXIT INT TERM
}
