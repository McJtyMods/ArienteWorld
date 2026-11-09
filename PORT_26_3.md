# Minecraft 26.3 / NeoForge port

Target: Minecraft 26.3, NeoForge 26.3.0.83, Java 25 and NeoGradle 7.1.39.
Sources start from the committed 26.2 NeoForge implementations. Registry names,
machine inventories, city assets and seeded generation algorithms are preserved.

Local branches:

- HoloGui, Ariente, ArienteWorld, McJtyLib and MultiWorkspace: `26.3_neo`.

## Changes

The mods use the 26.3 rendering interfaces, explicit render passes, item quad
groups, UV mappings, pose rotations and SDL key codes. Cameras retain their own
targets and restore the player renderer after each capture. Shader imports and
transparency pipelines are updated for Renderpearl.

Automation, storage and display readers use native NeoForge resource handlers.
Machine handlers retain transactional insertion/extraction; temporary filter
inventories also support rollback. Vehicle network updates feed the existing
movement interpolation through the new interpolation interface.

World generation uses feature and placement codecs. Configured feature resources
move to `worldgen/feature`, block-state providers use their new names, and biomes
declare mob spawns through environment attributes. The new motion tag preserves
heightmaps, fluid barriers and teleport surfaces for the mods' solid blocks.
Datagen now supplies reloadable recipe and loot registries, and the generated
recipes, advancements, loot and block tags are refreshed.

McJtyLib gains the 26.2 model-generation helpers and a 26.3 immediate rendering
adapter with explicit target selection. The library advances to `26.3-11.0.4`;
all three mods require that version. Shared Gradle settings include the new mods,
retain local source dependencies in development runs, and exclude datagen caches
from jars. Modrinth publishing dependencies include the three project IDs.

The [NeoForge migration primer](https://docs.neoforged.net/primer/docs/26.3/)
and the installed 26.3 sources were used as API references.

## Verification

With Java 25 selected, run from `../Multi_neo`:

```sh
./gradlew :Ariente:runClientData :ArienteWorld:runClientData
./gradlew :McJtyLib:test :ModHoloGui:build :Ariente:build :ArienteWorld:build :workspace:build
./gradlew :ArienteWorld:runGameTestServer
```

Validated on 2026-11-09:

- Integrated builds and both data generators.
- McJtyLib's 13 JUnit tests, including the model-generation regression checks.
- All ten required GameTests: native storage, component persistence, companion
  integration, world generation and saved-data migration, plus vanilla's test.
- Standalone builds of McJtyLib and all three mods in isolated directories under
  `/var/tmp`, with unpublished dependencies staged in a temporary local Maven
  repository. Dependency reports resolve McJtyLib `26.3-11.0.4`.
- Fresh Tutorial creation and a complete server restart verification, each using
  2,000 real ticks. Checks cover all 39 registered machine types, 14 chunks of
  raised city architecture, processing/recycling, sorting, fuel routing, 21
  crafted circuits, 60 shield panels, wireless controls, charged ship takeoff in
  eight headings, camera links and the 424-block sloped levitator circuit in both
  directions at four speeds.

- All 19 camera/tank pixel checks pass with Fancy and Fabulous graphics. They
  cover a fixed camera while moving the player, full-height tank beams from three
  views, inactive tanks, day/night skies, local-player visibility and 8/16/32/64
  block camera radii. Representative screenshots were visually inspected.
- Resource and release-jar audits: 1,426 Ariente resources and 286 companion
  resources; no stale resources, datagen caches or test fixtures in the jars.

The full-workspace client smoke also passes using a disposable Tutorial copy:
local source mods load together, the storage hologram and ship model render, and
the client enters the generated Ariente dimension with its custom sky. Screenshots
of both dimensions were visually inspected. Biome spawn data uses the native
`overlay` modifier so NeoForge biome modifiers can edit it.

To repeat that smoke, copy Tutorial to
`../Multi_neo/workspace/build/verification/port-smoke/saves/Tutorial`, then run:

```sh
./gradlew -I ../ArienteWorld_neo/tools/workspace_port_smoke.gradle :workspace:runClient
```

The original port was staged for review without creating commits.

## Tutorial reference world

The verified stopped save is installed at
`../Multi_neo/workspace/run/saves/Tutorial`. Launch `:workspace:runClient` and
select **Tutorial**. The authoring save and logs remain under
`build/tutorial/server`. Client checks use separate disposable copies under
`build/verification` so the reference save stays available for playtesting.

To create and install it again:

```sh
python3 tools/create_tutorial.py
python3 tools/create_tutorial.py --verify --saves ../Multi_neo/workspace/run/saves
```

Existing saves are protected unless `--replace` is explicitly supplied. Build
outputs, runtime worlds, logs and screenshots are ignored by Git.

## Release and remaining manual checks

Modrinth's project version APIs list no 26.3 releases of HoloGui, Ariente or
ArienteWorld. Their port versions retain the existing release numbers with a
26.3 prefix. CurseForge lists only 1.12.2 for HoloGui and ArienteWorld;
Ariente's CurseForge listing could not be loaded. McJtyLib
`26.3-11.0.3` is published, so the new support uses `26.3-11.0.4`.

Publish the updated shared Gradle script and matching dependencies before using
the standalone remote fallback without a local workspace. Unpublished artifacts
can be supplied with `-Pariente_dependency_repository=<Maven repository URL>`;
`-Pgradletools_path=<file>` selects a shared script explicitly.

The reference checks do not replace survival progression, multiplayer sessions
or manual interaction with every holographic control. Patchouli remains an
optional integration without a configured compatible release.
