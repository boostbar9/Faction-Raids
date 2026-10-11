"""Fresh strict Case L mutation fixtures. These are not native gameplay evidence."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import uuid

ROOT=Path(__file__).resolve().parents[2]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
V=module('case_l_oracle','scripts/verify-native-later-stage-helper.py')
PART=module('case_l_serializer_fixture','scripts/tests/test_native_helper_partition.py')
OLD=module('case_l_custody_fixture','scripts/tests/test_native_absent_helper_cancel.py')
C,H,O,_,P,G,T=OLD.C,OLD.H,OLD.O,OLD.A,OLD.P,OLD.G,OLD.T
R='00000000-0000-0000-0000-000000000008'
tag_uuid=OLD.tag_uuid

def snbt(values):return '{'+','.join(k+':'+v for k,v in sorted(values.items()))+'}'
def tags(values):return '['+','.join(snbt(v) for v in values)+']'
def long(v):return str(v)+'L'
def longs(values):return '[L;'+','.join(long(v) for v in values)+']'
def q(v):return json.dumps(v,separators=(',',':'))
def block(value):return snbt({'Name':q(value)})


def fixture(detach_immediately=True,a2_resupply=False):
    data=OLD.fixture();pinned=PART.fixture();plan,partition=pinned['plan'],pinned['partition']
    data.update(mode='active-helper-later-stage-disk',sourceCommit='1'*40,sourceTree='2'*40,
        laterStageReturnVerified=True,postDiskHelperPlacementVerified=True,canceledWhileAbsentVerified=False,stageSkippedReturnVerified=True,
        initialSetupAdmissions=[dict(worker=a,dimension='minecraft:overworld',loadedFromDisk=False,syntheticPrecommission=True) for a in (C,H)],compoundTagImplementation=PART.IMPLEMENTATION,serializerContract=PART.serializer_contract(),
        qaSectionTargetCap=64,nativePartition=copy.deepcopy(partition),genuineDiskAbsenceTicks=100,remainingA0AtFinalizedRemoval=28,candidateSha256=V.P.CANDIDATE_SHA256,candidatePartition=json.loads(V.P.CANDIDATE_PATH.read_text()),
        nativePartitionRoundTrips=[dict(ordinal=i,index=c['index'],layoutStageDigest=c['layoutStageDigest'],blueprintBlockCount=c['targetCount'],targetCount=c['targetCount'],nativeRoundTripPassed=True,problem=None) for i,c in enumerate(partition['validatorCalls'])],
        events=[],nativeWork=[],nativeWorkerJoins=[],projectGeneration=1,coreKey='team:native_handoff')
    p=dict(Version='1',Project=tag_uuid(P),Generation='1L',Owner=tag_uuid(O),Builder=tag_uuid(C),CoreKey=q(data['coreKey']),Core=long(V.P.packed(135,65,7)),Faction=q('native_handoff'),Material='0',FeeVersion='1',Price='64',Review=q('f'*64),Territory=longs([8]),State=q('RUNNING'),Revision='2L',Active='0',Blocker=q(''),Receipts='[]',Layout=q(V.P.LAYOUT_SHA256),Min=long(V.P.packed(*plan['min'])),Max=long(V.P.packed(*plan['max'])))
    palette=['minecraft:air',*V.MATERIALS];p['Palette']=tags([dict(Name=q(x)) for x in palette])
    p['Targets']=tags([dict(Pos=long(t['cell']),Target=str(palette.index(t['material'])),Before='0') for t in plan['targets']]);p['Clearance']=tags([dict(Pos=long(c),Before='0') for c in plan['clearance']])
    columns=[]
    for cell in plan['columns']:
        x,_,z=V.P.coordinates(cell);columns.append(dict(Base=long(cell+65),Support='0',Inward=str(min(x-127,144-x,z+1,16-z)),Component='0'))
    p['Columns']=tags(columns)
    text='perimeter-project-v1\n'+'\n'.join([P,'1',O,C,data['coreKey'],str(V.P.packed(135,65,7)),'native_handoff','0','f'*64,'1:64'])+'\nclaim:8\n'
    text+=''.join('column:'+':'.join([r['Base'][:-1],r['Support'],r['Inward'],r['Component']])+'\n' for r in columns)
    for label,values in [('target',{t['cell']:t['material'] for t in plan['targets']}),('before',{t['cell']:'minecraft:air' for t in plan['targets']}),('clear',dict.fromkeys(plan['clearance'],'minecraft:air'))]:text+=''.join(f'{label}:{c}:{m}\n' for c,m in sorted(values.items()))
    text+=V.P.LAYOUT_SHA256;manifest=V.digest(text);data['manifestHash']=manifest;p['Hash']=q(manifest)
    stages=[]
    for i,s in enumerate(partition['stages']):
        content='perimeter-stage-contract-v1\n'+manifest+'\n'+s['layoutStageDigest']+'\n'+''.join(f"{t['cell']}:{t['material']}:minecraft:air\n" for t in s['targets'])+''.join(f'clear:{c}:minecraft:air\n' for c in s['clearance'])
        digest=V.digest(content);area=str(uuid.UUID(bytes=hashlib.md5(f'siege-perimeter-stage-v1:{P}:1:{i}:{digest}'.encode()).digest(),version=3))
        stages.append(dict(Index=str(i),Area=tag_uuid(area),Digest=q(digest),LayoutDigest=q(s['layoutStageDigest']),Columns=longs(s['columns']),Min=long(V.P.packed(*s['min'])),Max=long(V.P.packed(*s['max'])),Origin=long(V.P.packed(s['max'][0],s['min'][1],s['min'][2]))))
    p['Stages']=tags(stages);p['Payment']=snbt({k:p[k] for k in ('Project','Generation','Hash','FeeVersion','Price')}|dict(Debited='64',Creative='0b'))
    data['acceptedProject']=snbt(p);areas=[V.nbt_uuid(s['Area']) for s in stages];data['firstStageArea']=areas[0]
    data['firstStageTargets']=[t['cell'] for t in partition['stages'][0]['targets']];data['firstStageMaterials']={str(t['cell']):t['material'] for t in partition['stages'][0]['targets']}
    hand=snbt(dict(Version='1',Receipt=tag_uuid(R),Builder=tag_uuid(H),Owner=tag_uuid(O),Area=stages[0]['Area'],Generation=tag_uuid(G)))
    data.update(originalHelperHandReceipt=hand,originalHelperHandReceiptId=R)
    sequence=0;counts={C:0,H:0};stock={C:[0,0],H:[0,0]};chest=[352,220];seen=set();active=0;verified=0;retired=-1;member_state='active';hand_stage=0;rebinds=0;revision=2;dirty_writes=0
    def receipts():return [{k:p[k] for k in ('Project','Generation','Hash')}|{k:stages[i][k] for k in ('Index','Area','Digest')}|{'Targets':str(V.COUNTS[i])} for i in range(verified)]
    def project(state='RUNNING'):return snbt(p|dict(State=q(state),Revision=long(revision),Active=str(active),Receipts=tags(receipts())))
    def persistent(actor,stage=None,pending=False,detached=False,terminal=False,bindings=None):
        i=active if stage is None else stage
        v={'SiegeProtectedHandLifecycle':hand if actor==H else snbt(dict(Version='1',Receipt=tag_uuid(T),Builder=tag_uuid(C),Owner=tag_uuid(O),Area=stages[0]['Area'],Generation=tag_uuid(G)))}
        n=rebinds if bindings is None else bindings
        if actor==H and n:v[V.C.REBINDS]=str(n)
        if actor==H and dirty_writes:v['SiegeProtectedStorageDirtyWrites']=str(dirty_writes)
        if not terminal:
            v['SiegeWholePerimeter']=snbt(dict(Version='1',Project=tag_uuid(P),Generation='1L',Hash=q(manifest),Core=q(data['coreKey']),LedgerGeneration=tag_uuid(G)))
            if not detached:v.update(SiegeProtectedAreaReceipt=stages[i]['Area'],SiegeProtectedLedgerGeneration=tag_uuid(G),SiegePlayerFortificationAreaId=stages[i]['Area'],SiegePlayerFortificationOwner=tag_uuid(O),SiegePlayerFortificationPos=long(123))
        if pending:v[V.C.HAND]='1b'
        return snbt(v)
    def inv(actor,stage=None,pending=False,detached=False,terminal=False,bindings=None):
        value=OLD.inv();value['persistent']=persistent(actor,stage,pending,detached,terminal,bindings)
        for index,count in enumerate(stock[actor]):
            value['slots'][5 if index==0 else 0]='{}' if not count else snbt(dict(Count=str(count)+'b',id=q(V.MATERIALS[index])))
        value['main']=value['slots'][5];return value
    def ledger(terminal=False):
        grants=[0,2] if hand_stage==2 else [0]
        m=dict(Worker=tag_uuid(H),Active='1b' if member_state=='active' else '0b',Ready='1b',Withdrawn='0b',AdmittedStage='0',RetiredStage='2' if terminal else '-1',Suspended='1b' if member_state=='suspended' else '0b',RejoinStage='-1',GrantedStages=longs([sum(1<<v for v in grants),0,0,0]),LegacyEnd='-1',Resolved='1b' if terminal else '0b')
        crew=snbt(dict(Version='3',LedgerGeneration=tag_uuid(G),Projects=tags([{k:p[k] for k in ('Project','Generation','Hash')}|dict(Original=tag_uuid(C),Members=tags([m]))])))
        s=partition['stages'][active]
        leases=[] if terminal else [dict(Id=tag_uuid(P),Generation='1L',Hash=q(manifest),Stages=tags([dict(Id=v['Area']) for v in stages]),Active=str(active),Retired='1b' if active<=retired else '0b',Cells=longs(sorted([t['cell'] for t in s['targets']]+s['clearance'])))]
        sites=[] if terminal else [dict(Id=tag_uuid(P),BuilderDestroyed='0b',ReservationVersion='1',Cells=longs(sorted([t['cell'] for t in plan['targets']]+plan['clearance'])),Edited=longs([]))]
        return snbt(dict(Sites=tags(sites),Retired='[]',Generation=tag_uuid(G),Invalid='0b',ProjectLeases=snbt(dict(Version='1',Projects=tags(leases))),HandLifecycles=tags([V.compound(hand)]),BuilderAssignments=snbt(dict(Version='2',Projects='[]')),BuilderCrew=crew))
    def journal(terminal=False):
        attempts=[dict(Index=str(i),Area=stages[i]['Area'],Marker='123L',State=q('RETIRED' if i<=retired else 'LIVE')) for i in range(active+1)]
        entries=[] if terminal else [dict(Id=tag_uuid(P),Generation='1L',Hash=q(manifest),Attempts=tags(attempts))]
        return snbt(dict(Version='1',Entries=tags(entries)))
    def terminal():
        r={k:p[k] for k in ('Project','Generation','Hash','Owner','Builder','CoreKey','Faction','FeeVersion','Price','Payment')}
        r.update(Version='1',Revision='9L',State=q('CANCELED'),Active='2',Verified='2',ClaimChunks='1',LedgerGeneration=tag_uuid(G),ReservationRetired=q('b'*64),BuilderDetached=q('c'*64),StagesCleaned=q('d'*64),Stages=tags([{k:s[k] for k in ('Index','Area','Digest')}|dict(Targets=str(V.COUNTS[i])) for i,s in enumerate(stages)]))
        r['ReceiptHash']=q(V.C.terminal_seal(r));return snbt(r)
    def census():
        values={t['cell']:t['material'] for t in plan['targets']}
        return {name:dict(built=sum(values[c]==V.MATERIALS[i] for c in seen),chest=chest[i],workerCustody=sum(stock[a][i] for a in (C,H)),owner=0,loose=0,total=[352,220][i]) for i,name in enumerate(('cobble','oak'))}
    def event(kind,tick,**extra):
        nonlocal sequence
        sequence+=1;value=dict(sequence=sequence,kind=kind,gameTime=tick);value.update(extra);data['events'].append(value);return value
    def boundary(kind,tick,terminal_mode=False,state='RUNNING',**extra):
        ps=None if terminal_mode else project(state);j=journal(terminal_mode);term=terminal() if terminal_mode else None
        core=snbt(dict(PerimeterProjects=snbt(dict(Version='2',Entries='[]' if terminal_mode else '['+ps+']',Terminals='['+term+']' if terminal_mode else '[]')),PerimeterStageRuntime=j))
        offline=kind in V.BOUNDARIES[3:9];holding=kind in ('l-outbound-settled','l-finalized-disk-removal','l-holding-disk-join','l-holding-cleanup-ready','l-before-native-return')
        workers={actor:dict(inventory=inv(actor,0 if actor==H and hand_stage==0 else active,detached=actor==H and member_state=='suspended' and (kind in ('l-holding-cleanup-ready','l-before-native-return','l-return-settled') or kind=='l-holding-disk-join' and detach_immediately),terminal=terminal_mode),dimension='minecraft:the_nether' if actor==H and (holding or offline) else 'minecraft:overworld',removed=actor==H and offline,handPending=False,mainAliasesSlot5=True,nativeState='IDLE',entityTick=tick,handRebindCount=rebinds if actor==H else 0) for actor in (C,H)}
        for actor,w in workers.items():
            persistent_data=V.compound(w['inventory']['persistent'])
            w.update(owner=O,currentArea=(V.nbt_uuid(persistent_data['SiegePlayerFortificationAreaId']) if 'SiegePlayerFortificationAreaId' in persistent_data and w['dimension']=='minecraft:overworld' else None),beforeItemSlot=w['inventory']['beforeItemSlot'],neededItemCount=0,position=[137.5,65.0,9.5])
        value=event(kind,tick,projectSnapshot=ps,coreSnapshot=core,ledgerSnapshot=ledger(terminal_mode),journalSnapshot=j,receipts=[snbt(r) for r in receipts()],stock={a:list(v) for a,v in stock.items()}|{'chest':list(chest)},targetStates={str(t['cell']):'Block{'+(t['material'] if t['cell'] in seen else 'minecraft:air')+'}' for t in plan['targets']},workers=workers,placementCounts=dict(counts),pendingTargets=572-len(seen),materials=census(),ownerMode='non-op Survival',claims=[],**extra)
        if terminal_mode:value.update(terminalReceipt=term,terminalMarkersAbsent=True,terminalReservationsAbsent=True)
        return value
    def marker():
        s=partition['stages'][active];lo,hi=s['min'],s['max']
        recipe=dict(Origin=stages[active]['Origin'],Facing=q('south'),Width=str(hi[0]-lo[0]+1),Height=str(hi[1]-lo[1]+1),Depth=str(hi[2]-lo[2]+1),Owner=tag_uuid(O),Builder=tag_uuid(C),CoreKey=p['CoreKey'],CorePos=p['Core'])
        blocks=[]
        for t in s['targets']:
            x,y,z=V.P.coordinates(t['cell']);blocks.append(dict(x=str(x-lo[0]),y=str(y-lo[1]),z=str(z-lo[2]),state=block(t['material'])))
        recipe['Structure']=snbt(dict(width=recipe['Width'],facing=q('south'),blocks=tags(blocks)))
        recipe['Before']=tags([dict(Pos=long(t['cell']),State=block('minecraft:air')) for t in s['targets']])
        recipe['Reservation']=snbt(dict(Version='1',Cells=longs(sorted([t['cell'] for t in s['targets']]+s['clearance'])),Clearance=tags([dict(Pos=long(c),State=block('minecraft:air')) for c in s['clearance']])))
        recipe['Completed']=longs(sorted(seen&{t['cell'] for t in s['targets']}));recipe['Cleared']=longs([])
        scope=dict(Version='1',Project=p['Project'],Generation=p['Generation'],Hash=p['Hash'],Core=p['CoreKey'],Stage=str(active),StageHash=stages[active]['Digest'])
        return snbt(dict(SiegeConstructionCommissionPaid='1b',SiegePerimeterProjectRequired='1b',SiegePerimeterProject=snbt(scope),SiegeProtectedConstructionV1=snbt(recipe)))
    def work(actor,tick,cells=None,supply=False):
        nonlocal sequence,dirty_writes
        before=list(stock[actor])+list(chest);authority=dict(workerInventory=inv(actor),ledgerSnapshot=ledger(),projectSnapshot=project(),currentArea=areas[active],owner=O);sequence+=1
        if supply:
            for i,amount in enumerate([64,64] if supply is True else supply):stock[actor][i]+=amount;chest[i]-=amount
            if actor==H and active==2:
                raw=V.compound(authority['workerInventory']['persistent']);raw['SiegeProtectedInventoryCleanup']='{Version:1,NEEDED:1}'
                authority['workerInventory']['persistent']=snbt(raw);dirty_writes+=1
            cells=[]
        else:
            for c in cells:
                material=next(t['material'] for t in partition['stages'][active]['targets'] if t['cell']==c);stock[actor][V.MATERIALS.index(material)]-=1;seen.add(c);counts[actor]+=1
        cb=0 if supply else before[0]-stock[actor][0];oak=0 if supply else before[1]-stock[actor][1]
        data['nativeWork'].append(dict(sequence=sequence,worker=actor,gameTime=tick,startGameTime=tick,endGameTime=tick,startDimension='minecraft:overworld',endDimension='minecraft:overworld',area=areas[active],markerUuid=areas[active],stage=active,stageDigest=V.string(stages[active]['Digest']),projectSnapshot=authority['projectSnapshot'],authority=authority,markerReceipt=marker(),kind='native-supply' if supply else 'native-placement',cells=cells,before=before,after=list(stock[actor])+list(chest),placedCobble=cb,placedOak=oak,authorized=True,nativeState='TAKE_NEEDED_ITEMS' if supply else 'PLACE_BLOCKS'))
    def join(disk,dimension,tick,detached=False):
        nonlocal sequence,rebinds
        raw=inv(H,0,detached=detached);sequence+=1;data['nativeWorkerJoins'].append(dict(sequence=sequence,gameTime=tick,worker=H,loadedFromDisk=disk,dimension=dimension,inventory=raw))
        armed=copy.deepcopy(raw);armed['persistent']=snbt(V.compound(raw['persistent'])|{V.C.HAND:'1b'})
        event('load-mirror-armed',tick,worker=H,raw=raw,armed=armed,split=True,thirdSlotAlias=False,slot5Count=stock[H][0],slot5Maximum=64)
        old=rebinds;rebinds+=1
        restored=inv(H,0,detached=detached or disk and detach_immediately)
        event('load-mirror-restored',tick,worker=H,before=armed,after=restored,split=True,mainAliasesSlot5=True,rebindsBefore=old,rebindsAfter=rebinds)
    boundary('l-commission',0);work(C,1,supply=True);work(H,1,supply=True)
    a0=[t['cell'] for t in partition['stages'][0]['targets']];work(C,2,[a0[0]]);work(H,2,[a0[1]])
    boundary('l-before-outbound',3);join(False,'minecraft:the_nether',3)
    event('ordinary-end-settled-custody',4,worker=H,phase='L_SETTLE',dimension='minecraft:the_nether',transferGameTime=3,nanoTime=110)
    event('l-outbound-ticket-released',4,phase='L_SETTLE',transferGameTime=3,settledGameTime=4,nanoTime=150)
    boundary('l-outbound-settled',4);member_state='suspended'
    boundary('l-finalized-disk-removal',50,remainingA0Targets=28,nanoTime=180)
    data['finalizedOfflineInventory']=inv(H,0)
    work(C,80,a0[2:]);verified=1;revision=3;boundary('l-stage-0-verified',80,state='STAGE_VERIFIED')
    retired=0;active=1;revision=4;boundary('l-stage-0-retired',80,markerPresent=False,leasePresent=False)
    work(C,140,[t['cell'] for t in partition['stages'][1]['targets']]);verified=2;revision=5;boundary('l-stage-1-verified',140,state='STAGE_VERIFIED')
    retired=1;active=2;revision=6;boundary('l-stage-1-retired',140,markerPresent=False,leasePresent=False)
    boundary('l-a2-ready-helper-offline',150,nanoTime=240);join(True,'minecraft:the_nether',151);boundary('l-holding-disk-join',151)
    boundary('l-holding-cleanup-ready',156);boundary('l-before-native-return',157);join(False,'minecraft:overworld',157,detached=True)
    event('ordinary-end-settled-custody',158,worker=H,phase='L_RETURN_SETTLE',dimension='minecraft:overworld',transferGameTime=157,nanoTime=310)
    event('l-return-ticket-released',158,phase='L_RETURN_SETTLE',transferGameTime=157,settledGameTime=158,nanoTime=350)
    boundary('l-return-settled',158);member_state='active';hand_stage=2;boundary('l-helper-a2-active',160)
    if a2_resupply:work(H,160,supply=[int(n<64) for n in stock[H]])
    work(H,161,[partition['stages'][2]['targets'][0]['cell']]);boundary('l-helper-a2-placement',161)
    revision=7;b=boundary('l-authoritative-canceled',162,state='CANCELED',authoritySource='live-project');b['authority']=b['projectSnapshot'];member_state='resolved';retired=2
    boundary('l-compacted-loaded',162,terminal_mode=True)
    for tick in range(163,263):
        for actor in (C,H):event('terminal-native-bracket',tick,worker=actor,authorized=False,changedTargets=0,targetStatesUnchanged=True,before=stock[actor]+chest,after=stock[actor]+chest)
    boundary('l-final-stable',262,terminal_mode=True)
    data.update(placementCounts=counts,retainedPlacedBlocks=len(seen),finalMaterials=census(),terminalObservedBrackets=200,canceledAuthorityGameTime=162,cancellationStableTicks=100,
        evidenceSeal=dict(sequence=sequence+1,gameTime=262,serverEnd=True),offlineRemovalProof=dict(reason='UNLOADED_TO_CHUNK',shouldDestroy=False,shouldSave=True,chunkAvailable=False,registered=False,allDimensionsAbsent=True,gameTime=50,unavailableAt=49))
    # Utility receipts remain from the unchanged two-cycle custody fixture; bind every
    # actual raw/high/armed/settled inventory to these freshly generated admissions.
    starts=[e for e in data['events'] if e['kind']=='load-mirror-armed']
    admission=0
    for e in data['activeHelperTransitEvents']:
        if e['kind']=='transfer-before':admission=0 if e['dimension']=='minecraft:overworld' else 2;e['inventory']=copy.deepcopy(data['nativeWorkerJoins'][admission]['inventory'])
        elif e['kind']=='join-high':
            admission=1 if e['loadedFromDisk'] else (0 if e['dimension']=='minecraft:the_nether' else 2);e['inventory']=copy.deepcopy(data['nativeWorkerJoins'][admission]['inventory'])
        elif e['kind'] in ('join-low','transfer-settled'):e['inventory']=copy.deepcopy(starts[admission]['armed'])
    for b in data['events']:
        if b['kind'] not in ('l-holding-disk-join','l-holding-cleanup-ready','l-before-native-return'):continue
        detached=b['kind']!='l-holding-disk-join' or detach_immediately
        identity=dict(receipt=R,builder=H,owner=O,area=areas[0],generation=G)
        b['helperProof']=dict(state='DETACHED_SUSPENDED' if detached else 'STALE_A0_SUSPENDED',helper=H,owner=O,project=P,generation=1,manifestHash=manifest,ledgerGeneration=G,core=data['coreKey'],coordinator=C,originalHandReceipt=R,handArea=areas[0],admissionArea=areas[0],unresolvedProject=P,
            current=dict(state='RUNNING',activeStage=2,verifiedStages=2),cleanupAreas=[areas[0]],originalHandExact=True,frozenPersistentExact=True,pastRetired=True,a2Lease=True,destructionAbsent=True,currentStageGranted=False,
            originalHand=identity,actorHand=identity,ledgerHand=identity,frozenHand=identity,pendingHand=False,rebinds=2,
            member=dict(admittedStage=0,active=False,ready=True,withdrawn=False,retiredStage=-1,suspended=True,rejoinStage=-1,grantedStages=[0],legacyEnd=-1,resolved=False,destroyed=False),
            receipts=[dict(project=P,generation=1,manifestHash=manifest,stageIndex=i,areaId=areas[i],stageDigest=V.string(stages[i]['Digest']),verifiedTargets=V.COUNTS[i]) for i in (0,1)],
            association=dict(wholeSelector=True,protectedChild=not detached,protectedGeneration=not detached,nativeArea=not detached,nativeOwner=not detached,currentArea=''))
    phases=[dict(sequence=0.5,kind='phase',gameTime=0,phase='COMMISSION')]
    for kind,name in [('l-commission','BOTH_WORK'),('l-before-outbound','L_OUT'),('l-before-outbound','L_SETTLE'),('l-outbound-settled','L_EVICT'),('l-finalized-disk-removal','L_OFFLINE_A0'),('l-stage-0-retired','L_OFFLINE_A1'),('l-stage-1-retired','L_WAIT_A2'),('l-a2-ready-helper-offline','L_LOAD'),('l-holding-disk-join','L_HOLDING'),('l-holding-cleanup-ready','L_RETURN'),('l-before-native-return','L_RETURN_SETTLE'),('l-return-settled','L_REJOIN'),('l-helper-a2-placement','L_CANCEL_MENU'),('l-helper-a2-placement','L_CANCEL_WAIT'),('l-compacted-loaded','L_FINAL_STABLE'),('l-final-stable','FINISHED')]:
        b=next(e for e in data['events'] if e['kind']==kind);phases.append(dict(sequence=b['sequence']+.01*len(phases),kind='phase',gameTime=b['gameTime'],phase=name))
    data['events'].extend(phases)
    for i,e in enumerate(sorted(data['events']+data['nativeWork']+data['nativeWorkerJoins'],key=lambda e:e['sequence']),1):e['sequence']=i
    data['events'].sort(key=lambda e:e['sequence']);data['evidenceSeal']['sequence']=max(e['sequence'] for e in data['events'])+1
    return data


def boundary(data,kind):return next(e for e in data['events'] if e['kind']==kind)
def mutate_nbt(data,kind,field,old,new):
    value=boundary(data,kind);assert old in value[field];value[field]=value[field].replace(old,new,1)


class LaterStageOracleTests(unittest.TestCase):
    def reject(self,mutate):
        value=fixture();mutate(value)
        with self.assertRaises((ValueError,KeyError,TypeError,IndexError)):V.verify_report(value)
    def test_exact_fixture_and_both_natural_holding_cleanup_timings(self):
        for detached in (True,False):self.assertTrue(V.verify_report(fixture(detached)))
    def test_group01_candidate_bytes_full_maps_proposals_and_codec_fail_closed(self):
        changes=[lambda d:d.__setitem__('candidateSha256','0'*64),lambda d:d['candidatePartition'].__setitem__('sourceCommit','0'*40),
            lambda d:d['candidatePartition']['candidate']['stages'][0]['targets'][0].__setitem__('material','minecraft:dirt'),
            lambda d:d['candidatePartition']['candidate']['stages'][0]['clearance'].pop(),lambda d:d['candidatePartition']['candidate'].__setitem__('cap',65),
            lambda d:d['nativePartitionRoundTrips'].reverse(),lambda d:d['nativePartitionRoundTrips'].pop(),lambda d:d['nativePartitionRoundTrips'][0].__setitem__('nativeRoundTripPassed',False)]
        for i,change in enumerate(changes):
            with self.subTest(case=i):self.reject(change)
    def test_group02_mode_source_and_original_actor_identity(self):
        for change in [lambda d:d.__setitem__('mode','active-helper-same-stage-disk'),lambda d:d.__setitem__('mode','native-helper-partition-preflight'),lambda d:d['workers'].append(R),lambda d:d['workers'].reverse(),lambda d:d.__setitem__('sourceTree','unknown')]:
            with self.subTest(change=change):self.reject(change)
    def test_group03_both_original_supplies_placements_and_no_fabricated_cells(self):
        for change in [lambda d:d['nativeWork'].pop(1),lambda d:d['nativeWork'].pop(3),lambda d:d['nativeWork'][3]['cells'].__setitem__(0,d['nativeWork'][2]['cells'][0]),lambda d:boundary(d,'l-finalized-disk-removal')['targetStates'].__setitem__(str(d['firstStageTargets'][2]),'Block{minecraft:cobblestone}')]:
            with self.subTest(change=change):self.reject(change)
    def test_group04_later_end_and_exact_ticket_release(self):
        for change in [lambda d:next(e for e in d['events'] if e['kind']=='ordinary-end-settled-custody').__setitem__('gameTime',3),lambda d:d['activeHelperTicketEvents'][3].__setitem__('nanoTime',105),lambda d:d['activeHelperTicketEvents'][4].__setitem__('nativeReturn',False),lambda d:d['activeHelperTicketEvents'].pop(5),lambda d:d['activeHelperTicketEvents'][2].__setitem__('chunkX',1)]:
            with self.subTest(change=change):self.reject(change)
    def test_group05_actual_disk_removal_and_exact_offline_custody(self):
        for key,value in [('reason','CHANGED_DIMENSION'),('reason','KILLED'),('shouldDestroy',True),('shouldSave',False),('registered',True),('allDimensionsAbsent',False),('unavailableAt',-300)]:
            with self.subTest(key=key,value=value):self.reject(lambda d,k=key,v=value:d['offlineRemovalProof'].__setitem__(k,v))
        self.reject(lambda d:d['nativeWorkerJoins'][1]['inventory'].__setitem__('off','{Count:1b,id:"minecraft:cobblestone"}'))
    def test_group06_finalized_empty_receipt_cut_natural_receipts_and_absence(self):
        for change in [lambda d:boundary(d,'l-finalized-disk-removal').__setitem__('projectSnapshot',boundary(d,'l-stage-0-verified')['projectSnapshot']),lambda d:boundary(d,'l-stage-1-verified').__setitem__('receipts',list(reversed(boundary(d,'l-stage-1-verified')['receipts']))),lambda d:boundary(d,'l-finalized-disk-removal').__setitem__('remainingA0Targets',27),lambda d:boundary(d,'l-a2-ready-helper-offline').__setitem__('gameTime',149),lambda d:d['nativeWorkerJoins'][1].__setitem__('sequence',boundary(d,'l-a2-ready-helper-offline')['sequence']-1),lambda d:d['nativeWork'][4].__setitem__('worker',H)]:
            with self.subTest(change=change):self.reject(change)
    def test_group07_fixed_A2_return_and_unchanged_clocks(self):
        for change in [lambda d:d.__setitem__('phaseLimitTicks',1201),lambda d:d.__setitem__('totalLimitSeconds',601),lambda d:d.__setitem__('elapsedSeconds',float('nan')),lambda d:next(e for e in d['events'] if e.get('phase')=='L_REJOIN').__setitem__('phase','L_LOAD'),lambda d:boundary(d,'l-a2-ready-helper-offline').__setitem__('projectSnapshot',boundary(d,'l-stage-0-retired')['projectSnapshot']),lambda d:d['nativeWork'][-1].__setitem__('stage',3),lambda d:boundary(d,'l-a2-ready-helper-offline').__setitem__('nanoTime',251)]:
            with self.subTest(change=change):self.reject(change)
    def test_group08_explicit_helper_grants_and_no_replacement(self):
        for before,after in [('[L;5L,0L,0L,0L]','[L;7L,0L,0L,0L]'),('[L;5L,0L,0L,0L]','[L;4L,0L,0L,0L]'),('LegacyEnd:-1','LegacyEnd:2'),('Resolved:0b','Resolved:1b'),('Original:'+tag_uuid(C),'Original:'+tag_uuid(H))]:
            with self.subTest(before=before,after=after):self.reject(lambda d,a=before,b=after:mutate_nbt(d,'l-helper-a2-active','ledgerSnapshot',a,b))
        self.reject(lambda d:d['nativeWorkerJoins'].append(copy.deepcopy(d['nativeWorkerJoins'][0])))
    def test_group09_historical_hand_proof_is_not_live_authority(self):
        for change in [lambda d:boundary(d,'l-holding-cleanup-ready')['helperProof'].__setitem__('state','ACTIVE_A2'),lambda d:boundary(d,'l-before-native-return')['helperProof'].__setitem__('pendingHand',True),lambda d:boundary(d,'l-holding-disk-join')['helperProof'].__setitem__('currentStageGranted',True),lambda d:d['nativeWork'][-1]['authority'].__setitem__('workerInventory',copy.deepcopy(boundary(d,'l-holding-cleanup-ready')['workers'][H]['inventory']))]:
            with self.subTest(change=change):self.reject(change)
    def test_group10_immutable_hand_and_all_association_channels(self):
        def hand_area(d):
            inv=d['nativeWork'][-1]['authority']['workerInventory'];inv['persistent']=inv['persistent'].replace(tag_uuid(d['firstStageArea']),tag_uuid(d['nativeWork'][-1]['area']),1)
        def unrelated(d):
            w=boundary(d,'l-holding-cleanup-ready')['workers'][H];p=V.compound(w['inventory']['persistent']);p['ForeignMutation']='1';w['inventory']['persistent']=snbt(p)
        for change in [hand_area,unrelated,lambda d:d['nativeWork'][-1]['authority'].__setitem__('currentArea',d['firstStageArea']),lambda d:boundary(d,'l-holding-cleanup-ready')['helperProof']['ledgerHand'].__setitem__('receipt',T)]:
            with self.subTest(change=change):self.reject(change)
    def test_group11_final_activation_and_failed_rejoin_prefixes_do_not_work(self):
        for before,after in [('Active:1b','Active:0b'),('RejoinStage:-1','RejoinStage:2'),('Ready:1b','Ready:0b'),('Suspended:0b','Suspended:1b')]:
            with self.subTest(before=before):self.reject(lambda d,a=before,b=after:d['nativeWork'][-1]['authority'].__setitem__('ledgerSnapshot',d['nativeWork'][-1]['authority']['ledgerSnapshot'].replace(a,b,1)))
        self.reject(lambda d:d['treasuryAccounting'].__setitem__('commissionDebit',128))
    def test_group12_observed_exact_mirror_identity_and_count_boundaries(self):
        def arm(d):return next(e for e in d['events'] if e['kind']=='load-mirror-armed')
        for change in [lambda d:arm(d).__setitem__('thirdSlotAlias',True),lambda d:arm(d).__setitem__('slot5Count',65),lambda d:arm(d).__setitem__('slot5Maximum',1),lambda d:arm(d)['raw'].__setitem__('main','{}'),lambda d:next(e for e in d['events'] if e['kind']=='load-mirror-restored').__setitem__('rebindsAfter',0),lambda d:d['nativeWorkerJoins'].pop(1)]:
            with self.subTest(change=change):self.reject(change)
    def test_group13_postcancel_observation_and_per_actor_custody(self):
        def redistribute(d):
            stock=boundary(d,'l-final-stable')['stock'];stock[C][0]+=1;stock[H][0]-=1
        for change in [redistribute,lambda d:next(e for e in d['events'] if e['kind']=='terminal-native-bracket').__setitem__('authorized',True),lambda d:next(e for e in d['events'] if e['kind']=='terminal-native-bracket')['after'].__setitem__(0,99),lambda d:d['events'].remove(next(e for e in d['events'] if e['kind']=='terminal-native-bracket')),lambda d:mutate_nbt(d,'l-final-stable','terminalReceipt','Verified:2','Verified:3'),lambda d:boundary(d,'l-final-stable').__setitem__('terminalMarkersAbsent',False)]:
            with self.subTest(change=change):self.reject(change)
    def test_group14_raw_proofs_boundaries_cross_tick_and_server_end_seal(self):
        for change in [lambda d:d['events'].clear(),lambda d:d['nativeWork'][-1].__setitem__('endGameTime',162),lambda d:d['nativeWork'][-1].__setitem__('endDimension','minecraft:the_nether'),lambda d:d.__setitem__('finalOwnedTicketCount',1),lambda d:d['evidenceSeal'].__setitem__('serverEnd',False),lambda d:d['evidenceSeal'].__setitem__('sequence',d['evidenceSeal']['sequence']-1),lambda d:boundary(d,'l-stage-1-retired').__setitem__('journalSnapshot','{Version:1,Entries:[]}')]:
            with self.subTest(change=change):self.reject(change)
    def test_real_pre_admission_commission_and_coordinator_work_are_allowed(self):
        d=fixture()
        def empty(raw):
            ledger=V.compound(raw);ledger['HandLifecycles']='[]';crew=V.compound(ledger['BuilderCrew']);crew['Projects']='[]';ledger['BuilderCrew']=snbt(crew);return snbt(ledger)
        b=boundary(d,'l-commission');b['ledgerSnapshot']=empty(b['ledgerSnapshot']);b['workers'][H]['inventory']['persistent']='{}';b['workers'][H]['currentArea']=None
        d['nativeWork'][0]['authority']['ledgerSnapshot']=empty(d['nativeWork'][0]['authority']['ledgerSnapshot'])
        self.assertTrue(V.verify_report(d))
    def test_first_authoritative_cancel_may_already_be_compacted(self):
        d=fixture();c=boundary(d,'l-authoritative-canceled');terminal=boundary(d,'l-compacted-loaded')
        for key in ('coreSnapshot','ledgerSnapshot','journalSnapshot','projectSnapshot','terminalReceipt','terminalMarkersAbsent','terminalReservationsAbsent','workers'):c[key]=copy.deepcopy(terminal[key])
        c['authoritySource']='compact-terminal';c['authority']=c['terminalReceipt'];self.assertTrue(V.verify_report(d))
    def test_terminal_seal_and_paid_contract_are_independently_reconstructed(self):
        for change in [lambda d:d.__setitem__('manifestHash','b'*64),lambda d:d.__setitem__('acceptedProject',d['acceptedProject'].replace('Debited:64','Debited:63')),lambda d:mutate_nbt(d,'l-final-stable','terminalReceipt','ReceiptHash:"','ReceiptHash:"0'),lambda d:d['nativeWork'][-1].__setitem__('markerReceipt',d['nativeWork'][-1]['markerReceipt'].replace('SiegeConstructionCommissionPaid:1b','SiegeConstructionCommissionPaid:0b'))]:
            with self.subTest(change=change):self.reject(change)


class RawEvidenceBindingTests(unittest.TestCase):
    """Regression specimens from independent v1 review, plus coherent custody mutations."""
    reject=LaterStageOracleTests.reject
    @staticmethod
    def persistent(d,kind,actor,change):
        inv=boundary(d,kind)['workers'][actor]['inventory'];raw=V.compound(inv['persistent']);change(raw);inv['persistent']=snbt(raw)
    def test_detached_whole_identity_is_raw_bound(self):
        self.reject(lambda d:self.persistent(d,'l-holding-cleanup-ready',H,lambda p:p.__setitem__('SiegeWholePerimeter','{}')))
        for key,value in [('Project',tag_uuid(T)),('Generation','2L'),('Hash',q('0'*64)),('Core',q('team:foreign')),('LedgerGeneration',tag_uuid(T)),('Version','2')]:
            def mutate(d,k=key,v=value):
                def raw(p):
                    selector=V.compound(p['SiegeWholePerimeter']);selector[k]=v;p['SiegeWholePerimeter']=snbt(selector)
                self.persistent(d,'l-holding-cleanup-ready',H,raw)
            with self.subTest(field=key):self.reject(mutate)
    def test_holding_raw_owner_current_area_pending_and_stale_channels(self):
        for change in [lambda d:self.persistent(d,'l-before-native-return',H,lambda p:p.__setitem__(V.C.HAND,'1b')),
            lambda d:self.persistent(d,'l-before-native-return',H,lambda p:p.__setitem__('SiegePlayerFortificationPos','999L')),
            lambda d:boundary(d,'l-before-native-return')['workers'][H].__setitem__('currentArea',T),
            lambda d:boundary(d,'l-before-native-return')['workers'][H].__setitem__('owner',T),
            lambda d:boundary(d,'l-before-native-return')['workers'][H].__setitem__('handPending',True)]:
            with self.subTest(change=change):self.reject(change)
    def test_closed_hands_requests_and_raw_rebind_counters(self):
        for key,value in [('beforeItemSlot',0),('neededItemCount',1),('mainAliasesSlot5',False),('handRebindCount',99),('removed',True)]:
            with self.subTest(field=key):self.reject(lambda d,k=key,v=value:boundary(d,'l-before-native-return')['workers'][H].__setitem__(k,v))
        for change in [lambda d:boundary(d,'l-before-native-return')['workers'][H]['inventory'].__setitem__('beforeItemSlot',0),
            lambda d:boundary(d,'l-before-native-return')['helperProof'].__setitem__('rebinds',99),
            lambda d:self.persistent(d,'l-before-native-return',H,lambda p:p.__setitem__(V.C.REBINDS,'99')),
            lambda d:self.persistent(d,'l-before-native-return',H,lambda p:p.__setitem__('SiegeProtectedHandReviewRequired','1b')),
            lambda d:self.persistent(d,'l-before-native-return',H,lambda p:p.__setitem__('SiegeProtectedInventoryCleanup','{}'))]:
            with self.subTest(change=change):self.reject(change)
    def test_raw_prework_stock_and_marker_position(self):
        self.reject(lambda d:d['nativeWork'][-1]['authority']['workerInventory'].update(slots=['{}']*6,main='{}',off='{}'))
        self.reject(lambda d:d['nativeWork'][-1]['authority']['workerInventory'].__setitem__('persistent',d['nativeWork'][-1]['authority']['workerInventory']['persistent'].replace('SiegePlayerFortificationPos:123L','SiegePlayerFortificationPos:999L')))
        self.reject(lambda d:d['nativeWork'][-1]['authority']['workerInventory'].__setitem__('main','{Count:65b,id:"minecraft:cobblestone"}'))
    def test_active_boundary_raw_authority_and_native_route(self):
        for kind,actor,key,value in [('l-helper-a2-active',H,'currentArea',None),('l-helper-a2-placement',H,'currentArea',T),('l-stage-1-verified',H,'removed',False),('l-a2-ready-helper-offline',H,'dimension','minecraft:overworld'),('l-stage-0-retired',C,'removed',True)]:
            with self.subTest(kind=kind,field=key):self.reject(lambda d,k=kind,a=actor,f=key,v=value:boundary(d,k)['workers'][a].__setitem__(f,v))
        self.reject(lambda d:self.persistent(d,'l-helper-a2-active',H,lambda p:p.__setitem__('SiegeWholePerimeter','{}')))
        self.reject(lambda d:self.persistent(d,'l-helper-a2-placement',H,lambda p:p.__setitem__('SiegePlayerFortificationPos','999L')))
    def test_offhand_construction_is_not_guessed_as_separate_stock(self):
        def change(d):
            inv=d['nativeWork'][-1]['authority']['workerInventory'];inv['slots'][4]=inv['off']='{Count:1b,id:"minecraft:cobblestone"}'
        self.reject(change)
    def test_settled_return_allows_only_exact_A2_persistent_binding_delta(self):
        self.reject(lambda d:self.persistent(d,'l-return-settled',H,lambda p:p.__setitem__('UnrelatedInjectedTag','1')))
        d=fixture();settled=boundary(d,'l-return-settled');active=boundary(d,'l-helper-a2-active')
        settled['workers'][H]=copy.deepcopy(active['workers'][H]);settled['ledgerSnapshot']=active['ledgerSnapshot']
        self.assertTrue(V.verify_report(d))
        self.persistent(d,'l-return-settled',H,lambda p:p.__setitem__('UnrelatedInjectedTag','1'))
        with self.assertRaises(ValueError):V.verify_report(d)
    def test_terminal_raw_stock_loaded_detached_and_canonical(self):
        self.reject(lambda d:boundary(d,'l-final-stable')['workers'][H]['inventory']['slots'].__setitem__(0,'{}'))
        for actor,key,value in [(H,'removed',True),(C,'currentArea',T),(H,'currentArea',T),(H,'mainAliasesSlot5',False),(C,'owner',T),(H,'beforeItemSlot',0),(C,'neededItemCount',1)]:
            with self.subTest(actor=actor,field=key):self.reject(lambda d,a=actor,k=key,v=value:boundary(d,'l-final-stable')['workers'][a].__setitem__(k,v))
    @staticmethod
    def local_cargo(d,index):
        def add(inv):
            item='{Count:1b,id:"minecraft:diamond"}';inv['off']=item;inv['slots'][4]=item
        add(d['nativeWorkerJoins'][index]['inventory'])
        arm=[e for e in d['events'] if e['kind']=='load-mirror-armed'][index];end=[e for e in d['events'] if e['kind']=='load-mirror-restored'][index]
        for inv in (arm['raw'],arm['armed'],end['before'],end['after']):add(inv)
        if index==1:
            add(d['finalizedOfflineInventory'])
            for e in d['activeHelperTransitEvents']:
                if e['kind'] in ('join-high','join-low') and e.get('loadedFromDisk'):add(e['inventory'])
        else:
            cut=[e['ordinal'] for e in d['activeHelperTransitEvents'] if e['kind']=='transfer-before'][1]
            for e in d['activeHelperTransitEvents']:
                if e['ordinal']>=cut and 'inventory' in e:add(e['inventory'])
    def test_coherent_local_disk_cargo_cannot_appear_then_disappear(self):self.reject(lambda d:self.local_cargo(d,1))
    def test_coherent_local_return_cargo_cannot_appear(self):self.reject(lambda d:self.local_cargo(d,2))
    def test_unrelated_offhand_cargo_is_preserved_without_material_reclassification(self):
        d=fixture()
        def visit(node):
            if isinstance(node,dict):
                if set(node)=={'slots','main','off','beforeItemSlot','persistent'}:
                    node['slots'][4]=node['off']='{Count:1b,id:"minecraft:diamond"}'
                else:
                    for child in node.values():visit(child)
            elif isinstance(node,list):
                for child in node:visit(child)
        visit(d);self.assertTrue(V.verify_report(d))
    def test_exact_stale_transient_area_may_be_null_or_A0(self):
        d=fixture(False);b=boundary(d,'l-holding-disk-join')
        b['workers'][H]['currentArea']=d['firstStageArea'];b['helperProof']['association']['currentArea']=d['firstStageArea']
        self.assertTrue(V.verify_report(d))
    def test_pending_snapshot_requires_actual_unrestored_admission(self):
        d=fixture(False);b=boundary(d,'l-holding-disk-join');arm=[e for e in d['events'] if e['kind']=='load-mirror-armed'][1]
        b['sequence']=arm['sequence']+.5;b['workers'][H].update(inventory=copy.deepcopy(arm['armed']),handPending=True,mainAliasesSlot5=False,handRebindCount=1)
        b['helperProof'].update(pendingHand=True,rebinds=1)
        for i,e in enumerate(sorted(d['events']+d['nativeWork']+d['nativeWorkerJoins'],key=lambda e:e['sequence']),1):e['sequence']=i
        d['events'].sort(key=lambda e:e['sequence']);d['evidenceSeal']['sequence']=max(e['sequence'] for e in d['events'])+1
        self.assertTrue(V.verify_report(d))
        d['workers'] and b['workers'][H]['inventory']['slots'].__setitem__(0,'{}')
        with self.assertRaises(ValueError):V.verify_report(d)


class PostReturnPersistentTests(unittest.TestCase):
    """Independent v2 false-pass specimens and exact recorded operation deltas."""
    reject=LaterStageOracleTests.reject
    persistent=staticmethod(RawEvidenceBindingTests.persistent)
    def test_unrelated_tag_at_active_A2_is_rejected(self):
        self.reject(lambda d:self.persistent(d,'l-helper-a2-active',H,lambda p:p.__setitem__('UnrelatedInjectedTag','1')))
    def test_unrelated_tag_at_A2_prework_is_rejected(self):
        def change(d):
            inv=d['nativeWork'][-1]['authority']['workerInventory'];p=V.compound(inv['persistent']);p['UnrelatedInjectedTag']='1';inv['persistent']=snbt(p)
        self.reject(change)
    def test_unrelated_tag_at_A2_placement_is_rejected(self):
        self.reject(lambda d:self.persistent(d,'l-helper-a2-placement',H,lambda p:p.__setitem__('UnrelatedInjectedTag','1')))
    def test_unrelated_terminal_tags_and_hand_counter_changes_are_rejected(self):
        for kind in ('l-authoritative-canceled','l-compacted-loaded','l-final-stable'):
            with self.subTest(kind=kind):self.reject(lambda d,k=kind:self.persistent(d,k,H,lambda p:p.__setitem__('UnrelatedInjectedTag','1')))
        self.reject(lambda d:self.persistent(d,'l-final-stable',H,lambda p:p.pop(V.C.REBINDS)))
    def test_malformed_cleanup_at_placement_prework_is_rejected(self):
        for raw in ('{}','{Version:1}','{Version:1,NEEDED:1}','{Version:1,NEEDED:2}'):
            def change(d,value=raw):
                inv=d['nativeWork'][-1]['authority']['workerInventory'];p=V.compound(inv['persistent']);p['SiegeProtectedInventoryCleanup']=value;inv['persistent']=snbt(p)
            with self.subTest(raw=raw):self.reject(change)
    def test_recorded_A2_supply_and_exact_needed_cleanup_are_allowed(self):
        d=fixture(a2_resupply=True);self.assertTrue(V.verify_report(d))
        supply=next(e for e in d['nativeWork'] if e['worker']==H and e['stage']==2 and e['kind']=='native-supply')
        self.assertEqual(V.compound(V.compound(supply['authority']['workerInventory']['persistent'])['SiegeProtectedInventoryCleanup']),{'Version':'1','NEEDED':'1'})
        for raw in ('{}','{Version:2,NEEDED:1}','{Version:1,NEEDED:2}','{Version:1,DEPOSIT:1}','{Version:1,NEEDED:1,Other:1}'):
            changed=copy.deepcopy(d);inv=next(e for e in changed['nativeWork'] if e['worker']==H and e['stage']==2 and e['kind']=='native-supply')['authority']['workerInventory']
            p=V.compound(inv['persistent']);p['SiegeProtectedInventoryCleanup']=raw;inv['persistent']=snbt(p)
            with self.subTest(raw=raw),self.assertRaises(ValueError):V.verify_report(changed)
    def test_dirty_counter_requires_exact_recorded_material_transfer(self):
        # Even a source-possible zero-material callback is unprovable with this
        # fixed WORK schema. It fails closed rather than receiving an exemption.
        self.reject(lambda d:self.persistent(d,'l-helper-a2-placement',H,lambda p:p.__setitem__('SiegeProtectedStorageDirtyWrites','1')))
        for count in ('0','2','-1','1L'):
            d=fixture(a2_resupply=True);self.persistent(d,'l-helper-a2-placement',H,lambda p:p.__setitem__('SiegeProtectedStorageDirtyWrites',count))
            with self.subTest(count=count),self.assertRaises(ValueError):V.verify_report(d)
    def test_unrelated_baseline_persistence_and_saturated_dirty_counter_are_preserved(self):
        d=fixture(a2_resupply=True)
        def visit(node):
            if isinstance(node,dict):
                if set(node)=={'slots','main','off','beforeItemSlot','persistent'}:
                    p=V.compound(node['persistent']);p['UnrelatedStableTag']='{Value:"preserved"}';p['SiegeProtectedStorageDirtyWrites']='2147483647';node['persistent']=snbt(p)
                else:
                    for value in node.values():visit(value)
            elif isinstance(node,list):
                for value in node:visit(value)
        visit(d);self.assertTrue(V.verify_report(d))
        self.persistent(d,'l-final-stable',H,lambda p:p.pop('UnrelatedStableTag'))
        with self.assertRaises(ValueError):V.verify_report(d)


class RuntimeBindingTests(unittest.TestCase):
    def test_loaded_provider_serializer_source_and_screenshot_closure(self):
        # Deliberately synthetic JAR bytes and mocked API replay. This executes
        # the new runtime-binding wrapper, never a native Forge attestation.
        setup=PART.AttestationTests('test_exact_synthetic_unique_entry_attestations_pass');setup.setUp()
        self.addCleanup(setup.doCleanups)
        d=fixture();d['loadedApiParity']=copy.deepcopy(setup.data['loadedApiParity']);d['serializerParity']=copy.deepcopy(setup.data['serializerParity'])
        evidence=setup.root/'evidence';(evidence/'api-gate').mkdir(parents=True)
        (evidence/'api-gate/gate.json').write_bytes(PART.encode(setup.gate))
        for field,file in [('sourceCommit','source-commit.txt'),('sourceTree','source-tree.txt')]:(evidence/file).write_text(d[field])
        for name in d['screenshots']:(evidence/name).write_bytes(b'\x89PNG\r\n\x1a\n'+b'SYNTHETIC IMAGE BYTES '*300)
        with patch.object(V.BASE.GATE,'verify_receipt',return_value=setup.gate):
            V.verify_runtime(d,evidence,setup.root/'synthetic-runtime.json')
            changes=[lambda v:v.__setitem__('sourceCommit','3'*40),lambda v:v['loadedApiParity'].__setitem__('gateSha256','0'*64),lambda v:v['loadedApiParity']['classes'][next(iter(v['loadedApiParity']['classes']))].__setitem__('provider','/unreviewed.jar'),lambda v:v['serializerParity'][0].__setitem__('rawResourceSha256','0'*64),lambda v:v['serializerContract'].__setitem__('instanceStable',False)]
            for i,change in enumerate(changes):
                value=copy.deepcopy(d);change(value)
                with self.subTest(case=i),self.assertRaises((ValueError,OSError)):V.verify_runtime(value,evidence,setup.root/'synthetic-runtime.json')
            (evidence/d['screenshots'][0]).unlink()
            with self.assertRaises(ValueError):V.verify_runtime(d,evidence,setup.root/'synthetic-runtime.json')


if __name__=='__main__':unittest.main()
