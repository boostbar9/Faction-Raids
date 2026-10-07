#!/usr/bin/env python3
"""Read-only javap evidence from the exact already-loaded pinned development JARs.

No downloads, vendor JAR upload, world access, authentication or JVM instrumentation.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

TARGETS = {
    'recruits': ('recruits-523860-8339846_mapped_official_1.20.1.jar', [
        'com.talhanation.recruits.entities.ai.RecruitUpkeepPosGoal',
        'com.talhanation.recruits.entities.ai.RecruitUpkeepEntityGoal',
        'com.talhanation.recruits.entities.AbstractRecruitEntity',
        'com.talhanation.recruits.entities.AbstractInventoryEntity',
        'com.talhanation.recruits.inventory.RecruitSimpleContainer',
        'com.talhanation.recruits.world.RecruitsClaimManager',
        'com.talhanation.recruits.world.RecruitsClaim',
        'com.talhanation.recruits.entities.ScoutEntity',
        'com.talhanation.recruits.entities.BowmanEntity',
        'com.talhanation.recruits.entities.ai.navigation.RecruitPathNavigation',
        'com.talhanation.recruits.pathfinding.AsyncPathNavigation',
        'com.talhanation.recruits.pathfinding.AsyncGroundPathNavigation',
        'com.talhanation.recruits.init.ModEntityTypes',
    ]),
    'workers': ('workers-567450-8351157_mapped_official_1.20.1.jar', [
        'com.talhanation.workers.entities.ai.GetNeededItemsFromStorage',
        'com.talhanation.workers.entities.ai.DepositItemsToStorage',
        'com.talhanation.workers.entities.ai.RecruitStorageUpkeepGoal',
        'com.talhanation.workers.entities.ai.AbstractChestGoal',
        'com.talhanation.workers.entities.ai.BuilderWorkGoal',
        'com.talhanation.workers.entities.ai.navigation.WorkerPathNavigation',
        'com.talhanation.workers.entities.ai.navigation.WorkersGroundPathNavigation',
        'com.talhanation.workers.entities.ai.navigation.WorkersAsyncPathfinder',
        'com.talhanation.workers.entities.AbstractWorkerEntity',
    ]),
}

def sha256(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''):
            digest.update(block)
    return digest.hexdigest()

def main():
    if len(sys.argv) != 2:
        raise SystemExit('usage: audit-native-inventory-bytecode.py EVIDENCE_DIRECTORY')
    evidence = Path(sys.argv[1]).resolve(strict=True)
    result_path = evidence / 'result.json'
    if not result_path.is_file():
        print('No actual loaded-runtime receipt; bytecode evidence is unavailable.')
        return
    result = json.loads(result_path.read_text())
    receipt = result['loadedCompanionArtifacts']
    cache = Path(os.environ['GRADLE_USER_HOME']).resolve(strict=True) / 'caches'
    javap = shutil.which('javap')
    if javap is None:
        raise RuntimeError('Java 17 javap is required; no alternate tool is downloaded')
    out = evidence / 'native-bytecode'
    out.mkdir(exist_ok=False)
    report = {'status': 'passed', 'scope': 'Exact pinned remapped development JAR bytecode, before runtime mixin transformation',
              'noVendorJarsUploaded': True, 'artifacts': {}}
    for mod, (filename, classes) in TARGETS.items():
        expected = receipt[mod]
        if expected['fileName'] != filename or len(expected['sha256']) != 64:
            raise RuntimeError(f'Unexpected actual runtime artifact for {mod}')
        candidates = sorted(path for path in cache.rglob(filename)
                            if path.is_file() and sha256(path) == expected['sha256'])
        if not candidates:
            raise RuntimeError(f'Exact already-loaded artifact is absent from Gradle cache: {mod}')
        jar = candidates[0]
        outputs = []
        for name in classes:
            target = out / (name.rsplit('.', 1)[-1] + '.javap.txt')
            if target.exists():
                raise RuntimeError('Refusing to overwrite bytecode evidence')
            completed = subprocess.run([javap, '-J-Xmx256m', '-c', '-p', '-l', '-classpath', str(jar), name],
                                       check=True, capture_output=True, text=True, timeout=60)
            if not completed.stdout.strip() or name not in completed.stdout:
                raise RuntimeError(f'Incomplete disassembly for {name}')
            target.write_text(completed.stdout)
            outputs.append({'class': name, 'file': target.name, 'sha256': sha256(target)})
        report['artifacts'][mod] = {'fileName': filename, 'sha256': expected['sha256'], 'classes': outputs}
    (evidence / 'native-bytecode-audit.json').write_text(json.dumps(report, indent=2) + '\n')
    total = sum(len(classes) for _, classes in TARGETS.values())
    print(f'Verified disassembly evidence for {total} exact pinned native inventory/goal/claim/spawn/navigation classes; no vendor JARs copied.')

if __name__ == '__main__':
    main()
