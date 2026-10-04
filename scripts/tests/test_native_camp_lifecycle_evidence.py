"""Synthetic receipt/parser regression tests; no Minecraft runtime evidence."""
from copy import deepcopy
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import unittest

SCRIPT=Path(__file__).resolve().parents[1]/'verify-native-camp-lifecycle-evidence.py'
spec=importlib.util.spec_from_file_location('strict_camp_lifecycle_evidence',SCRIPT)
verifier=importlib.util.module_from_spec(spec);spec.loader.exec_module(verifier)


def fixture(root):
    root.mkdir()
    shots=list(verifier.SHOTS)
    for name in shots:
        (root/name).write_bytes(b'\x89PNG\r\n\x1a\n'+b'\0'*4+b'IHDR'+struct.pack('>II',1280,720)+b'SYNTHETIC'*600)
    auth={'teamKey':'fixture','defensePoint':'core','factionId':'wilds_marauders','recovery':True,'retryTicks':1180,
          'searchStep':0,'searchTicks':0,'searchElapsedTicks':0,'searchCandidate':None,'terraformFallback':True,
          'searchAbandoned':False,'preparationTicks':3600,'preparationTotalTicks':3600,'wave':0,'pendingWaveSpawns':0,
          'diagnostics':{'exhausted':False,'counts':{'FLUID':200}}}
    snbt='{Team:"fixture",DefensePoint:"core",FactionId:"wilds_marauders",CampSearchRecovery:1b,CampSearchRetryTicks:1180,CampSearchStep:0,CampSearchTicks:0,CampSearchElapsedTicks:0,CampTerraformed:1b,CampSearchAbandoned:0b,PreparationTicks:3600,PreparationTotal:3600,Wave:0,PendingWaveSpawns:0,CampSearchDiagnostics:{Exhausted:0b,FLUID:200}}'
    common={'status':'passed','entry':'Actual non-op client /siegeoverhaul start; SYNTHETIC',
            'sawNaturalSearch':True,'sawEarthworksSearch':True,'sawRecoveryCooldown':True,'sawWiderRecovery':True,
            'searchTicksWithoutWaveOrAttackers':10000,'cooldownTicksWithoutWaveOrAttackers':1200,
            'passes':{key:{'maxCandidates':200,'maxElapsedTicks':4800} for key in ['natural','earthworks','recovery']},
            'protectedCellCount':6144,'protectedChests':2,'restorationExcludedProtectedCells':True,'wave':1,'totalSpawned':1,
            'recoveryCooldownAtGameTime':9000,'firstRecoverySearchAtGameTime':10220}
    impossible=deepcopy(common);impossible.update(scenario='no-safe-land-reload-fallback',realWorldReload=True,savedSearchAuthorityVerified=True,
            campSearchAbandoned=True,fallbackAtGameTime=15000,firstWaveAtGameTime=18600,establishedAtGameTime=-1,
            savedSearchAuthority=deepcopy(auth),loadedSearchAuthority=deepcopy(auth),reloadedAuthority=snbt,
            savedSearchAuthorityGameTime=9030,loadedSearchAuthorityGameTime=9030)
    recovery=deepcopy(common);recovery.update(scenario='recovery-only-island',realWorldReload=False,savedSearchAuthorityVerified=False,
            campSearchAbandoned=False,fallbackAtGameTime=-1,establishedAtGameTime=10500,firstWaveAtGameTime=12900,
            recoveryIsland={'width':57,'soilDepth':24,'fixtureWrites':57*57*24+4,'originalCandidatesCannotFitCore':True,'completedAtGameTime':8000,'recoveryCooldownAtGameTime':9000},
            nativeCamp={'nativeClaimChunks':25,'claimId':'11111111-1111-4111-8111-111111111111','enemyCore':'100, 80, 200',
                'ordinaryTicksSinceEstablishment':60,'preservedEdgesRescue':True,'warGateReadyAtCrewCheck':False,
                'expectedEnemyFaction':'enemy','nativeJobOwner':'22222222-2222-4222-8222-222222222222',
                'workers':[{'uuid':'33333333-3333-4333-8333-333333333333','type':'workers:builder','owner':'22222222-2222-4222-8222-222222222222','noAi':False,'faction':'enemy'}],
                'guards':[{'uuid':f'44444444-4444-4444-8444-{i:012d}','type':'recruits:recruit','owner':verifier.GUARD_OWNER,'noAi':False,'faction':'enemy'} for i in range(5)]})
    for index,case in enumerate([impossible,recovery]):
        case['campSearchDiagnostics']='{Exhausted:'+('1b' if index==0 else '0b')+',FLUID:200}'
        case['finalState']={'scenario':index,'gameTime':case['firstWaveAtGameTime']+1,'searchRecovery':True,'retryTicks':0,
                'searchStep':200 if index==0 else 1,'searchElapsedTicks':4800 if index==0 else 20,'searchCandidate':'null',
                'terraformFallback':True,'searchAbandoned':index==0,'campPosition':'null' if index==0 else 'BlockPos{x=100, y=80, z=200}',
                'preparationTicks':0 if index==0 else 1180,'preparationTotalTicks':3600,'wave':1,'totalSpawned':1,
                'raiders':1,'pendingWaveSpawns':0,'crewStarted':index==1,'diagnostics':case['campSearchDiagnostics']}
        case['framebuffer']={'file':shots[index],'readyFrames':8,'attempts':1,'changedPixelSamples':51,'elapsedSeconds':1.5}
    data={'SYNTHETIC_NOT_NATIVE_EVIDENCE':True,'mode':'camp-lifecycle','status':'passed',
          'fixtureConfiguration':{'siegePreparationMinutes':3,'waterDepth':24,'scoutingTimersChanged':False,'recoveryTimersChanged':False,'raidFieldsWritten':False},
          'loadedModVersions':{'minecraft':'1.20.1','forge':'47.4.16','workers':'2.0.3','recruits':'1.15.2','smallships':'2.0.0-b1.4','siegeweapons':'0.2.5'},
          'loadedCompanionArtifacts':{key:{'sha256':'a'*64} for key in ['workers','recruits','smallships','siegeweapons']},
          'screenshots':shots,'scenarios':[impossible,recovery]}
    audit={'status':'passed','noVendorJarsUploaded':True,'artifacts':{'SYNTHETIC':{'classes':['SYNTHETIC']*18}}}
    (root/'native-bytecode-audit.json').write_text(json.dumps(audit));(root/'result.json').write_text(json.dumps(data))
    return data


class StrictLifecycleEvidenceTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)/'evidence';self.data=fixture(self.root)

    def verify(self):
        (self.root/'result.json').write_text(json.dumps(self.data));return verifier.verify(self.root)

    def reject(self,mutate):
        saved=deepcopy(self.data);mutate(self.data)
        try:
            with self.assertRaises((AssertionError,KeyError,ValueError,TypeError)):self.verify()
        finally:self.data=saved

    def test_complete_typed_fixture_passes_with_muster_preparation_remaining(self):self.verify()

    def test_final_muster_preparation_boundaries(self):
        for value in [0,1180,1200]:
            self.data['scenarios'][1]['finalState']['preparationTicks']=value;self.verify()
        for value in [-1,1201,3600,True,1.0]:
            self.reject(lambda d:d['scenarios'][1]['finalState'].update(preparationTicks=value))

    def test_missing_duplicate_swapped_and_path_traversing_frame_names_reject(self):
        for value in [self.data['screenshots'][0],'../outside.png','/tmp/outside.png','./02-recovered-native-camp.png','sub/../02-recovered-native-camp.png','02-recovered-native-camp.PNG',None]:
            self.reject(lambda d:d['scenarios'][1]['framebuffer'].update(file=value))
        (self.root/self.data['screenshots'][1]).unlink()
        with self.assertRaises(AssertionError):self.verify()

    def test_symlink_framebuffer_rejects(self):
        path=self.root/self.data['screenshots'][1];outside=self.root.parent/'external.png';outside.write_bytes(path.read_bytes());path.unlink();path.symlink_to(outside)
        with self.assertRaises(AssertionError):self.verify()

    def test_truthy_booleans_reject_in_all_critical_receipts(self):
        paths=[('scenarios',0,'realWorldReload'),('scenarios',0,'savedSearchAuthorityVerified'),('scenarios',0,'sawNaturalSearch'),
               ('scenarios',0,'restorationExcludedProtectedCells'),('scenarios',1,'nativeCamp','preservedEdgesRescue'),
               ('scenarios',1,'nativeCamp','workers',0,'noAi'),('scenarios',1,'finalState','crewStarted'),
               ('scenarios',0,'loadedSearchAuthority','recovery'),('fixtureConfiguration','raidFieldsWritten')]
        for path in paths:
            for value in ['false','true',1,0,None]:
                def mutate(d):
                    node=d
                    for part in path[:-1]:node=node[part]
                    node[path[-1]]=value
                with self.subTest(path=path,value=value):self.reject(mutate)

    def test_nonfinite_json_is_rejected_in_any_field(self):
        for value in [float('inf'),float('-inf'),float('nan')]:
            self.reject(lambda d:d['scenarios'][0].update(cooldownTicksWithoutWaveOrAttackers=value))
            self.reject(lambda d:d.update(unusedField=value))
        raw=json.dumps(self.data).replace('"elapsedSeconds": 1.5','"elapsedSeconds": 1e999')
        (self.root/'result.json').write_text(raw)
        with self.assertRaises(AssertionError):verifier.verify(self.root)

    def test_duplicate_json_keys_rejected(self):
        raw=json.dumps(self.data).replace('"wave": 1','"wave": 0, "wave": 1',1)
        (self.root/'result.json').write_text(raw)
        with self.assertRaises(AssertionError):verifier.verify(self.root)

    def test_integer_counters_reject_booleans_floats_strings(self):
        for key in ['cooldownTicksWithoutWaveOrAttackers','wave','totalSpawned','firstRecoverySearchAtGameTime','recoveryCooldownAtGameTime']:
            for value in [True,False,1200.0,'1200']:
                with self.subTest(key=key,value=value):self.reject(lambda d:d['scenarios'][0].update({key:value}))

    def test_1199_ticks_and_search_before_full_cooldown_reject(self):
        self.reject(lambda d:d['scenarios'][0].update(cooldownTicksWithoutWaveOrAttackers=1199))
        self.reject(lambda d:d['scenarios'][1].update(firstRecoverySearchAtGameTime=10199))
        self.reject(lambda d:d['scenarios'][1].update(cooldownTicksWithoutWaveOrAttackers=1300))

    def test_contradictory_cooldown_island_and_establishment_times_reject(self):
        self.reject(lambda d:d['scenarios'][1]['recoveryIsland'].update(recoveryCooldownAtGameTime=1000000))
        self.reject(lambda d:d['scenarios'][1].update(establishedAtGameTime=9000,firstWaveAtGameTime=9001))
        self.reject(lambda d:d['scenarios'][1].update(establishedAtGameTime=10219))
        self.reject(lambda d:d['scenarios'][1].update(firstWaveAtGameTime=10500))

    def test_saved_loaded_authority_must_exist_match_and_describe_cooldown(self):
        self.reject(lambda d:d['scenarios'][0].pop('loadedSearchAuthority'))
        for key,value in [('retryTicks',0),('retryTicks',1201),('searchCandidate',123),('searchStep',1),('wave',1),('pendingWaveSpawns',1),('preparationTicks',3599),('preparationTotalTicks',3599),('recovery',False)]:
            self.reject(lambda d:d['scenarios'][0]['loadedSearchAuthority'].update({key:value}))
            self.reject(lambda d:[d['scenarios'][0][field].update({key:value}) for field in ['savedSearchAuthority','loadedSearchAuthority']])

    def test_authority_game_times_must_match_and_be_inside_cooldown(self):
        for key,value in [('loadedSearchAuthorityGameTime',9031),('savedSearchAuthorityGameTime',-1),('loadedSearchAuthorityGameTime',float('inf'))]:
            self.reject(lambda d:d['scenarios'][0].update({key:value}))
        self.reject(lambda d:d['scenarios'][0].update(savedSearchAuthorityGameTime=8999,loadedSearchAuthorityGameTime=8999))
        self.reject(lambda d:d['scenarios'][0].update(savedSearchAuthorityGameTime=10220,loadedSearchAuthorityGameTime=10220))

    def test_legacy_snbt_must_reconcile_with_typed_authority(self):
        for value in ['{CampSearchRecovery:0b,CampSearchRetryTicks:0}','{}','not nbt',
                      self.data['scenarios'][0]['reloadedAuthority'].replace('CampSearchRetryTicks:1180','CampSearchRetryTicks:1181'),
                      self.data['scenarios'][0]['reloadedAuthority'].replace('FLUID:200','FLUID:201'),
                      self.data['scenarios'][0]['reloadedAuthority'].replace('Wave:0','Wave:0,Wave:0')]:
            self.reject(lambda d:d['scenarios'][0].update(reloadedAuthority=value))

    def test_diagnostics_have_exact_booleans_bounded_int_counts(self):
        for value in [-1,0,10001,True,200.0,'200']:
            self.reject(lambda d:d['scenarios'][0]['loadedSearchAuthority']['diagnostics']['counts'].update(FLUID=value))
        self.reject(lambda d:d['scenarios'][0]['loadedSearchAuthority']['diagnostics'].update(exhausted='false'))

    def test_recovery_final_state_must_match_camp_wave_and_counters(self):
        self.reject(lambda d:d['scenarios'][1].update(finalState={'campPosition':'null','wave':0,'totalSpawned':0,'crewStarted':False,'preparationTicks':3600}))
        for key,value in [('campPosition','null'),('wave',0),('totalSpawned',2),('crewStarted',False),('searchRecovery',False),('retryTicks',1),('searchAbandoned',True),('gameTime',12000),('preparationTotalTicks',1),('scenario',0)]:
            self.reject(lambda d:d['scenarios'][1]['finalState'].update({key:value}))

    def test_impossible_fallback_has_zero_preparation_and_full_3600_delay(self):
        self.reject(lambda d:d['scenarios'][0].update(firstWaveAtGameTime=18599))
        self.reject(lambda d:d['scenarios'][0]['finalState'].update(preparationTicks=1))
        self.reject(lambda d:d['scenarios'][0].update(fallbackAtGameTime=10219))

    def test_native_identity_claim_owner_role_and_unique_uuid_required(self):
        camp=self.data['scenarios'][1]['nativeCamp']
        for key,value in [('claimId','null'),('enemyCore',True),('nativeJobOwner',verifier.GUARD_OWNER),('preservedEdgesRescue','false')]:
            self.reject(lambda d:d['scenarios'][1]['nativeCamp'].update({key:value}))
        for key,value in [('type','minecraft:pig'),('owner',verifier.GUARD_OWNER),('noAi',True),('faction','wrong')]:
            self.reject(lambda d:d['scenarios'][1]['nativeCamp']['workers'][0].update({key:value}))
        self.reject(lambda d:d['scenarios'][1]['nativeCamp']['guards'][0].update(uuid=camp['workers'][0]['uuid']))
        self.reject(lambda d:d['scenarios'][1]['nativeCamp']['guards'][0].update(owner=camp['nativeJobOwner']))

    def test_companion_hash_requires_exact_lower_hex(self):
        for value in ['x'*64,'A'*64,'a'*63,64]:
            self.reject(lambda d:d['loadedCompanionArtifacts']['recruits'].update(sha256=value))

    def test_audit_boolean_and_complete_classes_are_required(self):
        path=self.root/'native-bytecode-audit.json';data=json.loads(path.read_text());data['noVendorJarsUploaded']='false';path.write_text(json.dumps(data))
        with self.assertRaises(AssertionError):self.verify()


if __name__=='__main__':unittest.main()
