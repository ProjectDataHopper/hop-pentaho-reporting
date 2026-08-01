# Publish Pentaho Reporting Output to Nexus + Hop marketplace

## Coordinates

| | |
|--|--|
| groupId | `org.projectdatahopper.hop` |
| artifactId | `hop-pentaho-reporting-output` |
| version | Maven `project.version` (e.g. `1.0.0-SNAPSHOT`) |
| packaging | **zip** (marketplace installable) |
| Nexus repo | `https://repository.data-hopper.com/repository/hop-community-plugins/` |
| Install path | `plugins/transforms/pentaho-reporting-output/` |

Zip layout (unzip into **Hop client root**):

```text
plugins/transforms/pentaho-reporting-output/
  hop-transform-pentaho-reporting-output-*.jar
  version.xml
  LICENSE
  NOTICE
  lib/   # LGPL classic engine jars
```

## One-time Nexus

Hosted Maven 2 repo **`hop-community-plugins`** (already used by hop-datavault):

- Anonymous **read** for marketplace install
- Deploy user with **write** (Jenkins credential id `hop-community-plugins` recommended)

## What gets published

**One zip** on `hop-community-plugins`. That zip already **contains** the Hop
transform **and** the LGPL reporting engine under `lib/`. End users only install
that zip via marketplace — they never touch a `third-party` folder.

Maven builds the zip by resolving `classic-core` (and dependencies) and putting
them into the zip. Those artifacts should already be on
`pentaho-reporting-lgpl` (from the engine Jenkins job). Optionally, jars in
`third-party/pentaho-reporting-lib/` are merged in as a bootstrap fallback.

## Package & publish (local)

```bash
# Engine must be resolvable (prefer successful pentaho-reporting-lgpl-engine job)
export NEXUS_USER=... NEXUS_PASSWORD=...
./scripts/publish-to-marketplace.sh
# optional: --hop-version 2.19.0-SNAPSHOT
```

## Jenkins job

1. **New Item** → Pipeline → **Pipeline script from SCM** → this repo  
2. **Script Path:** `Jenkinsfile.marketplace`  
3. Credential ID matching parameter (default `hop-community-plugins`)  
4. Tools: `jdk-21`, `Maven 3.9.9` (adjust names in the file if needed)  
5. Prefer: engine job has already published `classic-core` to `pentaho-reporting-lgpl`

## Appear in Hop 2.19 marketplace

Marketplace **UI/CLI requires Hop 2.19+**.

### CLI (verified)

```bash
# Once: register the Data Hopper community repo
./hop marketplace repo import hop-marketplace-repo.yaml
# or after push:
# ./hop marketplace repo import \
#   https://raw.githubusercontent.com/ProjectDataHopper/hop-pentaho-reporting/refs/heads/main/hop-marketplace-repo.yaml

./hop marketplace query | grep -i reporting
./hop marketplace install hop-pentaho-reporting-output
# Restart Hop GUI / hop-server
```

Example successful install:

```text
$ sh hop marketplace install hop-pentaho-reporting-output
Resolved hop-pentaho-reporting-output → org.projectdatahopper.hop:hop-pentaho-reporting-output:1.0.0-SNAPSHOT (prefer repo 'data-hopper-community')
2026/08/01 00:36:11 - Marketplace - Downloading org.projectdatahopper.hop:hop-pentaho-reporting-output:1.0.0-SNAPSHOT from https://repository.data-hopper.com/repository/hop-community-plugins/org/projectdatahopper/hop/hop-pentaho-reporting-output/1.0.0-SNAPSHOT/hop-pentaho-reporting-output-1.0.0-20260731.223403-2.zip
Downloading: org.projectdatahopper.hop:hop-pentaho-reporting-output:1.0.0-SNAPSHOT (65.8MB)
  [████████████████████████] 100%  9.4MB/s - 65.8MB of 65.8MB
2026/08/01 00:36:20 - Marketplace - Downloaded …/plugins/.staging/.download/hop-pentaho-reporting-output-1.0.0-SNAPSHOT.zip (68990924 bytes)
  Unpacking...
  Installing files...
2026/08/01 00:36:20 - Marketplace - Installed org.projectdatahopper.hop:hop-pentaho-reporting-output:1.0.0-SNAPSHOT. Restart Hop to load the plugin.
Plugin org.projectdatahopper.hop:hop-pentaho-reporting-output:1.0.0-SNAPSHOT installed under <HOP_HOME> from repo 'data-hopper-community'. Restart Hop to load it.
```

If the repo `data-hopper-community` already exists (from hop-datavault), re-import updates metadata; with **`browse: true`**, the new zip is also discovered live from Nexus.

### Hop GUI

1. Marketplace toolbar → **Repositories**  
2. Import `hop-marketplace-repo.yaml` or add URL  
   `https://repository.data-hopper.com/repository/hop-community-plugins/`  
   with browse enabled  
3. Find **Pentaho Reporting Output** → Install → restart  

After restart: pipeline palette **Output** → **Pentaho Reporting Output**.

## Note on licenses

This plugin is **LGPL-2.1** and ships the classic engine. It is **not** an Apache Software Foundation release. Keep that clear in marketplace description (already in the YAML).
