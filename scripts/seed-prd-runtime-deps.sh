#!/usr/bin/env bash
# Seed runtime jars required by Pentaho Report Designer onto pentaho-reporting-lgpl.
#
# Replaces the empty simple-jndi stub used by the engine CI job with a real jar,
# and publishes launcher + versionchecker seeds so PRD can resolve/start.
#
# Usage:
#   export NEXUS_USER=... NEXUS_PASSWORD=...
#   ./scripts/seed-prd-runtime-deps.sh
#   ./scripts/seed-prd-runtime-deps.sh --dry-run
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SEED="${ROOT}/jenkins/seed-jars"
NEXUS_URL="${NEXUS_URL:-https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/}"
NEXUS_REPO_ID="${NEXUS_REPO_ID:-pentaho-reporting-lgpl}"
MVN="${MVN:-mvn}"
DRY_RUN=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --nexus-url) NEXUS_URL="$2"; shift 2 ;;
    --repo-id) NEXUS_REPO_ID="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help)
      sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

NEXUS_URL="${NEXUS_URL%/}/"

require_file() {
  local f="$1"
  if [[ ! -f "${f}" ]]; then
    echo "ERROR: missing seed jar: ${f}" >&2
    exit 1
  fi
  local size
  size=$(wc -c <"${f}")
  if [[ "${size}" -lt 1000 ]]; then
    echo "ERROR: seed jar looks empty/stub (${size} bytes): ${f}" >&2
    exit 1
  fi
}

deploy_jar() {
  local g="$1" a="$2" v="$3" f="$4"
  echo "→ ${g}:${a}:${v}  from $(basename "${f}")"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    return 0
  fi
  ${MVN} -B ${SETTINGS_ARGS[@]+"${SETTINGS_ARGS[@]}"} \
    org.apache.maven.plugins:maven-deploy-plugin:3.1.3:deploy-file \
    -DgroupId="${g}" \
    -DartifactId="${a}" \
    -Dversion="${v}" \
    -Dpackaging=jar \
    -Dfile="${f}" \
    -DgeneratePom=true \
    -DrepositoryId="${NEXUS_REPO_ID}" \
    -Durl="${NEXUS_URL}"
}

SETTINGS_ARGS=()
TMP_SETTINGS=""
if [[ -n "${NEXUS_USER:-}" && -n "${NEXUS_PASSWORD:-}" ]]; then
  TMP_SETTINGS="$(mktemp)"
  python3 - "${TMP_SETTINGS}" "${NEXUS_REPO_ID}" "${NEXUS_USER}" "${NEXUS_PASSWORD}" <<'PY'
import sys, xml.sax.saxutils
path, sid, user, pw = sys.argv[1:5]
esc = xml.sax.saxutils.escape
open(path, "w").write(f"""<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">
  <servers>
    <server>
      <id>{esc(sid)}</id>
      <username>{esc(user)}</username>
      <password>{esc(pw)}</password>
    </server>
  </servers>
</settings>
""")
PY
  SETTINGS_ARGS=(-s "${TMP_SETTINGS}")
  trap 'rm -f "${TMP_SETTINGS}"' EXIT
fi

SJ="${SEED}/simple-jndi-1.0.13.jar"
LAUNCHER="${SEED}/pentaho-application-launcher.jar"
VCHECK="${SEED}/pentaho-versionchecker-9.4.0.0-343.jar"
RSYNTAX="${SEED}/rsyntaxtextarea-1.3.2.jar"

require_file "${SJ}"
require_file "${LAUNCHER}"
require_file "${VCHECK}"
require_file "${RSYNTAX}"

echo "==> Seeding PRD runtime deps to ${NEXUS_URL}"
echo "    (overwrites empty simple-jndi stub if present)"

# Real simple-jndi under the GAV classic-core / designer expect.
# Content is the 1.0.10 CE jar re-coordinated as 1.0.13 (API-compatible for PRD).
deploy_jar pentaho simple-jndi 1.0.13 "${SJ}"

# Application launcher → assemblies rename to launcher.jar
deploy_jar pentaho pentaho-application-launcher 10.1.0.0-SNAPSHOT "${LAUNCHER}"

# Version checker (9.4 CE binary under 10.1 SNAPSHOT coord for resolution)
deploy_jar pentaho pentaho-versionchecker 10.1.0.0-SNAPSHOT "${VCHECK}"

# Keep rsyntaxtextarea honest (also done by engine job)
deploy_jar org.fife.ui rsyntaxtextarea 1.3.2 "${RSYNTAX}"

echo "==> Seed complete."
if [[ "${DRY_RUN}" -eq 1 ]]; then
  echo "    (dry-run — nothing deployed)"
fi
