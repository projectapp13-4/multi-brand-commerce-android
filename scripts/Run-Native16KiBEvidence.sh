#!/usr/bin/env bash

# Isolated GitHub Actions evidence for the inherited native libraries, not a release claim.
set -euo pipefail

if [[ "${CI:-}" != true || "${GITHUB_ACTIONS:-}" != true || "$(uname -s)" != Linux ]]; then
  echo "This probe runs only on the isolated Linux GitHub Actions runner." >&2
  exit 1
fi
if [[ $# -ne 1 ]]; then
  echo "Pass the standalone mobile-core Android test APK." >&2
  exit 1
fi

apk=$(realpath "$1")
sdk_root="${ANDROID_SDK_ROOT:?Android SDK is required}"
evidence_directory=$(mktemp -d "${RUNNER_TEMP:?Runner temporary directory is required}/native16k.XXXXXX")
readonly apk sdk_root evidence_directory
readonly adb="$sdk_root/platform-tools/adb"
readonly emulator="$sdk_root/emulator/emulator"
readonly aapt2="$sdk_root/build-tools/36.0.0/aapt2"
readonly zipalign="$sdk_root/build-tools/36.0.0/zipalign"
readonly image='system-images;android-36;google_apis_ps16k;x86_64'
readonly package='com.gurbakir.mobile.core.test'
readonly runner='androidx.test.runner.AndroidJUnitRunner'
readonly probe_class='com.gurbakir.mobile.nativecompat.Native16KiBCompatibilityTest'
readonly graphics_sha='4E56C996F13670E70082658DE7880C4020EABF4F25E43387F88ED78A713FC9F0'
readonly counter_sha='FB6C9208988C49AE94943BC3236FA763BA584722E044FA4EA9A12D2941027105'
readonly avd_name="native16k_ci_${GITHUB_RUN_ID:?}_${GITHUB_RUN_ATTEMPT:?}"
readonly port=5584
readonly serial="emulator-$port"
emulator_pid=''
owned_group=false
owned_serial=false
page_size='UNKNOWN'
abi='UNKNOWN'
api='UNKNOWN'
boot_completed='UNKNOWN'

sanitize_log() {
  awk 'tolower($0) !~ /pubkey|public[[:space:]_]*key|authorization|token|serialno/ { print }' "$1"
}

cleanup() {
  local result=$?
  trap - EXIT
  set +e
  if [[ "$owned_serial" == true ]]; then
    timeout --kill-after=2s 10s "$adb" -s "$serial" emu kill > /dev/null 2>&1
  fi
  if [[ -n "$emulator_pid" ]]; then
    if [[ "$owned_group" == true ]]; then
      # setsid created this emulator's private process group; never stop by process name.
      kill -TERM -- "-$emulator_pid" 2>/dev/null
      for _ in {1..10}; do
        kill -0 -- "-$emulator_pid" 2>/dev/null || break
        sleep 1
      done
      kill -KILL -- "-$emulator_pid" 2>/dev/null
    else
      kill -TERM "$emulator_pid" 2>/dev/null
      for _ in {1..10}; do
        kill -0 "$emulator_pid" 2>/dev/null || break
        sleep 1
      done
      kill -KILL "$emulator_pid" 2>/dev/null
    fi
    sleep 0.2
    process_state=$(ps -o stat= -p "$emulator_pid" 2>/dev/null) || process_state=''
    if [[ -z "$process_state" || "$process_state" == Z* ]]; then
      wait "$emulator_pid" 2>/dev/null
    else
      echo 'native16k_cleanup=Owned emulator exit remains unconfirmed after bounded stop.' >&2
      result=1
    fi
  fi
  printf 'native16k_exit=%s page_size=%s abi=%s api=%s boot_completed=%s owned_avd=%s\n' \
    "$result" "$page_size" "$abi" "$api" "$boot_completed" "$owned_serial"
  if [[ "$result" -ne 0 && -f "$evidence_directory/emulator.log" ]]; then
    sanitize_log "$evidence_directory/emulator.log" | tail -n 50
  fi
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

for tool in timeout unzip sha256sum avdmanager setsid ps ss; do
  command -v "$tool" > /dev/null || { echo "Missing required runner tool: $tool" >&2; exit 1; }
done
for tool in "$adb" "$emulator" "$aapt2" "$zipalign"; do
  [[ -x "$tool" ]] || { echo "A pinned Android tool is missing." >&2; exit 1; }
done
[[ -f "$apk" ]] || { echo "Standalone test APK is missing." >&2; exit 1; }
apk_sha=$(sha256sum "$apk" | awk '{print toupper($1)}')
printf 'native16k_source=%s apk_sha256=%s system_image=%s\n' "$(git rev-parse HEAD)" "$apk_sha" "$image"
timeout --kill-after=2s 30s "$aapt2" dump xmltree --file AndroidManifest.xml "$apk" > "$evidence_directory/manifest.txt"
manifest="$evidence_directory/manifest.txt"
grep -Fq 'A: package="com.gurbakir.mobile.core.test"' "$manifest"
instrumentation=$(awk '/E: instrumentation/ { inside=1; next } inside && /E:/ { exit } inside { print }' "$manifest")
application=$(awk '/E: application/ { inside=1; next } inside && /E:/ { exit } inside { print }' "$manifest")
grep -Eq '(android:|http://schemas.android.com/apk/res/android:)targetPackage\(0x01010021\)="com[.]gurbakir[.]mobile[.]core[.]test"' <<< "$instrumentation"
grep -Eq '(android:|http://schemas.android.com/apk/res/android:)name\(0x01010003\)="androidx[.]test[.]runner[.]AndroidJUnitRunner"' <<< "$instrumentation"
grep -Eq '(android:|http://schemas.android.com/apk/res/android:)pageSizeCompat\(0x010106ab\)=(64|0x0*40|\(type 0x10\)0x40)[[:space:]]*$' <<< "$application"
test_only_attribute=ABSENT
if grep -Eq '(android:|http://schemas.android.com/apk/res/android:)testOnly\(0x01010272\)=' <<< "$application"; then
  grep -Eq '(android:|http://schemas.android.com/apk/res/android:)testOnly\(0x01010272\)=(true|-1|0xffffffff|\(type 0x12\)0xffffffff)[[:space:]]*$' <<< "$application"
  test_only_attribute=true
fi
timeout --kill-after=2s 30s "$zipalign" -c -P 16 4 "$apk"
printf 'native16k_manifest=self_target test_only_attribute=%s compatibility=disabled zip_alignment=PASS\n' "$test_only_attribute"
for library in androidx.graphics.path datastore_shared_counter; do
  expected="$graphics_sha"
  [[ "$library" != datastore_shared_counter ]] || expected="$counter_sha"
  actual=$(unzip -p "$apk" "lib/x86_64/lib$library.so" | sha256sum | awk '{print toupper($1)}')
  [[ "$actual" == "$expected" ]] || { echo "Inherited native byte identity changed: $library" >&2; exit 1; }
  printf 'native16k_packaged_library=%s sha256=%s\n' "$library" "$actual"
done
if timeout --kill-after=2s 10s "$adb" devices | grep -Eq "^$serial[[:space:]]"; then
  echo "The selected emulator serial is already occupied." >&2
  exit 1
fi
if ss -H -ltn | awk '{print $4}' | grep -Eq ":($port|$((port + 1)))$"; then
  echo "The selected emulator ports are already occupied." >&2
  exit 1
fi
printf 'no\n' | timeout --kill-after=2s 60s avdmanager create avd --name "$avd_name" --package "$image" --device pixel_5 > "$evidence_directory/avd-create.log" 2>&1
setsid "$emulator" -avd "$avd_name" -port "$port" -read-only -no-window -no-snapshot \
  -no-audio -no-boot-anim -gpu swiftshader_indirect -memory 2048 -cores 2 -skin 480x800 \
  > "$evidence_directory/emulator.log" 2>&1 &
emulator_pid=$!
for _ in {1..10}; do
  actual_group=$(ps -o pgid= -p "$emulator_pid" | tr -d '[:space:]') || true
  if [[ "$actual_group" == "$emulator_pid" ]]; then owned_group=true; break; fi
  sleep 0.1
done
[[ "$owned_group" == true ]] || { echo "Owned emulator process-group identity was not established." >&2; exit 1; }

deadline=$((SECONDS + 360))
boot_probe() {
  local remaining=$((deadline - SECONDS))
  [[ "$remaining" -gt 0 ]] || return 124
  [[ "$remaining" -le 8 ]] || remaining=8
  timeout --kill-after=2s "$remaining" "$adb" -s "$serial" "$@" 2>/dev/null
}
ready=false
while [[ "$SECONDS" -lt "$deadline" ]]; do
  if name_reply=$(boot_probe emu avd name); then
    name_reply=$(printf '%s\n' "$name_reply" | tr -d '\r' | sed '/^OK$/d; /^[[:space:]]*$/d; s/^[[:space:]]*//; s/[[:space:]]*$//')
    [[ "$name_reply" != "$avd_name" ]] || owned_serial=true
  fi
  if [[ "$owned_serial" == true ]]; then
    if value=$(boot_probe shell getconf PAGE_SIZE); then
      page_candidate=$(tr -d '[:space:]' <<< "$value")
      if [[ "$page_candidate" =~ ^[0-9]+$ ]]; then page_size="$page_candidate"; fi
    fi
    if value=$(boot_probe shell getprop ro.product.cpu.abi); then
      abi_candidate=$(tr -d '[:space:]' <<< "$value")
      if [[ -n "$abi_candidate" ]]; then abi="$abi_candidate"; fi
    fi
    if value=$(boot_probe shell getprop ro.build.version.sdk); then
      api_candidate=$(tr -d '[:space:]' <<< "$value")
      if [[ "$api_candidate" =~ ^[0-9]+$ ]]; then api="$api_candidate"; fi
    fi
    if value=$(boot_probe shell getprop sys.boot_completed); then boot_completed=$(tr -d '[:space:]' <<< "$value"); fi
    if [[ "$page_size" =~ ^[0-9]+$ && "$page_size" -ne 16384 ]]; then
      echo "Observed kernel page size is not 16KiB; no compatibility flag will be changed." >&2
      exit 1
    fi
    if [[ "$page_size" == 16384 && "$abi" == x86_64 && "$api" =~ ^[0-9]+$ && "$api" -ge 34 ]]; then
      package_reply=$(boot_probe shell cmd package path android) || package_reply=''
      activity_reply=$(boot_probe shell am get-current-user) || activity_reply=''
      activity_reply=$(tr -d '[:space:]' <<< "$activity_reply")
      if [[ "$package_reply" == package:/system/* && "$activity_reply" =~ ^[0-9]+$ && "$boot_completed" == 1 ]]; then
        ready=true
        break
      fi
    fi
  fi
  sleep 2
done
printf 'native16k_readiness page_size=%s abi=%s api=%s boot_completed=%s services_ready=%s\n' \
  "$page_size" "$abi" "$api" "$boot_completed" "$ready"
[[ "$ready" == true ]] || { echo "Bounded native runtime boot/service readiness timeout." >&2; exit 1; }
timeout --kill-after=10s 120s "$adb" -s "$serial" install --no-streaming -r -t "$apk" > "$evidence_directory/install.log" 2>&1
sanitize_log "$evidence_directory/install.log"

probe_succeeded() {
  local result="$1" log="$2" library="$3" expected="$4"
  [[ "$result" == 0 ]] && grep -Fq 'OK (1 test)' "$log" \
    && grep -Fxq 'INSTRUMENTATION_STATUS: nativeProbePhase=native_operation_completed' "$log" \
    && grep -Fxq 'INSTRUMENTATION_STATUS: kernelPageSize=16384' "$log" \
    && grep -Fxq 'INSTRUMENTATION_STATUS: process64Bit=true' "$log" \
    && grep -Fxq "INSTRUMENTATION_STATUS: packagedNativeSha256=$expected" "$log" \
    && grep -Fxq "INSTRUMENTATION_STATUS: loadedNativeLibrary=$library" "$log" \
    && ! grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_STATUS_CODE:[[:space:]]*-[0-9]+' "$log"
}

run_probe() {
  local method="$1" library="$2" expected="$3" result
  local log="$evidence_directory/$method.log"
  local normalized="$evidence_directory/$method.normalized.log"
  set +e
  timeout --kill-after=10s 120s "$adb" -s "$serial" shell am instrument -w -r \
    -e require16k true -e class "$probe_class#$method" "$package/$runner" > "$log" 2>&1
  result=$?
  set -e
  tr -d '\r' < "$log" > "$normalized"
  sanitize_log "$normalized"
  if [[ "$result" == 124 || "$result" == 137 ]]; then
    timeout --kill-after=2s 10s "$adb" -s "$serial" shell am force-stop "$package" > /dev/null 2>&1 || true
  fi
  probe_succeeded "$result" "$normalized" "$library" "$expected"
}
passed=0
if run_probe publicConicConversionUsesThePackagedGraphicsNativeLibraryOn16KiB androidx.graphics.path "$graphics_sha"; then passed=$((passed + 1)); fi
if run_probe multiProcessDataStoreReadsAndUpdatesThroughThePackagedNativeCounterOn16KiB datastore_shared_counter "$counter_sha"; then passed=$((passed + 1)); fi
[[ "$(sha256sum "$apk" | awk '{print toupper($1)}')" == "$apk_sha" ]] || { echo "APK input changed during the probe." >&2; exit 1; }
[[ "$passed" == 2 ]] || { echo "One or both native operations failed, skipped, crashed or timed out." >&2; exit 1; }
echo 'native16k_result=PASS_SCOPED_X86_64_INHERITED_NATIVE_OPERATIONS'
echo 'native16k_scope=These two operations; ARM64, ELF RELRO diagnostics and final signed-release evidence remain separate.'
