# Sample Pentaho Reporting designs (`.prpt`)

Sample reports from the Pentaho Reporting designer CE assembly:

- Upstream path: `assemblies/prd-ce/src/main/resources/resource/samples`
- Source tree: [pentaho/pentaho-reporting samples](https://github.com/pentaho/pentaho-reporting/tree/master/assemblies/prd-ce/src/main/resources/resource/samples)
- **Extracted from branch `10.1` (LGPL-2.1)**, not BSL `master`, so sample assets stay on the free engine line this plugin targets.

## Layout

| Folder | Contents |
|--------|----------|
| `Advanced/` | HTML, JDBC, scripting, parameters, preprocessors |
| `Charts/` | Area, bar, pie, scatter, XY, radar, waterfall, … |
| `Financial Reports/` | Income statement |
| `Operational Reports/` | Inventory, orders, sales, product analysis |
| `Production Reports/` | Invoice statements |
| `evaluation_blank.prpt` | Blank evaluation template |
| `metadata.xmi` / `steelwheels.mondrian.xml` | Metadata / Mondrian supporting files |

Many samples expect the classic **Steel Wheels** / **SampleData** database or other
datasources; rendering needs a Hop RDBMS connection (JNDI names are rewritten by
the transform). Chart samples need the plugin’s `legacy-charts` + JFreeChart jars
on the classpath (blank ~900-byte PDFs mean those jars are missing). Most chart
designs do **not** require Hop parameter mappings (defaults are embedded).

## Re-import

From a local `pentaho-reporting` clone:

```bash
git archive origin/10.1 assemblies/prd-ce/src/main/resources/resource/samples \
  | tar -x --strip-components=7 -C samples/
```
