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

Full survival/balance, sustained multiplayer, and interactive camera/vehicle/equipment checks still require manual playtesting. The older Forge GameTest/client/tutorial fixtures and their Gradle scripts are retained as historical references and are not all migrated into this suite. The old `PORTING.txt` files describe that previous port and its broader historical validation, not current NeoForge results. The tutorial authoring tool still targets its documented Forge environment.

The new 1.21 versions have no Modrinth releases. CurseForge lists only the old 1.12 ArienteWorld release; HoloGui/Ariente file-list verification was unavailable, so publication status there remains uncertain. Version properties, new changelog sections, dependency ranges and NeoForge metadata agree. No publication or commits were performed.
