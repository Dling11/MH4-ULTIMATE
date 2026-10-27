#!/bin/sh
set -eu
cd /workspace
source_dir=/workspace/.local/azahar
source_commit=039adda20c31fdf644fd3c9cf6962812ff109818
if [ ! -d "$source_dir/.git" ]; then
  git init "$source_dir"
  git -C "$source_dir" remote add origin https://github.com/klark231/AzaharPlus.git
  git -C "$source_dir" fetch --depth 1 origin "$source_commit"
  git -C "$source_dir" checkout --detach FETCH_HEAD
fi
native_git() { git -c "safe.directory=$source_dir" -c core.autocrlf=true -C "$source_dir" "$@"; }
if [ "$(native_git rev-parse HEAD)" != "$source_commit" ]; then
  echo 'The cached native source is a different revision. Preserve it and use a separate cache.' >&2
  exit 1
fi
native_git submodule update --init --recursive --depth 1 --jobs 2
for patch_file in /workspace/port/azahar/azahar-android.patch /workspace/port/azahar/rowell-window-layout.patch /workspace/port/azahar/rowell-speed-present.patch; do
  if native_git apply --reverse --check "$patch_file" 2>/dev/null; then
    echo "Native patch already applied: $(basename "$patch_file")"
  else
    native_git apply --check "$patch_file"
    native_git apply --whitespace=nowarn "$patch_file"
  fi
done
