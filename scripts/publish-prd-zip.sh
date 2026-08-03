#!/usr/bin/env bash
# Deploy a Pentaho Report Designer (PRD) client zip to Data Hopper Nexus
# (pentaho-reporting-lgpl), alongside the LGPL reporting libraries.
#
# Last LGPL product line: 10.1 (10.2+ is BSL — do not publish).
#
# Usage:
#   export NEXUS_USER=... NEXUS_PASSWORD=...   # or rely on ~/.m2/settings.xml
#   ./scripts/publish-prd-zip.sh /path/to/prd-ce-10.1.0.0-dh1.zip
#   ./scripts/publish-prd-zip.sh /path/to/zip --version 10.1.0.0-dh1
#   ./scripts/publish-prd-zip.sh /path/to/zip --dry-run
#
# Coordinates (defaults):
#   org.pentaho.reporting:prd-ce:<version>:zip
#   → https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

GROUP_ID="${GROUP_ID:-org.pentaho.reporting}"
ARTIFACT_ID="${ARTIFACT_ID:-prd-ce}"
VERSION="${VERSION:-10.1.0.0-dh1}"
CLASSIFIER="${CLASSIFIER:-}"
NEXUS_URL="${NEXUS_URL:-https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/}"
NEXUS_REPO_ID="${NEXUS_REPO_ID:-pentaho-reporting-lgpl}"
MVN="${MVN:-mvn}"
DRY_RUN=0
ZIP_PATH=""
GENERATE_POM=true
POM_FILE=""

usage() {
  sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="$2"; shift 2 ;;
    --group-id) GROUP_ID="$2"; shift 2 ;;
    --artifact-id) ARTIFACT_ID="$2"; shift 2 ;;
    --classifier) CLASSIFIER="$2"; shift 2 ;;
    --nexus-url) NEXUS_URL="$2"; shift 2 ;;
    --repo-id) NEXUS_REPO_ID="$2"; shift 2 ;;
    --pom-file) POM_FILE="$2"; GENERATE_POM=false; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    -*)
      echo "Unknown arg: $1" >&2
      usage >&2
      exit 2
      ;;
    *)
      if [[ -n "${ZIP_PATH}" ]]; then
        echo "Unexpected extra arg: $1" >&2
        exit 2
      fi
      ZIP_PATH="$1"
      shift
      ;;
  esac
done

if [[ -z "${ZIP_PATH}" ]]; then
  echo "ERROR: path to PRD zip is required." >&2
  usage >&2
  exit 2
fi
if [[ ! -f "${ZIP_PATH}" ]]; then
  echo "ERROR: zip not found: ${ZIP_PATH}" >&2
  exit 1
fi

NEXUS_URL="${NEXUS_URL%/}/"
ZIP_PATH="$(cd "$(dirname "${ZIP_PATH}")" && pwd)/$(basename "${ZIP_PATH}")"

echo "==> PRD client publish"
echo "    GAV: ${GROUP_ID}:${ARTIFACT_ID}:${VERSION}:zip${CLASSIFIER:+:${CLASSIFIER}}"
echo "    File: ${ZIP_PATH} ($(du -h "${ZIP_PATH}" | awk '{print $1}'))"
echo "    Nexus: ${NEXUS_URL} (server id ${NEXUS_REPO_ID})"

# Guardrails: refuse obvious BSL product names in the zip path
base="$(basename "${ZIP_PATH}")"
if echo "${base}" | grep -qiE '10\.2|10\.3|11\.|bsl'; then
  echo "ERROR: zip name looks like a post-LGPL / BSL line — refusing (${base})." >&2
  exit 1
fi
if [[ "${VERSION}" == 10.2* || "${VERSION}" == 11.* ]]; then
  echo "ERROR: version ${VERSION} is not an allowed LGPL client pin." >&2
  exit 1
fi

DEPLOY_ARGS=(
  org.apache.maven.plugins:maven-deploy-plugin:3.1.3:deploy-file
  -DgroupId="${GROUP_ID}"
  -DartifactId="${ARTIFACT_ID}"
  -Dversion="${VERSION}"
  -Dpackaging=zip
  -Dfile="${ZIP_PATH}"
  -DrepositoryId="${NEXUS_REPO_ID}"
  -Durl="${NEXUS_URL}"
)
if [[ -n "${CLASSIFIER}" ]]; then
  DEPLOY_ARGS+=(-Dclassifier="${CLASSIFIER}")
fi
if [[ "${GENERATE_POM}" == "true" ]]; then
  DEPLOY_ARGS+=(-DgeneratePom=true)
else
  DEPLOY_ARGS+=(-DpomFile="${POM_FILE}")
fi

# Optional sidecar BUILD-INFO if present next to the zip
BUILD_INFO_DIR="$(dirname "${ZIP_PATH}")"
if [[ -f "${BUILD_INFO_DIR}/BUILD-INFO.txt" ]]; then
  echo "    Attaching BUILD-INFO.txt as classifier=build-info (type=txt)"
fi

if [[ "${DRY_RUN}" -eq 1 ]]; then
  echo "==> DRY RUN — would run:"
  echo "    ${MVN} -B ${DEPLOY_ARGS[*]}"
  exit 0
fi

# settings: prefer env credentials if set (same pattern as marketplace script)
SETTINGS_ARGS=()
TMP_SETTINGS=""
if [[ -n "${NEXUS_USER:-}" && -n "${NEXUS_PASSWORD:-}" ]]; then
  TMP_SETTINGS="$(mktemp)"
  # shellcheck disable=SC2016
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

echo "==> Deploying zip..."
${MVN} -B "${SETTINGS_ARGS[@]+"${SETTINGS_ARGS[@]}"}" "${DEPLOY_ARGS[@]}"

if [[ -f "${BUILD_INFO_DIR}/BUILD-INFO.txt" ]]; then
  ${MVN} -B "${SETTINGS_ARGS[@]+"${SETTINGS_ARGS[@]}"}" \
    org.apache.maven.plugins:maven-deploy-plugin:3.1.3:deploy-file \
    -DgroupId="${GROUP_ID}" \
    -DartifactId="${ARTIFACT_ID}" \
    -Dversion="${VERSION}" \
    -Dpackaging=txt \
    -Dclassifier=build-info \
    -Dfile="${BUILD_INFO_DIR}/BUILD-INFO.txt" \
    -DgeneratePom=false \
    -DrepositoryId="${NEXUS_REPO_ID}" \
    -Durl="${NEXUS_URL}" || echo "WARN: BUILD-INFO attach failed (zip still published)"
fi

echo "==> Done."
echo "    Download (example):"
echo "    ${NEXUS_URL}${GROUP_ID//.//}/${ARTIFACT_ID}/${VERSION}/${ARTIFACT_ID}-${VERSION}${CLASSIFIER:+-${CLASSIFIER}}.zip"
echo ""
echo "    Install:"
echo "      unzip ${ARTIFACT_ID}-${VERSION}.zip -d ~/tools"
echo "      cd ~/tools/report-designer && ./report-designer.sh"
