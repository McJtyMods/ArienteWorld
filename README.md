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
use ArienteWorld's city generator, with parks, buildings and levitator corridors. Numbered
signs and a lectern guide explain power, automation, both constructors, storage,
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
Existing worlds are never overwritten. All 31 registered block-entity types are
represented, including multipart nodes; the authoring run checks real power,
recirculating items, crafted circuits, shield panels and wireless signals before
saving. Fuel barrels and return hoppers keep the live exhibits running. The cart runs on
an exposed four-bend loop; door markers sit above the walking floor. Copy the
save before experimenting. The authoring sources are excluded from release jars.

For screenshots, copy the completed save to
`build/tutorial/client/saves/Tutorial` and run from the multiworkspace root:

```sh
./gradlew -I ArienteWorld/tools/tutorial.gradle -Pariente_runs -Ptutorial_preview :ArienteWorld:runClient
```

The preview uses its own copy and writes city, plaza, bend, beam, elevator and
cockpit images under `build/tutorial/screenshots`. Add `-Ptutorial_fabulous` to
check vanilla Fabulous graphics, or `-Ptutorial_render_mods` for Embeddium/Oculus.
Use `-Ptutorial_client_directory=<directory>` for a separate client profile.

To check the automation field renderer at close range, put a disposable copy of
Tutorial under `build/verification/autofield-vanilla/saves/Tutorial` and run
`./gradlew -I ArienteWorld/tools/autofield_rendering.gradle -Pariente_runs :ArienteWorld:runClient`
from the workspace root. This checks all 48 outline vertices, beam depth ordering
against translucent surfaces, real world frames and a resource reload.
Add `-Pautofield_render_mods` for Embeddium/Oculus and
`-Pautofield_check_directory=<directory>` for a separate client profile. Shader
packs can be enabled in that profile's Oculus configuration. Screenshots go in
its `screenshots` directory. Player saves are not changed by this check.
