#!/usr/bin/env python3
"""Package the built mod and Fabric API as an importable, self-contained mrpack."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def properties():
    return dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
                if '=' in line and not line.startswith('#'))


def package(mod_jar, api_jar, output):
    config = properties()
    with zipfile.ZipFile(mod_jar) as archive:
        metadata = json.loads(archive.read('fabric.mod.json'))
    if metadata.get('id') != 'nimbus_nova' or metadata.get('version') != config['version']:
        raise ValueError('Choose the current built Nimbus Nova mod jar, not sources or another mod.')
    with zipfile.ZipFile(api_jar) as archive:
        api_metadata = json.loads(archive.read('fabric.mod.json'))
    if api_metadata.get('id') != 'fabric-api' or api_metadata.get('version') != config['fabric_api_version']:
        raise ValueError('Fabric API does not match gradle.properties.')
    if mod_jar.name == api_jar.name:
        raise ValueError('The two input jars must have different names.')
    index = {'formatVersion': 1, 'game': 'minecraft', 'versionId': config['version'],
             'name': 'Nimbus Nova Mission Trainer',
             'summary': 'Cooperative STEM missions on Mars, the Moon and a rescue spacecraft.',
             'files': [], 'dependencies': {'minecraft': config['minecraft_version'],
                                          'fabric-loader': config['loader_version']}}
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_suffix('.tmp')
    manifest = {}
    with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as archive:
        archive.writestr('modrinth.index.json', json.dumps(index, indent=2) + '\n')
        for source in (mod_jar, api_jar):
            target = 'overrides/mods/' + source.name
            data = source.read_bytes()
            archive.writestr(target, data)
            manifest[target] = {'sha512': hashlib.sha512(data).hexdigest(), 'bytes': len(data)}
        archive.writestr('overrides/NIMBUS_RELEASE.json', json.dumps(manifest, indent=2) + '\n')
        for name in ('LICENSE', 'CREDITS.md', 'PLAYER_GUIDE.md', 'TEACHER_GUIDE.md'):
            path = ROOT / name
            if path.exists():
                archive.write(path, 'overrides/NimbusNova/' + name)
    with zipfile.ZipFile(temporary) as archive:
        if archive.testzip() is not None:
            raise ValueError('Release archive is corrupt.')
        actual = json.loads(archive.read('modrinth.index.json'))
        assert actual['dependencies']['minecraft'] == config['minecraft_version']
        assert len([name for name in archive.namelist() if name.startswith('overrides/mods/')]) == 2
        for path, record in manifest.items():
            assert hashlib.sha512(archive.read(path)).hexdigest() == record['sha512']
        assert not any(name.startswith(('overrides/saves/', 'overrides/.env', 'overrides/options.txt'))
                       for name in archive.namelist())
    temporary.replace(output)
    print(f'Packaged and verified {output} ({output.stat().st_size:,} bytes)')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--fabric-api', type=Path, required=True)
    parser.add_argument('--output', type=Path, default=ROOT / 'build/release/NimbusNova.mrpack')
    args = parser.parse_args()
    package(args.jar, args.fabric_api, args.output)
