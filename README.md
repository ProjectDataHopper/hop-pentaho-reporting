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

Unzip the assembly into your Hop client `plugins/` folder:

```bash
unzip hop-pentaho-reporting-output-*.zip -d "$HOP_HOME/plugins"
```

Expected layout:

```text
$HOP_HOME/plugins/transforms/pentaho-reporting-output/
  hop-transform-pentaho-reporting-output-*.jar
  version.xml
  LICENSE
  NOTICE
  lib/          # classic engine jars land here in a later phase
```

Restart Hop. The transform appears under **Output** as **Pentaho Reporting Output**
(plugin id `PentahoReportingOutput`).

## Current behavior

- Dialog configures report path, output path, processor type, and parameters
- Metadata saves/loads with Hop `@HopMetadataProperty` (legacy-style XML keys)
- Runtime boots the classic engine, binds parameters, and exports PDF / HTML /
  Excel / CSV / RTF via Hop VFS (plugin-isolated classloader + vendored `lib/`)

## Planned

1. Optional bump to **10.1.x** LGPL when artifacts can be resolved cleanly
2. “Get parameters from .prpt” in the dialog
3. Integration tests and sample `.prpt` pipelines
4. Exclude remaining optional/heavy extension jars if not needed

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
```

See [samples/README.md](samples/README.md) for report categories and re-import notes.
