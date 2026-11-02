# ArienteWorld

Minecraft **1.20.1 / Forge** companion to Ariente, adding an alien dimension,
cities and progression to obtain its technology. Requires matching Ariente,
HoloGui and McJtyLib; The One Probe is optional.

Ariente biomes do not spawn vanilla mobs, including outside cities.

Soldiers and occasional master soldiers spawn naturally in city biomes and inside
all dungeon types, including underground halls and the huge cube, at light levels
of 11 or lower. Natural patrols use the normal hostile mob cap and spawning distances
and do not require an alert or the whole dungeon to be loaded. This also applies
to existing generated dungeons.

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
The cube keeps three main-floor lifts and four upper-floor lifts away from the
outer thresholds. Plain inner thresholds have a single door plane with markers
on top of the floor; blue dot-framed outer passages remain open. Neighboring
outer-corridor rooms alternate radiator bays, relay arms, glass cages and low
consoles, with seeded fence frames, eye-level lights and different overhead
profiles on each corridor floor. Cross ends have ramped platforms, balconies
and horizontal/vertical technology spires. The outer halls keep their white ribs
but use dark floors and ceilings, inset flux lighting and hanging details.
Four main-floor sections reserve a 24×24×30 clear
volume for a future boss; no boss is included. Underground halls gain seeded
console banks, cooling stacks and machinery islands, while ships, stations and
smaller dungeons can use alternate illuminated hull detailing.

Surface dungeon approaches grade both high and low terrain toward their entrances.
Authored empty spaces clear natural vegetation without removing the ground beneath them.

These changes apply to new chunks. Existing generated structures are not
retrofitted; partially explored structures can meet their older sections.
To regenerate the additive city assets, run `python3 tools/expand_city_assets.py`.
Use a fresh world to review entrances, elevator stops, city skyline silhouettes,
underground flux-cart clearance and loot balance in Minecraft.

## Tutorial save

`tools/create_tutorial.py` authors a separate Forge 1.20.1 world named **Tutorial**.
It uses a void Overworld, a 144×144 city platform aligned around city-center
chunk (8, 8), and a flat 48×48 teaching plaza in chunks (7–9, 7–9). The outer 72 chunks
use ArienteWorld's city generator, with parks, buildings and levitator corridors. Numbered Ariente text
displays and a lectern guide explain power, automation, both constructors, storage,
forcefields, keycard doors, wireless controls, an elevator, flux vehicles and the
Warper. The city AI Core is explicitly identified as a dimension-specific exhibit;
its city behavior runs in Ariente, which the charged Warper can reach.

With Java 17 selected through `JAVA_HOME`, run from this checkout:

```sh
python3 tools/create_tutorial.py
python3 tools/create_tutorial.py --verify --saves ../workspace/run/saves
```

The first command builds under `build/tutorial/server`. The second restarts that
world, checks persistence and installs it in the development client's saves list.
Use `--directory` for another authoring directory or `--saves` for another client.
Existing worlds are protected unless `--replace` is supplied. For example,
`python3 tools/create_tutorial.py --verify --replace --saves ../workspace/run/saves`
replaces the installed Tutorial after verification succeeds, without a backup.
All registered block-entity types are represented, including multipart nodes.
The authoring run checks real power, smelting, fuel routing, sorted storage,
crafted circuits, shield panels, display layouts and wireless signals before saving.
Fuel barrels keep the main power exhibit running. The cart runs on
an exposed four-bend loop; door markers sit above the walking floor. Copy the
save before experimenting. The authoring sources are excluded from release jars.
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
Storage Cube. Recipe-aware input categories route all furnace inputs to a Smelter,
with two dedicated three-tank generators and a separate overhead combined-flux bus.
Negarite and Posirite dust, including dust smelted from their ores, refuel those
generators. Three Holo Storage Cubes receive non-smeltable wood, tagged ingots and
other items. Fuel dust is explicitly excluded from storage. Logs become charcoal;
planks go to Wood. Full destinations leave their backlog in the source. The saved
world starts with a fresh input batch, and the welcome Holo Storage contains more samples.

Booth 10, west of the center, compares three four-base, three-block-tall displays:
transparent cyan with a border, solid pale green without a border, and invisible
amber without a border. Live modules demonstrate power numbers, meters, history,
inventory lists and grids alongside different text sizes. All teaching labels use
installed Text Modules. Click bases/modules to configure, or hold right-click and
drag a module to rearrange it. The welcome Holo Storage includes spare display components.

For screenshots, copy the completed save to
`build/tutorial/client/saves/Tutorial` and run from the multiworkspace root:

```sh
./gradlew -I ArienteWorld/tools/tutorial.gradle -Pariente_runs -Ptutorial_preview :ArienteWorld:runClient
```

The preview uses its own copy and writes city, plaza, bend, beam, elevator and
cockpit, display-booth, automation and category-filter images under `build/tutorial/screenshots`. Add `-Ptutorial_fabulous` to
check vanilla Fabulous graphics, or `-Ptutorial_render_mods` for Embeddium/Oculus.
Use `-Ptutorial_client_directory=<directory>` for a separate client profile.
Use `-Ptutorial_scene=6` to start at the display booth, or `-Ptutorial_scene=8`
to start at the category-filter panel. Start at `-Ptutorial_scene=9` to check
the spawn view, both gamemode commands, the power monitors, construction,
forcefield controls and the keycard door.

To check the automation field renderer at close range, put a disposable copy of
Tutorial under `build/verification/autofield-vanilla/saves/Tutorial` and run
`./gradlew -I ArienteWorld/tools/autofield_rendering.gradle -Pariente_runs :ArienteWorld:runClient`
from the workspace root. This checks all 48 outline vertices, beam depth ordering,
uniform populated module backgrounds across display bases at three camera angles,
constant field-marker brightness at three pitches, outline/display depth interaction,
stable tank lighting after item renderers at three angles, tank sorting and stacked seams,
visible holograms in the final world framebuffer, real world frames and a resource reload. Add `-Phologram_fabulous` for Fabulous graphics.
Add `-Pautofield_render_mods` for Embeddium/Oculus and
`-Pautofield_check_directory=<directory>` for a separate client profile. Shader
packs can be enabled in that profile's Oculus configuration. Screenshots go in
its `screenshots` directory. Player saves are not changed by this check.
