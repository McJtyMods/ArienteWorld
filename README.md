# ArienteWorld

Minecraft **26.3 / NeoForge** companion to Ariente, adding an alien dimension,
cities and progression to obtain its technology. Requires matching Ariente,
HoloGui and McJtyLib; The One Probe is optional.

Ariente biomes do not spawn vanilla mobs, including outside cities.

The barren marble of rough biomes has irregular, branching Negarite and Posirite
crystal formations, with compact leaning spires about four to six blocks tall.
Each formation contains one mineral. The crystals are slightly transparent and
ring when broken.
A stone pickaxe or better yields one matching fuel dust per block; Silk Touch
collects the crystal itself. These formations appear in newly generated chunks.
Find the biome with `/locate biome arienteworld:ariente_rough`, or try a formation
on exposed marble with `/place feature arienteworld:crystal_formation`.

Half of tree placement attempts in plains, forests and swamps choose the alien
spiral tree: a corkscrew blue-log trunk with three staggered rings of blue leaves.
The other half retain the existing tree mix. This applies to newly generated
chunks. Inspect an individual tree on dirt or grass with
`/place feature arienteworld:spiral_tree`.

The dimension has seven equally weighted biomes: plains, hills, ocean, forest,
rough terrain, city and Ariente Swamp. The swamp has frequent shallow pools,
scattered alien trees and dense black bushes and dark grass, with occasional
small flowers. Two-block-tall dark grass with three top appearances grows only in
swamps; it can also be collected and replanted elsewhere. Blue-purple fog blends
at biome boundaries and limits distant terrain visibility. Find the swamp with
`/execute in arienteworld:ariente run locate biome arienteworld:ariente_swamp`.

Soldiers and occasional master soldiers spawn naturally in city biomes and inside
all dungeon types, including underground halls and the huge cube, at light levels
of 11 or lower. Natural patrols use the normal hostile mob cap and spawning distances
and do not require an alert or the whole dungeon to be loaded. This also applies
to existing generated dungeons.
Small surface bases and floating islands (up to three by three chunks) use
single-soldier natural patrols and half the alert reinforcement counts, rounded
down after any high-alert doubling. Larger dungeons keep their original patrol
packs and reinforcement strengths. Fixed defenders retain their authored posts;
already spawned soldiers are not removed by this change.

Each loaded dungeon section initializes its loot, locks and fixed defenders once.
Organized defenses continue in loaded sections even when the perimeter or the
AI Core's chunk is outside view distance. Unexplored cores still count toward
conquering the complex; the storage-access card is awarded after the last core
is destroyed. Loot and fixed defenders do not replenish when sections reload.

Sabre-wielding soldiers can kill unarmored players in one hit on every non-Peaceful
difficulty. Advanced complexes (`city2_1` and higher) have stronger foot soldiers
and combat drones whose lasers can kill players in plain netherite with one hit.
Protection enchantments still mitigate laser damage; sabre hits remain
survivable in full unenchanted netherite. City plans can tune these attacks with
`soldierSabreDamage` and `droneLaserDamage`, measured before armor and enchantments.

One in five new soldiers and guards carries a two-handed Energy Lance and fights
at range. Its lasers deal 30% more raw damage than that soldier's sabre damage,
including `soldierSabreDamage`. Use cover against lance wielders. Lances and their
reusable Constructor blueprints are rare storage-cube loot in cities with loot.
Players must leave the off hand empty to fire a lance.

See [PORTING.txt](PORTING.txt) for development status, build instructions,
verification commands and release gates, and the
[gameplay guide](../Ariente/ARIENTE_GUIDE.md) for the current systems and controls.

Block textures use `assets/arienteworld/textures/block/` and model references
such as `arienteworld:block/plants/darkgrass`. Minecraft discovers these textures
automatically; new block textures need no atlas entry.

Newly generated cities include 26 building types and 18 parks. The added skyline
variants retain the original bases and equipment and add stepped technological
spires, reaching up to 46 blocks above their foundation. Their details include
recessed glass chambers, instrument stacks, sloped caps, lamps and metal collars.
Crowns vary independently, with metal cages, forked aerials, fence needles and
different glowing terminals.

The huge cube has seeded three-deck room layouts, server and control bays,
unobstructed automatic elevators with floor selectors, and protected storage caches with advanced components,
modules and rare blueprints. Seeded wall details include fence frames, flat lights,
blue/red glowing strips and glass-edged inserts, interspersed with plain bays.
The basement and roof contain decorative machinery halls built specifically for
the cube, using the city's dark marble, blue glass and inset lighting. Chambers,
cooling banks, recessed consoles and stepped machinery rest on chamfered plinths
with ramped edges. Sloped caps, thin fence ribs, L-shaped desks and offset gantries
give each bay a different profile while keeping its supports intact. Smooth dark
surfaces dominate, with sparse red/blue indicators and occasional control panels.
Ceiling lights sit in attached beams.
The basement uses shorter assemblies, while the roof hall has taller structures.
Broad perimeter walks keep passages and elevator approaches clear. These halls
add no loot or fixed defenders.
Three hall lifts connect both halls and all six room decks; the fourth
upper-floor lift remains local, above the reserved arena. The lifts stay away
from the outer thresholds. These additions appear in newly generated chunks.
Plain inner thresholds have a single door plane with markers
on top of the floor; blue dot-framed outer passages remain open. Neighboring
outer-corridor rooms alternate radiator bays, relay arms, glass cages and low
consoles, with seeded fence frames, eye-level lights and different overhead
profiles on each corridor floor. Cross ends have ramped platforms, balconies
and horizontal/vertical technology spires. The outer halls keep their white ribs
but use dark floors and ceilings, inset flux lighting and hanging details.
Ten fixed guards and five Master Soldiers defend the room decks, with 48 mobile
reinforcement posts spread across all six room decks. Solo alerts sustain 8–12
soldiers and 3–5 combat drones, slightly above the underground installation's
7–10 soldiers and 2–4 drones. Reinforcement Master Soldier and power armor chances
are 10% and 35%; 25% of those armored reinforcements receive a forcefield.
Four main-floor sections retain a 24×24×30 clear hall. A Core Guardian protects
the central AI Core. Beside the core and forcefield generator, one lowest-tier
Holo Storage Cube holds 24 guaranteed stacks of conquered Overworld spoils:
netherite, Nether Stars, Ghast Tears, a Totem of Undying, precious materials,
four enchanted books, Fortune/Silk Touch diamond pickaxes and four potion variants.
Defeat the guardian and break the core for its storage-access card. The trophy
cache fills once, preserves its item data and does not replenish on reload.
Its vanilla loot table is `data/arienteworld/loot_table/chests/conquered_overworld.json`.
Underground halls gain seeded
console banks, cooling stacks and machinery islands, while ships, stations and
smaller dungeons can use alternate illuminated hull detailing.

Dungeon loot favors learning to craft equipment: each Powersuit piece's blueprint
has twice the weight of its rare finished piece, with equal weights for the four
pieces. Sabre and Lance blueprint/item pairs follow the same two-to-one ratio.
The cube retains four guaranteed rewards per cache and includes all four armor
pieces and their blueprints. Other caches still roll each slot at 70%, with item
amounts including the configured maximum. Lance item chances per occupied slot
rise from 0.1% in small outposts to 0.5% in the cube, with twice those chances for
their blueprints. Random blueprints retain their shared Constructor recipe pool.
Landscape building caches use their shared loot pool even without tile metadata;
skyline variants retain their original building's cache count.

Some city buildings and floating dungeon rooms, as well as huge cube control
desks and underground console banks, have alien glyph displays. Their decorative
Alien Text Modules have no crafting recipe or blueprint.
Sneak-right-click a module or break its display base to salvage it, preserving its
symbols and color for use on another display.

Surface dungeon approaches grade both high and low terrain toward their entrances.
Authored empty spaces clear natural vegetation without removing the ground beneath them.

Overworld portal rooms keep their seeded x/z and search floor heights from Y=-48
to Y=48 for a dry cave entrance. Direct openings take priority over tunnels of up
to seven blocks. Rooms require supported entrances and rock cover; sites without
a suitable connection retain their original seeded height. Pearls and
`/arienteworld findentrance` locate intact warpers at both old and new heights.

These changes apply to new chunks. Existing generated structures are not
retrofitted; partially explored structures can meet their older sections.
To regenerate the additive city assets, run `python3 tools/expand_city_assets.py`.
Use a fresh world to review entrances, elevator stops, city skyline silhouettes,
underground flux-cart clearance and loot balance in Minecraft.

## Hostile mobs

ArienteWorld owns the soldiers, master soldiers, combat drones, sentinel drones
and their models, textures, sounds and loot. Their entity IDs use the
`arienteworld` namespace. Ariente supplies the shared weapons, equipment,
vehicles and laser projectiles and can run without the dimension mod.

Darkbats are rare, solitary outdoor predators using the phantom's circling and
diving attacks. They are 60% larger than a phantom, have 40 health and deal 12
melee damage on Normal difficulty. They target visible survival players
immediately, independently of insomnia, and do not burn in daylight.
They can spawn in the plains, hills, ocean, forest, rough and swamp biomes at any light
level, under open sky, outside city biomes and dungeon footprints. Only one in
64 otherwise valid spawn attempts passes. Natural spawning also stops when any
non-spectator player within 128 horizontal blocks of the spawn already has three
living Darkbats within that radius, at any height. Overlapping players share their
nearby population; distant players have independent limits. This keeps Darkbats
from gradually filling the normal hostile mob cap while a player waits.
Set `spawning.darkbatMaxPerPlayer` in the world's
`serverconfig/arienteworld-server.toml` to change the limit, or use `0` to disable
natural Darkbat spawning. Existing mobs are left to despawn normally. Named,
summoned and spawner Darkbats count toward the limit, but commands, eggs and
spawners can still create them. The biome tag `arienteworld:darkbat_spawns`
identifies their outdoor biomes.
Use `/summon arienteworld:darkbat` to inspect one in a development world.

## Tutorial save

`tools/create_tutorial.py` authors a fresh NeoForge 26.3 world named **Tutorial**.
It uses a void Overworld, a 144×144 city platform aligned around city-center
chunk (8, 8), and a flat 48×48 teaching plaza in chunks (7–9, 7–9). The outer 72 chunks
use ArienteWorld's city generator, with parks, buildings and levitator corridors. Numbered Ariente text
displays and a lectern guide explain power, automation, both constructors, storage,
forcefields, keycard doors, wireless controls, an elevator, flux vehicles and the
Warper, plus ship charging, camera views, ore purification and loot recycling.
The city AI Core is explicitly identified as a dimension-specific exhibit;
its city behavior runs in Ariente, which the charged Warper can reach.

With Java 25 selected through `JAVA_HOME`, run from this checkout:

```sh
python3 tools/create_tutorial.py
python3 tools/create_tutorial.py --verify --saves ../Multi_neo/workspace/run/saves
```

The first command builds under `build/tutorial/server`. The second restarts that
world, checks persistence and installs it in the development client's saves list.
Use `--directory` for another authoring directory or `--saves` for another client.
Existing worlds are protected unless `--replace` is supplied. For example,
`python3 tools/create_tutorial.py --verify --replace --saves ../Multi_neo/workspace/run/saves`
replaces the installed Tutorial after verification succeeds, without a backup.
All registered block-entity types are represented, including multipart nodes.
The automation exhibit uses a closed Field Marker border with no interior markers.
Stations 06–08 and the ship instructions sit on the plaza side of their exhibits
and face north toward the center. The guide lectern is beside the welcome screen,
and the Ore Purifier display sits directly above its tubes.
The authoring run checks real power, purification followed by smelting, recycling,
ship charging from 10% to full, takeoff in eight headings, both linked camera captures, fuel routing, sorted storage,
crafted circuits, shield panels, display layouts and wireless signals before saving.
Fuel barrels keep the main power exhibit running. The cart runs on
an exposed 424-block circuit with eight bends and 64 sloped beams. It climbs from
Y=65 to Y=97, tours the eastern, northern and western city skyline, then descends
and returns to the plaza station. The cabin, rider and two end control holograms
follow the inclines. The authoring and reload checks drive the complete circuit
with a rider in both directions, at slow and maximum speed settings. Hold the
forward movement key (W by default) to accelerate gradually in the direction you
face and the back movement key (S) to slow down to a stop. Release the keys to keep
the speed; the panels can select reverse or stop instantly. Both panels are checked
again after reloading.
Door markers sit above the walking floor. Copy the
save before experimenting. Older soldier spawners in existing saves are migrated
to ArienteWorld entity IDs when their chunks load, preserving their spawn settings.
The authoring sources are excluded from release jars.
Cheats are enabled in the saved world, allowing `/gamemode survival` and
`/gamemode creative` without opening to LAN. The WELCOME display sits in front
of the spawn point facing the arriving player. Welcome supplies use a Holo
Storage Cube; the manual Constructor's four ingredients use an Ariente Storage
Cube. The keycard door and its reader-facing lock sit just behind station 05's
display. Station 04's reader-facing wireless switch is beside its display and
outside the shield railing. Station 01 has a six-base, four-block-tall display
with live meters and history modules for both generators, on a 70% transparent
surface. The AI Core demo alarm faces the reader on the front of its support.

The automation station takes four item types at a time in the original 2x2
Storage Cube. Recipe-aware input categories route supported ore blocks through a
three-tube Ore Purifier, yielding four raw metals per ore at default settings.
The field transfers these raw metals to the Smelter, then sorts the ingots.
Other furnace inputs go directly to the Smelter. Both machines and the Recycler
use two dedicated three-tank generators and a separate overhead combined-flux bus.
Negarite and Posirite dust, including dust smelted from their ores, refuel those
generators. Three Holo Storage Cubes receive non-smeltable wood, tagged ingots and
other items. Wood and Ingots use their categories with empty item filters. Various
blacklists Negarite and Posirite dust to reserve it for generators; its label and
the guide explain that these filter samples are exclusions. Logs become charcoal;
planks go to Wood. Full destinations leave their backlog in the source. The saved
world starts with a fresh input batch, and the welcome Holo Storage contains more samples.

The raised **EXCESS LOOT ONLY** Holo Storage Cube feeds the Recycler through a
hopper. An Output Node on the Recycler's back returns recovered ingredients to
the field for storage and generator refueling. This separate input prevents
recovered components from being recycled again. A sabre and a Hover Module
are ready to process; spare modules are in the welcome storage.

The Flux Ship is fully charged and centered on a working Charger in the plaza
floor, connected to the main power bus. Land back on the lit pad to recharge.
The lectern explains current flight controls, including Space to fire and the
Up/Down arrow keys to rise/descend, with Backspace as an alternate descend key.

Booth 11, north of the Warper, has a large rotating Camera Module linked to the
processing line and ship dock. Click the module and use Next/Previous to switch
views or adjust yaw, tilt, zoom and rotation speed. The welcome storage includes
a spare Camera Module and camera blocks for experimenting with additional links.

Booth 10, west of the center, compares three four-base, three-block-tall displays:
transparent cyan with a border, solid pale green without a border, and invisible
amber without a border. Live modules demonstrate power numbers, meters, history,
inventory lists and grids alongside different text sizes. All teaching labels use
installed Text Modules. Click bases/modules to configure, or hold right-click and
drag a module to rearrange it. The welcome Holo Storage includes spare display components.

For screenshots, copy the completed save to
`build/tutorial/client/saves/Tutorial` and run from the multiworkspace root:

```sh
./gradlew -I ../ArienteWorld_neo/tools/tutorial.gradle -Ptutorial_preview :ArienteWorld:runClient
```

The preview uses its own copy and writes city, plaza, bend, beam, elevator and
cockpit, display-booth, automation and category-filter images under `build/tutorial/screenshots`. Add `-Ptutorial_fabulous` to
check vanilla Fabulous graphics; those screenshots go under
`build/tutorial/screenshots-fabulous`. Renderer-mod compatibility needs separate
testing with NeoForge versions of those mods.
Use `-Ptutorial_client_directory=<directory>` for a separate client profile.
Use `-Ptutorial_scene=6` to start at the display booth, or `-Ptutorial_scene=8`
to start at the category-filter panel. Start at `-Ptutorial_scene=9` to check
the spawn view, both gamemode commands, the power monitors, construction,
forcefield controls and the keycard door.
Start at `-Ptutorial_scene=18` for the city circuit, a mounted slope view and
a moving cockpit view. The last two scenes also check that the client rider
stays inside the cart.
Start at `-Ptutorial_scene=21` for the purifier/recycler, camera display and
ship charging pad.
Start at `-Ptutorial_scene=24` for the southern booth instructions, wireless
station and the visible guide lectern beside the welcome screen.

The 37-scene NeoForge preview also checks disabled and newly placed empty
forcefields, rebuilding panels and hit effects, personal shields in both camera
perspectives, orthographic/perspective/live camera displays, resource reloads,
the Ariente sky, all six defender types and a flying, firing Flux Ship. It verifies
connected-texture UV bounds for all 256 neighbor
masks on all six faces, both before and after reloading resources. Successful
runs log `TUTORIAL PREVIEW: screenshot sequence complete` and exit. Use
`-Ptutorial_scene=27` to start at the forcefield regressions. The preview changes
its disposable save, so always copy the stopped authoring world again for a new
run or graphics mode. Use `-Ptutorial_scene=35` for just the defender and flight
scenes. The installed Tutorial remains at its welcome point.

For the display-position regression, copy the stopped authoring save to
`build/tutorial/position-check/saves/Tutorial`, then run from the workspace root:

```sh
./gradlew -I ../ArienteWorld_neo/tools/tutorial.gradle -Ptutorial_position_check -Ptutorial_client_directory=build/tutorial/position-check :ArienteWorld:runClient
```

This checks nine views: diagonal and cardinal turns, looking up/down, moving
sideways and third person. It compares the camera transform used by the deferred
display pass with Minecraft's block entity pass, and verifies that the original
render state is restored afterward. Screenshots go in that profile's `screenshots`
directory. Add `-Ptutorial_fabulous` to check Fabulous using another fresh copy.
Successful runs log `DISPLAY POSITION CHECK: all views passed` and exit.

For the forcefield sound regression, copy the stopped authoring save to
`build/tutorial/sound-check/saves/Tutorial`, then run from the workspace root:

```sh
./gradlew -I ../ArienteWorld_neo/tools/tutorial.gradle -Ptutorial_forcefield_sound_check -Ptutorial_client_directory=build/tutorial/sound-check :ArienteWorld:runClient
```

The check approaches and leaves the working shield, re-enters during fade-out,
and disables it. It checks panel distances at three shield scales, silent starts,
gradual volume changes, channel reuse and eventual sound shutdown. The client
profile contains `forcefield-sound.csv`; success logs `FORCEFIELD SOUND CHECK: passed`.
It changes the disposable save and its sound settings, so use a fresh copy.

For the live-camera and tank transparency regression, copy the stopped
authoring save to a disposable profile under `/var/tmp`, then run from `Multi_neo`:

```sh
TMPDIR=/var/tmp ./gradlew -I ../ArienteWorld_neo/tools/tutorial.gradle -Ptutorial_render_regression -Ptutorial_client_directory=/var/tmp/ariente-render-check :ArienteWorld:runClient
```

The profile must contain `saves/Tutorial`. The check builds a static camera
landmark and two eight-section tank columns inside that disposable world. It
compares actual camera framebuffer pixels after player translation, height and
yaw changes, and samples both beams at three heights per section from three
angles. It also checks that inactive tanks have no beam, that the live feed
renders daytime and nighttime skies, and that the local player appears in the
feed in both first-person and third-person mode when inside the mounted camera's
view and disappears when outside it. Six dock-camera cases cover radii of 8, 16,
32 and 64 blocks, distant terrain fading into fog, and stable chunk storage and
images while the player turns or moves. These cases alter the plaza inside the
disposable world; `-Ptutorial_camera_boundary_check` runs only those six cases. Add
`-Ptutorial_fabulous` with another fresh profile for Fabulous graphics. Results
and screenshots are written to `render-regression/` inside the profile; successful
runs log `RENDER REGRESSION: all camera and tank checks passed` and exit. These
fixtures are excluded from released mods. Remove disposable profiles when done.

For a focused power armor check, copy the stopped Tutorial save to
`build/tutorial/armor-client/saves/Tutorial`, then run from `Multi_neo`:

```sh
TMPDIR=/var/tmp ./gradlew -I ../ArienteWorld_neo/tools/tutorial.gradle -Ptutorial_armor_check -Ptutorial_client_directory=build/tutorial/armor-client :ArienteWorld:runClient
```

The check renders the unequipped player, every power armor slot separately, the
complete suit and armor removal. It checks that each piece appears and rejects
missing-texture pixels. Screenshots are saved under
`build/tutorial/armor-client/screenshots/power-armor-checks`; success logs
`NEOFORGE POWER ARMOR CHECKS PASSED`. Use a disposable save; the fixture changes
its terrain and player inventory and is excluded from release jars.

The older `tools/autofield_rendering.gradle` and broader Forge client/GameTest
fixtures are historical 1.20.1 checks; they are not part of the NeoForge suite.
Use the tutorial preview above for current client validation.

Development target: Minecraft 26.3, NeoForge 26.3.0.83, Java 25, branch `26.3_neo`.
Build with `./gradlew build` standalone, or `./gradlew :ArienteWorld:build` from MultiWorkspace.
The workspace uses the included McJtyLib and The One Probe sources. These ports require McJtyLib **26.3-11.0.4** or newer. Ariente and ArienteWorld also use included HoloGui/Ariente sources. Published Maven artifacts are standalone fallbacks.
Unpublished port dependencies can be provided with `-Pariente_dependency_repository=<repository URL>`; `-Pgradletools_path=<file>` selects an explicit shared Gradle script.

The [earlier 26.1.2 port report](PORT_26_1_2.md) records the previous port’s migration and validation history. Earlier Forge fixture commands in this file are historical.

### Minecraft 26.2 port (previous branch)

This branch carries the latest committed 26.1.2 NeoForge implementation forward.
Holograms and procedural geometry use extracted batches and native 26.2 staged
vertex buffers; render submissions, screen access, camera extraction, colored
vanilla content and tag generators use the 26.2 APIs. World effects follow the
reversed depth buffer. The snapshot camera retains its own forward-depth projection.

Patchouli has no listed Minecraft 26.2 release. The tablet/manual remains optional
and reports its unavailability; this branch does not load an older Patchouli jar.
McJtyLib 26.2-10.0.3 supplies the model helpers required by these ports. Publish
McJtyLib, HoloGui and Ariente in that order before distributing the standalone
companion. Maven fallbacks use the matching 26.2 versions.

From `../Multi_neo`, with Java 25 selected:

```sh
./gradlew :McJtyLib:build :ModHoloGui:build :Ariente:build :ArienteWorld:build :workspace:build
./gradlew :Ariente:runClientData :ArienteWorld:runClientData
./gradlew :ArienteWorld:runGameTestServer
```

The existing `PORT_26_1_2.md` report and its recorded results apply to the previous
Minecraft branch. Minecraft 26.2 validation is recorded below.

Publication checks on 2026-11-09 found no 26.2 NeoForge releases of these three
mods on Modrinth. CurseForge file listings returned HTTP 403, so their publication
status there could not be verified. McJtyLib 26.2-10.0.2 is published on Modrinth;
this port advances the library to 26.2-10.0.3. Unpublished dependencies were staged
in a temporary local Maven repository for standalone verification. Update the
shared Maven pin to the actual Jenkins artifact revision when releasing it.

The integrated client also reports an existing InControl global-loot-modifier
resource error. InControl is outside this port; the three mod checks and tutorial
checks use the matching local dependencies. Patchouli remains unavailable.

Validated on 2026-11-09:

- Workspace and standalone builds; McJtyLib's 11 JUnit tests; ten required native
  storage, integration, world-generation and saved-data GameTests.
- Both native client data generators and resource audits.
- Full-workspace client smoke: storage hologram, ship model and Ariente dimension.
- Fresh Tutorial creation and server restart, each running 2,000 real ticks.
- All 19 camera/tank pixel checks with both Fancy and Fabulous graphics: fixed
  camera views while moving the player, full-height beams in three directions,
  disabled tanks, day/night skies and local-player visibility. Representative
  screenshots were visually inspected. The sky renderer's retained render target
  is redirected and restored for offscreen cameras on 26.2. The six new distance
  cases also pass in the full workspace: radii of 8, 16, 32 and 64 blocks, opaque
  fog at distant surfaces, and no player terrain-storage repositions. Offscreen
  rendering preserves the player's visibility lists and uses the mounted lens
  to select terrain without moving the shared chunk meshes.

The verified stopped Tutorial is installed at
`../Multi_neo/workspace/run/saves/Tutorial`. Launch `:workspace:runClient` and select
**Tutorial**. The authoring save remains at `build/tutorial/server/Tutorial`;
visual tests use disposable copies under `build/verification/`. Logs and images
are build outputs and are excluded from Git. Full survival, multiplayer and
manual GUI interaction still need human playtesting.

### Minecraft 26.3 port

This branch ports the committed 26.2 NeoForge sources and tutorial tooling to
26.3. See the [26.3 port and verification report](PORT_26_3.md) for the
current build commands, tutorial save, test results and remaining manual checks.
