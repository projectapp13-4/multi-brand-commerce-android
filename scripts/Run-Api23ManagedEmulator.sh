#!/usr/bin/env bash

set -euo pipefail

readonly emulator_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly real_emulator="${emulator_directory}/emulator.real"

if [[ ! -x "${real_emulator}" ]]; then
  echo "API 23 emulator delegate is unavailable." >&2
  exit 1
fi

forwarded_arguments=()
for argument in "$@"; do
  # Current Gradle-managed devices always add this flag. The legacy API 23 AOSP
  # guest never transitions from host-visible ADB offline while it is enabled.
  # Removing only this transport delay still leaves AGP responsible for waiting
  # for sys.boot_completed, PackageManager readiness, snapshots, and test setup.
  if [[ "${argument}" != "-delay-adb" ]]; then
    forwarded_arguments+=("${argument}")
  fi
done

exec "${real_emulator}" "${forwarded_arguments[@]}"
