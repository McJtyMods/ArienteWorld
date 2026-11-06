#!/usr/bin/env python3
"""Rebuild additive skyline variants from the original 13 buildings and nine parks."""
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
        if path.stem == 'city5_1':
            palette['palette'] += [{'char': key(len(MATERIALS) + i), 'block': block}
                                  for i, block in enumerate(CUBE_MATERIALS)]
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
