#!/usr/bin/env bash
# Remove highdeps engine modules from a designer POM so slim PRD can compile
# without mondrian/pmd/kettle/reportdesigner-parser SNAPSHOTs.
#
# Usage:
#   scripts/strip-slim-designer-deps.sh designer/report-designer/pom.xml
#
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <pom.xml> [pom.xml ...]" >&2
  exit 2
fi

strip_one() {
  local pom="$1"
  if [[ ! -f "${pom}" ]]; then
    echo "WARN: pom not found, skip: ${pom}" >&2
    return 0
  fi
  local tmp
  tmp="$(mktemp)"
  # awk only — no python on Jenkins agents. Avoid backslash-regex (breaks Groovy
  # when this script is not used and logic is inlined in Jenkinsfile).
  awk '
    BEGIN { indep=0; block="" }
    index($0, "<dependency>") > 0 {
      indep=1
      block=$0
      next
    }
    indep==1 {
      block=block ORS $0
      if (index($0, "</dependency>") > 0) {
        if (index(block, "classic-extensions-reportdesigner-parser") == 0 &&
            index(block, "classic-extensions-mondrian") == 0 &&
            index(block, "classic-extensions-pmd") == 0 &&
            index(block, "classic-extensions-kettle") == 0 &&
            index(block, "classic-extensions-olap4j") == 0) {
          print block
        } else {
          print "slim: stripped highdeps dependency from " FILENAME > "/dev/stderr"
        }
        indep=0
        block=""
      }
      next
    }
    { print }
  ' "${pom}" > "${tmp}"
  mv "${tmp}" "${pom}"
  echo "Patched slim deps out of ${pom}"
}

for pom in "$@"; do
  strip_one "${pom}"
done
