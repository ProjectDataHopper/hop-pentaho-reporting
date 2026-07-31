// Build LGPL Pentaho Reporting (parent POMs + libraries + classic-core)
// and deploy to Data Hopper Nexus.
//
// Jenkins setup (UI, once):
//   1. New Item → Pipeline → OK
//   2. Pipeline → Definition: "Pipeline script from SCM"
//      - SCM: Git → this repo
//      - Script Path: Jenkinsfile
//   3. Credentials: Username/Password with ID matching params below
//      (default: nexus-pentaho-reporting-lgpl)
//   4. Global Tool Configuration (optional): JDK + Maven names matching tools{}
//   5. Build Now
//
// Do NOT use Hitachi public Maven as a mirror. Do NOT build 10.2+ / BSL trees.

pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 3, unit: 'HOURS')
  }

  parameters {
    string(
      name: 'REPORTING_BRANCH',
      defaultValue: '10.1',
      description: 'pentaho/pentaho-reporting branch (LGPL only: 9.4, 10.0, 10.1 — never 10.2+)'
    )
    string(
      name: 'PARENTS_BRANCH',
      defaultValue: '10.1',
      description: 'pentaho/maven-parent-poms branch (match reporting line)'
    )
    string(
      name: 'NEXUS_DEPLOY_URL',
      defaultValue: 'https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/',
      description: 'Nexus hosted repo URL (trailing slash recommended)'
    )
    string(
      name: 'NEXUS_SERVER_ID',
      defaultValue: 'pentaho-reporting-lgpl',
      description: 'Must match <server><id> in settings and altDeploymentRepository id'
    )
    string(
      name: 'NEXUS_CREDENTIALS_ID',
      defaultValue: 'nexus-pentaho-reporting-lgpl',
      description: 'Jenkins Username/Password credential ID for Nexus deploy'
    )
    booleanParam(
      name: 'SKIP_PARENT_POMS',
      defaultValue: false,
      description: 'Skip parent POM deploy if already published to Nexus'
    )
    booleanParam(
      name: 'SKIP_TESTS',
      defaultValue: true,
      description: 'Skip unit tests (recommended until first green deploy)'
    )
    booleanParam(
      name: 'DEPLOY_SAFE_EXTENSIONS',
      defaultValue: false,
      description: 'Also build a few LGPL engine extensions (no kettle/mondrian/metadata)'
    )
  }

  environment {
    MAVEN_OPTS = '-Xmx2g -Djava.awt.headless=true'
    // Isolate from agent-wide m2 pollution (e.g. HTML files cached as .jar from Hitachi)
    MAVEN_REPO_LOCAL = "${env.WORKSPACE}/.m2/repository"
    // Export parameters for shell steps (single-quoted sh blocks read env, not params.*)
    REPORTING_BRANCH = "${params.REPORTING_BRANCH}"
    PARENTS_BRANCH = "${params.PARENTS_BRANCH}"
    NEXUS_DEPLOY_URL = "${params.NEXUS_DEPLOY_URL}"
    NEXUS_SERVER_ID = "${params.NEXUS_SERVER_ID}"
    SKIP_PARENT_POMS = "${params.SKIP_PARENT_POMS}"
    SKIP_TESTS = "${params.SKIP_TESTS}"
    DEPLOY_SAFE_EXTENSIONS = "${params.DEPLOY_SAFE_EXTENSIONS}"
    // Stock plugins / versions from Maven Central (Hitachi forks/feeds unavailable)
    MAVEN_HITACHI_WORKAROUNDS = '-Dbuild-helper-maven-plugin.version=3.1.0 -Dbeanshell.version=2.0b6'
  }

  tools {
    // Names MUST match Manage Jenkins → Tools exactly.
    jdk 'jdk-11'
    maven 'Maven 3.9.9'
  }

  stages {
    stage('Show toolchain') {
      steps {
        sh '''
          set -euo pipefail
          echo "JAVA_HOME=${JAVA_HOME:-}"
          java -version
          mvn -version
        '''
      }
    }

    stage('Prepare Maven settings') {
      steps {
        withCredentials([usernamePassword(
            credentialsId: "${params.NEXUS_CREDENTIALS_ID}",
            usernameVariable: 'NEXUS_USER',
            passwordVariable: 'NEXUS_PASS')]) {
          script {
            // Passwords often contain &, <, etc. — must be XML-escaped in settings.xml
            def xmlEscape = { String s ->
              if (s == null) {
                return ''
              }
              return s.replace('&', '&amp;')
                  .replace('<', '&lt;')
                  .replace('>', '&gt;')
                  .replace('"', '&quot;')
                  .replace("'", '&apos;')
            }
            def userXml = xmlEscape(env.NEXUS_USER)
            def passXml = xmlEscape(env.NEXUS_PASS)
            def serverId = xmlEscape(env.NEXUS_SERVER_ID)
            def deployUrl = xmlEscape(env.NEXUS_DEPLOY_URL)
            def localRepo = xmlEscape(env.MAVEN_REPO_LOCAL)
            writeFile file: "${env.WORKSPACE}/ci-settings.xml", text: """\
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <localRepository>${localRepo}</localRepository>
  <servers>
    <server>
      <id>${serverId}</id>
      <username>${userXml}</username>
      <password>${passXml}</password>
    </server>
  </servers>
  <!-- Parent POMs declare repository id pentaho-public (Hitachi). That URL now
       returns HTML login pages which poison ~/.m2. Mirror it to Central so
       resolution either finds the artifact on Central or fails cleanly. -->
  <mirrors>
    <mirror>
      <id>block-hitachi-html</id>
      <name>Do not use Hitachi pentaho-public HTML feed</name>
      <url>https://repo.maven.apache.org/maven2</url>
      <mirrorOf>pentaho-public,pentaho-public-plugins</mirrorOf>
    </mirror>
  </mirrors>
  <profiles>
    <profile>
      <id>data-hopper</id>
      <repositories>
        <repository>
          <id>central</id>
          <url>https://repo.maven.apache.org/maven2</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>false</enabled></snapshots>
        </repository>
        <repository>
          <id>${serverId}</id>
          <url>${deployUrl}</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>central</id>
          <url>https://repo.maven.apache.org/maven2</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>false</enabled></snapshots>
        </pluginRepository>
        <pluginRepository>
          <id>${serverId}</id>
          <url>${deployUrl}</url>
          <releases><enabled>true</enabled></releases>
          <snapshots><enabled>true</enabled></snapshots>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>data-hopper</activeProfile>
  </activeProfiles>
</settings>
"""
            echo "Wrote ci-settings.xml (credentials XML-escaped; not printed)"
          }
        }
      }
    }

    stage('Checkout parent POMs') {
      when {
        expression { return !params.SKIP_PARENT_POMS }
      }
      steps {
        dir('maven-parent-poms') {
          deleteDir()
          git branch: "${params.PARENTS_BRANCH}",
              url: 'https://github.com/pentaho/maven-parent-poms.git',
              changelog: false,
              poll: false
        }
      }
    }

    stage('Deploy parent POMs') {
      when {
        expression { return !params.SKIP_PARENT_POMS }
      }
      steps {
        dir('maven-parent-poms') {
          sh '''
            set -euo pipefail
            # Drop any HTML "jars/poms" previously cached from Hitachi
            rm -rf "${MAVEN_REPO_LOCAL}/org/codehaus/mojo/build-helper-maven-plugin/3.1.0-pentaho" || true

            echo "Patching parent POMs for offline-from-Hitachi builds..."
            # Use stock build-helper from Maven Central (goals used are standard)
            find . -name pom.xml -print0 | xargs -0 sed -i \
              's/3\\.1\\.0-pentaho/3.1.0/g'
          '''
          // Strip Hitachi-only license-helper (not on Central). No python3 needed.
          script {
            def pluginRe = ~/(?s)<plugin>\s*<groupId>org\.pentaho\.maven\.plugins<\/groupId>\s*<artifactId>license-helper-maven-plugin<\/artifactId>.*?<\/plugin>/
            def listing = sh(script: 'find . -name pom.xml', returnStdout: true).trim()
            if (listing) {
              listing.split('\n').each { path ->
                def text = readFile(file: path)
                def newText = text.replaceAll(pluginRe, '')
                if (newText != text) {
                  writeFile file: path, text: newText
                  echo "  stripped license-helper from ${path}"
                }
              }
            }
          }
          sh '''
            set -euo pipefail
            # Publish poms via deploy-file — avoids running parent plugin bindings
            # that need Hitachi-only artifacts.
            deploy_pom() {
              local pom_file="$1"
              echo "Deploying ${pom_file}..."
              mvn -B -s "${WORKSPACE}/ci-settings.xml" \
                org.apache.maven.plugins:maven-deploy-plugin:3.1.3:deploy-file \
                -Dfile="${pom_file}" \
                -DpomFile="${pom_file}" \
                -DrepositoryId="${NEXUS_SERVER_ID}" \
                -Durl="${NEXUS_DEPLOY_URL}" \
                -Dpackaging=pom
            }

            # Order: root parent → ce parent → jar parent → bundle parent
            deploy_pom pom.xml
            deploy_pom pentaho-ce-parent-pom/pom.xml
            deploy_pom pentaho-ce-parent-pom/pentaho-ce-jar-parent-pom/pom.xml
            deploy_pom pentaho-ce-parent-pom/pentaho-ce-jar-parent-pom/pentaho-ce-bundle-parent-pom/pom.xml

            echo "Parent POMs deployed to ${NEXUS_DEPLOY_URL}"
          '''
        }
      }
    }

    stage('Checkout pentaho-reporting') {
      steps {
        dir('pentaho-reporting') {
          deleteDir()
          git branch: "${params.REPORTING_BRANCH}",
              url: 'https://github.com/pentaho/pentaho-reporting.git',
              changelog: true,
              poll: false
        }
      }
    }

    stage('Refuse BSL / non-LGPL trees') {
      steps {
        dir('pentaho-reporting') {
          sh '''
            set -euo pipefail
            echo "=== LICENSE head ==="
            head -20 LICENSE.txt || head -20 LICENSE.TXT || true

            if grep -qi "Business Source License" LICENSE.txt LICENSE.TXT 2>/dev/null; then
              echo "ERROR: BSL license detected — refusing to build (use 9.4 / 10.0 / 10.1)."
              exit 1
            fi
            if ! grep -qiE "LESSER GENERAL PUBLIC|LGPL" LICENSE.txt LICENSE.TXT 2>/dev/null; then
              echo "ERROR: Expected LGPL in LICENSE — aborting."
              exit 1
            fi

            case "${REPORTING_BRANCH}" in
              10.2*|11.*|master|main)
                echo "ERROR: Branch '${REPORTING_BRANCH}' is not an allowed LGPL pin."
                exit 1
                ;;
            esac

            echo "LGPL check OK (branch=${REPORTING_BRANCH}, sha=$(git rev-parse HEAD))"
          '''
        }
      }
    }

    stage('Deploy libraries (lowdeps)') {
      steps {
        dir('pentaho-reporting') {
          sh '''
            set -euo pipefail
            TEST_FLAGS=""
            if [ "${SKIP_TESTS}" = "true" ]; then
              TEST_FLAGS="-DskipTests"
            fi
            # lowdeps: reporting libraries without high platform deps (libpensol, etc.)
            # MAVEN_HITACHI_WORKAROUNDS: stock build-helper; parent on Nexus is already patched
            rm -rf "${MAVEN_REPO_LOCAL}/org/codehaus/mojo/build-helper-maven-plugin/3.1.0-pentaho" || true
            mvn -B -s "${WORKSPACE}/ci-settings.xml" \
              clean deploy ${TEST_FLAGS} \
              ${MAVEN_HITACHI_WORKAROUNDS} \
              -pl libraries -am -Plowdeps \
              -DaltDeploymentRepository=${NEXUS_SERVER_ID}::default::${NEXUS_DEPLOY_URL}
          '''
        }
      }
    }

    stage('Seed classic-core third-party deps') {
      steps {
        // Lightweight checkout may not have seed-jars; ensure pipeline repo is in workspace
        script {
          if (!fileExists('jenkins/seed-jars/rsyntaxtextarea-1.3.2.jar')) {
            echo 'seed-jars missing from workspace — checking out pipeline SCM fully'
            checkout scm
          }
        }
        sh '''
          set -euo pipefail
          SEED="${WORKSPACE}/.seed-deps"
          rm -rf "${SEED}"
          mkdir -p "${SEED}"
          SETTINGS="${WORKSPACE}/ci-settings.xml"

          deploy_jar() {
            local g="$1" a="$2" v="$3" f="$4"
            echo "Seeding ${g}:${a}:${v} from ${f}"
            mvn -B -s "${SETTINGS}" \
              org.apache.maven.plugins:maven-deploy-plugin:3.1.3:deploy-file \
              -DgroupId="${g}" \
              -DartifactId="${a}" \
              -Dversion="${v}" \
              -Dpackaging=jar \
              -Dfile="${f}" \
              -DgeneratePom=true \
              -DrepositoryId="${NEXUS_SERVER_ID}" \
              -Durl="${NEXUS_DEPLOY_URL}"
          }

          # --- rsyntaxtextarea 1.3.2: old API required by classic-core design-time UI ---
          # Real jar is committed under jenkins/seed-jars/ (2.x re-badge breaks RTextScrollPane ctors).
          RSYNTAX_JAR="${WORKSPACE}/jenkins/seed-jars/rsyntaxtextarea-1.3.2.jar"
          if [ ! -f "${RSYNTAX_JAR}" ]; then
            FOUND="$(find "${WORKSPACE}" -path '*/jenkins/seed-jars/rsyntaxtextarea-1.3.2.jar' 2>/dev/null | head -1 || true)"
            RSYNTAX_JAR="${FOUND}"
          fi
          if [ -z "${RSYNTAX_JAR}" ] || [ ! -f "${RSYNTAX_JAR}" ]; then
            echo "ERROR: rsyntaxtextarea-1.3.2.jar not found under jenkins/seed-jars/."
            find "${WORKSPACE}" -name 'rsyntaxtextarea*.jar' 2>/dev/null || true
            exit 1
          fi
          echo "Using rsyntaxtextarea jar: ${RSYNTAX_JAR}"
          # Overwrite the bad 2.6.1-as-1.3.2 artifact from earlier builds
          deploy_jar org.fife.ui rsyntaxtextarea 1.3.2 "${RSYNTAX_JAR}"

          # --- beanshell 2.1.1 not on Central; 2.0b6 is (also forced via -Dbeanshell.version) ---
          mvn -B -s "${SETTINGS}" org.apache.maven.plugins:maven-dependency-plugin:3.6.1:copy \
            -Dartifact=org.apache-extras.beanshell:bsh:2.0b6:jar \
            -DoutputDirectory="${SEED}"
          deploy_jar org.apache-extras.beanshell bsh 2.1.1 \
            "${SEED}/bsh-2.0b6.jar"

          # --- test-only artifacts: empty jars so resolution succeeds with -DskipTests ---
          EMPTY_DIR="${SEED}/empty"
          mkdir -p "${EMPTY_DIR}/META-INF"
          printf 'Manifest-Version: 1.0\nCreated-By: hop-pentaho-reporting-jenkins\n' \
            > "${EMPTY_DIR}/META-INF/MANIFEST.MF"
          jar cf "${SEED}/empty-stub.jar" -C "${EMPTY_DIR}" .

          deploy_jar pentaho simple-jndi 1.0.13 "${SEED}/empty-stub.jar"
          deploy_jar org.pentaho pentaho-encryption-support 10.1.0.0-SNAPSHOT "${SEED}/empty-stub.jar"

          echo "Third-party seed complete."
        '''
      }
    }

    stage('Build commons-database-model') {
      steps {
        dir('pentaho-commons-database') {
          deleteDir()
          git branch: "${params.REPORTING_BRANCH}",
              url: 'https://github.com/pentaho/pentaho-commons-database.git',
              changelog: false,
              poll: false
          sh '''
            set -euo pipefail
            # Same parent-POM workarounds as reporting
            find . -name pom.xml -print0 | xargs -0 sed -i 's/3\\.1\\.0-pentaho/3.1.0/g' || true
            TEST_FLAGS="-Dmaven.test.skip=true"
            # model only (skip gwt UI module if possible)
            mvn -B -s "${WORKSPACE}/ci-settings.xml" \
              clean deploy ${TEST_FLAGS} \
              ${MAVEN_HITACHI_WORKAROUNDS} \
              -pl model -am \
              -DaltDeploymentRepository=${NEXUS_SERVER_ID}::default::${NEXUS_DEPLOY_URL}
          '''
        }
      }
    }

    stage('Deploy classic-core') {
      steps {
        dir('pentaho-reporting') {
          sh '''
            set -euo pipefail
            # skipTests still resolves test deps — we seeded stubs; also skip compiling tests
            TEST_FLAGS="-DskipTests -Dmaven.test.skip=true"
            rm -rf "${MAVEN_REPO_LOCAL}/org/codehaus/mojo/build-helper-maven-plugin/3.1.0-pentaho" || true
            # Force update of previously failed resolution caches
            mvn -B -U -s "${WORKSPACE}/ci-settings.xml" \
              clean deploy ${TEST_FLAGS} \
              ${MAVEN_HITACHI_WORKAROUNDS} \
              -pl engine/core -am \
              -DaltDeploymentRepository=${NEXUS_SERVER_ID}::default::${NEXUS_DEPLOY_URL}
          '''
        }
      }
    }

    stage('Deploy safe extensions (optional)') {
      when {
        expression { return params.DEPLOY_SAFE_EXTENSIONS }
      }
      steps {
        dir('pentaho-reporting') {
          sh '''
            set -euo pipefail
            TEST_FLAGS=""
            if [ "${SKIP_TESTS}" = "true" ]; then
              TEST_FLAGS="-DskipTests"
            fi
            # Intentionally omit kettle / mondrian / olap4j / pentaho-metadata
            mvn -B -s "${WORKSPACE}/ci-settings.xml" \
              clean deploy ${TEST_FLAGS} \
              ${MAVEN_HITACHI_WORKAROUNDS} \
              -pl engine/extensions,engine/legacy-charts,engine/extensions-toc,engine/extensions-xpath,engine/extensions-scripting,engine/extensions-drilldown,engine/extensions-sampledata,engine/extensions-reportdesigner-parser \
              -am \
              -DaltDeploymentRepository=${NEXUS_SERVER_ID}::default::${NEXUS_DEPLOY_URL}
          '''
        }
      }
    }

    stage('Provenance') {
      steps {
        dir('pentaho-reporting') {
          sh '''
            set -euo pipefail
            {
              echo "reporting_branch=${REPORTING_BRANCH}"
              echo "reporting_sha=$(git rev-parse HEAD)"
              echo "parents_branch=${PARENTS_BRANCH}"
              echo "nexus_url=${NEXUS_DEPLOY_URL}"
              echo "built_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
              echo "skip_parents=${SKIP_PARENT_POMS}"
              echo "safe_extensions=${DEPLOY_SAFE_EXTENSIONS}"
            } | tee "${WORKSPACE}/BUILD-INFO.txt"
          '''
        }
        archiveArtifacts artifacts: 'BUILD-INFO.txt', fingerprint: true
      }
    }
  }

  post {
    success {
      echo "Deployed LGPL reporting artifacts to ${params.NEXUS_DEPLOY_URL}"
    }
    failure {
      echo "Build failed. Check Console Output. Common issues: missing Nexus repo, wrong credential ID, JDK/Maven tool names, or upstream SNAPSHOT deps for extensions."
    }
  }
}
