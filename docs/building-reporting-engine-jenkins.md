# Building LGPL Pentaho Reporting on Jenkins → Data Hopper Nexus

## Why we cannot just “pull from Maven”

1. **Hitachi’s public feed is unreliable.**  
   `https://repo.orl.eng.hitachivantara.com/artifactory/pnt-mvn/` often returns **HTML (login/UI)** instead of JARs/POMs. Maven then caches garbage as `.jar` files.

2. **Upstream versions are SNAPSHOTs of a private suite.**  
   Branch `10.1` of `pentaho-reporting` parents on  
   `org.pentaho:pentaho-ce-jar-parent-pom:10.1.0.0-SNAPSHOT`, which is **not on Maven Central**. Full suite builds also expect other Hitachi SNAPSHOTs (metadata, platform, kettle, …) for designer/extensions.

3. **License line we care about is frozen.**  
   | Branch / line | License | Use? |
   |---------------|---------|------|
   | `9.4`, `10.0`, `10.1` | **LGPL-2.1** | Yes |
   | `10.2+`, `master` | **BSL 1.1** | **No** |

Goal: build **libraries + classic engine** (LGPL) on [jenkins.data-hopper.com](https://jenkins.data-hopper.com/), deploy to [repository.data-hopper.com](https://repository.data-hopper.com/), and resolve them from `hop-pentaho-reporting`.

---

## Recommended architecture

```text
┌─────────────────────────────┐
│ Jenkins agent (JDK 11/17)   │
│  1) maven-parent-poms @10.1 │ ──► mvn deploy
│  2) pentaho-reporting @10.1 │ ──► mvn deploy (libs + engine only)
└──────────────┬──────────────┘
               │
               ▼
┌──────────────────────────────────────────────┐
│ Nexus hosted:  pentaho-reporting-lgpl        │
│ https://repository.data-hopper.com/          │
│   repository/pentaho-reporting-lgpl/         │
│ (Mixed: SNAPSHOT + release)                  │
└──────────────┬───────────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────────┐
│ hop-pentaho-reporting (JDK 21)               │
│  repository: pentaho-reporting-lgpl          │
│  deps: classic-core, libbase, …              │
└──────────────────────────────────────────────┘
```

Use a **dedicated Nexus hosted repo** (do not mix with `hopper` application artifacts unless you want one big kitchen sink). Suggested name:

| Field | Suggestion |
|-------|------------|
| Nexus repo id / Maven server id | `pentaho-reporting-lgpl` |
| URL | `https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/` |
| Format | Maven 2 |
| Version policy | **Mixed** (or split snapshots/releases) |
| Layout | Strict |

Credentials: Jenkins credentials store + agent `~/.m2/settings.xml` (same pattern as hopper publishing).

---

## What to build (minimal vs full)

For **Hop Reporting Output** you need the **runtime engine**, not Report Designer.

### Minimal (recommended first pipeline)

| Step | Repo | Branch | Maven modules / profiles | Artifacts produced |
|------|------|--------|---------------------------|--------------------|
| A | [pentaho/maven-parent-poms](https://github.com/pentaho/maven-parent-poms) | `10.1` (or matching line) | whole tree | `pentaho-ce-parent-pom`, `pentaho-ce-jar-parent-pom`, … |
| B | [pentaho/pentaho-reporting](https://github.com/pentaho/pentaho-reporting) | **`10.1`** | `libraries` (lowdeps) + `engine` (core + safe extensions) | `libbase`, `libfonts`, `classic-core`, … |

**Skip** on first pass:

- `designer` / `assemblies` (PRD UI)
- `extensions-kettle` (pulls PDI)
- `extensions-mondrian` / `extensions-olap4j` / `extensions-pentaho-metadata` (platform/Mondrian SNAPSHOTs)

Those extensions are what force you into the rest of the Hitachi suite.

### Full stack (later, optional)

Full `mvn clean install -Drelease` builds designer + OSGi and needs many more SNAPSHOTs. Only do this if you want PRD itself, not just Hop export.

---

## Jenkinsfile (this is the “source pin”)

The ready-to-run pipeline lives at the repo root:

**[`Jenkinsfile`](../Jenkinsfile)**

It clones fixed branches (`10.1` by default), refuses BSL trees, builds libraries +
`classic-core`, and deploys to Nexus. Optional checkbox deploys a few safe
extensions (no Kettle/Mondrian).

### Create the job in the web UI (no Docker shell)

1. Open https://jenkins.data-hopper.com/
2. **New Item** → name e.g. `pentaho-reporting-lgpl-engine` → **Pipeline** → OK  
3. **Pipeline** section:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git** → URL of this repo (`hop-pentaho-reporting`)
   - Script Path: `Jenkinsfile`
4. **Manage Jenkins → Credentials**: add Username/Password for Nexus  
   - Suggested ID: `nexus-pentaho-reporting-lgpl` (must match job parameter default, or change the parameter on first build)
5. **Manage Jenkins → Tools**: JDK / Maven names `jdk-11` and `maven-3.9`  
   - Or edit the `tools { }` block in the Jenkinsfile to match your existing tool names
6. Nexus: create hosted repo `pentaho-reporting-lgpl` if it does not exist yet  
7. Open the job → **Build with Parameters** → **Build**

You do **not** run `git clone` / `mvn` by hand in the container. Console Output shows those steps as the pipeline runs.

### Parameters you can change per build

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `REPORTING_BRANCH` | `10.1` | LGPL pin for pentaho-reporting |
| `PARENTS_BRANCH` | `10.1` | LGPL pin for maven-parent-poms |
| `NEXUS_DEPLOY_URL` | `…/repository/pentaho-reporting-lgpl/` | Deploy target |
| `NEXUS_CREDENTIALS_ID` | `nexus-pentaho-reporting-lgpl` | Jenkins credential |
| `SKIP_PARENT_POMS` | false | Skip after parents are already in Nexus |
| `SKIP_TESTS` | true | Faster first green |
| `DEPLOY_SAFE_EXTENSIONS` | false | Extra engine modules without kettle/mondrian |

### Optional safe extensions (still LGPL)

Enable **Deploy safe extensions** on the job. Still avoided: `extensions-kettle`,
`extensions-mondrian`, `extensions-olap4j`, `extensions-pentaho-metadata`.

---

## Version strategy

Upstream uses `10.1.0.0-SNAPSHOT`. Two options:

### A. Keep SNAPSHOT (simplest CI)

- Jenkins rebuilds `10.1` on a schedule or webhook
- Consumers depend on `10.1.0.0-SNAPSHOT`
- Fast iteration; less provenance

### B. Stamp a **Data Hopper release** version (recommended for production)

After a green build, re-version and deploy once:

```bash
# Example: freeze LGPL 10.1 tip as our own coordinate line
mvn versions:set -DnewVersion=10.1.0.0-dh1 -DgenerateBackupPoms=false
mvn clean deploy -DskipTests ...
```

Then pin `hop-pentaho-reporting`:

```xml
<pentaho-reporting.version>10.1.0.0-dh1</pentaho-reporting.version>
```

Document the **git SHA** of `pentaho-reporting` and `maven-parent-poms` in the Jenkins build description / a `BUILD-INFO.txt` artifact.

**Do not** publish BSL trees under this repo.

---

## Agent prerequisites (Jenkins node)

| Tool | Version | Jenkins tool **name** (must match `Jenkinsfile`) |
|------|---------|---------------------------------------------------|
| JDK | **11** | **`jdk-11`** (name on jenkins.data-hopper.com) |
| Maven | 3.9.x | **`Maven 3.9.9`** (already present on data-hopper) |
| Git | 2.x | usually on PATH |
| Disk | ≥ 5 GB free on agent workspace + local m2 | |
| Memory | `MAVEN_OPTS=-Xmx2g` minimum; 4g safer | |

### How to get Java 11 (Jenkins UI — no Docker shell)

You do **not** need to enter the Jenkins container for the normal path. Jenkins can download and install a JDK as a **global tool**:

1. Open **Manage Jenkins** → **Tools** (older UI: **Global Tool Configuration**).
2. Scroll to **JDK installations** → **Add JDK**.
3. **Name:** `jdk-11`  
   ⚠️ This string must match the Jenkinsfile: `jdk 'jdk-11'`.
4. Check **Install automatically**.
5. **Add Installer** → prefer one of:
   - **Install from adoptium.net** / **Eclipse Temurin** → version **11** (LTS), or  
   - **Extract *.zip/*.tar.gz** if you host a Temurin 11 tarball yourself.
6. Save.

On the next build, Jenkins downloads JDK 11 onto the agent and sets `JAVA_HOME` for the job. You do not install it system-wide.

**If “Install automatically” is missing:** install the **Oracle Java SE Development Kit Installer** / **JDK Tool** plugins, or use **Adoptium** installer plugin from Manage Plugins.

**Alternative (agent OS package):** only if tools UI is awkward — on the agent machine/container:

```bash
# Debian/Ubuntu example
apt-get update && apt-get install -y openjdk-11-jdk
```

Then either leave `tools { jdk 'jdk-11' }` pointing at a **manual** JDK entry (`JAVA_HOME` = `/usr/lib/jvm/java-11-openjdk-amd64`), or remove the `jdk` line from `tools {}` and ensure `java` on PATH is 11.

**Check after the first stage:** Console Output should show `java -version` → 11.x from stage **Show toolchain**.

### Maven tool name

This server already has Maven configured as **`Maven 3.9.9`**. The Jenkinsfile uses that exact name. If you rename the tool in Jenkins, update the Jenkinsfile to match.

Optional:

- Headless: already set via `MAVEN_OPTS` / pipeline env  
- Job-local m2: `MAVEN_REPO_LOCAL=$WORKSPACE/.m2/repository` (in Jenkinsfile)

**Clean polluted Hitachi downloads** on agents that already tried the public feed:

```bash
rm -rf ~/.m2/repository/org/pentaho/reporting
# or entire org/pentaho if you only use this for reporting
```

---

## Maven `settings.xml` for Jenkins (outline)

```xml
<settings>
  <servers>
    <server>
      <id>pentaho-reporting-lgpl</id>
      <username>${env.NEXUS_USER}</username>
      <password>${env.NEXUS_PASS}</password>
    </server>
  </servers>

  <profiles>
    <profile>
      <id>data-hopper</id>
      <repositories>
        <repository>
          <id>central</id>
          <url>https://repo.maven.apache.org/maven2</url>
        </repository>
        <repository>
          <id>pentaho-reporting-lgpl</id>
          <url>https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/</url>
          <snapshots><enabled>true</enabled></snapshots>
          <releases><enabled>true</enabled></releases>
        </repository>
        <!-- Optional fallback ONLY if still works for some third-party coords -->
        <!-- Prefer NOT using Hitachi as a mirrorOf * -->
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>central</id>
          <url>https://repo.maven.apache.org/maven2</url>
        </pluginRepository>
        <pluginRepository>
          <id>pentaho-reporting-lgpl</id>
          <url>https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/</url>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>data-hopper</activeProfile>
  </activeProfiles>
</settings>
```

---

## Example Declarative Jenkins pipeline

**Use the repo-root [`Jenkinsfile`](../Jenkinsfile)** — do not paste a second copy.
Create the job as **Pipeline script from SCM** pointing at this repository.


---

## Wire `hop-pentaho-reporting` to Nexus

In the plugin parent POM (after artifacts exist):

```xml
<repositories>
  <repository>
    <id>pentaho-reporting-lgpl</id>
    <url>https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/</url>
    <releases><enabled>true</enabled></releases>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>

<properties>
  <pentaho-reporting.version>10.1.0.0-SNAPSHOT</pentaho-reporting.version>
  <!-- or 10.1.0.0-dh1 after you stamp a release -->
</properties>
```

Then drop the `scripts/vendor-reporting-libs.sh` / `third-party/` path for CI builds (keep the script as a laptop bootstrap fallback).

Assembly can use `maven-dependency-plugin` `copy-dependencies` for runtime `lib/` instead of a flat vendor directory.

---

## Fallback if the full Maven build is too painful

If parent/suite SNAPSHOT resolution still fails after parents deploy:

### Bootstrap path (works today)

1. On a one-off agent, run  
   `scripts/vendor-reporting-libs.sh /path/to/pdi-*/plugins/pentaho-reporting-plugins/lib`  
   using a **9.4 CE** (or 10.1 CE if you have it) install.
2. Install/deploy those jars into Nexus with fixed GAVs (`org.pentaho.reporting.engine:classic-core:9.4.0.0-343`, …).
3. Later replace with source-built 10.1 when the pipeline is green.

That is **not** as pure as building from git, but it is LGPL and unblocks Hop while you stabilize Jenkins.

A small helper job can loop:

```bash
mvn deploy:deploy-file \
  -DgroupId=org.pentaho.reporting.engine \
  -DartifactId=classic-core \
  -Dversion=9.4.0.0-343 \
  -Dpackaging=jar \
  -Dfile=classic-core-9.4.0.0-343.jar \
  -DrepositoryId=pentaho-reporting-lgpl \
  -Durl=https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/
```

---

## Expected failure modes

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| HTML stored as `.jar` | Hitachi URL hit | Remove `org/pentaho` from local m2; stop using that mirror |
| Missing `pentaho-ce-jar-parent-pom` | Parents not deployed | Stage A of pipeline |
| Missing `pentaho-metadata` / kettle | Built highdeps extensions | Stay on `-Plowdeps` + `engine/core` |
| Tests fail on headless agent | AWT/fonts | `-DskipTests` first; add headless props later |
| Deploy 401 | Wrong server id / credentials | `server.id` must match `repositoryId` / altDeploymentRepository id |

---

## Suggested Jenkins job split

1. **`pentaho-maven-parent-poms-lgpl`** — rare, only when parent branch changes  
2. **`pentaho-reporting-lgpl-engine`** — weekly or on manual trigger; libraries + classic-core (+ safe extensions)  
3. **`hop-pentaho-reporting`** — builds the Hop plugin against Nexus  

Job (2) is the one that replaces “we can’t fetch the libraries from Maven anymore.”

---

## Success criteria

- [ ] Nexus hosts `classic-core` + `libbase`/`libloader`/`libfonts`/`librepository`/… under LGPL line  
- [ ] LICENSE check stage refuses BSL trees  
- [ ] `hop-pentaho-reporting` resolves those GAVs without `third-party/`  
- [ ] BUILD-INFO records git SHA of reporting source  
- [ ] Agents never use Hitachi as `mirrorOf *`

---

## References

- Upstream build notes (10.1 README): JDK 11+, Maven 3+, `mvn clean install`  
- Parent POMs: https://github.com/pentaho/maven-parent-poms/tree/10.1  
- Reporting: https://github.com/pentaho/pentaho-reporting/tree/10.1  
- Data Hopper Nexus pattern (hopper app): `hopper-presentation-core/docs/publishing.md`  
- This plugin license baseline: [LICENSE-BASELINE.md](LICENSE-BASELINE.md)
