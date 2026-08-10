#!/usr/bin/env bash
# Three gates that fail even when the code compiles. See docs/guides/build-and-test.md.
set -uo pipefail
cd "$(dirname "$0")/.."
FAIL=0
SRC=src/main/java
[ -d "$SRC" ] || exit 0

# 1. no file headers  (docs/handbook/java-patterns.md#no-file-headers)
if grep -rln --include='*.java' -i 'Copyright\|SPDX-License-Identifier' src 2>/dev/null; then
  echo "FILE HEADER FORBIDDEN: no copyright or licence banner on any source file."; FAIL=1
fi

# 2. no Javadoc  (docs/handbook/java-patterns.md#no-javadoc)
if grep -rn --include='*.java' '/\*\*' "$SRC" 2>/dev/null; then
  echo "JAVADOC FORBIDDEN: names carry what, tests carry how, ADRs carry why."; FAIL=1
fi

# 3. suppression ladder
if grep -rn --include='*.java' 'NOSONAR' src 2>/dev/null; then
  echo "// NOSONAR is forbidden. Use @SuppressWarnings(\"java:SNNNN\") with a WHY comment."; FAIL=1
fi

[ $FAIL -eq 0 ] && echo "source hygiene: OK"
exit $FAIL
