# Hop Pentaho Reporting

Third-party **Apache Hop** transform plugin that generates reports from Pentaho
Reporting design files (`.prpt`).

| | |
|---|---|
| License | **LGPL-2.1** (see [LICENSE](LICENSE)) |
| Hop | 2.18.1+ (Java 21) |
| Status | **Runtime wired** — LGPL classic engine export (PDF/HTML/Excel/CSV/RTF) |
| Engine | **9.4.0.0-343** LGPL (vendored; upgrade path to 10.1.x LGPL) |
| ASF? | **No** — cannot ship inside Apache Hop (LGPL reporting engine) |

## Why this exists

PDI ships a *Pentaho Reporting Output* step. It was never ported into Apache Hop
because the classic reporting engine is LGPL (and 10.2+ is BSL), which is
incompatible with ASF releases. This repository provides that capability as an
**external** plugin so migrators can keep generating PDF/HTML/Excel reports.

## Build

Requirements:

- JDK 21
- Maven 3.9+
- Apache Hop 2.18.1 artifacts available (local `~/.m2` or Maven Central)
- LGPL reporting jars vendored (see below)

### Vendor the classic engine (LGPL only)

Hitachi’s public Maven feed is unreliable for these artifacts.

**Preferred long-term:** build LGPL `10.1` on Jenkins and publish to Nexus — use
the root [`Jenkinsfile`](Jenkinsfile) (Pipeline script from SCM) and
[docs/building-reporting-engine-jenkins.md](docs/building-reporting-engine-jenkins.md).

**Laptop bootstrap:** copy jars from a **PDI CE** install (plugin lib) that still
uses LGPL reporting (9.4 / 10.0 / 10.1):

```bash
scripts/vendor-reporting-libs.sh \
  /path/to/data-integration/plugins/pentaho-reporting-plugins/lib
```

This fills `third-party/pentaho-reporting-lib/` (gitignored) and installs compile
artifacts into `~/.m2`. **Do not** vendor 10.2+ (BSL).

```bash
mvn clean package
```

Plugin zip:

```text
assemblies/pentaho-reporting-output/target/hop-pentaho-reporting-output-1.0.0-SNAPSHOT.zip
```

## Install

### Manual zip

Marketplace-style zip expands into the **Hop client root** (not only `plugins/`):

```bash
unzip hop-pentaho-reporting-output-*.zip -d "$HOP_HOME"
```

Expected layout:

```text
$HOP_HOME/plugins/transforms/pentaho-reporting-output/
  hop-transform-pentaho-reporting-output-*.jar
  version.xml
  LICENSE
  NOTICE
  lib/          # LGPL classic engine jars
```

The zip intentionally **does not** ship jars already on Hop’s app classpath
(`commons-vfs2`, `commons-io`, Guava, SLF4J, …). Hop’s plugin classloader is
child-first; a second `commons-vfs2` causes `LinkageError` when the transform
calls `HopVfs.getFileObject()` (`FileObject` from two classloaders).

Restart Hop. The transform appears under **Output** as **Pentaho Reporting Output**
(plugin id `PentahoReportingOutput`).

### Hop 2.19 marketplace (Nexus)

You publish **one zip** that already includes the transform **and** the reporting
engine jars under `lib/`. Users only install that zip.

```bash
# After classic-core is on pentaho-reporting-lgpl (engine Jenkins job)
export NEXUS_USER=... NEXUS_PASSWORD=...
./scripts/publish-to-marketplace.sh
```

Details: [docs/marketplace-publish.md](docs/marketplace-publish.md).

Users (Hop **2.19+**):

```bash
# once: register the Data Hopper community repo
./hop marketplace repo import hop-marketplace-repo.yaml

# install (example output below)
./hop marketplace install hop-pentaho-reporting-output
# Restart Hop so the plugin loads
```

Example install session:

```text
$ sh hop marketplace install hop-pentaho-reporting-output
Resolved hop-pentaho-reporting-output → org.projectdatahopper.hop:hop-pentaho-reporting-output:1.0.0-SNAPSHOT (prefer repo 'data-hopper-community')
… Marketplace - Downloading org.projectdatahopper.hop:hop-pentaho-reporting-output:1.0.0-SNAPSHOT from https://repository.data-hopper.com/repository/hop-community-plugins/…
Downloading: org.projectdatahopper.hop:hop-pentaho-reporting-output:1.0.0-SNAPSHOT (65.8MB)
  [████████████████████████] 100%
… Marketplace - Installed org.projectdatahopper.hop:hop-pentaho-reporting-output:1.0.0-SNAPSHOT. Restart Hop to load the plugin.
Plugin … installed under <HOP_HOME> from repo 'data-hopper-community'. Restart Hop to load it.
```

After restart, the transform **Pentaho Reporting Output** appears under **Output**  
(plugin id `PentahoReportingOutput`).

Repository: `https://repository.data-hopper.com/repository/hop-community-plugins/`  
GAV: `org.projectdatahopper.hop:hop-pentaho-reporting-output:{version}` (zip, ~66MB including LGPL engine)

## Current behavior

- Dialog configures report path, output path, processor type, parameters, and
  **JNDI → Hop connection** mappings
- Metadata saves/loads with Hop `@HopMetadataProperty` (legacy-style XML keys)
- Runtime boots the classic engine, rewrites JNDI SQL providers to Hop RDBMS
  connections, binds parameters, and exports PDF / HTML / Excel / CSV / RTF via
  Hop VFS (plugin-isolated classloader + vendored `lib/`)

### Database / JNDI (important for migrated `.prpt` files)

Pentaho Report Designer and PDI typically store SQL datasources as **JNDI names**
(e.g. `SampleData`), not JDBC URLs. PDI resolved those via file-based
**simple-jndi** (`$PDI_HOME/simple-jndi/jdbc.properties`). Apache Hop has no
equivalent JNDI environment.

This plugin **rewrites** JNDI connection providers on the loaded report to use
Hop **RDBMS connection** metadata before export:

1. **Explicit map** in the transform dialog: JNDI name → Hop connection
2. **Same-name** (default on): JNDI `SampleData` binds to a Hop connection
   named `SampleData` if it exists
3. Otherwise fail early with a clear error listing unbound names and available
   Hop connections (toggle **Fail if JNDI datasource is not mapped**)

**Quick fix for the classic sample error** (`Cannot find the requested datasource
'SampleData'`): create a Hop RDBMS connection named `SampleData` (or map
`SampleData` → your connection in the transform), ensure the JDBC driver is
available to Hop, then re-run.

Reports that already use driver/URL connections in the `.prpt` are left unchanged.

### Charts (legacy JFreeChart)

PRD chart samples use **legacy-chart** elements (`legacy-charts` module + JFreeChart).
The plugin zip must include `legacy-charts`, `jfreechart`, and `jcommon` under
`lib/`. Without them, export still produces a valid but **blank** PDF (≈900 bytes).

| Jar | How it gets into the zip |
|-----|---------------------------|
| `jfreechart` / `jcommon` | Maven Central (plugin dependencies) |
| `legacy-charts` | Committed seed: `jenkins/seed-jars/legacy-charts-*.jar` (assembly) — **not** a hard Maven dep, so marketplace CI does not need it on Nexus |

Optional later: engine job **DEPLOY_SAFE_EXTENSIONS** can publish a matching
`legacy-charts` to `pentaho-reporting-lgpl`; the seed remains the reliable path.

## Report Designer (client download)

To **author** `.prpt` files you need Pentaho Report Designer (PRD). The last
**LGPL** line is **10.1** (10.2+ is BSL). We publish a client zip on the same
Nexus repo as the libraries:

| | |
|--|--|
| GAV | `org.pentaho.reporting:prd-ce:10.1.0.0-dh1:zip` |
| Repo | `https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/` |
| Build | [`Jenkinsfile.prd`](Jenkinsfile.prd) — see [docs/building-prd-jenkins.md](docs/building-prd-jenkins.md) |

```bash
# after the PRD Jenkins job has published:
curl -fLO \
  "https://repository.data-hopper.com/repository/pentaho-reporting-lgpl/org/pentaho/reporting/prd-ce/10.1.0.0-dh1/prd-ce-10.1.0.0-dh1.zip"
unzip prd-ce-10.1.0.0-dh1.zip -d ~/tools
cd ~/tools/report-designer && ./report-designer.sh   # JDK 11+
```

Default CI mode is a **slim** Hop companion (layout/charts + several datasources;
JDBC connection UI needs the fuller PDI stack). Manual re-host of an existing
LGPL CE zip:

```bash
export NEXUS_USER=... NEXUS_PASSWORD=...
./scripts/publish-prd-zip.sh /path/to/prd-ce.zip --version 10.1.0.0-dh1
```

## Planned

1. Optional bump Hop plugin runtime to **10.1.x** LGPL when artifacts resolve cleanly
2. Full PRD CE (JDBC UI + Kettle/Mondrian) once suite deps are on Nexus
3. “Get parameters / JNDI names from .prpt” in the dialog
4. Optional simple-jndi file compatibility mode for PDI drop-in configs
5. Integration tests and sample `.prpt` pipelines
6. Mondrian/OLAP JNDI providers if needed

## License baseline (do not break)

| Component | Allowed | Forbidden |
|-----------|---------|-----------|
| Reporting engine | 10.1.x LGPL-2.1 | 10.2+, 11.x, BSL master |
| Step source baseline | Pre-BSL Kettle (Apache-2.0 headers) | Post-`HNC-766` BSL header trees as copy source |

## Modules

```text
hop-pentaho-reporting/
  plugins/pentaho-reporting-output/   # transform jar
  assemblies/pentaho-reporting-output/  # installable zip
  samples/                            # LGPL .prpt samples from PRD CE
  Jenkinsfile                         # engine → Nexus
  Jenkinsfile.prd                     # Report Designer client zip → Nexus
  jenkins/prd-slim/                   # slim PRD assembly
  scripts/publish-prd-zip.sh          # manual PRD zip publish
```

See [samples/README.md](samples/README.md) for report categories and re-import notes.
