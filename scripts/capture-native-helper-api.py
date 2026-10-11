#!/usr/bin/env python3
"""Capture exact prepared transfer/ticket bytecode for review. Never authorizes gameplay.

Reads only the exported runClient classpath, never searches a cache or downloads a JAR.
Outputs text/metadata only. These are pre-mixin classes, not transformed runtime proof.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import zipfile


ROOT_CLASSES = (
    'net.minecraft.world.entity.Entity',
    'net.minecraft.world.entity.LivingEntity',
    'net.minecraft.world.entity.Mob',
    'net.minecraft.world.entity.PathfinderMob',
    'net.minecraft.server.level.ServerLevel',
    'net.minecraft.server.level.ServerChunkCache',
    'net.minecraft.server.level.DistanceManager',
    'net.minecraft.server.level.TicketType',
    'net.minecraft.world.level.portal.PortalInfo',
    'net.minecraft.world.level.ForcedChunksSavedData',
    'net.minecraft.world.level.entity.PersistentEntitySectionManager',
    'net.minecraftforge.common.util.ITeleporter',
    'net.minecraftforge.common.world.ForgeChunkManager',
    'net.minecraftforge.common.ForgeHooks',
    'net.minecraftforge.event.entity.EntityJoinLevelEvent',
    'net.minecraftforge.event.entity.EntityLeaveLevelEvent',
    'com.talhanation.recruits.pathfinding.AsyncPathfinderMob',
    'com.talhanation.recruits.entities.AbstractChunkLoaderEntity',
    'com.talhanation.recruits.entities.AbstractInventoryEntity',
    'com.talhanation.recruits.entities.AbstractRecruitEntity',
    'com.talhanation.workers.entities.AbstractWorkerEntity',
    'com.talhanation.workers.entities.BuilderEntity',
)
MAX_CLASSES = 256
MAX_CLASS_BYTES = 8 * 1024 * 1024
PINNED = dict(minecraftVersion='1.20.1', forgeVersion='47.4.16', mappingChannel='official', mappingVersion='1.20.1')


def file_hash(path):
    digest = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''):
            digest.update(block)
    return digest.hexdigest()


def parse_unique(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError(f'Duplicate JSON key: {key}')
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=pairs)


def load_unique(path):
    return parse_unique(path.read_bytes())


def disassemble(jar, name):
    javap = shutil.which('javap')
    if not javap:
        raise ValueError('Java 17 javap is required; no alternate tool is downloaded')
    result = subprocess.run([javap, '-J-Xmx256m', '-c', '-p', '-s', '-l', '-classpath', str(jar), name],
                            check=True, capture_output=True, text=True, timeout=60)
    return result.stdout


def capture(manifest_path, output, runner=disassemble):
    manifest_path = manifest_path.resolve(strict=True)
    manifest_bytes = manifest_path.read_bytes()
    manifest_hash = hashlib.sha256(manifest_bytes).hexdigest()
    manifest = parse_unique(manifest_bytes)
    if manifest.get('formatVersion') != 1 or any(manifest.get(k) != v for k, v in PINNED.items()):
        raise ValueError('Prepared runtime is not the reviewed Minecraft/Forge/mapping version')
    entries = manifest.get('entries')
    if not isinstance(entries, list) or not 1 <= len(entries) <= 1024:
        raise ValueError('Unexpected runtime entry count')
    paths, suppliers, artifacts = set(), {}, {}
    roots = {name.replace('.', '/') for name in ROOT_CLASSES}

    def wanted(entry):
        if not entry.endswith('.class'):
            return False
        name = entry[:-6]
        return name in roots or any(name.startswith(root + '$') for root in roots)

    for entry in entries:
        if not isinstance(entry, dict) or entry.get('kind') not in {'file', 'directory', 'absent'}:
            raise ValueError('Malformed runtime entry')
        raw = entry.get('path')
        if not isinstance(raw, str) or not Path(raw).is_absolute():
            raise ValueError('Runtime paths must be absolute exported paths')
        path = Path(raw)
        if path.is_symlink() or str(path.resolve()) != raw or raw in paths:
            raise ValueError('Runtime path is not unique and canonical')
        paths.add(raw)
        coordinates = entry.get('coordinates')
        if not isinstance(coordinates, list) or any(not isinstance(c, str) or not c for c in coordinates):
            raise ValueError('Missing resolved coordinate metadata')
        if entry['kind'] == 'absent':
            if path.exists():
                raise ValueError('Previously absent runtime entry appeared')
            continue
        if entry['kind'] == 'directory':
            if not path.is_dir():
                raise ValueError('Prepared runtime directory disappeared')
            if any((path / (root + '.class')).exists()
                   or any((path / root).parent.glob((path / root).name + '$*.class')) for root in roots):
                raise ValueError('Unexpected directory supplies a required native class')
            continue
        if not path.is_file() or not re.fullmatch('[0-9a-f]{64}', str(entry.get('sha256', ''))):
            raise ValueError('Runtime artifact or hash missing')
        if file_hash(path) != entry['sha256']:
            raise ValueError('Runtime artifact changed after preparation')
        if not zipfile.is_zipfile(path):
            if path.suffix.lower() == '.jar':
                raise ValueError('Prepared JAR is not readable ZIP bytecode')
            continue
        with zipfile.ZipFile(path) as jar:
            if any((versioned := re.fullmatch(r'META-INF/versions/[^/]+/(.+)', info.filename))
                   and wanted(versioned.group(1)) for info in jar.infolist()):
                raise ValueError('Multi-release variant of a required native class is not supported')
            matches = [info for info in jar.infolist() if wanted(info.filename)]
            if not matches:
                continue
            manifest_text = jar.read('META-INF/MANIFEST.MF').decode('utf-8', errors='replace') if 'META-INF/MANIFEST.MF' in jar.namelist() else ''
            artifacts[raw] = dict(path=raw, fileName=path.name, sha256=entry['sha256'],
                                  resolvedCoordinates=coordinates, jarManifest=manifest_text[:65536])
            for info in matches:
                name = info.filename[:-6].replace('/', '.')
                if not re.fullmatch(r'[A-Za-z0-9_.$]+', name):
                    raise ValueError('Unexpected native class entry name')
                if name in suppliers:
                    raise ValueError(f'Ambiguous required class supplier: {name}: {suppliers[name][0]} and {path}')
                if info.file_size <= 0 or info.file_size > MAX_CLASS_BYTES:
                    raise ValueError(f'Unexpected class size: {name}')
                bytecode = jar.read(info)
                suppliers[name] = (path, hashlib.sha256(bytecode).hexdigest())
    missing = (set(ROOT_CLASSES) | {'net.minecraft.world.entity.Entity$RemovalReason'}) - set(suppliers)
    if missing:
        raise ValueError('Prepared runtime lacks a required transfer, removal, ticket or inherited worker class: ' + ', '.join(sorted(missing)))
    if len(suppliers) > MAX_CLASSES:
        raise ValueError('Required class closure exceeds bounded audit')
    if output.absolute() != output.resolve():
        raise ValueError('Refusing a symlinked evidence output path')
    output.mkdir(parents=True, exist_ok=False)
    bytecode_dir = output / 'bytecode'
    bytecode_dir.mkdir()
    classes = []
    for name, (jar, class_hash) in sorted(suppliers.items()):
        text = runner(jar, name)
        if not isinstance(text, str) or not text.strip() or name not in text or 'descriptor:' not in text:
            raise ValueError(f'Incomplete disassembly/signatures: {name}')
        if len(text.encode()) > 4 * 1024 * 1024:
            raise ValueError('Disassembly exceeds bounded output')
        file = bytecode_dir / (name + '.javap.txt')
        file.write_text(text)
        classes.append(dict(name=name, supplier=str(jar), classSha256=class_hash,
                            disassemblyFile=str(file.relative_to(output)), disassemblySha256=file_hash(file)))
    if any(file_hash(Path(path)) != artifact['sha256'] for path, artifact in artifacts.items()):
        raise ValueError('Supplying artifact changed during capture')
    if file_hash(manifest_path) != manifest_hash:
        raise ValueError('Prepared runtime manifest changed during capture')
    report = dict(formatVersion=1, status='captured-unreviewed', gameplayAuthorized=False,
                  scope='Exact prepared runtime JARs before mixin transformation; semantic review and loaded-artifact parity are still required',
                  noVendorJarsUploaded=True, runtimeManifestSha256=manifest_hash,
                  versionIdentityStatus='Declared prepared-build versions only; exact supplier metadata requires independent review',
                  versions=PINNED, artifacts=list(artifacts.values()), classes=classes)
    (output / 'capture.json').write_text(json.dumps(report, indent=2, sort_keys=True) + '\n')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('runtime_manifest', type=Path)
    parser.add_argument('output_directory', type=Path)
    args = parser.parse_args()
    try:
        report = capture(args.runtime_manifest, args.output_directory)
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        parser.exit(1, f'Refused native helper API capture: {error}\n')
    print(f"Captured {len(report['classes'])} exact classes for independent review; gameplay is NOT authorized.")


if __name__ == '__main__':
    main()
