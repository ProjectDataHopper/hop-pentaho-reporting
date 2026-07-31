# Seed jars for Jenkins LGPL engine build

These jars are **not** available (or not under the correct GAV) on Maven Central,
but are required to compile Pentaho Reporting `classic-core` 10.1.

| File | Deploy as | Source |
|------|-----------|--------|
| `rsyntaxtextarea-1.3.2.jar` | `org.fife.ui:rsyntaxtextarea:1.3.2` | PDI CE reporting plugin lib (LGPL-era PRD stack) |

**Do not** re-badge modern `com.fifesoft:rsyntaxtextarea:2.x` as 1.3.2 — the
design-time query editor UI needs the old `RTextScrollPane(int,int,RTextArea,boolean)` constructor.

The Jenkinsfile stage **Seed classic-core third-party deps** deploys this file into
`pentaho-reporting-lgpl` on Nexus before building `classic-core`.
