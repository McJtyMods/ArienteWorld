#!/usr/bin/env python3
"""Check companion assets, authored world data, datagen drift and release jars."""
import argparse
import json
from pathlib import Path
import re
import sys
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
NAMESPACE = 'arienteworld'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--generated', type=Path, help='isolated runData --all output')
    parser.add_argument('--jar', type=Path, help='reobfuscated companion jar')
    args = parser.parse_args()
    files = {}
    errors = []
    for root in (ROOT / 'src/main/resources', ROOT / 'src/generated/resources'):
        for path in root.rglob('*'):
            if path.is_file() and '.cache' not in path.parts:
                name = path.relative_to(root).as_posix()
                if name in files:
                    errors.append(f'Duplicate resource: {name}')
                files[name] = path
    docs = {}
    for name, path in files.items():
        if name.endswith(('.json', '.mcmeta')):
            try:
                docs[name] = json.loads(path.read_text())
            except (ValueError, UnicodeError) as error:
                errors.append(f'{name}: {error}')

    def reference(identifier, kind, suffix):
        namespace, separator, path = identifier.partition(':')
        if separator and namespace in ('arienteworld', 'ariente', 'hologui'):
            roots = [ROOT] if namespace == NAMESPACE else [ROOT.parent / ('Ariente' if namespace == 'ariente' else 'ModHoloGui')]
            name = f'assets/{namespace}/{kind}/{path}{suffix}'
            if not any((root / source / name).is_file() for root in roots
                       for source in ('src/main/resources', 'src/generated/resources')):
                errors.append(f'Missing {kind}: {identifier}')

    def blockstate_models(value):
        if isinstance(value, dict):
            for key, child in value.items():
                if key == 'model' and isinstance(child, str):
                    reference(child, 'models', '.json')
                else:
                    blockstate_models(child)
        elif isinstance(value, list):
            for child in value:
                blockstate_models(child)

    for name, doc in docs.items():
        if '/models/' in name:
            if 'parent' in doc:
                reference(doc['parent'], 'models', '.json')
            for texture in doc.get('textures', {}).values():
                if not texture.startswith('#'):
                    reference(texture, 'textures', '.png')
        if '/blockstates/' in name:
            blockstate_models(doc)
        if name.endswith('/sounds.json'):
            for event in doc.values():
                for sound in event.get('sounds', []):
                    if isinstance(sound, str):
                        reference(sound, 'sounds', '.ogg')
                    elif sound.get('type', 'file') == 'file':
                        reference(sound['name'], 'sounds', '.ogg')
        if name.startswith(f'data/{NAMESPACE}/worldgen/biome/'):
            for step in doc['features']:
                for feature in step:
                    if feature.startswith(NAMESPACE + ':'):
                        target = f'data/{NAMESPACE}/worldgen/placed_feature/{feature.split(":", 1)[1]}.json'
                        if target not in docs:
                            errors.append(f'Missing biome feature: {feature}')
        if '/worldgen/placed_feature/' in name and isinstance(doc['feature'], str):
            feature = doc['feature']
            if feature.startswith(NAMESPACE + ':') and f'data/{NAMESPACE}/worldgen/configured_feature/{feature.split(":", 1)[1]}.json' not in docs:
                errors.append(f'Missing configured feature: {feature}')
    if docs.get('pack.mcmeta', {}).get('pack', {}).get('pack_format') != 15:
        errors.append('Expected 1.20.1 resource pack format 15')
    registration = (ROOT / 'src/main/java/mcjty/arienteworld/setup/Registration.java').read_text()
    entities = re.findall(r'ENTITIES.register\("([^"]+)"', registration)
    for entity in entities:
        if f'entity.{NAMESPACE}.{entity}' not in docs.get(f'assets/{NAMESPACE}/lang/en_us.json', {}):
            errors.append(f'Missing entity translation: {entity}')
    for name, doc in docs.items():
        if name.startswith(f'data/{NAMESPACE}/worldgen/biome/'):
            for spawners in doc.get('spawners', {}).values():
                for spawner in spawners:
                    mob = spawner['type']
                    if mob.startswith(NAMESPACE + ':') and mob.split(':', 1)[1] not in entities:
                        errors.append(f'Unknown biome spawn entity: {mob}')
    blocks = re.findall(r'BLOCKS.register\("([^"]+)"', registration)
    language = docs.get('assets/arienteworld/lang/en_us.json', {})
    for block in blocks:
        for name in (f'assets/{NAMESPACE}/blockstates/{block}.json', f'assets/{NAMESPACE}/models/item/{block}.json',
                     f'data/{NAMESPACE}/loot_tables/blocks/{block}.json'):
            if name not in docs:
                errors.append(f'Missing registered block resource: {name}')
        if f'block.{NAMESPACE}.{block}' not in language:
            errors.append(f'Missing block translation: {block}')
    if args.generated:
        generated = [p for p in args.generated.rglob('*.json') if '.cache' not in p.parts]
        if not generated:
            errors.append('No generated JSON files found')
        for path in generated:
            name = path.relative_to(args.generated).as_posix()
            if name not in docs or docs[name] != json.loads(path.read_text()):
                errors.append(f'Datagen drift: {name}')
        for path in (ROOT / 'src/generated/resources').rglob('*.json'):
            if not (args.generated / path.relative_to(ROOT / 'src/generated/resources')).is_file():
                errors.append(f'Stale generated file: {path.relative_to(ROOT)}')
        print(f'Compared {len(generated)} generated JSON files')
    if args.jar:
        with ZipFile(args.jar) as jar:
            names = jar.namelist()
            if len(names) != len(set(names)):
                errors.append('Duplicate jar entries')
            for name, path in files.items():
                if name not in names or jar.read(name) != path.read_bytes():
                    errors.append(f'Jar resource missing or stale: {name}')
            for source in (ROOT / 'src/gameTest/java').rglob('*.java'):
                prefix = source.relative_to(ROOT / 'src/gameTest/java').with_suffix('').as_posix()
                if any(name == prefix + '.class' or name.startswith(prefix + '$') for name in names):
                    errors.append(f'Fixture/test in release jar: {prefix}')
            for name in ('mcjty/arienteworld/ArienteWorld.class', 'META-INF/mods.toml'):
                if name not in names:
                    errors.append(f'Missing jar entry: {name}')
            city_code = jar.read('mcjty/arienteworld/ai/CityAI.class')
            if b'getBlockEntity' in city_code or b'm_7702_' not in city_code:
                errors.append('Release jar must contain reobfuscated 1.20.1 bytecode; run build/reobfJar after development runs')
            version = next(line.split('=', 1)[1] for line in (ROOT / 'gradle.properties').read_text().splitlines()
                           if line.startswith('version='))
            manifest = jar.read('META-INF/MANIFEST.MF').decode()
            if f'Implementation-Version: {version}\r\n' not in manifest:
                errors.append('Jar manifest version differs from gradle.properties')
            if not (ROOT / 'changelog.txt').read_text().startswith(version + ':\n'):
                errors.append('Changelog heading differs from gradle.properties')
            if 'FMLAT:' in manifest and 'META-INF/accesstransformer.cfg' not in names:
                errors.append('Jar declares a missing access transformer')
    for error in sorted(set(errors)):
        print(error, file=sys.stderr)
    print(f'Audited {len(files)} resources ({len(docs)} JSON/metadata files), {len(blocks)} registered blocks; {len(set(errors))} errors')
    return bool(errors)


if __name__ == '__main__':
    sys.exit(main())
