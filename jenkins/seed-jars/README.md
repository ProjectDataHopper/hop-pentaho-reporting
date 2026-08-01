# Seed jars for Jenkins / marketplace packaging

These jars are **not** always available on Maven Central (or under the GAV our
pipeline expects), but are required for a green build or full-featured plugin zip.

| File | Role | Source |
|------|------|--------|
| `rsyntaxtextarea-1.3.2.jar` | Engine job: `classic-core` design-time editor | PDI CE reporting lib (LGPL-era) |
| `legacy-charts-9.4.0.0-343.jar` | Marketplace zip: chart samples (legacy-chart + JFreeChart expressions) | PDI CE `plugins/pentaho-reporting-plugins/lib` |

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
