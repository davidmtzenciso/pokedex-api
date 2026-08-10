#!/usr/bin/env bash
# Copyright (c) 2026 ElatusDev
# Three gates that fail even when the code compiles. See docs/guides/build-and-test.md.
set -uo pipefail
cd "$(dirname "$0")/.."
FAIL=0
SRC=src/main/java
[ -d "$SRC" ] || exit 0

# 1. copyright header in the first 10 lines of every source file
while IFS= read -r f; do
  head -10 "$f" | grep -q 'Copyright (c) 2026 ElatusDev' || { echo "MISSING COPYRIGHT HEADER: $f"; FAIL=1; }
done < <(find "$SRC" src/test/java -name '*.java' 2>/dev/null)

# 2. no Javadoc  (ADR-0011 / java-patterns.md#no-javadoc)
if grep -rn --include='*.java' '/\*\*' "$SRC" 2>/dev/null; then
  echo "JAVADOC FORBIDDEN: names carry what, tests carry how, ADRs carry why."; FAIL=1
fi

# 3. suppression ladder
if grep -rn --include='*.java' 'NOSONAR' src 2>/dev/null; then
  echo "// NOSONAR is forbidden. Use @SuppressWarnings(\"java:SNNNN\") with a WHY comment."; FAIL=1
fi

[ $FAIL -eq 0 ] && echo "source hygiene: OK"
exit $FAIL
