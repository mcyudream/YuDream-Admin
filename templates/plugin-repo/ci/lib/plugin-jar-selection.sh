#!/usr/bin/env sh

select_final_plugin_jars() {
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

write_final_plugin_jars() {
  root_dir=$1
  output_file=$2

  : > "$output_file"
  select_final_plugin_jars "$root_dir" >> "$output_file"
  [ -s "$output_file" ]
}
