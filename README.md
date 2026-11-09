# ArienteWorld

Minecraft **26.1.2 / Fabric** companion to Ariente, adding an alien dimension,
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

Use Java 25 from this checkout:

```sh
python3 tools/create_tutorial.py
python3 tools/create_tutorial.py --verify --saves ../Multi_fabric/workspace/runs/client/saves
```

The generator builds a 9 by 9 chunk city in a void world, with a central teaching
plaza. It runs real server ticks and checks inventories and nested transactions,
mining speed, power, purification, smelting, recycling, crafting, refueling,
wireless controls, shields, camera links and vehicle charging. The 424-block
levitator circuit is checked in both directions at multiple speeds. `--verify`
checks the same systems after reloading. Existing worlds require `--replace`;
`--port` selects the loopback authoring-server port.

For the opt-in client smoke check, copy the generated tutorial to
`build/tutorial/client/saves/PortSmoke`, then run from `Multi_fabric`:

```sh
./gradlew -I ../ArienteWorld_fabric/tools/tutorial.gradle -Ptutorial_client_smoke :ArienteWorld:runClient
```

This disposable copy checks the baked ship, armor and holographic interfaces,
enters the Ariente dimension, captures screenshots and exits. The same check can cover every mod included in MultiWorkspace: copy the save to
`../Multi_fabric/workspace/build/verification/port-smoke/saves/PortSmoke`, then use:

```sh
./gradlew -I ../ArienteWorld_fabric/tools/tutorial.gradle -I ../ArienteWorld_fabric/tools/workspace_port_smoke.gradle :workspace:runClient
```

A focused rendering regression uses the same disposable `PortSmoke` copy:

```sh
./gradlew -I ../ArienteWorld_fabric/tools/tutorial.gradle -Ptutorial_render_checks :ArienteWorld:runClient
```

It checks synchronized multipart input, output, sensor and round-robin devices
and their rendered pixels, then compares an opaque display with clouds off and
on. It also renders every power armor slot, the complete suit and the unequipped
player, rejecting missing-texture pixels. Screenshots are saved under
`build/tutorial/client/screenshots/render-checks`.

Live-camera checks exercise repeated native world renders, player movement,
holographic/live switching, texture resizing and camera pan:

```sh
./gradlew -I ../ArienteWorld_fabric/tools/tutorial.gradle -Ptutorial_live_camera_checks :ArienteWorld:runClient
```

Add `-Ptutorial_live_camera_fabulous` to cover the Fabulous graphics preset.
The check uses the same disposable `PortSmoke` save. Captures are saved under
`build/tutorial/client/screenshots/live-camera-checks`.

The authoring fixtures are excluded from release jars. Datagen uses `:ArienteWorld:runDatagen`.

## Fabric development

Targets Minecraft 26.1.2 with Java 25, on local branch `26.1.2_fabric`.
Build with `./gradlew build` in this checkout, or `./gradlew :ArienteWorld:build`
from the sibling `Multi_fabric` workspace. Included McJtyLib, TOP, HoloGui and
Ariente checkouts are used in compilation and development runs. Isolated builds
need matching Fabric Maven artifacts; `-Pgradletools_path=<file>` selects an
explicit shared build script.

Requires McJtyLib **26.1.2-10.0.3-fabric** or newer. HoloGui, Ariente and
ArienteWorld have separate common/client Fabric entrypoints and use native
Fabric networking, events, registry access and transactional storage.
