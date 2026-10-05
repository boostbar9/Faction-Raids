#!/usr/bin/env python3
"""Place exact captured Small Ships defaults in a brand-new isolated QA directory."""
import argparse
import hashlib
import json
from pathlib import Path

HASHES = {
    'smallships-server.toml': '3558c0b7908980d54d61251655a5f0fbd8363a6bad3f6a1e5993d68b73e8ea84',
    'smallships-client.toml': 'bb0b961d8334d10588b7d5543faf62e6f767a38225f75d04c772b113148cd81f',
}
LEGACY_HASHES = {'smallships-common.toml': '2c2914337fc96d44520ac1758afed504616cd9b13a11a0992c3d3528c79a5c5b'}
DIRECTORIES = {
    'legacy': 'build/native-ships-legacy-qa/server',
    'final': 'build/native-ships-final-qa/server',
    'final-adapter': 'build/native-ships-final-adapter-qa/server',
    'final-client': 'build/native-ships-final-client-qa/client',
}


def no_symlinks(root, path):
    current = root
    for part in path.relative_to(root).parts:
        current = current / part
        if current.is_symlink():
            raise ValueError(f'Refusing symlink in isolated QA path: {current}')


def prepare(root, fixture):
    root = root.resolve(strict=True)
    if fixture not in DIRECTORIES:
        raise ValueError('Unsupported exact-release fixture')
    directory = root / DIRECTORIES[fixture]
    no_symlinks(root, directory)
    if directory.exists():
        raise ValueError('Refusing an existing game directory; this helper never resets configs')
    pin = (root / 'gradle/native-smallships-qa.gradle').read_text()
    if "shipsRelease == 'final' ? '9061578' : '5566900'" not in pin:
        raise ValueError('Unreviewed native Small Ships artifact selection')
    legacy = fixture == 'legacy'
    version = '2.0.0-b1.4' if legacy else '2.0.0'
    hashes = LEGACY_HASHES if legacy else HASHES
    source = root / 'scripts/fixtures' / ('smallships-' + version)
    data = {}
    for name, expected in hashes.items():
        path = source / name
        no_symlinks(root, path)
        content = path.read_bytes()
        if hashlib.sha256(content).hexdigest() != expected:
            raise ValueError(f'Captured untouched native defaults changed: {name}')
        data[name] = content
    directory.mkdir(parents=True, exist_ok=False)
    # Forge copies server defaults into a NEW world; no pre-existing world is created or touched.
    if legacy:
        (directory / 'config').mkdir()
        (directory / 'config/smallships-common.toml').write_bytes(data['smallships-common.toml'])
    else:
        (directory / 'defaultconfigs').mkdir()
        (directory / 'defaultconfigs/smallships-server.toml').write_bytes(data['smallships-server.toml'])
    if fixture == 'final-client':
        (directory / 'config').mkdir()
        (directory / 'config/smallships-client.toml').write_bytes(data['smallships-client.toml'])
    (directory / '.native-smallships-defaults.json').write_text(json.dumps({
        'fixture': fixture, 'coordinate': 'curse.maven:small-ships-450659:' + ('5566900' if legacy else '9061578'),
        'sha256': hashes, 'gameplayValuesChanged': False,
        'origin': ('Existing reviewed beta common-default fixture; original beta helper unchanged' if legacy else
                   'Captured unmodified native-generated defaults from run37276889470; final server bytes matched independent fresh dedicated and integrated worlds'),
        'limitation': 'Avoids intermittent native first-start schematicVersion race in isolated QA; does not repair that upstream startup defect or reset user config',
    }, indent=2) + '\n')
    return directory


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fixture', required=True, choices=DIRECTORIES)
    args = parser.parse_args()
    try:
        print(prepare(Path(__file__).resolve().parents[1], args.fixture))
    except (ValueError, OSError) as error:
        parser.exit(1, f'Refused native defaults setup: {error}\n')
