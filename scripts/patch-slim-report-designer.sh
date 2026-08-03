#!/usr/bin/env bash
# Patch upstream designer/report-designer for slim PRD compile:
#  - add legacy-charts, commons-lang, log4j-api/core deps
#  - exclude Mondrian-only ClearMondrianCacheAction from compilation
#
# Usage (from pentaho-reporting checkout root):
#   patch-slim-report-designer.sh
#
set -euo pipefail

POM="designer/report-designer/pom.xml"
if [[ ! -f "${POM}" ]]; then
  echo "ERROR: ${POM} not found (run from pentaho-reporting root)" >&2
  exit 1
fi

if grep -q 'slim-prd-patches' "${POM}"; then
  echo "report-designer pom already patched"
  exit 0
fi

tmp="$(mktemp)"

# 1) Inject compile deps before first project-level </dependencies>
awk '
  BEGIN { dm=0; deps_depth=0; injected=0 }
  /<dependencyManagement>/ { dm=1 }
  /<\/dependencyManagement>/ { dm=0 }
  /<dependencies>/ {
    deps_depth++
    print
    next
  }
  /<\/dependencies>/ {
    if (dm==0 && deps_depth==1 && injected==0) {
      print "    <!-- slim-prd-patches: compile deps -->"
      print "    <dependency>"
      print "      <groupId>org.pentaho.reporting.engine</groupId>"
      print "      <artifactId>legacy-charts</artifactId>"
      print "      <version>${project.version}</version>"
      print "    </dependency>"
      print "    <dependency>"
      print "      <groupId>commons-lang</groupId>"
      print "      <artifactId>commons-lang</artifactId>"
      print "      <version>2.6</version>"
      print "    </dependency>"
      print "    <dependency>"
      print "      <groupId>org.apache.logging.log4j</groupId>"
      print "      <artifactId>log4j-api</artifactId>"
      print "      <version>2.17.1</version>"
      print "    </dependency>"
      print "    <dependency>"
      print "      <groupId>org.apache.logging.log4j</groupId>"
      print "      <artifactId>log4j-core</artifactId>"
      print "      <version>2.17.1</version>"
      print "    </dependency>"
      injected=1
    }
    deps_depth--
    print
    next
  }
  { print }
' "${POM}" > "${tmp}.1"

# 2) Exclude ClearMondrianCacheAction (needs mondrian.olap)
if grep -q '<build>' "${tmp}.1"; then
  if ! grep -q 'ClearMondrianCacheAction' "${tmp}.1"; then
    awk '
      /<\/build>/ && !done {
        print "    <!-- slim-prd-patches: exclude Mondrian-only sources -->"
        print "    <plugins>"
        print "      <plugin>"
        print "        <groupId>org.apache.maven.plugins</groupId>"
        print "        <artifactId>maven-compiler-plugin</artifactId>"
        print "        <configuration>"
        print "          <excludes>"
        print "            <exclude>**/ClearMondrianCacheAction.java</exclude>"
        print "          </excludes>"
        print "        </configuration>"
        print "      </plugin>"
        print "    </plugins>"
        done=1
      }
      { print }
    ' "${tmp}.1" > "${tmp}.2"
    mv "${tmp}.2" "${tmp}.1"
  fi
else
  awk '
    /<\/project>/ && !done {
      print "  <!-- slim-prd-patches -->"
      print "  <build>"
      print "    <plugins>"
      print "      <plugin>"
      print "        <groupId>org.apache.maven.plugins</groupId>"
      print "        <artifactId>maven-compiler-plugin</artifactId>"
      print "        <configuration>"
      print "          <excludes>"
      print "            <exclude>**/ClearMondrianCacheAction.java</exclude>"
      print "          </excludes>"
      print "        </configuration>"
      print "      </plugin>"
      print "    </plugins>"
      print "  </build>"
      done=1
    }
    { print }
  ' "${tmp}.1" > "${tmp}.2"
  mv "${tmp}.2" "${tmp}.1"
fi

mv "${tmp}.1" "${POM}"
rm -f "${tmp}" "${tmp}.2" 2>/dev/null || true
echo "Patched ${POM} for slim PRD"
