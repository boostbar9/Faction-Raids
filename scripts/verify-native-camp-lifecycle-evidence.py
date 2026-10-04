#!/usr/bin/env python3
"""Strict receipt contract for the actual opt-in integrated-client lifecycle run.

This is reusable unchanged as scripts/verify-native-camp-lifecycle-evidence.py.
It validates recorded evidence; actual pixels and source assertions need review.
"""
import json
import math
from pathlib import Path
import re
import struct
import sys
import uuid

SHOTS = ['01-bounded-campless-fallback.png', '02-recovered-native-camp.png']
SCENARIOS = ['no-safe-land-reload-fallback', 'recovery-only-island']
GUARD_OWNER = 'fac10000-4a1d-4e75-8a1d-000000000001'
REASONS = {'CLAIM_SETUP', 'CLAIM_SAFETY', 'UNLOADED', 'SURFACE', 'NAVAL_SPACE', 'RELIEF',
           'FLUID', 'PROTECTED_BLOCKS', 'BUDGET', 'NO_LAND_EXIT', 'CLAIM_CREATE', 'TERRAIN_APPLY'}


def integer(value, minimum=0, maximum=2**63-1):
    assert type(value) is int and minimum <= value <= maximum, ('invalid integer', value)
    return value


def boolean(value, expected=None):
    assert type(value) is bool and (expected is None or value is expected), ('invalid boolean', value, expected)
    return value


def text(value):
    assert type(value) is str and value.strip() and value != 'null', ('invalid text', value)
    return value


def identifier(value):
    assert type(value) is str and str(uuid.UUID(value)) == value, ('invalid UUID', value)
    return value


def pairs(items):
    result = {}
    for key, value in items:
        assert key not in result, ('duplicate JSON key', key)
        result[key] = value
    return result


def reject_constant(value):
    raise AssertionError('Non-finite JSON constant: ' + value)


def load(path):
    assert path.is_file() and not path.is_symlink(), ('missing/unsafe receipt', str(path))
    data = json.loads(path.read_text(), object_pairs_hook=pairs, parse_constant=reject_constant)
    def finite(value):
        if type(value) is float:
            assert math.isfinite(value), 'Non-finite JSON number'
        elif type(value) is dict:
            for child in value.values(): finite(child)
        elif type(value) is list:
            for child in value: finite(child)
    finite(data)
    return data


def compound(value):
    """Read top-level SNBT fields without flattening quoted/nested compounds.

    Only the known primitive and compound fields emitted by RaidState authority
    are interpreted below. Duplicate fields and unbalanced structures fail.
    """
    text(value)
    assert value.startswith('{') and value.endswith('}'), 'Expected SNBT compound'
    body = value[1:-1]; fields = []; stack = []; quoted = None; escaped = False; start = 0
    for i, char in enumerate(body):
        if quoted:
            if escaped: escaped = False
            elif char == '\\': escaped = True
            elif char == quoted: quoted = None
        elif char in ('"', "'"): quoted = char
        elif char in '{[': stack.append(char)
        elif char in '}]':
            assert stack and stack.pop() == {'}':'{', ']':'['}[char], 'Unbalanced SNBT'
        elif char == ',' and not stack:
            fields.append(body[start:i]); start = i + 1
    assert not stack and quoted is None and not escaped, 'Unbalanced SNBT'
    if body: fields.append(body[start:])
    result = {}
    for field in fields:
        key, sep, raw = field.partition(':');key=key.strip();raw=raw.strip()
        assert sep and re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*', key) and raw and key not in result, 'Malformed or duplicate SNBT field'
        result[key] = raw
    return result


def snbt_int(value, suffix=''):
    assert type(value) is str and re.fullmatch(r'-?(?:0|[1-9][0-9]*)' + suffix, value), ('Invalid SNBT integer', value)
    return int(value[:-len(suffix)] if suffix else value)


def snbt_string(value):
    if value.startswith('"'):
        parsed = json.loads(value)
        assert type(parsed) is str
        return parsed
    # Minecraft's simple strings need no quoting; quoted forms use either quote.
    if value.startswith("'"):
        assert value.endswith("'")
        out = ''; escaped = False
        for char in value[1:-1]:
            if escaped:
                assert char in ("'", '\\'), 'Invalid SNBT escape'
                out += char; escaped = False
            elif char == '\\': escaped = True
            else: out += char
        assert not escaped
        return out
    assert re.fullmatch(r'[A-Za-z0-9._+\-]+', value), 'Invalid SNBT string'
    return value


def diagnostics(value, exhausted):
    assert type(value) is dict and set(value) == {'exhausted', 'counts'}, 'Invalid typed diagnostics'
    boolean(value['exhausted'], exhausted)
    counts=value['counts'];assert type(counts) is dict and set(counts) <= REASONS
    for count in counts.values(): integer(count, 1, 10000)
    return value


def authority(value):
    keys={'teamKey','defensePoint','factionId','recovery','retryTicks','searchStep','searchTicks','searchElapsedTicks',
          'searchCandidate','terraformFallback','searchAbandoned','preparationTicks','preparationTotalTicks',
          'wave','pendingWaveSpawns','diagnostics'}
    assert type(value) is dict and set(value)==keys, 'Incomplete typed saved search authority'
    for key in ['teamKey','defensePoint','factionId']:text(value[key])
    boolean(value['recovery'],True);boolean(value['terraformFallback'],True);boolean(value['searchAbandoned'],False)
    integer(value['retryTicks'],1,1200)
    for key in ['searchStep','searchTicks','searchElapsedTicks','wave','pendingWaveSpawns']:
        assert integer(value[key])==0, ('Reload must occur during pre-search cooldown',key)
    assert value['searchCandidate'] is None, 'Cooldown must not select a candidate'
    assert integer(value['preparationTicks'])==integer(value['preparationTotalTicks'])==3600
    diagnostics(value['diagnostics'],False)
    return value


def reconcile_authority_snbt(raw, value):
    fields=compound(raw)
    string_keys={'Team':'teamKey','DefensePoint':'defensePoint','FactionId':'factionId'}
    bool_keys={'CampSearchRecovery':'recovery','CampTerraformed':'terraformFallback','CampSearchAbandoned':'searchAbandoned'}
    int_keys={'CampSearchRetryTicks':'retryTicks','CampSearchStep':'searchStep','CampSearchTicks':'searchTicks',
              'CampSearchElapsedTicks':'searchElapsedTicks','PreparationTicks':'preparationTicks',
              'PreparationTotal':'preparationTotalTicks','Wave':'wave','PendingWaveSpawns':'pendingWaveSpawns'}
    assert set(fields)==set(string_keys)|set(bool_keys)|set(int_keys)|{'CampSearchDiagnostics'}, 'Unexpected/missing authority SNBT field'
    for key,field in string_keys.items():assert snbt_string(fields[key])==value[field],key
    for key,field in bool_keys.items():assert snbt_int(fields[key],'b')==int(value[field]),key
    for key,field in int_keys.items():assert snbt_int(fields[key])==value[field],key
    recorded=compound(fields['CampSearchDiagnostics']);counts=value['diagnostics']['counts']
    assert set(recorded)==set(counts)|{'Exhausted'}
    assert snbt_int(recorded['Exhausted'],'b')==int(value['diagnostics']['exhausted'])
    for key,count in counts.items():assert snbt_int(recorded[key])==count,key


def verify(root):
    root=Path(root)
    assert root.is_dir() and not root.is_symlink(), 'Unsafe evidence root'
    data=load(root/'result.json')
    assert type(data) is dict and data['mode']=='camp-lifecycle' and data['status']=='passed'
    config=data['fixtureConfiguration']
    assert type(config) is dict and set(config)=={'siegePreparationMinutes','waterDepth','scoutingTimersChanged','recoveryTimersChanged','raidFieldsWritten'}
    assert integer(config['siegePreparationMinutes'])==3 and integer(config['waterDepth'])==24
    for key in ['scoutingTimersChanged','recoveryTimersChanged','raidFieldsWritten']:boolean(config[key],False)
    versions=data['loadedModVersions']
    for mod,version in {'minecraft':'1.20.1','forge':'47.4.16','workers':'2.0.3','recruits':'1.15.2','smallships':'2.0.0-b1.4','siegeweapons':'0.2.5'}.items():
        assert versions[mod]==version,mod
    for mod in ['workers','recruits','smallships','siegeweapons']:
        artifact=data['loadedCompanionArtifacts'][mod]
        assert type(artifact['sha256']) is str and re.fullmatch(r'[0-9a-f]{64}',artifact['sha256']),mod
    assert data['screenshots']==SHOTS and type(data['screenshots']) is list
    cases=data['scenarios'];assert type(cases) is list and len(cases)==2
    assert [case['scenario'] for case in cases]==SCENARIOS
    for index,case in enumerate(cases):
        assert case['status']=='passed' and text(case['entry']).startswith('Actual non-op client /siegeoverhaul start')
        for key in ['sawNaturalSearch','sawEarthworksSearch','sawRecoveryCooldown','sawWiderRecovery','restorationExcludedProtectedCells']:
            boolean(case[key],True)
        integer(case['searchTicksWithoutWaveOrAttackers'],101)
        cooldown=integer(case['cooldownTicksWithoutWaveOrAttackers'],1200)
        assert type(case['passes']) is dict and set(case['passes'])=={'natural','earthworks','recovery'}
        for budget in case['passes'].values():
            assert type(budget) is dict and set(budget)=={'maxCandidates','maxElapsedTicks'}
            integer(budget['maxCandidates'],1,200);integer(budget['maxElapsedTicks'],1,4800)
        integer(case['protectedCellCount'],6144);integer(case['protectedChests'],2)
        integer(case['wave'],1);integer(case['totalSpawned'],1)
        recovery_at=integer(case['recoveryCooldownAtGameTime'])
        search_at=integer(case['firstRecoverySearchAtGameTime'])
        wave_at=integer(case['firstWaveAtGameTime'])
        assert search_at-recovery_at>=1200 and cooldown<=search_at-recovery_at, 'Wider search began before full observed cooldown'
        assert wave_at>search_at, 'First wave preceded wider search'
        boolean(case['campSearchAbandoned'],index==0)
        boolean(case['realWorldReload'],index==0);boolean(case['savedSearchAuthorityVerified'],index==0)
        shot=case['framebuffer'];assert type(shot) is dict
        assert type(shot['file']) is str and shot['file']==SHOTS[index], 'Scenario must use its exact unique framebuffer basename'
        integer(shot['readyFrames'],8);integer(shot['attempts'],1);integer(shot['changedPixelSamples'],51)
        elapsed=shot['elapsedSeconds'];assert type(elapsed) in (int,float) and math.isfinite(elapsed) and 0<=elapsed<30
        path=root/SHOTS[index]
        assert path.is_file() and not path.is_symlink() and path.resolve().parent==root.resolve(), 'Unsafe/missing framebuffer'
        content=path.read_bytes()
        assert content.startswith(b'\x89PNG\r\n\x1a\n') and len(content)>4096 and content[12:16]==b'IHDR'
        width,height=struct.unpack('>II',content[16:24])
        assert 0<width<=8192 and 0<height<=8192, 'Invalid framebuffer dimensions'
        state=case['finalState'];assert type(state) is dict
        assert integer(state['scenario'],0,1)==index and integer(state['gameTime'])>=wave_at
        boolean(state['searchRecovery'],True);boolean(state['terraformFallback'],True)
        boolean(state['searchAbandoned'],index==0);boolean(state['crewStarted'],index==1)
        assert integer(state['retryTicks'])==0
        integer(state['searchStep'],0,200);integer(state['searchElapsedTicks'],0,4800)
        assert type(state['searchCandidate']) is str
        assert integer(state['wave'],1)==case['wave'] and integer(state['totalSpawned'],1)==case['totalSpawned']
        integer(state['raiders']);integer(state['pendingWaveSpawns'])
        assert integer(state['preparationTotalTicks'])==3600
        integer(state['preparationTicks'],0,0 if index==0 else 1200)
        assert type(state['campPosition']) is str and ((state['campPosition']=='null') if index==0 else bool(re.fullmatch(r'BlockPos\{x=-?\d+, y=-?\d+, z=-?\d+\}',state['campPosition'])))
        assert text(state['diagnostics'])==text(case['campSearchDiagnostics'])
        final_diagnostics=compound(state['diagnostics'])
        assert snbt_int(final_diagnostics['Exhausted'],'b')==int(index==0)
    impossible,recovery=cases
    assert integer(impossible['establishedAtGameTime'],-1)==-1
    assert integer(impossible['fallbackAtGameTime'])>=impossible['firstRecoverySearchAtGameTime']
    assert impossible['firstWaveAtGameTime']-impossible['fallbackAtGameTime']>=3600
    saved=authority(impossible['savedSearchAuthority']);loaded=authority(impossible['loadedSearchAuthority'])
    assert saved==loaded, 'Saved and loaded search authority differ'
    reconcile_authority_snbt(impossible['reloadedAuthority'],loaded)
    saved_at=integer(impossible['savedSearchAuthorityGameTime'])
    loaded_at=integer(impossible['loadedSearchAuthorityGameTime'])
    assert saved_at==loaded_at and impossible['recoveryCooldownAtGameTime']<=saved_at<impossible['firstRecoverySearchAtGameTime'], 'Reload game time changed or escaped cooldown'
    assert integer(recovery['fallbackAtGameTime'],-1)==-1
    assert recovery['firstRecoverySearchAtGameTime']<=integer(recovery['establishedAtGameTime'])<recovery['firstWaveAtGameTime']
    island=recovery['recoveryIsland'];assert type(island) is dict
    assert integer(island['width'])==57 and integer(island['soilDepth'])==24 and integer(island['fixtureWrites'])==57*57*24+4
    boolean(island['originalCandidatesCannotFitCore'],True)
    assert integer(island['recoveryCooldownAtGameTime'])==recovery['recoveryCooldownAtGameTime'], 'Duplicated cooldown times disagree'
    assert integer(island['completedAtGameTime'])<island['recoveryCooldownAtGameTime']
    camp=recovery['nativeCamp'];assert type(camp) is dict
    assert integer(camp['nativeClaimChunks'])==25
    identifier(camp['claimId']);identifier(camp['nativeJobOwner']);assert camp['nativeJobOwner']!=GUARD_OWNER
    assert re.fullmatch(r'-?\d+, -?\d+, -?\d+',text(camp['enemyCore'])), 'Missing actual enemy-core coordinates'
    boolean(camp['preservedEdgesRescue'],True);boolean(camp['warGateReadyAtCrewCheck'])
    assert integer(camp['ordinaryTicksSinceEstablishment'],60)<=recovery['firstWaveAtGameTime']-recovery['establishedAtGameTime']
    faction=text(camp['expectedEnemyFaction'])
    assert type(camp['workers']) is list and camp['workers'] and type(camp['guards']) is list and len(camp['guards'])==5
    identities=set()
    for kind in ['workers','guards']:
        for unit in camp[kind]:
            ident=identifier(unit['uuid']);assert ident not in identities, 'Duplicate native entity identity';identities.add(ident)
            boolean(unit['noAi'],False);assert text(unit['faction'])==faction
            owner=identifier(unit['owner']);assert owner==(camp['nativeJobOwner'] if kind=='workers' else GUARD_OWNER)
            if kind=='workers':assert unit['type']=='workers:builder'
            else:assert type(unit['type']) is str and re.fullmatch(r'recruits:[a-z_]+',unit['type'])
    audit=load(root/'native-bytecode-audit.json')
    assert audit['status']=='passed';boolean(audit['noVendorJarsUploaded'],True)
    assert type(audit['artifacts']) is dict and audit['artifacts']
    assert all(type(value['classes']) is list for value in audit['artifacts'].values())
    assert sum(len(value['classes']) for value in audit['artifacts'].values())==18
    return data


if __name__=='__main__':
    if len(sys.argv)!=2:raise SystemExit('usage: verify-native-camp-lifecycle-evidence.py EVIDENCE_DIRECTORY')
    verify(Path(sys.argv[1]))
    print('Strict bounded recovery/reload/native-camp and first-wave receipts verified. Recovery first-wave muster does not imply completed preparation or combat. Review framebuffers separately.')
