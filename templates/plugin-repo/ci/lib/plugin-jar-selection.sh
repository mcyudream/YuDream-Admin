#!/usr/bin/env sh

select_flat_plugin_jars() {
  root_dir=$1
  flat_dir="$root_dir/dist/plugins"

  if [ ! -d "$flat_dir" ]; then
    return 1
  fi

  find "$flat_dir" -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name 'original-*.jar' | sort
}

select_target_plugin_jars() {
  root_dir=$1

  find "$root_dir/yudream-plugins" -mindepth 1 -maxdepth 1 -type d | sort | while IFS= read -r module_dir; do
    target_dir="$module_dir/target"
    if [ ! -d "$target_dir" ]; then
      continue
    fi

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
