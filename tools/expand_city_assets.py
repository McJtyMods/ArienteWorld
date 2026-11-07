#!/usr/bin/env python3
"""Rebuild skyline variants, dungeon palettes and authored underground access."""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/arienteworld/citydata'
MATERIALS = ['minecraft:air', 'ariente:marble_smooth@color=black',
             'ariente:marble_bricks@color=black', 'ariente:blacktech@type=lines_glow',
             'ariente:blacktech@type=red_lines_glow', 'ariente:storage@facing=south',
             'ariente:elevator', 'ariente:marble_pilar@color=black',
             'ariente:fluxglow', 'ariente:level_marker',
             'ariente:marble_smooth@color=darkblue', 'ariente:blacktech@type=panel',
             'ariente:blacktech@type=red_var1', 'minecraft:stained_glass@color=cyan',
             'ariente:blacktech@type=var1', 'ariente:tech_fence',
             'ariente:slope@facing=north', 'ariente:slope@facing=south',
             'ariente:slope@facing=west', 'ariente:slope@facing=east',
             'ariente:flatlight@facing=north', 'ariente:flatlight@facing=south',
             'ariente:flatlight@facing=west', 'ariente:flatlight@facing=east',
             'ariente:marble_fence', 'ariente:blue_glass_fence',
             'ariente:blacktech@type=darkblue_blue', 'ariente:blacktech@type=darkblue_red',
             'ariente:blacktech@type=var1_anim', 'ariente:blacktech@type=red_var1_anim',
             'ariente:door_marker@facing=north', 'ariente:door_marker@facing=west']
CUBE_MATERIALS = ['ariente:storage@facing=north', 'ariente:storage@facing=east',
                  'ariente:storage@facing=west']
CUBE_DECK_RAILS = [('n', 'tech_fence', 'north=true,south=true'),
                   ('e', 'tech_fence', 'east=true,west=true'),
                   ('m', 'marble_fence', 'north=true,south=true'),
                   ('w', 'marble_fence', 'east=true,west=true'),
                   ('b', 'blue_glass_fence', 'north=true,south=true'),
                   ('c', 'blue_glass_fence', 'east=true,west=true')]
UNDERGROUND_CEILING = [('p', 'ariente:marbletech_variation1@facing=down'),
                       ('b', 'ariente:marble@color=blue'),
                       ('n', 'ariente:slope@facing=north_up'),
                       ('s', 'ariente:slope@facing=south_up'),
                       ('w', 'ariente:slope@facing=west_up'),
                       ('e', 'ariente:slope@facing=east_up'),
                       ('t', 'ariente:tech_fence@east=true,west=true'),
                       ('l', 'ariente:wireless_lock@facing=east'),
                       ('i', 'ariente:invisible_door@facing=west'),
                       ('g', 'ariente:blue_glass_fence@north=true,south=true')]


def underground_palette(palette):
    palette['palette'] = [p for p in palette['palette'] if not p['char'].startswith('~')]
    palette['palette'] += [{'char': '~' + symbol, 'block': block} for symbol, block in UNDERGROUND_CEILING]
    for entry in palette['palette']:
        if entry['char'] == 'm ':
            entry['block'] = 'ariente:flatlight@facing=down'


def underground_access(data):
    """Author the visible guardian entrance and its shared wireless-button gate.

    Coordinates span the four-by-four cellar; Y is relative to its floor at world Y=4.
    """
    parts = {entry['name']: entry for entry in data if entry['type'] == 'part'}

    def block(x, y, z, material):
        part = parts['c3_1_' + chr(ord('a') + (z // 16) * 4 + x // 16)]
        row = part['slices'][y][z % 16]
        offset = (x % 16) * 2
        part['slices'][y][z % 16] = row[:offset] + material + row[offset + 2:]

    def equipment(x, y, z, values):
        part = parts['c3_1_' + chr(ord('a') + (z // 16) * 4 + x // 16)]
        position = (x % 16, y, z % 16)
        part['tedata'] = [entry for entry in part['tedata']
                          if (entry['x'], entry['y'], entry['z']) != position]
        part['tedata'].append({'x': position[0], 'y': y, 'z': position[2], 'values': values})

    # Four-wide stairs rise five blocks from the central floor onto the existing balcony.
    for x in range(16, 24):
        for z in range(22, 28):
            for y in range(7, 18):
                block(x, y, z, '  ')
    for x in range(18, 23):
        tread = 29 - x
        for z in range(23, 27):
            for y in range(7, tread):
                block(x, y, z, '!2')
            block(x, tread, z, 'o ')
        for z in (22, 27):
            for y in range(7, tread):
                block(x, y, z, '!2')
            block(x, tread, z, '!3')
            block(x, tread + 1, z, '~t')
    for x in range(14, 18):
        for z in range(23, 27):
            for y in range(7, 12):
                block(x, y, z, '!2')
            for y in range(12, 16):
                block(x, y, z, '  ')
    for x in (16, 17):
        for z in (22, 27):
            for y in range(7, 11):
                block(x, y, z, '!2')
            block(x, 11, z, '!3')
            block(x, 12, z, '~t')
    # The obvious stair entrance stays locked until the same wireless button is used.
    for z in (22, 27):
        for y in range(11, 17):
            block(15, y, z, '!3' if y in (13, 15) else '!a')
    for z in range(22, 28):
        block(15, 16, z, '~p')
        block(16, 16, z, '!a')
    for z in range(23, 27):
        block(15, 12, z, '!v')
        equipment(15, 12, z, [{'key': 'locked', 'boolean': True}])
        for y in range(13, 16):
            block(15, y, z, '~i')
    # Gate the northern entrance as well, so the old tower route cannot bypass the button.
    for z in (5, 6):
        equipment(15, 12, z, [{'key': 'locked', 'boolean': True}])
        for y in (13, 14):
            block(15, y, z, '~i')
    for z in (4, 5):
        equipment(48, 7, z, [{'key': 'locked', 'boolean': True}])
    for z in (4, 22):
        block(16, 13, z, '~l')
        equipment(16, 13, z, [{'key': 'channel', 'integer': 2}])
    # Continuous window panes preserve the view without gaps between isolated fence posts.
    for z in range(59, 62):
        for y in range(16, 19):
            block(14, y, z, '~g')
    # Blue floor strips connect the central hall, stairs, balcony and core chamber.
    for x in range(23, 32):
        for z in (24, 25):
            block(x, 6, z, '!3' if x in (23, 27, 31) else '!a')
    for x in range(11, 18):
        for z in (24, 25):
            block(x, 11, z, '!3' if x in (14, 17) else '!a')
    for z in range(27, 59):
        block(12, 11, z, '!3' if z % 8 == 7 else '!a')

    # Move the same wireless control/channel up beside the locked tower entrance.
    control = parts['c3_1_c']
    values = next(entry['values'] for entry in control['tedata']
                  if (entry['x'], entry['y'], entry['z']) in ((14, 2, 4), (15, 8, 8)))
    control['tedata'] = [entry for entry in control['tedata']
                         if (entry['x'], entry['y'], entry['z']) not in ((14, 2, 4), (15, 8, 8))]
    control['tedata'].append({'x': 15, 'y': 8, 'z': 8, 'values': values})
    block(46, 2, 4, '  ')
    block(47, 8, 8, 'W ')
    block(47, 9, 8, 'y ')
    # Keep the alarm and guard in this room, clear of the highlighted control.
    block(47, 8, 6, '  ')
    block(47, 8, 12, 'N ')
    block(46, 7, 7, '  ')
    block(46, 7, 11, 'L ')
    for y in range(7, 10):
        for z in range(7, 10):
            block(48, y, z, '~p' if z == 8 else '!a')
    for z in (7, 9):
        block(48, 8, z, '!3')
    for x in (45, 46):
        for z in range(7, 10):
            block(x, 6, z, '!3' if x == 45 and z != 8 else '!a')


def key(index):
    return "!" + (str(index) if index < 10 else chr(ord("a") + index - 10))


def main():
    for path in sorted(ROOT.glob('*.json')):
        data = json.loads(path.read_text())
        if path.stem == 'general':
            continue
        palette = next(a for a in data if a['type'] == 'palette')
        palette['palette'] = [p for p in palette['palette'] if not p['char'].startswith('!')]
        palette['palette'] += [{'char': key(i), 'block': block} for i, block in enumerate(MATERIALS)]
        if path.stem == 'city3_1':
            underground_palette(palette)
            underground_access(data)
        if path.stem == 'city5_1':
            palette['palette'] += [{'char': key(len(MATERIALS) + i), 'block': block}
                                  for i, block in enumerate(CUBE_MATERIALS)]
            rail_keys = {'%' + symbol for symbol, _, _ in CUBE_DECK_RAILS}
            palette['palette'] = [p for p in palette['palette'] if p['char'] not in rail_keys]
            palette['palette'] += [{'char': '%' + symbol, 'block': 'ariente:' + block + '@' + properties}
                                  for symbol, block, properties in CUBE_DECK_RAILS]
        if path.stem in ('city3_1', 'city4_1', 'city5_1', 'landscapecities'):
            palette['palette'] = [p for p in palette['palette'] if p['char'] not in ('@n', '@s', '@w', '@e')]
            palette['palette'] += [{'char': '@' + facing[0], 'block': 'ariente:display_base@facing=' + facing}
                                   for facing in ('north', 'south', 'west', 'east')]
        if path.stem == 'city5_1':
            palette['palette'] = [p for p in palette['palette'] if p['char'] not in ('^g', '^s', '^m', '^t')]
            palette['palette'] += [{'char': '^' + symbol, 'block': 'arienteworld:' + name + '@facing=south'}
                                   for symbol, name in [('g', 'guard_dummy'), ('s', 'soldier_dummy'),
                                                        ('m', 'master_soldier_dummy')]]
            palette['palette'].append({'char': '^t', 'block': 'ariente:holo_storage_1'})
            core = next(a for a in data if a['type'] == 'part' and a['name'] == 'c5_C1')
            row = core['slices'][27][8]
            core['slices'][27][8] = row[:14] + '^t' + row[16:]
            core['tedata'] = [entry for entry in core.get('tedata', [])
                              if (entry['x'], entry['y'], entry['z']) != (7, 27, 8)]
            core['tedata'].append({'x': 7, 'y': 27, 'z': 8, 'values': [
                {'key': 'lootTable', 'string': 'arienteworld:chests/conquered_overworld'}]})
        if path.stem == 'landscapecities':
            data = [a for a in data if not (a['type'] == 'part' and
                    ((a['name'].startswith('building') and int(a['name'][8:]) > 13) or
                     (a['name'].startswith('park') and int(a['name'][4:]) > 9)))]
            for prefix, count in [('building', 13), ('park', 9)]:
                for i in range(1, count + 1):
                    part = copy.deepcopy(next(a for a in data if a['name'] == prefix + str(i)))
                    part['name'] = prefix + str(i + count)
                    grid = [[[row[x:x+2] for x in range(0, 32, 2)] for row in layer] for layer in part['slices']]
                    # Add a different roof silhouette to each existing architectural family.
                    # Equipment, entrances and street-level connections remain authored.
                    heights = [3, 6, 15, 4, 20, 8, 3, 12, 5, 18, 6, 10, 4]
                    height = heights[i-1] if prefix == 'building' else 3 + i % 3
                    original = len(grid)
                    grid += [[['!0'] * 16 for _ in range(16)] for _ in range(height)]
                    blocks = {entry['char']: entry['block'] for entry in palette['palette']}
                    air = {key for key, block in blocks.items() if block == 'minecraft:air'}
                    # A complete solid roof pad, not merely a non-space palette character.
                    # Reject glass, machinery, lights, fences, slabs and occupied columns.
                    structural = {key for key, block in blocks.items() if block.split('@')[0] in
                                  ('ariente:marble', 'ariente:marble_smooth', 'ariente:marble_bricks',
                                   'ariente:marble_pilar', 'ariente:blacktech', 'ariente:pattern',
                                   'ariente:double_marble_slab')}
                    candidates = []
                    for base in range(original - 1, -1, -1):
                        for x in range(2, 14):
                            for z in range(2, 14):
                                footprint = [(x + dx, z + dz) for dx in range(-1, 2) for dz in range(-1, 2)]
                                if all(grid[base][pz][px] in structural and
                                       all(grid[y][pz][px] in air for y in range(base + 1, original))
                                       for px, pz in footprint):
                                    candidates.append((base, x, z))
                    footprint_radius = 1
                    if not candidates:
                        # Narrow/sloped roofs get a slender mast on a solid column.
                        footprint_radius = 0
                        for base in range(original - 1, -1, -1):
                            for x in range(2, 14):
                                for z in range(2, 14):
                                    if grid[base][z][x] in structural and all(
                                            grid[y][z][x] in air for y in range(base + 1, original)):
                                        candidates.append((base, x, z))
                    # One or two restrained towers, rather than a forest of identical masts.
                    chosen = []
                    for base, x, z in candidates:
                        if any(abs(x - px) < 5 and abs(z - pz) < 5 for _, px, pz in chosen):
                            continue
                        chosen.append((base, x, z))
                        if len(chosen) == (1 if prefix == 'park' else 1 + i % 2):
                            break
                    assert chosen, f"No supported roof pad for {part['name']}"
                    for tower_index, (base, x, z) in enumerate(chosen):
                        tip = original + height - 1 - ((x + z + i) % 3)
                        if prefix == 'park':
                            tip = min(tip, base + height)
                        crown = (i - 1 + tower_index * 2) % 5
                        neck = max(base + 1, tip - (2 + crown % 3))
                        for y in range(base + 1, tip + 1):
                            radius = footprint_radius if y < neck else 0
                            for dx in range(-radius, radius + 1):
                                for dz in range(-radius, radius + 1):
                                    # Broad dark faces, vertical ribs and one short light inset.
                                    material = 7 if dx != 0 and dz != 0 else 1
                                    if dx == 0 and dz == -radius and base + 2 <= y <= base + 4:
                                        material = 3
                                    grid[y][z+dz][x+dx] = key(material)
                        # Distinct technology modules recessed between the structural ribs.
                        # All changes stay within the new tower; no original building is touched.
                        theme = (i + x + z) % 4
                        if footprint_radius == 1 and neck - base >= 6:
                            lo = base + 3
                            hi = min(neck - 3, lo + 3 + i % 3)
                            faces = [(0, -1, 16, 20), (0, 1, 17, 21),
                                     (-1, 0, 18, 22), (1, 0, 19, 23)]
                            for dx, dz, slope, light in faces:
                                for y in range(lo, hi + 1):
                                    # Blue glass chamber, red instrument stack, blue machinery,
                                    # or a sparse sensor mast with interrupted light strips.
                                    material = [13, 12, 14, 10][theme]
                                    if theme == 3 and (y - lo) % 2 == 0:
                                        material = 3
                                    grid[y][z+dz][x+dx] = key(material)
                                grid[hi+1][z+dz][x+dx] = key(slope)
                                if hi + 2 < neck:
                                    grid[hi+2][z+dz][x+dx] = key(light)
                            if theme == 1:
                                # A supported metal collar around the lower instrument bank.
                                for dx, dz, _, _ in faces:
                                    grid[lo-1][z+dz][x+dx] = key(15)
                            elif theme == 0:
                                grid[lo][z][x] = key(8)
                        # Independent crowns and terminals: beveled shoulder, metal cage,
                        # glass beacon, forked aerial, or a recessed instrument crown.
                        if footprint_radius == 1 and neck - base >= 3:
                            shoulder = neck - 1
                            for dx, dz, slope, light in [(0, -1, 16, 20), (0, 1, 17, 21),
                                                        (-1, 0, 18, 22), (1, 0, 19, 23)]:
                                grid[shoulder][z+dz][x+dx] = key([slope, 15, 25, 7, 28][crown])
                                grid[shoulder-1][z+dz][x+dx] = key([light, 12, 26, 10, light][crown])
                            if crown in (1, 3):
                                for dx in (-1, 1):
                                    for dz in (-1, 1):
                                        grid[neck][z+dz][x+dx] = key(15 if crown == 1 else 24)
                                if crown == 3:
                                    for dx in (-1, 1):
                                        grid[neck+1][z][x+dx] = key(24)
                                        grid[neck][z][x+dx] = key(24)
                        for y in range(neck, tip):
                            grid[y][z][x] = key([15, 24, 25, 15, 7][crown])
                        grid[tip][z][x] = key([28, 29, 8, 27, 26][crown])
                    part['slices'] = [[''.join(row) for row in layer] for layer in grid]
                    data.insert(-1, part)
        path.write_text(json.dumps(data, indent=2) + '\n')


if __name__ == '__main__':
    main()
