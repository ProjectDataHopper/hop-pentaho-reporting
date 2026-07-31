#!/usr/bin/env bash
# Copy LGPL Pentaho Reporting classic engine jars from a PDI plugin lib folder
# and install the compile-time artifacts into the local Maven repository.
#
# Usage:
#   scripts/vendor-reporting-libs.sh /path/to/data-integration/plugins/pentaho-reporting-plugins/lib
#
# Allowed lines: 9.4.x / 10.0.x / 10.1.x (LGPL-2.1). Do NOT use 10.2+ (BSL).
set -euo pipefail

SRC="${1:-}"
if [[ -z "${SRC}" || ! -d "${SRC}" ]]; then
  echo "Usage: $0 <pdi-pentaho-reporting-plugins-lib-dir>" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${ROOT}/third-party/pentaho-reporting-lib"
mkdir -p "${DEST}"
rm -f "${DEST}"/*.jar

# Detect version from classic-core jar name
CORE_JAR="$(ls "${SRC}"/classic-core-*.jar 2>/dev/null | head -1 || true)"
if [[ -z "${CORE_JAR}" ]]; then
  echo "No classic-core-*.jar found in ${SRC}" >&2
  exit 1
fi
VERSION="$(basename "${CORE_JAR}" | sed -E 's/classic-core-(.*)\.jar/\1/')"
echo "Vendoring Pentaho Reporting ${VERSION} from ${SRC}"

# Skip heavy/optional extensions that pull Kettle/Mondrian/platform
EXCLUDE_REGEX='classic-extensions-(kettle|mondrian|olap4j|pmd)-|mondrian-|pentaho-metadata-|pentaho-platform-|pdi-engine-api-|metastore-|actionsequence-dom-|axis2-|commons-xul-core-|pentaho-connections-|pentaho-registry-|pentaho-service-coordinator-|pentaho-encryption-support-|commons-database-model-'

shopt -s nullglob
for jar in "${SRC}"/*.jar; do
  base="$(basename "${jar}")"
  if [[ "${base}" =~ ${EXCLUDE_REGEX} ]]; then
    continue
  fi
  cp -a "${jar}" "${DEST}/"
done

install_jar() {
  local group=$1 artifact=$2 file=$3
  if [[ ! -f "${file}" ]]; then
    echo "skip missing ${file}"
    return 0
  fi
  mvn -q install:install-file \
    -DgroupId="${group}" \
    -DartifactId="${artifact}" \
    -Dversion="${VERSION}" \
    -Dpackaging=jar \
    -Dfile="${file}" \
    -DgeneratePom=true
  echo "  m2: ${group}:${artifact}:${VERSION}"
}

echo "Installing compile artifacts into local Maven repository..."
install_jar org.pentaho.reporting.engine classic-core "${DEST}/classic-core-${VERSION}.jar"
install_jar org.pentaho.reporting.engine classic-extensions "${DEST}/classic-extensions-${VERSION}.jar"
for lib in libbase libdocbundle libfonts libformat libformula libloader libpixie \
           librepository libserializer libsparkline libswing libxml flute; do
  install_jar org.pentaho.reporting.library "${lib}" "${DEST}/${lib}-${VERSION}.jar"
done

echo
echo "Done. Vendored $(ls "${DEST}" | wc -l) jars into ${DEST}"
echo "Set -Dpentaho-reporting.version=${VERSION} if different from the project POM property."
