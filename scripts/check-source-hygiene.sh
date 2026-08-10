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
#    String literals are stripped first: an Ant-style path pattern such as "/v1/pokedex/**"
#    or "/**" in a request matcher is not a comment, and a gate that cannot tell the
#    difference gets switched off by the first person it blocks.
while IFS= read -r f; do
  if sed -e 's/"[^"]*"//g' -e 's://.*::' "$f" | grep -n '/\*\*' >/dev/null; then
    echo "JAVADOC FORBIDDEN: $f"
    sed -e 's/"[^"]*"//g' -e 's://.*::' "$f" | grep -n '/\*\*'
    FAIL=1
  fi
done < <(find "$SRC" -name '*.java' 2>/dev/null)
[ $FAIL -eq 1 ] && echo "names carry what, tests carry how, ADRs carry why."

# 3. suppression ladder
if grep -rn --include='*.java' 'NOSONAR' src 2>/dev/null; then
  echo "// NOSONAR is forbidden. Use @SuppressWarnings(\"java:SNNNN\") with a WHY comment."; FAIL=1
fi

[ $FAIL -eq 0 ] && echo "source hygiene: OK"
exit $FAIL
