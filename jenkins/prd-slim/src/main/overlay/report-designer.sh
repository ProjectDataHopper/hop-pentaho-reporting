#!/usr/bin/env bash
# Data Hopper slim LGPL PRD launcher (POSIX-friendly; works when invoked as bash).
# Prefer:  ./report-designer.sh
# Avoid:   sh report-designer.sh   (dash does not support [[ )

set -e

DIR="$(cd "$(dirname "$0")" && pwd)"

# shellcheck source=/dev/null
. "$DIR/set-pentaho-env.sh"
setPentahoEnv

JAVA_LOCALE_COMPAT=""
JAVA_ADD_OPENS=""

# JDK 11+ needs add-opens for the classic launcher / VFS / macOS LAF hooks
JAVA_VER_OUT="$("$_PENTAHO_JAVA" -version 2>&1 || true)"
case "$JAVA_VER_OUT" in
  *version\ \"1.[0-9]*|*version\ \"1[1-9]*|*version\ \"2[0-9]*)
    JAVA_LOCALE_COMPAT="-Djava.locale.providers=COMPAT,SPI"
    JAVA_ADD_OPENS="--add-opens java.base/java.net=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.net.www.protocol.jar=ALL-UNNAMED --add-opens java.desktop/com.apple.eawt=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED"
    ;;
esac

# Ensure plugins dir exists (launcher.properties lists it on the classpath)
mkdir -p "$DIR/plugins"

# Prefer log4j2 over misconfigured commons-logging bridges when present
export LOG4J_CONFIGURATION_FILE="${LOG4J_CONFIGURATION_FILE:-$DIR/resources/log4j2.xml}"

if [ "$(uname -s 2>/dev/null)" = "Darwin" ]; then
  exec "$_PENTAHO_JAVA" $JAVA_ADD_OPENS -Xms1024m -Xmx2048m \
    -Dapple.laf.useScreenMenuBar=true \
    $JAVA_LOCALE_COMPAT \
    -Dorg.apache.commons.logging.LogFactory=org.apache.logging.log4j.jcl.LogFactoryImpl \
    -jar "$DIR/launcher.jar" "$@"
else
  exec "$_PENTAHO_JAVA" $JAVA_ADD_OPENS -Xms1024m -Xmx2048m \
    $JAVA_LOCALE_COMPAT \
    -Dorg.apache.commons.logging.LogFactory=org.apache.logging.log4j.jcl.LogFactoryImpl \
    -jar "$DIR/launcher.jar" "$@"
fi
