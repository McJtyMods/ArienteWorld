# Minecraft 1.21.1 / NeoForge port

HoloGui, Ariente and ArienteWorld are on `1.21_neo` in the Multi21_neo workspace. Their source baselines are the latest local Forge 1.20 commits: HoloGui `7604a1a`, Ariente `f1b019f`, and ArienteWorld `ab9178b`. Java 21 and the Gradle 8.14.2 wrappers are required.

The workspace compiles and runs the included McJtyLib 9.0.22 and The One Probe sources. Standalone builds use the shared script's published 1.21 dependencies, including McJtyLib `1.21-9.0.21.20`. Unpublished HoloGui/Ariente artifacts can be supplied through `-Pariente_dependency_repository=<repository URL>`. The build supports an explicit `-Pgradletools_path=<file>`, the workspace/parent script, and the matching remote script as fallback. No machine paths are embedded in the build.

Networking uses NeoForge payload registration and McJtyLib's typed command payloads. Item custom state uses copy-on-write components; machine drops carry persistent machine data. Block entity saves and synchronization receive registry lookups, including initialization during generation before a block entity has a Level. Material tags use `c`, loader resources use `neoforge`, and vanilla data directories use the 1.21 singular names. The existing registry IDs, assets, city layouts and generation algorithms are retained.

Run these checks from the workspace root:

```sh
./gradlew :ModHoloGui:build :Ariente:build :ArienteWorld:build :workspace:compileJava
./gradlew :Ariente:runData :ArienteWorld:runData
./gradlew :ArienteWorld:runGameTestServer
python ArienteWorld/tools/audit_resources.py --jar ArienteWorld/build/libs/arienteworld-1.21-0.0.31-alpha.jar
```

The eight focused GameTests cover grouped storage capacity, persistence and synchronization, blueprints, machine-drop components, item copy isolation, display settings, dimension integration, and display initialization during world generation. Its test-only flat preset includes the Ariente dimension; vanilla 1.21 GameTest servers otherwise omit extra dimensions. Test classes and fixtures stay out of release jars.

For the optional client check, copy the stopped GameTest server's `ArienteWorld/build/verification/gameTestServer/world` to a fresh disposable `ArienteWorld/build/verification/client/saves/PortSmoke`, then run:

```sh
./gradlew :ArienteWorld:runClient -Pport_client_smoke
```

This fixture modifies its disposable save, renders a populated Holo Storage panel and Flux Ship, travels to fresh Ariente terrain, captures `port-overworld.png` and `port-ariente.png` under the client's screenshots directory, and exits. Never run it in a player save. Normal individual runs use `build/verification/<run>`; the integrated workspace keeps its usual run directory.

Verification on 2026-11-08: all three workspace builds, the integrated workspace compile, both datagens, all three standalone builds, the focused server suite, and the native client smoke check pass. The standalone check used an external temporary Maven repository for the unpublished port dependencies. The companion resource audit checks both resources and the release jar. Source runs exclude local built jars and duplicate per-project Minecraft artifacts; asset indexes are version-specific.

Full survival/balance, sustained multiplayer and interactive configuration still require manual playtesting. The older Forge GameTest/client fixtures and their Gradle scripts are retained as historical references and are not all migrated into this suite. The old `PORTING.txt` files describe that previous port and its broader historical validation, not current NeoForge results. The tutorial authoring tool and its 37-scene preview now target NeoForge 1.21.1. See the README's Tutorial save section for generation, reload verification and preview commands. Tutorial sources remain excluded from release jars.

Rendering follow-up on 2026-11-08: forcefields safely skip empty panel meshes, including disabled/new fields and the frames where rebuilding panels emit no vertices. Tank glass/beams, connected and replacement textures, laser sprites and ship exhaust use 1.21's normalized sprite coordinates. Automation fields subscribe to concrete chunk load/unload events, avoiding NeoForge's abstract-event registration crash.

Deferred displays, automation outlines and module placement/drag previews restore the event's world camera matrix during `AFTER_LEVEL`, then restore the original render state. In 1.21 the level renderer removes that matrix before this event, so retaining its identity pose stack alone left displays fixed in front of the camera. The separate `tutorial_position_check` fixture compares the actual block entity and deferred draw transforms across nine camera views, including yaw, pitch, translation and third person. It reproduces the original failure and passes with the correction in both Fancy and Fabulous. See the README for the command and disposable-save setup.

The forcefield hum now measures actual distance to finite panel faces, starts silently, and fades in/out. Leaving range retains the fading channel for quick re-entry; stopped, muted or stale-world channels recover cleanly. These fixes are also applied to the Forge 1.20.1 branch. Native checks on both loaders cover 720 translated/scaled panel-distance cases, approach/exit, abrupt entry, quick re-entry and shield deactivation. Both runs have two silent starts and at most 0.1 volume change per tick, versus three starts and an immediate 1.798 volume jump in the legacy baseline. The OGG asset is unchanged.

The NeoForge tutorial was generated from scratch and passed both its creation and reload checks, covering all 39 machine types, processing/refueling/sorting, ship charging and eight takeoff headings, wireless/security controls and the complete 424-block cart circuit at four speed settings. A verified copy is installed at `workspace/run/saves/Tutorial`; its ZIP is `ArienteWorld/build/tutorial/Tutorial-1.21.1-NeoForge.zip` relative to the workspace root. Authoring, preview worlds, screenshots and logs are build artifacts and remain outside Git.

All 37 preview scenes passed in native Fancy and Fabulous modes, including empty/rebuilt/hit forcefields, first/third-person personal shields, holograms, camera modes, resource reload, the dimension sky, all six defender types, ship thrust/exhaust and cannon fire. Before and after resource reload, 6,126 connected quads and six retextured faces passed atlas-bound checks. All three workspace and standalone builds, integrated compilation, eight GameTests and the companion resource/jar audit pass. The tutorial source set also compiles standalone. Tutorial code stays outside release jars. Shader packs and third-party renderer integrations were not tested.

The new 1.21 versions have no Modrinth releases. CurseForge lists only the old 1.12 ArienteWorld release; HoloGui/Ariente file-list verification was unavailable, so publication status there remains uncertain. Version properties, new changelog sections, dependency ranges and NeoForge metadata agree. No publication or commits were performed.
