#!/usr/bin/env python3
"""Strict read-only development census evidence gate. Never creates an approved profile."""
import importlib.util
import hashlib
import json
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit

DIRT_HASH = '9222e5df0ffbb258af7ad3c42a563b432d46bdaf398bf505eccd1df89db24d25'
BASE = 'com.devfarinsky.siegeoverhaul.'
TRUSTED = sorted([BASE + 'nativecompat.' + x for x in (
    'NativeDirtPolicy', 'NativeDirtCensus', 'NativeDirtRuntime', 'NativeDirtIntrospection',
    'NativeDirtListeners', 'NativeDirtModuleView', 'NativeConstructionEvents', 'NativeConstructionGuard')]
    + [BASE + x for x in ('RaidEvents', 'core.CoreCivilians', 'compat.EnemyHiringProtection', 'compat.SiegeCorpseCleanup')])
REQUIRED_CODE = set(TRUSTED) | {
    'net.minecraft.world.level.Level', 'net.minecraft.world.level.block.Block', 'net.minecraft.world.item.Item',
    'net.minecraft.world.item.BlockItem', 'net.minecraft.world.item.ItemStack',
    'net.minecraft.world.level.storage.loot.LootTable', 'net.minecraft.world.level.storage.loot.LootPool',
    'net.minecraft.world.level.gameevent.GameEvent', 'net.minecraft.world.level.gameevent.GameEventDispatcher',
    'net.minecraft.world.level.gameevent.EuclideanGameEventListenerRegistry', 'net.minecraft.world.level.chunk.LevelChunk',
    'net.minecraft.server.level.ServerLevel', 'net.minecraft.world.level.gameevent.GameEventListenerRegistry',
    'net.minecraft.world.entity.Entity', 'net.minecraft.world.entity.item.ItemEntity',
    'net.minecraft.world.level.block.state.BlockBehaviour', 'net.minecraft.world.level.storage.loot.entries.LootItem',
    'net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer',
    'net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer',
    'net.minecraft.world.level.storage.loot.providers.number.ConstantValue',
    'net.minecraft.world.level.storage.loot.predicates.ExplosionCondition',
    'net.minecraftforge.eventbus.api.EventListenerHelper', 'net.minecraftforge.common.ForgeInternalHandler',
    'net.minecraftforge.common.ForgeHooks', 'net.minecraftforge.common.loot.LootModifierManager',
    'net.minecraftforge.eventbus.EventBus', 'net.minecraftforge.eventbus.ASMEventHandler',
    'net.minecraftforge.eventbus.ClassLoaderFactory', 'net.minecraftforge.eventbus.ModLauncherFactory',
    'net.minecraftforge.eventbus.internal.CacheConcurrent',
    'com.talhanation.workers.entities.AbstractWorkerEntity', 'com.talhanation.workers.entities.BuilderEntity',
    'com.talhanation.workers.entities.ai.BuilderWorkGoal', 'com.talhanation.recruits.entities.AbstractRecruitEntity',
    'cpw.mods.cl.JarModuleFinder', 'cpw.mods.cl.JarModuleFinder$JarModuleReader', 'cpw.mods.cl.JarModuleFinder$JarModuleReference',
    'cpw.mods.jarhandling.impl.Jar', 'cpw.mods.jarhandling.impl.Jar$JarModuleDataProvider',
    'cpw.mods.niofs.union.UnionFileSystem', 'cpw.mods.niofs.union.UnionFileSystemProvider', 'cpw.mods.niofs.union.UnionPath'}
EVENTS = {'net.minecraftforge.event.LootTableLoadEvent', 'net.minecraftforge.event.entity.EntityJoinLevelEvent',
          'net.minecraftforge.event.entity.EntityEvent$EntityConstructing', 'net.minecraftforge.event.AttachCapabilitiesEvent',
          'net.minecraftforge.event.level.BlockEvent$NeighborNotifyEvent', 'net.minecraftforge.event.VanillaGameEvent'}
UNCHANGED = {'clock', 'registries', 'blockEntities', 'pendingBlockEntities', 'worldCells', 'rng', 'inventories',
             'entities', 'nativeQueues', 'scheduledTickCounts', 'ledger', 'workerData'}
RNG_TYPES = {'net.minecraft.world.level.levelgen.' + x for x in
             ('LegacyRandomSource', 'ThreadSafeLegacyRandomSource', 'XoroshiroRandomSource')}

SELF_TEST_CASES = [
    'combined-roots-and-fresh-seal', 'overlay-precedence-and-backing-seal', 'hidden-directory-visible-descendant',
    'multi-release-version-only-and-boundary', 'unsupported-multi-release-spelling', 'raw-entry-budget-before-union-allocation',
    'literal-backslash-refused', 'backing-file-symlink-refused', 'backing-root-symlink-refused', 'removed-root-refused',
    'zip-and-directory-combined-view']


def require(ok, message):
    if not ok:
        raise ValueError(message)


def keys(obj, expected):
    require(type(obj) is dict and set(obj) == set(expected.split()), 'Unknown/missing evidence fields')


def integer(value, minimum=0, maximum=2**63 - 1):
    require(type(value) is int and minimum <= value <= maximum, 'Invalid bounded integer')
    return value


def text(value, pattern=r'[A-Za-z0-9_.+$:-]{1,256}'):
    require(type(value) is str and re.fullmatch(pattern, value), 'Unsafe or unknown identifier')
    return value


def digest(value):
    return text(value, '[0-9a-f]{64}')


def check(value, reason):
    keys(value, 'reason detail')
    require(value['reason'] == reason, f'Expected {reason}, observed refusal {value["reason"]}')
    text(value['detail'], r'[A-Za-z0-9 _;:.,()/+-]{1,512}')


def resource(value):
    keys(value, 'pack builtin sha256 bytes')
    text(value['pack'], 'utf16-sha256:[0-9a-f]{64}'); digest(value['sha256'])
    require(type(value['builtin']) is bool, 'Missing resource built-in provenance')
    integer(value['bytes'], 1, 8192)


def source(value):
    require(type(value) is str and len(value) <= 4096, 'Unbounded origin')
    parsed = urlsplit(value)
    require(not parsed.netloc and not parsed.query and not parsed.fragment, 'Private URI components in origin')
    if parsed.scheme == 'jar':
        require(value.endswith('!/') and value.count('!/') == 1 and not value[4:].startswith('jar:'), 'Nested/non-root jar origin')
        source(value[4:-2])
    else:
        require(parsed.scheme in {'file', 'union'}, 'Non-local origin')


def origin(value, modules):
    keys(value, 'type module source classResourceSha256')
    text(value['type'], r'[A-Za-z_$][A-Za-z0-9_.$]{0,4095}'); text(value['module'])
    source(value['source']); digest(value['classResourceSha256'])
    require(value['module'] in modules, 'Loaded class module is absent from actual catalog')


def catalog(value, fill):
    keys(value, 'launch distribution javaRuntime mods modules layers services pendingMixins transformations firstParty')
    text(value['launch']); require(value['launch'].startswith('development:'), 'This gate is development-only')
    require(value['distribution'] == 'CLIENT', 'Integrated-client profile required')
    text(value['javaRuntime'], r'17[0-9A-Za-z_.+() /-]{1,250}')
    mods = value['mods']; require(type(mods) is list and 7 <= len(mods) <= 32, 'Incomplete/unbounded mod catalog')
    for row in mods:
        keys(row, 'mod version sourceKind sha256 bytes'); text(row['mod']); text(row['version']); digest(row['sha256'])
        require(row['sourceKind'] in {'development-file', 'development-combined-module'}, 'Original/remapped mode conflated')
        integer(row['bytes'], 1, 256 * 1024**2)
    require([r['mod'] for r in mods] == sorted(set(r['mod'] for r in mods)), 'Unstable/duplicate artifact ordering')
    by_mod = {r['mod']: r for r in mods}
    require({'minecraft', 'forge', 'siegeoverhaul', 'workers', 'recruits', 'smallships', 'siegeweapons'} <= by_mod.keys(),
            'Missing required runtime input')
    require(by_mod['minecraft']['version'] == '1.20.1' and by_mod['forge']['version'] == '47.4.16', 'Wrong native runtime')
    for mod in ('workers', 'recruits', 'smallships', 'siegeweapons'):
        require(by_mod[mod]['sourceKind'] == 'development-file'
                and by_mod[mod]['sha256'] == fill['loadedCompanionArtifacts'][mod]['sha256'], 'Runtime differs from actual one-FILL companions')
    require(by_mod['siegeoverhaul']['sourceKind'] == 'development-combined-module', 'Entire remapped QA module is not pinned')
    modules = value['modules']; require(type(modules) is list and 1 <= len(modules) <= 256, 'Unbounded module catalog')
    module_keys = []
    expected_layers = {'EMPTY': [], 'JVM_BOOT': ['EMPTY'], 'BOOT': ['JVM_BOOT'],
                       'SERVICE': ['BOOT'], 'PLUGIN': ['BOOT'], 'GAME': ['PLUGIN', 'SERVICE']}
    layers = value['layers']
    require(type(layers) is list and layers == [{'name': name, 'parents': expected_layers[name]} for name in sorted(expected_layers)],
            'Pinned complete layer graph or ordered parent edges differ')
    for row in modules:
        keys(row, 'layer name version contentSha256 providers')
        require(row['layer'] in {'BOOT', 'SERVICE', 'PLUGIN', 'GAME', 'JVM_BOOT'}, 'Unknown module layer'); text(row['name'])
        text(row['version'], r'[A-Za-z0-9_.+() /-]{0,256}')
        if row['contentSha256'] == 'trusted-java-runtime':
            require(row['name'].startswith(('java.', 'jdk.')), 'Unhashed game module')
        else: digest(row['contentSha256'])
        providers = row['providers']; require(type(providers) is list and len(providers) <= 256, 'Unbounded module providers')
        for provider in providers: text(provider, r'[A-Za-z0-9_.$]+=[A-Za-z0-9_.$]+')
        require(providers == sorted(providers), 'Unstable provider ordering')
        module_keys.append((row['layer'], row['name']))
    require(module_keys == sorted(set(module_keys)), 'Duplicate/unordered module catalog')
    require({key[0] for key in module_keys} == {'BOOT', 'SERVICE', 'PLUGIN', 'GAME', 'JVM_BOOT'}, 'Missing actual launcher layer')
    require(('JVM_BOOT', 'cpw.mods.securejarhandler') in module_keys, 'Actual SecureJar JVM ancestor absent')
    names = {key[1] for key in module_keys}
    services = value['services']; require(type(services) is list and 1 <= len(services) <= 256, 'Missing/unbounded launcher services')
    for row in services:
        keys(row, 'name type file'); text(row['name'], '[A-Za-z0-9_.-]{1,128}'); text(row['file'], '[A-Za-z0-9_.+-]{1,256}')
        require(row['type'] in {'PLUGINSERVICE', 'TRANSFORMATIONSERVICE'}, 'Unexpected launcher service')
    require(value['pendingMixins'] == [], 'Pending/unvisited mixin admission')
    activities = value['transformations']; require(type(activities) is list and 1 <= len(activities) <= 2048, 'Missing/unbounded transformation audit')
    for row in activities:
        keys(row, 'owner kind contextDigests'); text(row['owner'], r'[A-Za-z0-9_.$]{1,4096}'); text(row['kind'])
        require(type(row['contextDigests']) is list and len(row['contextDigests']) <= 16, 'Unbounded audit context')
        for entry in row['contextDigests']: digest(entry)
    first = value['firstParty']; keys(first, 'mod module trustedClasses')
    require(first['mod'] == 'siegeoverhaul' and first['trustedClasses'] == TRUSTED, 'First-party trust set differs')
    text(first['module']); own = next((r for r in modules if (r['layer'], r['name']) == ('GAME', first['module'])), None)
    require(own and own['contentSha256'] == by_mod['siegeoverhaul']['sha256'], 'First-party actual GAME/ModFile content mismatch')
    return names


def census(value, root, fill):
    keys(value, 'generation gameTime target dirt modifierLayers listeners implementation runtime chunks sections registries lootGraph activeModifiers observation')
    check(value['observation'], 'READY'); check(value['lootGraph'], 'READY')
    integer(value['generation'], 1); integer(value['gameTime']); integer(value['target'], -(2**63))
    require(value['generation'] == root['lifecycle']['currentGeneration'] and value['gameTime'] == root['captureGameTime'], 'Stale live observation')
    x, y, z = root['target']; packed = ((x & 0x3ffffff) << 38) | ((z & 0x3ffffff) << 12) | (y & 0xfff)
    if packed >= 2**63: packed -= 2**64
    require(value['target'] == packed and value['chunks'] == 9 and value['sections'] == 27, 'Wrong or incomplete native radius')
    resource(value['dirt']); require(value['dirt']['builtin'] is True and value['dirt']['bytes'] == 376
                                    and value['dirt']['sha256'] == DIRT_HASH, 'Vanilla dirt resource differs')
    require(type(value['modifierLayers']) is list and len(value['modifierLayers']) <= 16, 'Unbounded modifier layers')
    for layer in value['modifierLayers']: resource(layer)
    require(type(value['activeModifiers']) is int and value['activeModifiers'] == 0, 'Active modifier not refused')
    expected = [(cx, sy, cz) for cx in range((x-16) >> 4, ((x+16) >> 4)+1)
                for cz in range((z-16) >> 4, ((z+16) >> 4)+1) for sy in range((y-16) >> 4, ((y+16) >> 4)+1)]
    registries = value['registries']; require(type(registries) is list and len(registries) == 27, 'Incomplete section coordinate evidence')
    actual = []
    for row in registries:
        keys(row, 'chunkX sectionY chunkZ kind check')
        for key in ('chunkX', 'sectionY', 'chunkZ'): integer(row[key], -(2**31), 2**31 - 1)
        actual.append((row['chunkX'], row['sectionY'], row['chunkZ']))
        require(row['kind'] in {'ABSENT', 'NOOP', 'NATIVE'}, 'Unknown native registry accepted'); check(row['check'], 'READY')
    require(actual == expected, 'Section keys clipped, duplicated, omitted, or reordered')
    modules = catalog(value['runtime'], fill)
    code = value['implementation']; require(type(code) is list and len(code) <= 128, 'Unbounded class catalog')
    for row in code: origin(row, modules)
    require(len({r['type'] for r in code}) == len(code) and REQUIRED_CODE <= {r['type'] for r in code}, 'Required actual class origins absent')
    for row in code:
        if row['type'] in TRUSTED:
            require(row['module'] == value['runtime']['firstParty']['module'], 'First-party class escaped its actual module')
    listeners = value['listeners']; require(type(listeners) is list and 1 <= len(listeners) <= 1536, 'Missing/unbounded inherited listeners')
    for row in listeners:
        keys(row, 'event callback priority receiveCanceled genericFilter owner declaringClass wrapper')
        require(row['event'] in EVENTS, 'Unrecognized inherited event census')
        text(row['callback'], r'[A-Za-z0-9_.$<>#;:()/\[\]-]{1,4096}')
        require(row['priority'] in {'HIGHEST', 'HIGH', 'NORMAL', 'LOW', 'LOWEST', 'MONITOR'}, 'Unknown listener priority')
        require(type(row['receiveCanceled']) is bool, 'Missing cancellation semantics')
        text(row['genericFilter'], r'[A-Za-z0-9_.$<>*;()/\[\]-]{0,4096}'); text(row['wrapper'], r'[A-Za-z0-9_.$/]{1,4096}')
        origin(row['owner'], modules); origin(row['declaringClass'], modules)


def validate(result, fill):
    receipt = fill.get('dirtCensus'); keys(receipt, 'enabled status artifactSha256 failureType failurePhase')
    require(receipt['enabled'] is True and receipt['status'] == 'captured' and receipt['failureType'] == '' and receipt['failurePhase'] == '', 'Optional census capture was refused')
    digest(receipt['artifactSha256'])
    keys(result, 'schema profileStatus packagedProductionAcceptance miningCallbacksInvoked packIdentityEncoding secureJarSelfTest secureJarSelfTestScope inputSealCensus inputSealMetrics inputSealMatches inputSealScope postInputSealStateCaptured bindingAttempted postBindStateCaptured postInspectionStatesCaptured noEffectsEvidenceComplete target buildHeight stableSinceGameTime captureGameTime lifecycle binding bindingCensus decision censuses metrics states unchanged status')
    require(result['packIdentityEncoding'] == 'sha256-length-framed-utf16-code-units', 'Raw or unknown pack identity encoding')
    self_test = result['secureJarSelfTest']; keys(self_test, 'status failedCase failureType passedCases')
    require(self_test['status'] == 'passed' and self_test['failedCase'] == self_test['failureType'] == ''
            and self_test['passedCases'] == SELF_TEST_CASES, 'Real SecureJar synthetic cases did not all pass')
    require(result['secureJarSelfTestScope'] == 'synthetic fresh files only; no module activation; separate from world evidence', 'Self-test scope conflated')
    require(result['inputSealMatches'] is True and result['postInputSealStateCaptured'] is True
            and result['inputSealScope'] == 'independent-off-pulse-frozen-fixture-input-seals; not mutable-input work validation', 'Missing actual fresh input seal')
    require(result['bindingAttempted'] is True and result['postBindStateCaptured'] is True
            and result['postInspectionStatesCaptured'] == [True, True] and all(type(v) is bool for v in result['postInspectionStatesCaptured'])
            and result['noEffectsEvidenceComplete'] is True, 'Post-state was not actually captured for every read window')
    require(result['schema'] == 'native-dirt-census-qa-v1' and result['status'] == 'captured', 'Successful native census absent')
    require(result['profileStatus'] == 'PROFILE_UNREVIEWED' and result['packagedProductionAcceptance'] is False,
            'Observed development census approved itself or claimed production acceptance')
    require(type(result['miningCallbacksInvoked']) is int and result['miningCallbacksInvoked'] == 0, 'Read-only scope changed')
    require(result['target'] == fill['target'] == [141, 64, 9], 'Not the genuine one-FILL target')
    require(result['buildHeight'] == [-64, 320], 'Wrong Overworld build bounds')
    require(integer(result['captureGameTime']) == fill['after']['gameTime'] and result['stableSinceGameTime'] == fill['stableSinceGameTime']
            and result['captureGameTime'] - result['stableSinceGameTime'] >= 40, 'Census outside stable one-FILL finish')
    lifecycle = result['lifecycle']; keys(lifecycle, 'signal successfulCompletion startedGameTime startedGeneration currentGeneration')
    require(lifecycle['signal'] == 'ServerStartedEvent' and lifecycle['successfulCompletion'] is True, 'Resource lifecycle not proven')
    require(integer(lifecycle['startedGeneration'], 1) == integer(lifecycle['currentGeneration'], 1), 'Reload generation changed')
    require(integer(lifecycle['startedGameTime']) <= fill['acceptedGameTime'], 'Lifecycle established after work')
    check(result['binding'], 'READY'); check(result['decision'], 'PROFILE_UNREVIEWED')
    census(result['bindingCensus'], result, fill)
    require(result['bindingCensus']['observation'] == result['binding'], 'Binding check differs from its actual Census')
    require(type(result['censuses']) is list and len(result['censuses']) == 2, 'Missing fresh repeated observations')
    for row in result['censuses']: census(row, result, fill)
    census(result['inputSealCensus'], result, fill)
    require(result['bindingCensus'] == result['censuses'][0] == result['censuses'][1] == result['inputSealCensus'], 'Same-tick actual binding/fresh census drift')
    metrics = result['metrics']; require(type(metrics) is list and len(metrics) == 4, 'Missing bind/fresh read counters')
    for row in metrics:
        keys(row, 'bytesRead artifactsHashed modulesHashed cachedFileChecks cachedModuleChecks')
        for key, value in row.items(): integer(value, 0, 1024**3 if key == 'bytesRead' else 100000)
    require(all(v == 0 for v in metrics[0].values()), 'Runtime bound before measured off-pulse commissioning')
    require(0 < metrics[1]['bytesRead'] == metrics[2]['bytesRead'] == metrics[3]['bytesRead'] <= 1024**3, 'Fresh inspection reread startup bytes')
    for key in ('artifactsHashed', 'modulesHashed'):
        require(0 < metrics[1][key] == metrics[2][key] == metrics[3][key], 'Runtime input set changed')
    for key in ('cachedFileChecks', 'cachedModuleChecks'):
        require(metrics[1][key] < metrics[2][key] < metrics[3][key], 'Runtime inputs were not freshly revalidated')
    seal_metrics = result['inputSealMetrics']; require(type(seal_metrics) is list and len(seal_metrics) == 2, 'Missing fresh seal counters')
    for row in seal_metrics:
        keys(row, 'bytesRead artifactsHashed modulesHashed cachedFileChecks cachedModuleChecks')
        for key, value in row.items(): integer(value, 0, 1024**3 if key == 'bytesRead' else 100000)
    require(all(v == 0 for v in seal_metrics[0].values()) and seal_metrics[1] == metrics[1], 'Input seal reused cache or captured different input bytes')
    states = result['states']; require(type(states) is list and len(states) == 6, 'Missing before/after no-effects and seal states')
    for row in states:
        keys(row, 'gameTime workerTick registryMapSizes existingBlockEntityCounts pendingBlockEntityCounts worldCellCount worldCellsSha256 targetState rngSha256 rngKinds inventorySha256 inventorySlots entities itemEntities xpEntities xpValue nativeQueueSizes requestCount nativeQueuesSha256 scheduledTickCounts ledgerSha256 workerDataSha256 journalState journalReceipts inFlight')
        require(row['gameTime'] == result['captureGameTime'] and row['workerTick'] == fill['after']['workerTickCount'], 'World advanced during observation')
        require(row['worldCellCount'] == 33**3 and row['targetState'] == 'minecraft:dirt', 'World envelope differs')
        require(row['journalState'] == 'STAGE_VERIFIED' and type(row['journalReceipts']) is int and row['journalReceipts'] == 1 and row['inFlight'] is False, 'Receipt/fence changed')
        require(type(row['rngKinds']) is list and len(row['rngKinds']) == 3 and set(row['rngKinds']) <= RNG_TYPES, 'RNG coverage unavailable')
        for key in ('worldCellsSha256', 'rngSha256', 'inventorySha256', 'nativeQueuesSha256', 'ledgerSha256', 'workerDataSha256'): digest(row[key])
        for key in ('registryMapSizes', 'existingBlockEntityCounts', 'pendingBlockEntityCounts'):
            require(type(row[key]) is list and len(row[key]) == 9, 'Missing chunk-map snapshots')
            for count in row[key]: integer(count, 0, 128)
        require(type(row['inventorySlots']) is list and len(row['inventorySlots']) == 4
                and row['inventorySlots'][0] == 27 and row['inventorySlots'][2:] == [41, 2], 'Missing exact chest/player/hand inventory coverage')
        for count in row['inventorySlots']: integer(count, 1, 64)
        require(row['nativeQueueSizes'] == [0, 0, 0, 0] and row['requestCount'] == 0, 'Native work queue not settled')
        require(type(row['scheduledTickCounts']) is list and len(row['scheduledTickCounts']) == 2, 'Missing scheduled queue counts')
        for count in row['scheduledTickCounts']: integer(count)
        integer(row['entities'], 1, 128)
        for key in ('itemEntities', 'xpEntities', 'xpValue'): require(type(row[key]) is int and row[key] == 0, 'Unexpected loose resources')
    require(all(row == states[0] for row in states), 'Before/after bounded state differs')
    require(type(result['unchanged']) is dict and set(result['unchanged']) == UNCHANGED
            and all(value is True for value in result['unchanged'].values()), 'Exact private no-effects equality failed or missing')
    return result


def strict_load(path):
    data = Path(path).read_bytes(); require(len(data) <= 2_000_000, 'Evidence too large')
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'Duplicate JSON field'); result[key] = value
        return result
    def invalid_constant(_): raise ValueError('Non-finite JSON value')
    return json.loads(data, object_pairs_hook=unique, parse_constant=invalid_constant)


def verify_sidecar_digest(path, fill):
    receipt = fill.get('dirtCensus'); keys(receipt, 'enabled status artifactSha256 failureType failurePhase')
    digest(receipt['artifactSha256'])
    require(receipt['status'] == 'captured' and receipt['enabled'] is True and receipt['failureType'] == '' and receipt['failurePhase'] == '', 'Refused sidecar capture')
    data = Path(path).read_bytes(); require(len(data) <= 2_000_000, 'Sidecar exceeds output bound')
    require(hashlib.sha256(data).hexdigest() == receipt['artifactSha256'], 'Sidecar differs from the exact one-FILL capture receipt')


def ci_identity(directory):
    receipt = strict_load(Path(directory) / 'ci-run.json'); keys(receipt, 'runId runAttempt')
    return tuple(text(receipt[key], '[1-9][0-9]{0,19}') for key in ('runId', 'runAttempt'))


def distinct_ci_runs(first, second):
    require(ci_identity(first) != ci_identity(second), 'Distinct CI run/attempt receipts are required')


def verify_directory(path):
    path = Path(path).resolve(strict=True)
    spec = importlib.util.spec_from_file_location('one_fill', Path(__file__).with_name('verify-native-earthworks.py'))
    original = importlib.util.module_from_spec(spec); spec.loader.exec_module(original)
    original.verify_directory(path)
    ci_identity(path)
    fill = strict_load(path / 'result.json')
    result = validate(strict_load(path / 'dirt-census.json'), fill)
    verify_sidecar_digest(path / 'dirt-census.json', fill)
    for name in ('source-commit.txt', 'source-tree.txt'): text((path / name).read_text().strip(), '[0-9a-f]{40}')
    return result


def profile_shape(result):
    """Portable equality only, never a Profile factory. Only installation-specific source URIs are removed."""
    value = json.loads(json.dumps(result['censuses'][0]))
    for key in ('generation', 'gameTime', 'target', 'chunks', 'sections', 'registries', 'observation'): value.pop(key)
    for origin_value in value['implementation']:
        origin_value.pop('source')
    for listener in value['listeners']:
        listener['owner'].pop('source'); listener['declaringClass'].pop('source')
    return value


def compare_directories(first, second):
    first, second = Path(first).resolve(strict=True), Path(second).resolve(strict=True)
    require(first != second, 'Two different clean launch artifacts required')
    distinct_ci_runs(first, second)
    a, b = verify_directory(first), verify_directory(second)
    for name in ('source-commit.txt', 'source-tree.txt'):
        require((first / name).read_text().strip() == (second / name).read_text().strip(), 'Clean launch source identity differs')
    fa, fb = strict_load(first / 'result.json'), strict_load(second / 'result.json')
    require(fa['startedUtc'] != fb['startedUtc'] and fa['identity']['builder'] != fb['identity']['builder'], 'Same launch copied as two launches')
    require(profile_shape(a) == profile_shape(b), 'Actual development profile is not reproducible across clean launches')


if __name__ == '__main__':
    require(len(sys.argv) in {2, 3}, 'Usage: verify-native-dirt-census.py EVIDENCE [SECOND_CLEAN_EVIDENCE]')
    if len(sys.argv) == 3:
        compare_directories(sys.argv[1], sys.argv[2])
        print('Two clean development censuses are reproducible; PROFILE_UNREVIEWED. No native CUT or packaged acceptance.')
    else:
        verify_directory(sys.argv[1])
        print('Read-only development census verified; PROFILE_UNREVIEWED. Independent review still required.')
