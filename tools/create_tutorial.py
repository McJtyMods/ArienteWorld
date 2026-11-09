#!/usr/bin/env python3
"""Create and verify the playable Tutorial save using real Fabric server ticks.

Requires Java 25 in JAVA_HOME. The generated world and server logs stay under
build/tutorial. Supply --saves to install the verified save in a Minecraft client.
Use --replace to explicitly replace an existing Tutorial save. Run --verify to check persistence
by restarting the generated server world before installing it.
"""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=ROOT / 'build/tutorial/server')
    parser.add_argument('--saves', type=Path, help='Minecraft saves directory to install Tutorial into')
    parser.add_argument('--port', type=int, default=25579, help='loopback port for the authoring server')
    parser.add_argument('--verify', action='store_true', help='restart and verify the completed server world')
    parser.add_argument('--replace', action='store_true', help='replace existing Tutorial worlds without a backup')
    args = parser.parse_args()
    directory = args.directory.resolve()
    world = directory / 'Tutorial'
    destination = args.saves.resolve() / 'Tutorial' if args.saves else None
    if destination and (destination == world or destination in world.parents or world in destination.parents):
        parser.error('The installed save must be separate from the authoring world')
    if destination and destination.exists() and not args.replace:
        parser.error(f'Refusing to overwrite an existing save: {destination}')
    if args.verify:
        if not (world / 'Tutorial.complete').is_file():
            parser.error(f'No completed Tutorial map to verify: {world}')
    else:
        if world.exists():
            if not args.replace:
                parser.error(f'Refusing to overwrite an existing server world: {world}')
            shutil.rmtree(world)
        directory.mkdir(parents=True, exist_ok=True)
        generator = json.dumps({
            'layers': [{'block': 'minecraft:air', 'height': 1}],
            'biome': 'minecraft:the_void', 'features': False, 'lakes': False,
            'structure_overrides': []
        }, separators=(',', ':'))
        properties = {
            'level-name': 'Tutorial', 'level-type': 'minecraft:flat', 'level-seed': '20261102',
            'generator-settings': generator, 'generate-structures': 'false',
            'gamemode': 'creative', 'difficulty': 'peaceful', 'allow-flight': 'true',
            'spawn-protection': '0', 'spawn-monsters': 'false', 'spawn-animals': 'false',
            'spawn-npcs': 'false', 'view-distance': '5', 'simulation-distance': '5',
            'server-ip': '127.0.0.1', 'server-port': str(args.port), 'enable-query': 'false',
            'enable-rcon': 'false', 'max-tick-time': '-1', 'sync-chunk-writes': 'true',
            'pause-when-empty-seconds': '-1'
        }
        (directory / 'server.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
        (directory / 'eula.txt').write_text('eula=true\n')
    workspace = next((path for path in (ROOT.parent, ROOT.parent / 'Multi_fabric') if (path / 'settings.gradle').is_file()), ROOT)
    task = ':ArienteWorld:runServer' if workspace != ROOT else ':runServer'
    log = directory / ('verify.log' if args.verify else 'create.log')
    command = [str(workspace / 'gradlew'), '-I', str(ROOT / 'tools/tutorial.gradle'),
               f'-Ptutorial_directory={directory}',
               f'-Ptutorial_verify={str(args.verify).lower()}', task, '--console=plain']
    print(f'Creating/verifying Tutorial; server log: {log}', flush=True)
    with log.open('w') as output:
        result = subprocess.run(command, cwd=workspace, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode or not (world / 'Tutorial.complete').is_file() or 'TUTORIAL VERIFIED:' not in log.read_text():
        print(log.read_text()[-12000:], file=sys.stderr)
        return 1
    if destination:
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            shutil.rmtree(destination)
        shutil.copytree(world, destination, ignore=shutil.ignore_patterns('session.lock'))
        print(f'Installed verified save: {destination}')
    else:
        print(f'Verified save: {world}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
