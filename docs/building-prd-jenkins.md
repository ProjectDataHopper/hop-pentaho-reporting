# Building LGPL Pentaho Report Designer (PRD) → Data Hopper Nexus

Publish the **last LGPL** Report Designer as a **client download** next to the
reporting libraries on Nexus.

| | |
|--|--|
| LGPL pin | Branch **`10.1`** (`10.1.0.0-*`) |
| Forbidden | **`10.2+` / `master` (BSL 1.1)** |
| Nexus repo | `pentaho-reporting-lgpl` |
| Client GAV | `org.pentaho.reporting:prd-ce:{version}:zip` |
| Example URL | `https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/org/pentaho/reporting/prd-ce/10.1.0.0-dh1/prd-ce-10.1.0.0-dh1.zip` |
| Pipeline | [`Jenkinsfile.prd`](../Jenkinsfile.prd) |

Related: engine libraries only → [building-reporting-engine-jenkins.md](building-reporting-engine-jenkins.md).

---

## Why a separate job?

The Hop **Pentaho Reporting Output** plugin only needs the **classic engine**.
PRD is a **desktop product** (`assemblies/prd-ce`) that also pulls:

- Designer modules + UI libraries (`libformula-ui`, `configuration-editor`, commons-xul)
- Application launcher (`launcher.jar`)
- Real `simple-jndi` (not the empty stub used for engine test resolution)
- Full CE additionally: **PDI/Kettle**, Mondrian, metadata, many plugin zips, OSGi/Karaf

Stock `prd-ce` is therefore a multi-repo suite build. We ship a **slim** companion
first (enough to author many `.prpt` files for Hop), then expand toward full CE.

---

## Architecture

```text
┌──────────────────────────────────────────────┐
│ Jenkins: pentaho-reporting-lgpl-prd (JDK 11) │
│  seed runtime jars (simple-jndi, launcher)   │
│  libraries + classic-core + designer modules │
│  commons-xul @10.1                           │
│  assemble zip (slim or full-ce)              │
│  deploy zip → Nexus                          │
└──────────────────┬───────────────────────────┘
                   ▼
┌──────────────────────────────────────────────┐
│ Nexus: pentaho-reporting-lgpl                │
│  … libraries / classic-core (already)        │
│  … prd-ce/{version}/prd-ce-{version}.zip     │
└──────────────────────────────────────────────┘
                   ▼
         Desktop unzip → report-designer/
```

---

## Build modes

| Mode | Parameter | What you get | Missing |
|------|-----------|--------------|---------|
| **slim** (default) | `BUILD_MODE=slim` | Designer UI, charts, table/scriptable/xpath/external/reflection/CDA datasources, samples | JDBC connection UI, Kettle, Mondrian, PMD, OSGi/Hadoop, legacy `.report` XML parser (`reportdesigner-parser`) |
| **full-ce** | `BUILD_MODE=full-ce` | Stock upstream `prd-ce` assembly | Requires kettle + suite SNAPSHOTs on Nexus — experimental until Phase 4 stack is green |

Client version: stamp **`10.1.0.0-dh1`** (then `dh2`…) for redistributable downloads; use `*-SNAPSHOT` while iterating.

---

## Jenkins job setup

1. https://jenkins.data-hopper.com/ → **New Item** → `pentaho-reporting-lgpl-prd` → **Pipeline**
2. **Pipeline script from SCM** → this repo → Script path: **`Jenkinsfile.prd`**
3. Credentials: `nexus-pentaho-reporting-lgpl` (same as engine job)
4. Tools: **`jdk-11`**, **`Maven 3.9.9`**
5. Prefer: engine job already published parents + `classic-core`
6. **Build with Parameters** → `BUILD_MODE=slim`, `CLIENT_VERSION=10.1.0.0-dh1`

### Important parameters

| Parameter | Default | Notes |
|-----------|---------|-------|
| `REPORTING_BRANCH` | `10.1` | LGPL only |
| `XUL_BRANCH` | `10.1` | commons-xul |
| `BUILD_MODE` | `slim` | or `full-ce` |
| `CLIENT_VERSION` | `10.1.0.0-dh1` | zip version |
| `SKIP_PARENT_POMS` | true | parents usually already on Nexus |
| `SEED_RUNTIME_DEPS` | true | overwrites empty simple-jndi stub |
| `PUBLISH_ZIP` | true | deploy client zip |

Agent: ≥ **4 GB** heap (`MAVEN_OPTS`), **15+ GB** free disk for full-ce.

### commons-xul note

PRD only needs **`core` + `swing`** modules from `pentaho-commons-xul` @10.1  
(`-pl core,swing -am`). Building the full reactor fails on GWT (`gwt-incubator`,
`commons-gwt-widgets`). Do not use a full-reactor fallback.

---

## End-user install

```bash
curl -fLO \
  "https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/org/pentaho/reporting/prd-ce/10.1.0.0-dh1/prd-ce-10.1.0.0-dh1.zip"

unzip prd-ce-10.1.0.0-dh1.zip -d ~/tools
cd ~/tools/report-designer
chmod +x report-designer.sh set-pentaho-env.sh
./report-designer.sh    # Linux / macOS — JDK 11+ (21 OK); use bash, not: sh report-designer.sh
# report-designer.bat   # Windows
```

**Run notes (slim build):**

- Invoke with **`./report-designer.sh`** (shebang bash). `sh report-designer.sh` fails on `[[` under dash.
- Logging uses **log4j2** (`log4j-jcl` + `log4j-1.2-api` in `lib/`).
- Empty `plugins/` is created so the launcher classpath entry is valid.

Layout:

```text
report-designer/
  launcher.jar
  report-designer.sh | .bat
  lib/                 # classic engine + designer jars
  lib/jdbc/            # embedded DBs (H2/HSQL for samples)
  resources/
  samples/             # LGPL sample .prpt designs
```

Designed reports (`.prpt`) are consumed by the Hop marketplace plugin
`hop-pentaho-reporting-output` (engine jars should preferably be the same 10.1 line).

---

## Manual / bootstrap publish

If you already have a LGPL CE zip (e.g. historical 9.4/10.1 CE build):

```bash
export NEXUS_USER=... NEXUS_PASSWORD=...
./scripts/publish-prd-zip.sh /path/to/prd-ce.zip --version 10.1.0.0-dh1
```

Dry-run:

```bash
./scripts/publish-prd-zip.sh /path/to/prd-ce.zip --version 10.1.0.0-dh1 --dry-run
```

Seed runtime jars only (real simple-jndi, launcher, versionchecker):

```bash
./scripts/seed-prd-runtime-deps.sh
```

---

## Seed jars (`jenkins/seed-jars/`)

| File | Deployed as | Notes |
|------|-------------|-------|
| `simple-jndi-1.0.13.jar` | `pentaho:simple-jndi:1.0.13` | **Real** jar (1.0.10 CE content). Overwrites engine CI empty stub. |
| `pentaho-application-launcher.jar` | `pentaho:pentaho-application-launcher:10.1.0.0-SNAPSHOT` | Becomes `launcher.jar` in the zip |
| `pentaho-versionchecker-9.4.0.0-343.jar` | `pentaho:pentaho-versionchecker:10.1.0.0-SNAPSHOT` | Startup check |
| `rsyntaxtextarea-1.3.2.jar` | `org.fife.ui:rsyntaxtextarea:1.3.2` | Shared with engine job |

**Flat POMs only.** These jars embed Hitachi `parent` POMs (9.2 / 8.2 / 9.4)
that are not on our Nexus. The pipeline deploys an explicit flat POM (`-DpomFile`
+ `-DgeneratePom=false`). Using bare `-DgeneratePom=true` makes deploy-file
publish the embedded POM and classic-core fails with:

`Could not find artifact org.pentaho:pentaho-ce-jar-parent-pom:pom:9.2.0.1-364`

**Local m2 cache:** `simple-jndi:1.0.13` is a **release** coordinate. Maven will
keep a previously downloaded bad POM forever in the job workspace
(`.m2/repository`) even after Nexus is fixed. The seed stage therefore
**purges** that path and **`install-file`s** the flat POM into the job-local
repo before classic-core runs.
---

## Slim assembly source

[`jenkins/prd-slim/`](../jenkins/prd-slim/) — Maven assembly that stages `report-designer/`
from built modules + upstream CE resources/scripts (from the `pentaho-reporting`
checkout), then zips.

---

## Roadmap to full CE

1. ✅ Engine libraries on Nexus  
2. ✅ PRD slim pipeline + client zip coordinate  
3. ⬜ Build `kettle-core` / `kettle-engine` / `kettle-dbdialog` @10.1 → enable JDBC UI  
4. ⬜ Mondrian / metadata editors  
5. ⬜ Stock `assemblies/prd-ce` no-OSGi zip as `prd-ce` release artifact  
6. ⬜ Optional OSGi/hadoop-addon (low priority)

---

## Success criteria

- [ ] Nexus hosts `prd-ce` zip for LGPL 10.1  
- [ ] Zip launches on a desktop JDK 11+  
- [ ] Can open sample `.prpt` and save designs  
- [ ] Hop transform can render those designs  
- [ ] `BUILD-INFO.txt` records git SHAs + LGPL branch  
- [ ] No BSL 10.2+ content in the zip  

---

## References

- Upstream PRD CE: https://github.com/pentaho/pentaho-reporting/tree/10.1/assemblies/prd-ce  
- commons-xul 10.1: https://github.com/pentaho/pentaho-commons-xul/tree/10.1  
- Engine pipeline: [`Jenkinsfile`](../Jenkinsfile)  
- License baseline: [LICENSE-BASELINE.md](LICENSE-BASELINE.md)
