#!/usr/bin/env python3
"""Disassemble only exact already-loaded companion artifacts. Never upload vendor JARs."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

CLASSES = {
    'recruits': ['com.talhanation.recruits.Main',
                 'com.talhanation.recruits.compat.smallships.SmallShips',
                 'com.talhanation.recruits.entities.CaptainEntity',
                 'com.talhanation.recruits.entities.ai.controller.SmallShipsController'],
    'smallships': ['com.talhanation.smallships.world.entity.ship.Ship',
                   'com.talhanation.smallships.world.entity.ship.CogEntity',
                   'com.talhanation.smallships.world.entity.ship.BriggEntity'],
}
EXPECTED_ORIGINALS = {
    'recruits-523860-8339846.jar': '4b53c1b752e886ba10985aad2df30d668971810230eb77867e07428a21d1af7f',
    'small-ships-450659-9061578.jar': 'fa9dbc2332b79d16c97075b19c3e6a23d6b3e3cd8455ab1419f2666d85407add',
}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    evidence = Path(sys.argv[1]).resolve(strict=True)
    if not (evidence / 'result.json').exists():
        print('No loaded-runtime receipt: exact binary audit is unavailable.')
        return
    data = json.loads((evidence / 'result.json').read_text())
    cache = Path(os.environ['GRADLE_USER_HOME']).resolve(strict=True) / 'caches'
    javap = shutil.which('javap')
    if not javap:
        raise RuntimeError('Java 17 javap is required')
    report = {'status': 'passed', 'noVendorJarsUploaded': True,
              'scope': 'Exact remapped artifacts before runtime mixin application', 'artifacts': {}, 'originals': {}}
    out = evidence / 'bytecode'
    out.mkdir(exist_ok=False)
    for mod, classes in CLASSES.items():
        expected = data['loadedCompanionArtifacts'][mod]
        paths = [p for p in cache.rglob(expected['fileName']) if p.is_file() and digest(p) == expected['sha256']]
        if not paths:
            raise RuntimeError(f'Loaded {mod} binary is unavailable')
        if mod == 'smallships' and data['fixtureRelease'] == 'final':
            classes = classes + ['com.talhanation.smallships.world.entity.ship.abilities.Seatable',
                                 'com.talhanation.smallships.world.entity.ship.abilities.Sailable']
        entries = []
        for name in classes:
            result = subprocess.run([javap, '-J-Xmx256m', '-c', '-p', '-classpath', str(paths[0]), name],
                                    check=True, capture_output=True, text=True, timeout=60)
            target = out / (name.rsplit('.', 1)[-1] + '.javap.txt')
            target.write_text(result.stdout)
            entries.append({'class': name, 'file': target.name, 'sha256': digest(target)})
        report['artifacts'][mod] = dict(expected, classes=entries)
    for name, expected in EXPECTED_ORIGINALS.items():
        if '9061578' in name and data['fixtureRelease'] != 'final':
            continue
        candidates = [p for p in cache.rglob(name) if p.is_file()]
        if not candidates or not any(digest(p) == expected for p in candidates):
            raise RuntimeError(f'Exact official original artifact not verified: {name}')
        report['originals'][name] = expected
    (evidence / 'bytecode-audit.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    main()
