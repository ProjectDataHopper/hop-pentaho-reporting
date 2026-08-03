# Seed jars for Jenkins / marketplace packaging

These jars are **not** always available on Maven Central (or under the GAV our
pipeline expects), but are required for a green build or full-featured plugin zip.

| File | Role | Source |
|------|------|--------|
| `rsyntaxtextarea-1.3.2.jar` | Engine job: `classic-core` design-time editor | PDI CE reporting lib (LGPL-era) |
| `legacy-charts-9.4.0.0-343.jar` | Marketplace zip: chart samples (legacy-chart + JFreeChart expressions) | PDI CE `plugins/pentaho-reporting-plugins/lib` |
| `simple-jndi-1.0.13.jar` | **Real** simple-jndi for PRD (overwrites empty engine stub) | PDI CE `lib/simple-jndi-1.0.10.jar` re-coordinated as 1.0.13 |
| `simple-jndi-1.0.10.jar` | Same bytes as 1.0.13 seed (provenance) | PDI CE |
| `pentaho-application-launcher.jar` | PRD `launcher.jar` | PDI CE `launcher/launcher.jar` |
| `pentaho-versionchecker-9.4.0.0-343.jar` | PRD startup version check GAV | PDI CE version-checker plugin |
| `barbecue-1.5-beta1.jar` | `barbecue:barbecue:1.5-beta1` (classic-extensions; not on Central under that GAV) | PDI CE reporting lib / same as Central `net.sourceforge.barbecue` |
| `avalon-framework-4.1.5.jar` | Optional seed; usually on Central as `avalon-framework:avalon-framework:4.1.5` | PDI CE reporting lib |
| `jcifs-1.3.3.jar` | `jcifs:jcifs:1.3.3` (designer ext-legacy-charts; removed from Central) | PDI CE `lib/` |

## rsyntaxtextarea

Deploy as `org.fife.ui:rsyntaxtextarea:1.3.2`.

**Do not** re-badge modern `com.fifesoft:rsyntaxtextarea:2.x` as 1.3.2 — the
design-time query editor needs the old
`RTextScrollPane(int,int,RTextArea,boolean)` constructor.

The engine `Jenkinsfile` stage **Seed classic-core third-party deps** deploys this
into `pentaho-reporting-lgpl` before building `classic-core`.

## legacy-charts

Not a compile dependency of the Hop transform. Packaged into
`plugins/transforms/pentaho-reporting-output/lib/` by the assembly from this
folder so marketplace CI does **not** need `legacy-charts` on Nexus.

When `pentaho-reporting-lgpl-engine` publishes a matching
`legacy-charts` (`DEPLOY_SAFE_EXTENSIONS`), you may switch the assembly to prefer
Maven; until then keep this seed jar.

JFreeChart (`jfree:jfreechart` / `jfree:jcommon`) still resolves from Maven Central.

## PRD runtime seeds

Used by [`Jenkinsfile.prd`](../../Jenkinsfile.prd) and
[`scripts/seed-prd-runtime-deps.sh`](../../scripts/seed-prd-runtime-deps.sh):

| Seed | Maven GAV |
|------|-----------|
| `simple-jndi-1.0.13.jar` | `pentaho:simple-jndi:1.0.13` |
| `pentaho-application-launcher.jar` | `pentaho:pentaho-application-launcher:10.1.0.0-SNAPSHOT` |
| `pentaho-versionchecker-9.4.0.0-343.jar` | `pentaho:pentaho-versionchecker:10.1.0.0-SNAPSHOT` |
| `barbecue-1.5-beta1.jar` | `barbecue:barbecue:1.5-beta1` |

**Critical:** the engine job may have deployed an **empty** `simple-jndi` stub so
tests resolve. PRD **must** overwrite it with the real jar before packaging.

**Critical (POM):** CE jars embed Hitachi parent POMs (e.g. `9.2.0.1-364`).  
Always deploy with a **flat** POM (`-DpomFile=... -DgeneratePom=false`).  
Bare `-DgeneratePom=true` on a real CE jar publishes the embedded parent POM and
breaks classic-core resolution.
