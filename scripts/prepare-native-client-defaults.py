#!/usr/bin/env python3
"""Seed unchanged Small Ships defaults only in a new, known native client QA dir."""
import argparse
import hashlib
import json
from pathlib import Path
import re


VERSION = '2.0.0-b1.4'
COORDINATE = 'curse.maven:small-ships-450659:5566900'
CONFIG_SHA256 = {
    'smallships-client.toml': 'bcb7919d3911085ed068e902f46de9971bf152a7b09376bc06e29e6d3d4fdf35',
    'smallships-common.toml': '2c2914337fc96d44520ac1758afed504616cd9b13a11a0992c3d3528c79a5c5b',
}
GAME_DIRECTORIES = {
    'baseline': 'build/native-qa/client',
    'perimeter-completion': 'build/native-perimeter-qa/client',
    'territory-perimeter': 'build/native-territory-qa/client',
    'camp-spawn': 'build/native-camp-qa/client',
    'camp-lifecycle': 'build/native-camp-lifecycle-qa/client',
    'staged-perimeter': 'build/native-staged-qa/client',
    'staged-unload': 'build/native-unload-qa/client',
    'staged-handoff': 'build/native-handoff-qa/client',
    'hud': 'build/native-hud-qa/client',
}
MARKER = '.native-smallships-defaults.json'


def reject_symlinks(root, path):
    """Check before mkdir or reading files; never redirect setup outside this repo."""
    current = root
    for part in path.relative_to(root).parts:
        current = current / part
        if current.is_symlink():
            raise ValueError(f'Refusing symlink in QA path: {current}')


def prepare(root, mode, version):
    if version != VERSION:
        raise ValueError(f'Unsupported Small Ships version {version}; expected {VERSION}')
    if mode not in GAME_DIRECTORIES:
        raise ValueError(f'Unsupported native QA mode: {mode}')
    root = root.resolve(strict=True)
    # Fail closed when the native companion artifact changes; never silently reuse
    # these captured defaults for a newer release, server QA, or a user game dir.
    pins = re.findall(r"nativeQaCompanions\s+fg\.deobf\('([^']*small-ships[^']*)'\)",
                      (root / 'build.gradle').read_text())
    if pins != [COORDINATE]:
        raise ValueError(f'Native QA Small Ships artifact differs from {COORDINATE}: {pins}')

    fixture = root / 'scripts' / 'fixtures' / f'smallships-{VERSION}'
    configs = {}
    for name, digest in CONFIG_SHA256.items():
        path = fixture / name
        reject_symlinks(root, path)
        content = path.read_bytes()
        if hashlib.sha256(content).hexdigest() != digest:
            raise ValueError(f'Captured default bytes differ: {name}')
        configs[name] = content

    directory = root / GAME_DIRECTORIES[mode]
    reject_symlinks(root, directory)
    marker = (json.dumps({
        'purpose': 'Fresh native client QA Small Ships defaults only',
        'qaMode': mode,
        'gameDirectory': GAME_DIRECTORIES[mode],
        'smallshipsVersion': VERSION,
        'coordinate': COORDINATE,
        'configSha256': CONFIG_SHA256,
        'fixtureProvenance': f'scripts/fixtures/smallships-{VERSION}/provenance.json',
        'smallshipsGameplayValuesChanged': False,
    }, indent=2, sort_keys=True) + '\n').encode()

    if directory.exists():
        # A retry is read-only and accepted only before any other preparation or
        # client run. Even an unmarked empty directory or identical user config
        # is refused; the helper must have created this exact fresh QA directory.
        if not directory.is_dir() or {p.name for p in directory.iterdir()} != {'config', MARKER}:
            raise ValueError(f'QA directory is not a fresh helper-only setup: {directory}')
        config_dir = directory / 'config'
        for path in [config_dir, directory / MARKER, *(config_dir / n for n in configs)]:
            reject_symlinks(root, path)
        if not config_dir.is_dir() or {p.name for p in config_dir.iterdir()} != set(configs):
            raise ValueError(f'Unexpected or missing existing QA config: {config_dir}')
        for path, expected in [(directory / MARKER, marker),
                               *((config_dir / n, content) for n, content in configs.items())]:
            if not path.is_file() or path.read_bytes() != expected:
                raise ValueError(f'Existing QA setup differs; leaving it unchanged: {path}')
        return directory

    directory.mkdir(parents=True, exist_ok=False)
    (directory / 'config').mkdir()
    # Exclusive creation, with the marker last. An interrupted setup is refused
    # on retry rather than repaired, overwritten, or deleted automatically.
    for path, content in [*((directory / 'config' / n, b) for n, b in configs.items()),
                          (directory / MARKER, marker)]:
        with path.open('xb') as stream:
            stream.write(content)
    return directory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', required=True, choices=GAME_DIRECTORIES)
    parser.add_argument('--smallships-version', required=True)
    args = parser.parse_args()
    try:
        directory = prepare(Path(__file__).resolve().parents[1], args.mode, args.smallships_version)
    except (OSError, ValueError) as error:
        parser.exit(1, f'Refused native QA setup: {error}\n')
    print(f'Verified unchanged Small Ships {VERSION} defaults in {directory}')


if __name__ == '__main__':
    main()
