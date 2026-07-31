# License baseline

This document freezes the license constraints for the port. Update SHAs when the
chosen baseline commits are locked in for source extraction.

## Reporting engine (runtime dependency)

| Item | Value |
|------|-------|
| Product | Pentaho Reporting classic engine |
| Allowed line | **9.4.x / 10.0.x / 10.1.x** (LGPL-2.1) |
| Currently vendored | **9.4.0.0-343** (from PDI CE plugin lib; LGPL verified on `origin/9.4`) |
| License | **LGPL-2.1** |
| Forbidden | 10.2.0+, 11.x, current `master` (**BSL 1.1**) |

Hitachi public Maven (`repo.orl.eng.hitachivantara.com`) currently serves HTML rather than jars.
Install binaries with:

```bash
scripts/vendor-reporting-libs.sh /path/to/data-integration/plugins/pentaho-reporting-plugins/lib
```

Local source clone (optional rebuilds): `/home/matt/git/pentaho/pentaho-reporting` branch `origin/10.1` or `origin/9.4`.

## PDI step source (port baseline)

| Item | Value |
|------|-------|
| Product | Pentaho Data Integration “Pentaho Reporting Output” step |
| Path | `plugins/pentaho-reporting/` in `pentaho-kettle` |
| Baseline rule | Last tree **before** BSL header rewrite `HNC-766` (`6c1a9a19ed0`, 2024-09-29) |
| Pre-BSL parent | `6c1a9a19ed0^` — Apache License 2.0 file headers |
| Forbidden as copy source | Files whose headers state Business Source License |

Example extraction:

```bash
cd /home/matt/git/pentaho/pentaho-kettle
git show '6c1a9a19ed0^:plugins/pentaho-reporting/impl/src/main/java/org/pentaho/di/trans/steps/pentahoreporting/PentahoReportingOutput.java' | head
```

## This project

| Item | Value |
|------|-------|
| License | LGPL-2.1 (`LICENSE`) |
| Distribution | Third-party / Project Data Hopper — **not** ASF |

## Rationale

Apache Hop is Apache-2.0 and cannot ship LGPL (or BSL) engines inside ASF
releases. An external LGPL plugin is the supported path for migrators who still
need `.prpt` rendering.
