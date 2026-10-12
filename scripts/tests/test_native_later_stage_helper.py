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


def fixture(detach_immediately=True,a2_resupply=False,retained_request=True):
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
    coordinator_hand=snbt(dict(Version='1',Receipt=tag_uuid(T),Builder=tag_uuid(C),Owner=tag_uuid(O),Area=stages[0]['Area'],Generation=tag_uuid(G)))
    data.update(originalHelperHandReceipt=hand,originalHelperHandReceiptId=R)
    sequence=0;counts={C:0,H:0};stock={C:[0,0],H:[0,0]};chest=[352,220];seen=set();active=0;verified=0;retired=-1;member_state='active';hand_stage=0;rebinds=0;revision=2;dirty_writes=0
    def receipts():return [{k:p[k] for k in ('Project','Generation','Hash')}|{k:stages[i][k] for k in ('Index','Area','Digest')}|{'Targets':str(V.COUNTS[i])} for i in range(verified)]
    def project(state='RUNNING'):return snbt(p|dict(State=q(state),Revision=long(revision),Active=str(active),Receipts=tags(receipts())))
    def persistent(actor,stage=None,pending=False,detached=False,terminal=False,bindings=None):
        i=active if stage is None else stage
        v={'SiegeProtectedHandLifecycle':hand if actor==H else coordinator_hand}
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
        return snbt(dict(Sites=tags(sites),Retired='[]',Generation=tag_uuid(G),Invalid='0b',ProjectLeases=snbt(dict(Version='1',Projects=tags(leases))),HandLifecycles=tags([V.compound(hand),V.compound(coordinator_hand)]),BuilderAssignments=snbt(dict(Version='2',Projects='[]')),BuilderCrew=crew))
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
    attach_request_evidence(data,retained_request)
    return data


def boundary(data,kind):return next(e for e in data['events'] if e['kind']==kind)
def mutate_nbt(data,kind,field,old,new):
    value=boundary(data,kind);assert old in value[field];value[field]=value[field].replace(old,new,1)


def attach_request_evidence(data,retained=True):
    """Explicit synthetic identity graph, no native callback/resource attestation."""
    catalog=[]
    def ref(name,actor=None):
        row=dict(id=len(catalog)+1,**{'class':name})
        if actor:row['workerUuid']=actor
        catalog.append(row);return row['id']
    loader=ref('synthetic.NativeLoader')
    data['requestFieldContract']=dict(neededItemClass=V.NEEDED_CLASS,neededItemLoaderRef=loader,builderWorkGoalClass=V.GOAL_CLASS,builderWorkGoalLoaderRef=loader,fields=[dict(owner='com.talhanation.workers.entities.AbstractWorkerEntity',name='neededItems',type='java.util.List',modifiers=1,synthetic=False)]+[dict(owner=V.NEEDED_CLASS,name=n,type=t,modifiers=m,synthetic=False) for n,t,m in [('matcher','java.util.function.Predicate',17),('count','int',1),('required','boolean',17),('sourceKey','java.lang.Object',17),('cachedMatchKey','java.lang.Object',2),('matchKeyResolved','boolean',2)]])
    lists={};stores={}
    for actor in data['workers']:
        for epoch in (range(4) if actor==H else range(1)):
            lists[actor,epoch]=dict(listRef=ref('java.util.ArrayList'),listClass='java.util.ArrayList',count=0,records=[])
            if epoch not in (0,3):continue
            worker=ref(V.WORKER_CLASS,actor);session=ref(V.NATIVE_PACKAGE+'ProtectedInventoryGoal$Session');wrappers=[]
            for i,kind in enumerate(V.KINDS):
                cls=V.NATIVE_PACKAGE+('ProtectedStorageAccess' if i<2 else 'ProtectedUpkeepAccess')
                wrappers.append(dict(kind=kind,wrappedRef=ref('net.minecraft.world.entity.ai.goal.WrappedGoal'),wrapperRef=ref(cls),delegateRef=ref(V.DELEGATES[i]),sessionRef=session,workerRef=worker,delegateWorkerRef=worker,wrapperClass=cls,delegateClass=V.DELEGATES[i],wrappedRunning=False,started=False,protectedLifecycle=False,stopFailed=False))
            stores[actor,epoch]=dict(workerRef=worker,sessionRef=session,sessionWorkerRef=worker,wrappers=wrappers,pending=[],owners={},upkeepOwnerRef=0,draining=False,cleanupJournalPresent=False,cleanupJournal={},closed=True)
    req=dict(requestRef=ref(V.NEEDED_CLASS),matcherRef=ref('synthetic.NativeMatcher/0x001'),matcherClass='synthetic.NativeMatcher/0x001',matcherHidden=True,matcherSynthetic=True,matcherFinal=True,matcherNestHost=V.GOAL_CLASS,matcherLoaderRef=loader,count=12,required=True,sourceRef=0,cacheResolved=False,cacheRef=0,cacheRegistryKey=None)
    final=copy.deepcopy(lists[C,0])
    if retained:final.update(count=1,records=[req])
    cut=boundary(data,'l-authoritative-canceled')['sequence']
    last_coordinator=[w for w in data['nativeWork'] if w['worker']==C][-1]
    request_at=last_coordinator['sequence']-.005
    def epoch(actor,seq):return sum(j['sequence']<seq for j in data['nativeWorkerJoins']) if actor==H else 0
    for e in data['events']:
        if e['kind'] in V.BOUNDARIES:
            for actor,w in e['workers'].items():
                value=final if actor==C and e['sequence']>request_at else lists[actor,epoch(actor,e['sequence'])]
                w['nativeRequests']=copy.deepcopy(value);w['neededItemCount']=value['count']
                if e['sequence']>=cut:w['inventoryWork']=copy.deepcopy(stores[actor,epoch(actor,e['sequence'])])
        elif e['kind']=='terminal-native-bracket':
            actor=e['worker'];value=final if actor==C else lists[H,3]
            e.update(requestsBefore=copy.deepcopy(value),requestsAfter=copy.deepcopy(value),inventoryWorkBefore=copy.deepcopy(stores[actor,epoch(actor,e['sequence'])]),inventoryWorkAfter=copy.deepcopy(stores[actor,epoch(actor,e['sequence'])]),phase='L_FINAL_STABLE')
    firsts=[data['nativeWork'][0],data['nativeWork'][1],next(w for w in data['nativeWork'] if w['worker']==H and w['stage']==2)]
    for w in firsts:
        actor=w['worker'];k=(actor,epoch(actor,w['sequence']));value=lists[k]
        data['events'].append(dict(sequence=w['sequence']-.01,kind='native-request-frame',phase='BOTH_WORK' if w['stage']==0 else 'L_REJOIN',worker=actor,gameTime=w['gameTime'],stage=w['stage'],area=w['area'],authorized=True,nativeStateBefore=w['nativeState'],nativeStateAfter=w['nativeState'],authority=copy.deepcopy(w['authority']),before=copy.deepcopy(value),after=copy.deepcopy(value),inventoryWorkBefore=copy.deepcopy(stores[k]),inventoryWorkAfter=copy.deepcopy(stores[k])))
    if retained:
        w=last_coordinator
        data['events'].append(dict(sequence=request_at,kind='native-request-frame',phase='L_OFFLINE_A1',worker=C,gameTime=w['gameTime'],stage=w['stage'],area=w['area'],authorized=True,nativeStateBefore='PLACE_BLOCKS',nativeStateAfter='PLACE_BLOCKS',authority=copy.deepcopy(w['authority']),before=copy.deepcopy(lists[C,0]),after=copy.deepcopy(final),inventoryWorkBefore=copy.deepcopy(stores[C,0]),inventoryWorkAfter=copy.deepcopy(stores[C,0])))
    # A deliberately modeled admitted supply frame can carry the exact live NEEDED journal.
    for e in data['events']:
        if e['kind']!='native-request-frame' or e['inventoryWorkBefore'] is None:continue
        persistent=V.compound(e['authority']['workerInventory']['persistent'])
        if 'SiegeProtectedInventoryCleanup' in persistent:
            before=e['inventoryWorkBefore'];w=before['wrappers'][0]
            w.update(wrappedRunning=True,started=True,protectedLifecycle=True)
            before.update(owners={'NEEDED':w['wrapperRef']},cleanupJournalPresent=True,cleanupJournal={'NEEDED':1},closed=False)
    data['canceledRequests']={C:copy.deepcopy(final),H:copy.deepcopy(lists[H,3])}
    data['canceledInventoryWork']={C:copy.deepcopy(stores[C,0]),H:copy.deepcopy(stores[H,3])}
    data['requestReferenceCatalog']=catalog
    for n,e in enumerate(sorted(data['events']+data['nativeWork']+data['nativeWorkerJoins'],key=lambda e:e['sequence']),1):e['sequence']=n
    data['events'].sort(key=lambda e:e['sequence']);data['evidenceSeal']['sequence']=max(e['sequence'] for e in data['events'])+1


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
            ledger=V.compound(raw);ledger['HandLifecycles']=tags([h for h in V.snbt_list(ledger['HandLifecycles']) if V.nbt_uuid(h['Builder'])!=H]);crew=V.compound(ledger['BuilderCrew']);crew['Projects']='[]';ledger['BuilderCrew']=snbt(crew);return snbt(ledger)
        b=boundary(d,'l-commission');b['ledgerSnapshot']=empty(b['ledgerSnapshot']);b['workers'][H]['inventory']['persistent']='{}';b['workers'][H]['currentArea']=None
        work=d['nativeWork'][0];work['authority']['ledgerSnapshot']=empty(work['authority']['ledgerSnapshot'])
        # Both records are emitted from the same immutable native Observation.
        frame=next(e for e in d['events'] if e['kind']=='native-request-frame' and e['worker']==work['worker'] and e['gameTime']==work['gameTime'])
        frame['authority']=copy.deepcopy(work['authority'])
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
        for actor,key,value in [(H,'removed',True),(C,'currentArea',T),(H,'currentArea',T),(H,'mainAliasesSlot5',False),(C,'owner',T),(H,'beforeItemSlot',0),(C,'neededItemCount',2)]:
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


class ReturnedWaitingStatusTests(unittest.TestCase):
    @staticmethod
    def waiting(data):
        restored=[e for e in data['events'] if e['kind']=='load-mirror-restored'][2]
        restored['phase']='L_RETURN_SETTLE'  # The native later-END cut observed in run38182894683.
        for inv in (restored['after'],boundary(data,'l-return-settled')['workers'][H]['inventory']):
            value=V.compound(inv['persistent']);value['SiegeConstructionPause']=q(V.RETURN_WAIT_STATUS);inv['persistent']=snbt(value)
        return data
    def test_exact_detached_return_wait_then_active_removal(self):
        self.assertTrue(V.verify_report(self.waiting(fixture())))
    def test_arbitrary_earlier_or_retained_active_wait_is_rejected(self):
        mutations=[
            lambda d:RawEvidenceBindingTests.persistent(d,'l-helper-a2-active',H,lambda p:p.__setitem__('SiegeConstructionPause',q(V.RETURN_WAIT_STATUS))),
            lambda d:RawEvidenceBindingTests.persistent(d,'l-helper-a2-placement',H,lambda p:p.__setitem__('SiegeConstructionPause',q(V.RETURN_WAIT_STATUS))),
            lambda d:RawEvidenceBindingTests.persistent(d,'l-return-settled',H,lambda p:p.__setitem__('SiegeConstructionPause',q('foreign wait'))),
        ]
        for change in mutations:
            data=self.waiting(fixture());change(data)
            with self.subTest(change=change),self.assertRaises(ValueError):V.verify_report(data)
        for index in (0,1):
            data=fixture();after=[e for e in data['events'] if e['kind']=='load-mirror-restored'][index]['after'];p=V.compound(after['persistent']);p['SiegeConstructionPause']=q(V.RETURN_WAIT_STATUS);after['persistent']=snbt(p)
            with self.subTest(join=index),self.assertRaisesRegex(ValueError,'Restoration changed'):V.verify_mirrors(data)
    def test_return_wait_does_not_allow_other_fields_or_premature_selector(self):
        for key,value in [('ForeignTag','1'),('SiegeConstructionPause',q('foreign wait')),('SiegeProtectedAreaReceipt',tag_uuid(T))]:
            data=self.waiting(fixture());after=[e for e in data['events'] if e['kind']=='load-mirror-restored'][2]['after'];p=V.compound(after['persistent']);p[key]=value;after['persistent']=snbt(p)
            with self.subTest(key=key),self.assertRaises(ValueError):V.verify_mirrors(data)


class DormantRequestOracleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.base=fixture()
    def check(self,data):
        bounds={k:boundary(data,k) for k in V.BOUNDARIES};original=V.compound(data['acceptedProject']);stages=V.snbt_list(original['Stages'])
        return V.verify_request_observations(data,bounds,original,stages)
    def reject(self,change):
        data=copy.deepcopy(self.base);change(data)
        with self.assertRaises((ValueError,KeyError,TypeError,IndexError)):self.check(data)
    def test_exact_dormant_and_empty_terminal_requests_pass(self):
        self.assertTrue(V.verify_report(self.base));self.assertTrue(V.verify_report(fixture(retained_request=False)))
    def test_catalog_ids_classes_loaders_and_field_metadata_are_exact(self):
        changes=[lambda d:d.pop('requestReferenceCatalog'),lambda d:d['requestReferenceCatalog'].pop(0),lambda d:d['requestReferenceCatalog'][0].__setitem__('id',2),lambda d:d['requestFieldContract'].__setitem__('builderWorkGoalLoaderRef',2),lambda d:d['requestFieldContract']['fields'][5].__setitem__('modifiers',1),lambda d:d['requestFieldContract']['fields'].reverse(),lambda d:d['requestFieldContract']['fields'][0].__setitem__('type','java.util.ArrayList')]
        for change in changes:
            with self.subTest(change=change):self.reject(change)
    def test_request_shape_and_matcher_provenance_negative_matrix(self):
        catalog,loader=V.request_catalog(self.base);value=self.base['canceledRequests'][C]
        changes=[lambda v:v.__setitem__('listClass','java.util.LinkedList'),lambda v:v.__setitem__('listRef',1),lambda v:v.__setitem__('count',2),lambda v:v.__setitem__('count',True),lambda v:(v['records'].append(copy.deepcopy(v['records'][0])),v.__setitem__('count',2))]
        for key,bad in [('requestRef',1),('matcherRef',1),('matcherClass','foreign.Matcher'),('matcherHidden',False),('matcherSynthetic',False),('matcherFinal',False),('matcherNestHost','foreign.Owner'),('matcherLoaderRef',2),('count',0),('count',65),('required',False),('sourceRef',1),('cacheResolved',0),('cacheRef',1),('cacheRegistryKey','minecraft:cobblestone')]:changes.append(lambda v,k=key,b=bad:v['records'][0].__setitem__(k,b))
        for i,change in enumerate(changes):
            v=copy.deepcopy(value);change(v)
            with self.subTest(case=i),self.assertRaises((ValueError,KeyError,TypeError)):V.request_snapshot(v,catalog,loader)
    def test_cache_unresolved_resolved_null_and_registered_item_are_distinct(self):
        data=copy.deepcopy(self.base);catalog,loader=V.request_catalog(data);value=copy.deepcopy(data['canceledRequests'][C])
        V.request_snapshot(value,catalog,loader);value['records'][0]['cacheResolved']=True;V.request_snapshot(value,catalog,loader)
        item=len(catalog)+1;catalog[item]={'id':item,'class':'net.minecraft.world.item.BlockItem'}
        value['records'][0].update(cacheRef=item,cacheRegistryKey='minecraft:cobblestone');V.request_snapshot(value,catalog,loader)
        value['records'][0]['cacheResolved']=False
        with self.assertRaises(ValueError):V.request_snapshot(value,catalog,loader)
    def test_every_frozen_request_field_mutation_and_missing_origin_reject(self):
        def frame(d):return next(e for e in d['events'] if e['kind']=='terminal-native-bracket' and e['worker']==C)
        for key,bad in [('listRef',1),('count',0),('records',[])]:
            with self.subTest(key=key):self.reject(lambda d,k=key,b=bad:frame(d)['requestsAfter'].__setitem__(k,b))
        for key,bad in [('requestRef',1),('matcherRef',1),('count',11),('required',False),('sourceRef',1),('cacheResolved',True),('cacheRef',1),('cacheRegistryKey','minecraft:oak_planks')]:
            with self.subTest(key=key):self.reject(lambda d,k=key,b=bad:frame(d)['requestsAfter']['records'][0].__setitem__(k,b))
        self.reject(lambda d:d['events'].remove(next(e for e in d['events'] if e['kind']=='native-request-frame' and e['before']!=e['after'])))
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='native-request-frame' and e['before']!=e['after']).__setitem__('authorized',False))
        self.reject(lambda d:boundary(d,'l-final-stable')['workers'][C].__setitem__('neededItemCount',0))
        self.reject(lambda d:d['canceledRequests'].__setitem__(C,copy.deepcopy(d['canceledRequests'][H])))
    def test_two_ordered_requests_cannot_be_reordered_replaced_added_or_removed(self):
        data=copy.deepcopy(self.base);records=data['requestReferenceCatalog'];extra=len(records)+1
        records.append({'id':extra,'class':V.NEEDED_CLASS});listref=data['canceledRequests'][C]['listRef']
        def expand(value):
            if isinstance(value,dict):
                if set(value)=={'listRef','listClass','count','records'} and value['listRef']==listref and value['count']==1:
                    second=copy.deepcopy(value['records'][0]);second.update(requestRef=extra,count=7);value['records'].append(second);value['count']=2
                else:
                    for child in value.values():expand(child)
            elif isinstance(value,list):
                for child in value:expand(child)
        expand(data)
        for e in data["events"]:
            for worker in e.get("workers",{}).values():worker["neededItemCount"]=worker["nativeRequests"]["count"]
        self.assertTrue(self.check(data))
        for mode in ('reorder','remove','add','replace'):
            value=copy.deepcopy(data);snapshot=next(e for e in value['events'] if e['kind']=='terminal-native-bracket' and e['worker']==C)['requestsAfter']
            if mode=='reorder':snapshot['records'].reverse()
            elif mode=='remove':snapshot['records'].pop();snapshot['count']=1
            elif mode=='add':snapshot['records'].append(copy.deepcopy(snapshot['records'][0]));snapshot['count']=3
            else:snapshot['records'][0]['requestRef']=extra
            with self.subTest(mode=mode),self.assertRaises(ValueError):self.check(value)
    def test_storage_exact_ownership_and_closed_state_matrix(self):
        catalog,_=V.request_catalog(self.base);base=self.base['canceledInventoryWork'][C]
        changes=[lambda v:v['wrappers'].pop(),lambda v:v['wrappers'].reverse(),lambda v:v.__setitem__('workerRef',1),lambda v:v.__setitem__('sessionRef',1),lambda v:v.__setitem__('sessionWorkerRef',1),lambda v:v.__setitem__('upkeepOwnerRef',1),lambda v:v.__setitem__('pending',[1]),lambda v:v.__setitem__('owners',{'NEEDED':1}),lambda v:v.__setitem__('draining',True),lambda v:v.__setitem__('cleanupJournalPresent',True),lambda v:v.__setitem__('cleanupJournal',{'NEEDED':1})]
        for index in range(4):
            for key,bad in [('wrappedRef',1),('wrapperRef',1),('delegateRef',1),('sessionRef',1),('workerRef',1),('delegateWorkerRef',1),('wrapperClass','foreign.Wrapper'),('delegateClass','foreign.Delegate'),('wrappedRunning',True),('started',True),('protectedLifecycle',True),('stopFailed',True)]:changes.append(lambda v,i=index,k=key,b=bad:v['wrappers'][i].__setitem__(k,b))
        for i,change in enumerate(changes):
            value=copy.deepcopy(base);change(value)
            with self.subTest(case=i),self.assertRaises((ValueError,KeyError,TypeError)):V.storage_snapshot(value,catalog,C,True)
    def test_pending_ownership_can_close_naturally_but_not_resume(self):
        data=copy.deepcopy(self.base);pending=copy.deepcopy(data['canceledInventoryWork'][C]);w=pending['wrappers'][0]
        w['protectedLifecycle']=True;pending.update(pending=[w['wrapperRef']],owners={'NEEDED':w['wrapperRef']},cleanupJournalPresent=True,cleanupJournal={'NEEDED':1},closed=False)
        data['canceledInventoryWork'][C]=copy.deepcopy(pending);worker=boundary(data,'l-authoritative-canceled')['workers'][C];worker['inventoryWork']=copy.deepcopy(pending)
        worker['inventory']['persistent']=snbt(V.compound(worker['inventory']['persistent'])|{'SiegeProtectedInventoryCleanup':'{Version:1,NEEDED:1}'})
        self.assertTrue(self.check(data))
        bracket=next(e for e in data['events'] if e['kind']=='terminal-native-bracket' and e['worker']==C);bracket['inventoryWorkBefore']=pending
        with self.assertRaisesRegex(ValueError,'closed inventory'):self.check(data)
    def test_postcancel_wrapper_identity_and_raw_count_disagreement_reject(self):
        self.reject(lambda d:boundary(d,'l-final-stable')['workers'][C]['inventoryWork']['wrappers'][0].__setitem__('delegateRef',1))
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='terminal-native-bracket').pop('requestsBefore'))
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='terminal-native-bracket').pop('inventoryWorkAfter'))
        self.reject(lambda d:boundary(d,'l-commission')['workers'][C]['nativeRequests'].__setitem__('count',1))
        self.reject(lambda d:boundary(d,'l-authoritative-canceled')['workers'][C]['inventoryWork'].__setitem__('cleanupJournalPresent',True))
    def test_one_request_frame_per_worker_ordinary_tick(self):
        data=copy.deepcopy(self.base);frame=next(e for e in data['events'] if e['kind']=='native-request-frame' and e['before']==e['after'])
        duplicate=copy.deepcopy(frame);duplicate['sequence']+=0.01;data['events'].insert(data['events'].index(frame)+1,duplicate)
        with self.assertRaisesRegex(ValueError,'one ordinary tick'):self.check(data)
    def test_pre_cut_request_authority_is_not_a_boolean(self):
        def f(d):return next(e for e in d['events'] if e['kind']=='native-request-frame' and e['before']!=e['after'])
        for change in [lambda d:f(d).__setitem__('stage',2),lambda d:f(d)['authority'].__setitem__('owner',H),lambda d:f(d)['authority'].__setitem__('currentArea',T),lambda d:f(d)['authority'].__setitem__('projectSnapshot',boundary(d,'l-authoritative-canceled')['projectSnapshot']),lambda d:f(d)['authority']['workerInventory'].__setitem__('persistent','{}')]:
            with self.subTest(change=change):self.reject(change)


class DormantReferenceTimelineTests(unittest.TestCase):
    """Exact independent full-report counterexamples, plus adjacent identity cases."""
    @classmethod
    def setUpClass(cls):cls.base=fixture()
    @staticmethod
    def walk(value,fn):
        if isinstance(value,dict):
            fn(value)
            for child in list(value.values()):DormantReferenceTimelineTests.walk(child,fn)
        elif isinstance(value,list):
            for child in value:DormantReferenceTimelineTests.walk(child,fn)
    @staticmethod
    def renumber(data):
        for n,e in enumerate(sorted(data['events']+data['nativeWork']+data['nativeWorkerJoins'],key=lambda e:e['sequence']),1):e['sequence']=n
        data['events'].sort(key=lambda e:e['sequence']);data['evidenceSeal']['sequence']=max(e['sequence'] for e in data['events'])+1
    def reject(self,change,reason):
        data=copy.deepcopy(self.base);change(data)
        with self.assertRaisesRegex(ValueError,reason):V.verify_report(data)
    def test_review_session_cannot_be_cached_material(self):
        def change(d):
            ref=d['canceledInventoryWork'][C]['sessionRef']
            self.walk(d,lambda v:v.update(cacheResolved=True,cacheRef=ref,cacheRegistryKey='minecraft:cobblestone') if 'requestRef' in v else None)
        self.reject(change,'Global reference role')
    def test_review_list_cannot_be_matcher(self):
        def change(d):
            ref=d['canceledRequests'][C]['listRef']
            self.walk(d,lambda v:v.update(matcherRef=ref,matcherClass='java.util.ArrayList') if 'requestRef' in v else None)
        self.reject(change,'Global reference role')
    def test_loader_role_cannot_alias_native_request_list(self):
        def change(d):
            ref=d['canceledRequests'][C]['listRef']
            d['requestFieldContract'].update(neededItemLoaderRef=ref,builderWorkGoalLoaderRef=ref)
            self.walk(d,lambda v:v.update(matcherLoaderRef=ref) if 'requestRef' in v else None)
        self.reject(change,'Global reference role')
    def test_catalog_names_alone_do_not_prove_java_hierarchy(self):
        # Explicit evidence limit: source getClassLoader() supplies actual typing.
        # A standalone forged class string is not repaired with a blacklist.
        data=copy.deepcopy(self.base);data['requestReferenceCatalog'][0]['class']='java.lang.String'
        self.assertTrue(V.verify_report(data))
    @classmethod
    def remap(cls,data,old,new,fields):
        mapping={new[k]:old[k] for k in fields}
        for a,b in zip(old['wrappers'],new['wrappers']):mapping.update({b[k]:a[k] for k in ('wrappedRef','wrapperRef','delegateRef')})
        def replace(v):
            for key,value in list(v.items()):
                if key.endswith('Ref') and type(value) is int and value in mapping:v[key]=mapping[value]
        cls.walk(data,replace)
    def test_review_old_helper_graph_cannot_survive_disk_reconstruction(self):
        def change(d):
            old=next(e for e in d['events'] if e.get('kind')=='native-request-frame' and e['worker']==H)['inventoryWorkBefore']
            self.remap(d,old,d['canceledInventoryWork'][H],('workerRef','sessionRef'))
        self.reject(change,'Global reference role/actor/load ownership')
    def test_review_workers_cannot_share_session_or_wrapper_graph(self):
        self.reject(lambda d:self.remap(d,d['canceledInventoryWork'][H],d['canceledInventoryWork'][C],('sessionRef',)),'Global reference role/actor/load ownership')
    def test_material_singletons_have_bijective_global_reference_mapping(self):
        data=copy.deepcopy(self.base);catalog,loader=V.request_catalog(data);n=len(catalog)
        catalog[n+1]={'id':n+1,'class':'net.minecraft.world.item.BlockItem'}
        catalog[n+2]={'id':n+2,'class':'net.minecraft.world.item.BlockItem'}
        value=copy.deepcopy(data['canceledRequests'][C]);r=value['records'][0];r.update(cacheResolved=True,cacheRef=n+1,cacheRegistryKey='minecraft:cobblestone')
        roles=V.ReferenceRoles(catalog,loader);roles.requests(value,(C,0));roles.requests(value,(C,0))
        changed=copy.deepcopy(value);changed['records'][0]['cacheRef']=n+2
        with self.assertRaisesRegex(ValueError,'multiple reference identities'):roles.requests(changed,(C,0))
        changed=copy.deepcopy(value);changed['records'][0]['cacheRegistryKey']='minecraft:oak_planks'
        with self.assertRaisesRegex(ValueError,'Global reference role'):roles.requests(changed,(C,0))
        changed=copy.deepcopy(value);changed['listRef']=n+3
        catalog[n+3]={'id':n+3,'class':'java.util.ArrayList'}
        with self.assertRaisesRegex(ValueError,'Global reference role'):roles.requests(changed,(H,3))
    @classmethod
    def delayed_stable(cls,d,tick):
        b=next(e for e in d['events'] if e['kind']=='terminal-native-bracket' and e['gameTime']==tick)
        compact=boundary(d,'l-compacted-loaded');compact.update(sequence=b['sequence']-.2,gameTime=tick)
        phase=next(e for e in d['events'] if e.get('kind')=='phase' and e.get('phase')=='L_FINAL_STABLE');phase.update(sequence=b['sequence']-.1,gameTime=tick)
        for e in d['events']:
            if e['kind']=='terminal-native-bracket' and e['gameTime']<tick:
                e['phase']='L_CANCEL_WAIT'
                for key in ('inventoryWorkBefore','inventoryWorkAfter'):
                    v=e[key];w=v['wrappers'][0];w['protectedLifecycle']=True
                    v.update(pending=[w['wrapperRef']],owners={'NEEDED':w['wrapperRef']},cleanupJournalPresent=True,cleanupJournal={'NEEDED':1},closed=False)
        cls.renumber(d)
    def test_review_one_closed_tick_after_pending_does_not_count_as_hundred(self):
        self.reject(lambda d:self.delayed_stable(d,261),'full 100-tick continuously closed')
    def test_full_hundred_closed_ticks_after_observed_pending_is_allowed(self):
        data=copy.deepcopy(self.base);self.delayed_stable(data,163)
        # The real driver enters closed stability at END, after both actor brackets.
        at=[e for e in data['events'] if e['kind']=='terminal-native-bracket' and e['gameTime']==163]
        compact=boundary(data,'l-compacted-loaded');compact['sequence']=at[-1]['sequence']+.1
        phase=next(e for e in data['events'] if e.get('kind')=='phase' and e['phase']=='L_FINAL_STABLE');phase['sequence']=at[-1]['sequence']+.2
        for e in at:e['phase']='L_CANCEL_WAIT'
        final=boundary(data,'l-final-stable');last=[e for e in data['events'] if e['kind']=='terminal-native-bracket' and e['gameTime']==262]
        for i,e in enumerate(last):
            new=copy.deepcopy(e);new.update(sequence=final['sequence']-.2+i*.1,gameTime=263);data['events'].append(new)
        final['gameTime']=263
        next(e for e in data['events'] if e.get('kind')=='phase' and e['phase']=='FINISHED')['gameTime']=263
        data['evidenceSeal']['gameTime']=263;data['cancellationStableTicks']=101;data['terminalObservedBrackets']+=2
        self.renumber(data);self.assertTrue(V.verify_report(data))
    def test_review_future_stage_request_before_its_native_receipt_rejects(self):
        def change(d):
            e=next(e for e in d['events'] if e['kind']=='native-request-frame' and e['before']!=e['after'])
            first=next(x for x in d['events'] if x['kind']=='native-request-frame' and x['worker']==C and x['before']==x['after'])
            e.update(sequence=first['sequence']+.1,gameTime=1);d['events'].remove(first)
            for b in d['events']:
                if b['kind'] in V.BOUNDARIES and b['sequence']>e['sequence']:
                    w=b['workers'][C];w['nativeRequests']=copy.deepcopy(e['after']);w['neededItemCount']=1
            self.renumber(d)
        self.reject(change,'observed stage/receipt chronology')
    def test_review_raw_request_cargo_must_follow_finite_native_work(self):
        def change(d):
            e=next(e for e in d['events'] if e['kind']=='native-request-frame' and e['before']!=e['after'])
            e['authority']['workerInventory']['slots'][5]='{Count:1b,id:"minecraft:cobblestone"}'
            e['authority']['workerInventory']['main']=e['authority']['workerInventory']['slots'][5]
        self.reject(change,'raw cargo differs from finite native work')
    def test_same_native_frame_binds_nonmaterial_raw_cargo_and_persistence(self):
        def change(d,key,value):
            e=next(e for e in d['events'] if e['kind']=='native-request-frame' and e['before']!=e['after'])
            if key=='slot':e['authority']['workerInventory']['slots'][2]=value
            else:
                inv=e['authority']['workerInventory'];inv['persistent']=snbt(V.compound(inv['persistent'])|{'Unrelated':value})
        self.reject(lambda d:change(d,'slot','{Count:1b,id:"minecraft:diamond"}'),'raw authority differs within the same native bracket')
        self.reject(lambda d:change(d,'persistent','1'),'raw authority differs within the same native bracket')


class RequestOnlyAuthorityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.base=fixture()
    @staticmethod
    def pure(data,actor):
        if actor==C:
            frame=next(e for e in data['events'] if e['kind']=='native-request-frame' and e['before']!=e['after']);frame['gameTime']=139
        else:
            frame=next(e for e in data['events'] if e['kind']=='native-request-frame' and e['worker']==H and e['stage']==2);frame['gameTime']=160
        return frame
    def test_coordinator_and_helper_request_only_controls_pass(self):
        for actor in (C,H):
            data=copy.deepcopy(self.base);self.pure(data,actor)
            with self.subTest(actor=actor):self.assertTrue(V.verify_report(data))
    def test_every_authorized_request_only_frame_rejects_hand_review(self):
        for actor in (C,H):
            data=copy.deepcopy(self.base);inv=self.pure(data,actor)['authority']['workerInventory']
            inv['persistent']=snbt(V.compound(inv['persistent'])|{'SiegeProtectedHandReviewRequired':'1b'})
            with self.subTest(actor=actor),self.assertRaisesRegex(ValueError,'Hand-review flag'):V.verify_report(data)
    def test_helper_request_only_frame_retains_exact_A2_persistent_projection(self):
        for key,value in [('SiegeProtectedStorageDirtyWrites','999'),('UnrelatedInjectedTag','1'),('SiegeProtectedHandRefreshRebinds','99')]:
            data=copy.deepcopy(self.base);inv=self.pure(data,H)['authority']['workerInventory']
            inv['persistent']=snbt(V.compound(inv['persistent'])|{key:value})
            with self.subTest(key=key),self.assertRaisesRegex(ValueError,'persistent data changed outside'):V.verify_report(data)
    def test_request_only_frame_cannot_invent_live_supply_cleanup(self):
        data=copy.deepcopy(self.base);frame=self.pure(data,H);inv=frame['authority']['workerInventory']
        inv['persistent']=snbt(V.compound(inv['persistent'])|{'SiegeProtectedInventoryCleanup':'{Version:1,NEEDED:1}'})
        # Keep the source-shaped raw journal and observer graph consistent; the
        # missing same-tick material-supply receipt must independently reject it.
        value=frame['inventoryWorkBefore'];w=value['wrappers'][0];w.update(wrappedRunning=True,started=True,protectedLifecycle=True)
        value.update(owners={'NEEDED':w['wrapperRef']},cleanupJournalPresent=True,cleanupJournal={'NEEDED':1},closed=False)
        with self.assertRaisesRegex(ValueError,'exact recorded live supply checkpoint'):V.verify_report(data)
    def test_real_same_tick_supply_retains_existing_checkpoint_rule(self):
        data=fixture(a2_resupply=True)
        frame=next(e for e in data['events'] if e['kind']=='native-request-frame' and e['worker']==H and e['stage']==2)
        self.assertIn('SiegeProtectedInventoryCleanup',V.compound(frame['authority']['workerInventory']['persistent']))
        self.assertTrue(V.verify_report(data))

    def test_request_hand_requires_exact_unique_native_ledger_receipt(self):
        for actor in (C,H):
            for mode in ('missing','receipt','owner','builder','generation','area','zero','extra','duplicate','missing-ledger'):
                data=copy.deepcopy(self.base);frame=self.pure(data,actor);auth=frame['authority'];inv=auth['workerInventory'];p=V.compound(inv['persistent']);h=V.compound(p['SiegeProtectedHandLifecycle'])
                if mode=='missing':p.pop('SiegeProtectedHandLifecycle')
                elif mode in ('duplicate','missing-ledger'):
                    ledger=V.compound(auth['ledgerSnapshot']);hands=V.snbt_list(ledger['HandLifecycles'])
                    if mode=='duplicate':hands.append(h)
                    else:hands=[x for x in hands if V.nbt_uuid(x['Builder'])!=actor]
                    ledger['HandLifecycles']=tags(hands);auth['ledgerSnapshot']=snbt(ledger)
                else:
                    if mode=='extra':h['Extra']='1'
                    elif mode=='zero':h['Receipt']=tag_uuid(str(uuid.UUID(int=0)))
                    else:h[mode.title()]=tag_uuid(P)
                    p['SiegeProtectedHandLifecycle']=snbt(h)
                inv['persistent']=snbt(p)
                with self.subTest(actor=actor,mode=mode),self.assertRaises(ValueError):V.verify_report(data)
    def test_coordinator_historical_hand_area_is_not_current_stage_authority(self):
        data=copy.deepcopy(self.base);frame=self.pure(data,C);hand=V.compound(V.compound(frame['authority']['workerInventory']['persistent'])['SiegeProtectedHandLifecycle'])
        self.assertEqual(V.nbt_uuid(hand['Area']),data['firstStageArea']);self.assertNotEqual(frame['area'],data['firstStageArea'])
        self.assertTrue(V.verify_report(data))

    def test_detached_return_empty_observation_is_not_authorized_work(self):
        def insert(data,authorized):
            b=boundary(data,'l-return-settled');a=boundary(data,'l-helper-a2-active');w=b['workers'][H]
            active=next(e for e in data['events'] if e['kind']=='native-request-frame' and e['worker']==H and e['stage']==2)
            data['events'].append(dict(sequence=a['sequence']-.5,kind='native-request-frame',phase='L_REJOIN',worker=H,gameTime=158,stage=2,area=active['area'],authorized=authorized,nativeStateBefore='IDLE',nativeStateAfter='IDLE',authority=dict(workerInventory=copy.deepcopy(w['inventory']),ledgerSnapshot=b['ledgerSnapshot'],projectSnapshot=b['projectSnapshot'],currentArea=None,owner=data['owner']),before=copy.deepcopy(w['nativeRequests']),after=copy.deepcopy(w['nativeRequests']),inventoryWorkBefore=None,inventoryWorkAfter=None))
            DormantReferenceTimelineTests.renumber(data)
        data=copy.deepcopy(self.base);insert(data,False);self.assertTrue(V.verify_report(data))
        data=copy.deepcopy(self.base);insert(data,True)
        with self.assertRaisesRegex(ValueError,'authorization/storage presence'):V.verify_report(data)
    def test_authorized_paid_frame_cannot_omit_both_native_storage_snapshots(self):
        data=copy.deepcopy(self.base);frame=next(e for e in data['events'] if e['kind']=='native-request-frame' and e['worker']==C)
        frame.update(inventoryWorkBefore=None,inventoryWorkAfter=None)
        with self.assertRaisesRegex(ValueError,'authorization/storage presence'):V.verify_report(data)


class RuntimeBindingTests(unittest.TestCase):
    def test_loaded_provider_serializer_source_and_screenshot_closure(self):
        # Deliberately synthetic JAR bytes and mocked API replay. This executes
        # the new runtime-binding wrapper, never a native Forge attestation.
        setup=PART.AttestationTests('test_exact_synthetic_unique_entry_attestations_pass');setup.setUp()
        self.addCleanup(setup.doCleanups)
        setup.entries.append((V.NEEDED_CLASS.replace('.','/')+'.class',b'SYNTHETIC NEEDED ITEM RESOURCE'))
        setup.write_jar();setup.repin_synthetic_jar()
        d=fixture();d['loadedApiParity']=copy.deepcopy(setup.data['loadedApiParity']);d['serializerParity']=copy.deepcopy(setup.data['serializerParity']);d['requestClassParity']=setup.proof(V.NEEDED_CLASS)
        evidence=setup.root/'evidence';(evidence/'api-gate').mkdir(parents=True)
        (evidence/'api-gate/gate.json').write_bytes(PART.encode(setup.gate))
        for field,file in [('sourceCommit','source-commit.txt'),('sourceTree','source-tree.txt')]:(evidence/file).write_text(d[field])
        for name in d['screenshots']:(evidence/name).write_bytes(b'\x89PNG\r\n\x1a\n'+b'SYNTHETIC IMAGE BYTES '*300)
        with patch.object(V.BASE.GATE,'verify_receipt',return_value=setup.gate):
            with self.assertRaisesRegex(ValueError,'reviewed Workers'):V.verify_runtime(d,evidence,setup.root/'synthetic-runtime.json')
        # Only this synthetic plumbing test replaces the immutable supplier pin.
        with patch.object(V.BASE.GATE,'verify_receipt',return_value=setup.gate), patch.object(V,'WORKERS_HASH',d['requestClassParity']['supplierSha256']):
            V.verify_runtime(d,evidence,setup.root/'synthetic-runtime.json')
            changes=[lambda v:v['requestClassParity'].__setitem__('rawResourceSha256','0'*64),lambda v:v['requestClassParity'].__setitem__('supplierSha256','0'*64),lambda v:v['requestClassParity'].__setitem__('name',V.GOAL_CLASS),lambda v:v['requestClassParity'].__setitem__('provider','/foreign.jar'),lambda v:v['requestClassParity'].__setitem__('scope','transformed bytecode proven'),lambda v:v.__setitem__('sourceCommit','3'*40),lambda v:v['loadedApiParity'].__setitem__('gateSha256','0'*64),lambda v:v['loadedApiParity']['classes'][next(iter(v['loadedApiParity']['classes']))].__setitem__('provider','/unreviewed.jar'),lambda v:v['serializerParity'][0].__setitem__('rawResourceSha256','0'*64),lambda v:v['serializerContract'].__setitem__('instanceStable',False)]
            for i,change in enumerate(changes):
                value=copy.deepcopy(d);change(value)
                with self.subTest(case=i),self.assertRaises((ValueError,OSError)):V.verify_runtime(value,evidence,setup.root/'synthetic-runtime.json')
            (evidence/d['screenshots'][0]).unlink()
            with self.assertRaises(ValueError):V.verify_runtime(d,evidence,setup.root/'synthetic-runtime.json')


if __name__=='__main__':unittest.main()
