#!/usr/bin/env bash
# Proves release builds carry no test-tag exposure: the Compose flag that turns
# test tags into Android resource ids (the "TestTagsAsResourceId" semantics
# property) must live only in app/src/debug.
# Checks the sources, then the compiled classes of both variants, with a debug
# positive control so the class search cannot pass vacuously. Needs the Android
# SDK and Gradle; no emulator and no network beyond the Gradle cache.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
flag="TestTagsAsResourceId"
# Main-source class output of each variant (AGP built-in Kotlin); the unit-test
# class directories are siblings and are deliberately not searched.
kotlinc_out="$root/app/build/intermediates/built_in_kotlinc"
release_classes="$kotlinc_out/release/compileReleaseKotlin/classes"
debug_classes="$kotlinc_out/debug/compileDebugKotlin/classes"

failures=0
fail() {
  echo "FAIL: $1"
  failures=$((failures + 1))
}

# Prints the files under the given paths that reference the flag (case-insensitive,
# so a differently cased reference cannot slip past the source check).
sources_with_flag() {
  grep -rIil --include='*.kt' --include='*.java' "$flag" "$@" 2>/dev/null || true
}

# Prints the class files under a directory that reference the flag.
classes_with_flag() {
  grep -rl --include='*.class' "$flag" "$1" 2>/dev/null || true
}

# --- Sources: the flag must not appear in shared or release code ---
for dir in "$root/app/src/main" "$root/app/src/release"; do
  if [[ -d "$dir" ]]; then
    hits="$(sources_with_flag "$dir")"
    if [[ -n "$hits" ]]; then
      fail "$flag referenced outside app/src/debug: ${hits//$'\n'/, }"
    fi
  fi
done

# --- Bytecode: compile both variants and search the class output ---
(cd "$root" && ./gradlew --quiet :app:compileReleaseKotlin :app:compileDebugKotlin)

# A missing class directory would make the greps below vacuous.
for dir in "$release_classes" "$debug_classes"; do
  if [[ ! -d "$dir" ]]; then
    echo "FAIL: class output not found: $dir"
    exit 1
  fi
done

release_hits="$(classes_with_flag "$release_classes")"
if [[ -n "$release_hits" ]]; then
  fail "release classes reference $flag: ${release_hits//$'\n'/, }"
fi

# Positive control: the debug build must contain the flag, otherwise the
# release result above proves nothing.
if [[ -z "$(classes_with_flag "$debug_classes")" ]]; then
  fail "positive control: debug classes do not reference $flag"
fi

# --- Behavior: the release e2eRoot is the identity modifier ---
if ! (cd "$root" && ./gradlew --quiet :app:testReleaseUnitTest --tests 'com.librosleidos.ui.E2eRootReleaseTest'); then
  fail "release e2eRoot identity test failed"
fi

if [[ "$failures" -gt 0 ]]; then
  echo "$failures release test-tag check(s) failed"
  exit 1
fi
echo "release test-tag checks passed"
