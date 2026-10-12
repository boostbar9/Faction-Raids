"""Strict synthetic Case C receipts. Executed Java decisions are tested separately; no engine emulation."""
import copy
import importlib.util
from pathlib import Path
import unittest
import uuid

ROOT=Path(__file__).resolve().parents[2]
def module(name,path):
    spec=importlib.util.spec_from_file_location(name,ROOT/path);value=importlib.util.module_from_spec(spec);spec.loader.exec_module(value);return value
VERIFY=module('absent_cancel_oracle','scripts/verify-native-absent-helper-cancel.py')
BASE=module('baseline_fixture','scripts/tests/test_native_active_helper_qa.py')
C,H,O,A,P,G,T=BASE.C,BASE.H,BASE.O,BASE.A,BASE.P,BASE.G,BASE.T
R='00000000-0000-0000-0000-000000000008'
S='00000000-0000-0000-0000-000000000009'
inv=BASE.inv
def tag_uuid(value):
    number=uuid.UUID(value).int
    return '[I;'+','.join(str((part-2**32) if part>=2**31 else part) for part in [(number>>shift)&0xffffffff for shift in (96,64,32,0)])+']'
def terminal_tag():
    pairs={'Version':'1','Project':tag_uuid(P),'Generation':'1L','Hash':'"'+'a'*64+'"','Owner':tag_uuid(O),'Builder':tag_uuid(C),
           'CoreKey':'"team:qa"','Faction':'"qa"','Revision':'3L','State':'"CANCELED"','Active':'0','Verified':'0','ClaimChunks':'1',
           'LedgerGeneration':tag_uuid(G),'FeeVersion':'1','Price':'64','ReceiptHash':'"'+'b'*64+'"','ReservationRetired':'"'+'c'*64+'"',
           'BuilderDetached':'"'+'d'*64+'"','StagesCleaned':'"'+'e'*64+'"','Stages':'[{Index:0,Area:'+tag_uuid(A)+',Digest:"'+'a'*64+'",Targets:139},{Index:1,Area:'+tag_uuid(T)+',Digest:"'+'b'*64+'",Targets:147},{Index:2,Area:'+tag_uuid(R)+',Digest:"'+'c'*64+'",Targets:147},{Index:3,Area:'+tag_uuid(S)+',Digest:"'+'d'*64+'",Targets:139}]',
           'Payment':'{Project:'+tag_uuid(P)+',Generation:1L,Hash:"'+'a'*64+'",FeeVersion:1,Price:64,Debited:64,Creative:0b}'}
    pairs['ReceiptHash']='"'+VERIFY.terminal_seal(pairs)+'"'
    return '{'+','.join(k+':'+v for k,v in pairs.items())+'}'


def fixture(immediate_cleanup=True):
    data=BASE.fixture();data.update(mode='active-helper-cancel-while-absent',sameStageReturnVerified=False,
        coordinatorFirstDiskOrderVerified=False,postDiskHelperPlacementVerified=False,canceledWhileAbsentVerified=True,
        terminalCleanupOnlyReturnVerified=True,canceledOfflineStableTicks=100,cancellationStableTicks=100,
        canceledAuthorityGameTime=411,originalHelperHandReceipt='{Version:1,Receipt:'+tag_uuid(R)+',Builder:'+tag_uuid(H)+',Owner:'+tag_uuid(O)+',Area:'+tag_uuid(A)+',Generation:'+tag_uuid(G)+'}',originalHelperHandReceiptId=R,
        projectGeneration=1,coreKey='team:qa',syntheticPartition=True,qaSectionTargetCap=192,materialCounts={'minecraft:cobblestone':352,'minecraft:oak_planks':220},events=[],nativeWork=[],nativeWorkerJoins=[])
    summary=dict(state='CANCELED',activeStage=0,verifiedStages=0,targetCount=572,claimChunks=1,feeVersion=1,
                 quotedPrice=64,payment='paid64',stages=['0:'+A+':'+'a'*64+':139','1:'+T+':'+'b'*64+':147','2:'+R+':'+'c'*64+':147','3:'+S+':'+'d'*64+':139'])
    data['expectedCanceledSummary']=summary
    raw_terminal=VERIFY.compound(terminal_tag());original={key:raw_terminal[key] for key in ('Project','Generation','Hash','Owner','Builder','CoreKey','Faction','FeeVersion','Price','Payment')}
    original.update(State='"RUNNING"',Active='0',Receipts='[]',Revision='2L',Blocker='""')
    accepted_stages=[]
    for stage in VERIFY.compound_list(raw_terminal['Stages']):
        fields={key:stage[key] for key in ('Index','Area','Digest')};fields.update(Columns='[L;]',LayoutDigest='"'+'f'*64+'"',Min='0L',Max='0L',Origin='0L')
        accepted_stages.append('{'+','.join(k+':'+v for k,v in fields.items())+'}')
    original['Stages']='['+','.join(accepted_stages)+']'
    data['acceptedProject']='{'+','.join(k+':'+v for k,v in original.items())+'}'
    counts={C:0,H:0};stock={C:0,H:0};chest=352;sequence=0;cell=0
    def snbt(values):return '{'+','.join(key+':'+value for key,value in sorted(values.items()))+'}'
    associations={'SiegeWholePerimeter':'{Version:1,Project:'+tag_uuid(P)+',Generation:1L,Hash:"'+'a'*64+'",Core:"team:qa",LedgerGeneration:'+tag_uuid(G)+'}',
        'SiegeProtectedAreaReceipt':tag_uuid(A),'SiegeProtectedLedgerGeneration':tag_uuid(G),
        'SiegePlayerFortificationAreaId':tag_uuid(A),'SiegePlayerFortificationOwner':tag_uuid(O),'SiegePlayerFortificationPos':'123L'}
    retained={'SiegeProtectedHandLifecycle':data['originalHelperHandReceipt']}
    raw_persistent=[{**associations,**retained},{**associations,**retained,VERIFY.REBINDS:'1'},{**retained,VERIFY.REBINDS:'2'}]
    restored_persistent=[{**associations,**retained,VERIFY.REBINDS:'1'},
        {**({} if immediate_cleanup else associations),**retained,VERIFY.REBINDS:'2'}, {**retained,VERIFY.REBINDS:'3'}]
    def loaded_inventory(values):
        value=inv();value['persistent']=snbt(values);value['slots'][5]='{Count:63b,id:"minecraft:cobblestone"}';value['main']=value['slots'][5];return value
    def census():return dict(cobble=dict(built=sum(counts.values()),chest=chest,workerCustody=sum(stock.values()),owner=0,loose=0,total=352),
                            oak=dict(built=0,chest=220,workerCustody=0,owner=0,loose=0,total=220))
    def proof(resolved,pending=False):
        return dict(state='DETACHED_RESOLVED' if resolved else 'STALE_UNRESOLVED',helper=H,owner=O,project=P,generation=1,
          manifestHash='a'*64,ledgerGeneration=G,originalHandReceipt=R,handArea=A,cleanupAreas=[A],resolved=resolved,
          pendingHand=pending,rebinds=1,core='team:qa',coordinator=C,terminal=copy.deepcopy(summary),
          member=dict(admittedStage=0,active=False,ready=True,withdrawn=False,retiredStage=0 if resolved else -1,
            suspended=not resolved,rejoinStage=-1,grantedStages=[0],legacyEnd=-1,resolved=resolved,destroyed=False),
          unresolvedProject='' if resolved else P,admissionArea=A,reservationsAbsent=True,destructionAbsent=True,
          originalHandExact=True,frozenPersistentExact=True,association=dict(wholeSelector=not resolved,protectedChild=not resolved,
            protectedGeneration=not resolved,nativeArea=not resolved,nativeOwner=not resolved,currentArea=''))
    def event(kind,tick,boundary=False,suspended=False,terminal=False,resolved=False,**extra):
        nonlocal sequence
        sequence+=1;value=dict(sequence=sequence,kind=kind,gameTime=tick)
        if boundary:
            value.update(placementCounts=counts.copy(),pendingTargets=572-sum(counts.values()),ownerMode='non-op Survival',materials=census(),
               workers={actor:dict(dimension='minecraft:overworld',removed=suspended and actor==H,inventory=inv(),handPending=False,
                   mainAliasesSlot5=True,nativeState='IDLE',entityTick=tick,handRebindCount=0) for actor in (C,H)},claims=[])
            if not terminal:value.update(projectState='RUNNING',stage=0,helperSuspended=suspended,helperActive=not suspended,helperGranted=not suspended,helperRejoining=False)
            else:value.update(terminalReceipt=terminal_tag(),stock=freeze_stock(),targetStates=freeze_world(),terminalProofState='DETACHED_RESOLVED' if resolved else 'STALE_UNRESOLVED',
                 terminalProof=proof(resolved),terminalReservationsAbsent=True,terminalMarkersAbsent=True,helperUnresolved='null' if resolved else P)
        value.update(extra);data['events'].append(value);return value
    def freeze_stock():return {'chest':[chest,220],C:[stock[C],0],H:[stock[H],0]}
    def freeze_world():return {str(i):'Block{minecraft:cobblestone}' if i<sum(counts.values()) else 'Block{minecraft:air}' for i in range(572)}
    def work(actor,supply=False):
        nonlocal sequence,cell,chest
        before=[stock[actor],0,chest,220];sequence+=1
        if supply:stock[actor]+=64;chest-=64;cells=[]
        else:stock[actor]-=1;counts[actor]+=1;cells=[cell];cell+=1
        data['nativeWork'].append(dict(sequence=sequence,worker=actor,area=A,kind='native-supply' if supply else 'native-placement',gameTime=sequence,
            cells=cells,before=before,after=[stock[actor],0,chest,220],placedCobble=len(cells),placedOak=0,authorized=True,
            nativeState='TAKE_NEEDED_ITEMS' if supply else 'PLACE_BLOCKS'))
    def join(disk,dimension):
        nonlocal sequence
        index=len(data['nativeWorkerJoins']);raw=loaded_inventory(raw_persistent[index]);sequence+=1
        data['nativeWorkerJoins'].append(dict(sequence=sequence,worker=H,loadedFromDisk=disk,dimension=dimension,inventory=raw))
        armed=loaded_inventory({**raw_persistent[index],VERIFY.HAND:'1b'})
        event('load-mirror-armed',sequence,worker=H,raw=raw,armed=armed,split=True)
        event('load-mirror-restored',sequence,worker=H,before=armed,after=loaded_inventory(restored_persistent[index]),split=True,mainAliasesSlot5=True,rebindsBefore=index,rebindsAfter=index+1)
    def bracket(actor,tick):
        value=[stock[actor],0,chest,220]
        event('terminal-native-bracket',tick,worker=actor,authorized=False,changedTargets=0,targetStatesUnchanged=True,before=value,after=value.copy())
    event('commission',0,True);work(C,True);work(H,True);work(C);work(H);event('c-before-outbound',10,True)
    join(False,'minecraft:the_nether')
    event('ordinary-end-settled-custody',11,worker=H,phase='C_SETTLE',dimension='minecraft:the_nether',transferGameTime=10,nanoTime=110)
    event('c-outbound-ticket-released',11,phase='C_SETTLE',transferGameTime=10,settledGameTime=11,nanoTime=150)
    event('c-disk-unloaded',300,True,suspended=True);work(C);event('c-absent-progress',400,True,suspended=True)
    event('c-authoritative-canceled',411,placementCounts=counts.copy(),pendingTargets=572-sum(counts.values()),materials=census(),source='compact-terminal',authority=terminal_tag(),stock=freeze_stock(),targetStates=freeze_world())
    bracket(C,411);event('c-compacted-while-absent',411,True,terminal=True)
    for tick in range(412,512):bracket(C,tick)
    event('c-canceled-offline-stable',511,True,terminal=True);join(True,'minecraft:the_nether')
    bracket(C,512);event('c-terminal-disk-join',512,True,terminal=True,resolved=immediate_cleanup)
    for tick in range(513,518):bracket(C,tick)
    event('c-cleanup-resolved-in-holding',517,True,terminal=True,resolved=True)
    bracket(C,518);event('c-before-detached-return',518,True,terminal=True,resolved=True);join(False,'minecraft:overworld')
    event('ordinary-end-settled-custody',519,worker=H,phase='C_RETURN_SETTLE',dimension='minecraft:overworld',transferGameTime=518,nanoTime=310)
    event('c-return-ticket-released',519,phase='C_RETURN_SETTLE',transferGameTime=518,settledGameTime=519,nanoTime=350)
    bracket(C,519);event('c-detached-return-settled',519,True,terminal=True,resolved=True)
    for tick in range(520,620):bracket(C,tick);bracket(H,tick)
    event('c-final-loaded-stable',619,True,terminal=True,resolved=True)
    data.update(placementCounts=counts,retainedPlacedBlocks=sum(counts.values()),finalMaterials=census(),finalizedOfflineInventory=loaded_inventory(raw_persistent[1]),terminalObservedBrackets=309,
        offlineRemovalProof=dict(reason='UNLOADED_TO_CHUNK',shouldDestroy=False,shouldSave=True,chunkAvailable=False,registered=False,allDimensionsAbsent=True,gameTime=300,unavailableAt=299))
    transfers=[]
    for index,(source,dest) in enumerate([('minecraft:overworld','minecraft:the_nether'),('minecraft:the_nether','minecraft:overworld')]):
        for offset,kind in enumerate(['transfer-before','join-high','join-low','dimension-source-leave','transfer-settled']):
            value=dict(ordinal=index*5+offset+1,kind=kind,uuid=H,owner=O,dimension=source if kind in ('transfer-before','dimension-source-leave') else dest,
                alive=True,removed=False,neededItems=0,followState=6,inventory=loaded_inventory(raw_persistent[0 if index==0 else 2]),nanoTime=100+index*200+offset)
            if kind in ('join-low','transfer-settled'):value['inventory']['persistent']=snbt({**raw_persistent[0 if index==0 else 2],VERIFY.HAND:'1b'})
            if kind.startswith('join-'):value.update(loadedFromDisk=False,canceled=False)
            if kind=='dimension-source-leave':value['sourceInventoryResampled']=False
            transfers.append(value)
    disk=[]
    for kind in ('join-high','join-low'):
        value=dict(kind=kind,uuid=H,owner=O,dimension='minecraft:the_nether',alive=True,removed=False,neededItems=0,followState=6,
                   inventory=loaded_inventory(raw_persistent[1]),loadedFromDisk=True,canceled=False,nanoTime=280+(kind=='join-low'))
        if kind=='join-low':value['inventory']['persistent']=snbt({**raw_persistent[1],VERIFY.HAND:'1b'})
        disk.append(value)
    transfers[5:5]=disk
    for index,event in enumerate(transfers):event['ordinal']=index+1
    data['activeHelperTransitEvents']=transfers
    key='minecraft:the_nether/siegeoverhaul/0/TickingEntities/'+T;baseline={'minecraft:overworld/Forced':'[L;]'};tickets=[]
    for cycle in range(2):
        for offset,kind in enumerate(['zero-owned-verified','add-pending','add-return','remove-pending','remove-return','zero-owned-verified']):
            census_value=baseline.copy()
            if kind in ('add-return','remove-pending'):census_value[key]='[I;0,0,0,7]'
            value=dict(ordinal=cycle*6+offset+1,kind=kind,owner=T,dimension='minecraft:the_nether',mod='siegeoverhaul',chunkX=0,chunkZ=0,ticking=True,
                census=census_value,nanoTime=([50,51,52,120,130,140] if cycle==0 else [250,251,252,320,330,340])[offset])
            if kind in ('add-return','remove-return'):value['nativeReturn']=True
            tickets.append(value)
    for index,event in enumerate(sorted(transfers+tickets,key=lambda e:e['nanoTime'])):event['ordinal']=index+1
    data.update(activeHelperTicketEvents=tickets,finalOwnedTicketCount=0)
    return data


def boundary(data,kind):return next(e for e in data['events'] if e['kind']==kind)

class CancelOracleTests(unittest.TestCase):
    def reject(self,mutate):
        data=fixture();mutate(data)
        with self.assertRaises((ValueError,KeyError,TypeError)):VERIFY.verify_report(data)
    def test_both_realistic_cleanup_timings_accepted(self):
        for immediate in (False,True):self.assertTrue(VERIFY.verify_report(fixture(immediate)))
    def test_baseline_mode_and_oracle_remain_separate(self):
        self.assertTrue(BASE.VERIFY.verify_report(BASE.fixture()))
        with self.assertRaises(ValueError):VERIFY.verify_report(BASE.fixture())
        with self.assertRaises(ValueError):BASE.VERIFY.verify_report(fixture())
    def test_required_boundaries_cannot_hide_cancellation_gap(self):
        for kind in ['c-authoritative-canceled','c-compacted-while-absent','c-terminal-disk-join','c-final-loaded-stable']:
            self.reject(lambda d:d['events'].remove(boundary(d,kind)))
    def test_boolean_or_changed_caps_and_fee_fail(self):
        for key,value in [('formatVersion',True),('totalLimitSeconds',601),('phaseLimitTicks',1201),('paidFee',128),('ownerMode','Creative')]:
            self.reject(lambda d:d.update({key:value}))
    def test_native_unload_finalization_and_offline_interval_required(self):
        for key,value in [('reason','CHANGED_DIMENSION'),('shouldSave',False),('shouldDestroy',True),('allDimensionsAbsent',False),('chunkAvailable',True),('registered',True),('unavailableAt',0)]:
            self.reject(lambda d:d['offlineRemovalProof'].update({key:value}))
        self.reject(lambda d:boundary(d,'c-disk-unloaded').update(gameTime=350))
        self.reject(lambda d:boundary(d,'c-absent-progress').update(placementCounts={C:1,H:1}))
    def test_early_resolution_absent_and_missing_resolution_loaded_fail(self):
        self.reject(lambda d:boundary(d,'c-compacted-while-absent').update(terminalProofState='DETACHED_RESOLVED'))
        self.reject(lambda d:boundary(d,'c-cleanup-resolved-in-holding').update(terminalProofState='STALE_UNRESOLVED'))
        self.reject(lambda d:boundary(d,'c-before-detached-return').update(helperUnresolved=P))
    def test_full_historical_identity_and_association_fail_closed(self):
        for key,value in [('helper',C),('owner',C),('project',O),('generation',2),('core','foreign'),('ledgerGeneration',O),('manifestHash','b'*64),('originalHandReceipt',G),('cleanupAreas',[T]),('admissionArea',T),('frozenPersistentExact',False)]:
            self.reject(lambda d:boundary(d,'c-compacted-while-absent')['terminalProof'].update({key:value}))
        for key,value in [('wholeSelector',False),('nativeOwner',False),('protectedGeneration',False),('currentArea',T)]:
            self.reject(lambda d:boundary(d,'c-compacted-while-absent')['terminalProof']['association'].update({key:value}))
        self.reject(lambda d:boundary(d,'c-final-loaded-stable')['terminalProof']['association'].update(nativeArea=True))
    def test_new_grant_admission_death_withdrawal_or_live_reservation_fail(self):
        for key,value in [('grantedStages',[0,1]),('admittedStage',1),('active',True),('withdrawn',True),('destroyed',True),('rejoinStage',0),('ready',False),('resolved',True)]:
            self.reject(lambda d:boundary(d,'c-compacted-while-absent')['terminalProof']['member'].update({key:value}))
        for key in ('terminalReservationsAbsent','terminalMarkersAbsent'):
            self.reject(lambda d:boundary(d,'c-final-loaded-stable').update({key:False}))
    def test_receipt_payment_and_stability_cannot_change(self):
        self.reject(lambda d:boundary(d,'c-final-loaded-stable').update(terminalReceipt='{Refund:1b}'))
        self.reject(lambda d:boundary(d,'c-final-loaded-stable')['terminalProof']['terminal'].update(payment='refunded'))
        self.reject(lambda d:d.update(canceledOfflineStableTicks=99))
        self.reject(lambda d:d.update(cancellationStableTicks=99))
        self.reject(lambda d:d['treasuryAccounting'].update(singleNegativeLedgerDelta=-128))
    def test_raw_terminal_and_original_hand_values_are_authenticated(self):
        self.reject(lambda d:d.update(originalHelperHandReceipt=d['originalHelperHandReceipt'].replace('Version:1','Version:2')))
        self.reject(lambda d:d.update(originalHelperHandReceiptId=T))
        for old,new in [('Debited:64','Debited:0'),('Targets:139','Targets:140'),('Creative:0b','Creative:1b'),('State:"CANCELED"','State:"RUNNING"')]:
            def mutate(d):
                for event in d['events']:
                    if 'terminalReceipt' in event:event['terminalReceipt']=event['terminalReceipt'].replace(old,new)
            self.reject(mutate)
    def test_stock_and_targets_frozen_from_authoritative_cancel(self):
        for kind in ('c-compacted-while-absent','c-terminal-disk-join','c-final-loaded-stable'):
            self.reject(lambda d:boundary(d,kind)['stock'][H].__setitem__(0,64))
            self.reject(lambda d:boundary(d,kind)['targetStates'].__setitem__('571','Block{minecraft:oak_planks}'))
        self.reject(lambda d:boundary(d,'c-final-loaded-stable')['materials']['cobble'].update(workerCustody=126,chest=223))
    def test_continuous_postcancel_observer_rejects_conserved_supply_or_placement(self):
        for change in ({'authorized':True},{'changedTargets':1},{'targetStatesUnchanged':False},{'after':[64,0,223,220]}):
            self.reject(lambda d:boundary(d,'terminal-native-bracket').update(change))
        self.reject(lambda d:d['events'].remove(boundary(d,'terminal-native-bracket')))
        self.reject(lambda d:d['nativeWork'][-1].update(sequence=boundary(d,'c-authoritative-canceled')['sequence']+1))
    def test_exact_three_admissions_and_only_hand_arm(self):
        self.reject(lambda d:d['nativeWorkerJoins'].pop())
        self.reject(lambda d:d['nativeWorkerJoins'][1].update(worker=C))
        self.reject(lambda d:d['nativeWorkerJoins'][1].update(loadedFromDisk=False))
        self.reject(lambda d:d['nativeWorkerJoins'][1].update(dimension='minecraft:overworld'))
        self.reject(lambda d:d['nativeWorkerJoins'][1]['inventory'].update(persistent='{Foreign:1b}'))
        self.reject(lambda d:d['activeHelperTransitEvents'][2].update(canceled=True))
        self.reject(lambda d:d['activeHelperTransitEvents'][5].update(canceled=True))
        self.reject(lambda d:d['activeHelperTransitEvents'][5].update(loadedFromDisk=False))
        self.reject(lambda d:d['activeHelperTransitEvents'][5].update(owner=C))
        for persistent in ('{}','{SiegeProtectedHandRefreshPending:1b,Extra:1b}'):
            self.reject(lambda d:d['activeHelperTransitEvents'][2]['inventory'].update(persistent=persistent))
            self.reject(lambda d:boundary(d,'load-mirror-armed')['armed'].update(persistent=persistent))
    def test_mirror_requires_exact_values_real_pair_identity_and_rebind(self):
        self.reject(lambda d:d['events'].remove(boundary(d,'load-mirror-armed')))
        self.reject(lambda d:boundary(d,'load-mirror-restored').update(mainAliasesSlot5=False))
        self.reject(lambda d:boundary(d,'load-mirror-restored').update(rebindsAfter=0))
        self.reject(lambda d:boundary(d,'load-mirror-armed')['armed']['slots'].__setitem__(0,'{Count:1b}'))
    def test_exact_two_transfers_tickets_and_later_end_sequence(self):
        self.reject(lambda d:d['activeHelperTransitEvents'].pop())
        self.reject(lambda d:d['activeHelperTicketEvents'].pop(2))
        self.reject(lambda d:d['activeHelperTicketEvents'][3].update(nanoTime=109))
        self.reject(lambda d:boundary(d,'c-return-ticket-released').update(settledGameTime=518))
        self.reject(lambda d:d.update(finalOwnedTicketCount=1))
    def test_observer_gap_or_fabricated_ticks_and_custody_are_rejected(self):
        def gap(d):
            cut=boundary(d,'c-detached-return-settled')['sequence']
            d['events']=[e for e in d['events'] if e['kind']!='terminal-native-bracket' or e['sequence']>cut]
            d['terminalObservedBrackets']=sum(e['kind']=='terminal-native-bracket' for e in d['events'])
        self.reject(gap)
        self.reject(lambda d:[e.update(gameTime=411) for e in d['events'] if e['kind']=='terminal-native-bracket'])
        self.reject(lambda d:boundary(d,'terminal-native-bracket').update(before=[572]*4,after=[572]*4))
    def test_restoration_must_preserve_exact_cargo_and_provenance(self):
        for index in (0,1,2):
            def cargo(d):
                rows=[e for e in d['events'] if e['kind']=='load-mirror-restored'];rows[index]['after']['slots'][0]='{id:"minecraft:cobblestone",Count:64b}'
            def foreign(d):
                rows=[e for e in d['events'] if e['kind']=='load-mirror-restored'];rows[index]['after']['persistent']='{ForeignProject:1b}'
            self.reject(cargo);self.reject(foreign)
    def test_authoritative_body_and_original_paid_identity_are_required(self):
        self.reject(lambda d:boundary(d,'c-authoritative-canceled').pop('authority'))
        self.reject(lambda d:boundary(d,'c-authoritative-canceled').update(authority='{State:"RUNNING"}'))
        self.reject(lambda d:boundary(d,'c-final-loaded-stable').update(projectState='RUNNING'))
        self.reject(lambda d:d.update(acceptedProject=d['acceptedProject'].replace('Price:64','Price:128')))
        value=fixture();body=VERIFY.compound(value['acceptedProject']);body.update(State='"CANCELED"',Revision='3L',Blocker='"Owner canceled"')
        boundary(value,'c-authoritative-canceled').update(source='active-canceled',authority='{'+','.join(k+':'+v for k,v in body.items())+'}')
        self.assertTrue(VERIFY.verify_report(value))
    def test_original_paid_stage_sequence_cannot_disagree_with_terminal(self):
        def change(d):
            original=VERIFY.compound(d['acceptedProject']);stages=VERIFY.compound_list(original['Stages']);stages[0]['Digest']='"'+'f'*64+'"'
            original['Stages']='['+','.join('{'+','.join(k+':'+v for k,v in stage.items())+'}' for stage in stages)+']'
            d['acceptedProject']='{'+','.join(k+':'+v for k,v in original.items())+'}'
        self.reject(change)
        self.reject(lambda d:d.update(acceptedProject=d['acceptedProject'].replace('Index:0','Index:1',1)))
    def test_terminal_faction_and_seal_are_source_authenticated(self):
        def mutate(d):
            for e in d['events']:
                if 'terminalReceipt' in e:e['terminalReceipt']=e['terminalReceipt'].replace('Faction:"qa"','Faction:"unrelated"')
        self.reject(mutate)
        def seal(d):
            for e in d['events']:
                if 'terminalReceipt' in e:
                    fields=VERIFY.compound(e['terminalReceipt']);fields['ReceiptHash']='"'+'0'*64+'"'
                    e['terminalReceipt']='{'+','.join(k+':'+v for k,v in fields.items())+'}'
        self.reject(seal)
    def test_shared_utility_ordinals_and_ticket_time_order_are_exact(self):
        self.reject(lambda d:d['activeHelperTransitEvents'][0].update(ordinal=d['activeHelperTicketEvents'][0]['ordinal']))
        self.reject(lambda d:[e.update(nanoTime=60+e['ordinal']) for e in d['activeHelperTicketEvents'][6:9]])
        self.reject(lambda d:d['activeHelperTransitEvents'][5].update(nanoTime=100))
        self.reject(lambda d:d['activeHelperTicketEvents'][8].pop('nanoTime'))
        self.reject(lambda d:d['activeHelperTransitEvents'][5].pop('nanoTime'))
    def test_snbt_parser_rejects_duplicates_and_malformed_deltas(self):
        self.assertTrue(VERIFY.only_hand_arm('{A:{Nested:[I;1,2]},B:"comma,quote\\\""}','{A:{Nested:[I;1,2]},B:"comma,quote\\\"",SiegeProtectedHandRefreshPending:1b}'))
        for text in ('{A:1,A:2}','{A:{broken]}','{A:"unterminated}','{A:}'):
            with self.assertRaises(ValueError):VERIFY.compound(text)


class CancelSourceContracts(unittest.TestCase):
    def text(self,path):return (ROOT/path).read_text()
    def test_case_is_separate_same_repo_alpha_opt_in(self):
        workflow=self.text('.github/workflows/native-absent-helper-cancel-qa.yml')
        for value in ('native-absent-helper-cancel-qa','github.event.pull_request.number == 295','contents: read','persist-credentials: false',
                      '-PnativeActiveHelperCase=cancel-while-absent','scripts/verify-native-absent-helper-cancel.py','timeout --signal=KILL 600s'):
            self.assertIn(value,workflow)
        self.assertNotIn('workflow_dispatch:',workflow);self.assertNotIn('pull_request_target:',workflow)
        self.assertEqual(workflow.count('-PnativeActiveHelperCase=cancel-while-absent'),2)
        self.assertLess(workflow.index('scripts/verify-native-helper-api.py'),workflow.index(' runClient'))
        gradle=self.text('build.gradle');self.assertIn("getOrElse('same-stage-disk')",gradle)
        self.assertIn("activeHelperCase in ['same-stage-disk', 'cancel-while-absent', 'later-stage-disk']",gradle)
    def test_both_terminal_eligibility_and_pending_census_use_same_proof(self):
        java=self.text('src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperQa.java')
        self.assertEqual(java.count('handProvenance(worker, ledger)'),2)
        hand=java[java.index('private static boolean handProvenance'):java.index('/** Called before every later native')]
        for text in ('CANCELED_ABSENT','worker.getUUID().equals(helper)','terminal(projectId) != null','terminalProof(worker).valid()',
                     'ProtectedBuilderHandLifecycle.matches','NativeConstructionGuard.validHandReceipt'):self.assertIn(text,hand)
    def test_observer_survives_compaction_and_preserves_prior_receipts(self):
        java=self.text('src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperQa.java')
        living=java[java.index('public static void living('):java.index('private static void flush(')]
        self.assertLess(living.index('flush(level)'),living.index('observeCanceledAuthority'))
        self.assertIn('!CANCELED_ABSENT',living);self.assertIn('PerimeterProject.State.RUNNING',living)
        self.assertIn('before.receiptArea()',java);self.assertIn('before.canceledTargets()',java)
        self.assertIn('CanceledObservation.verify',java)
    def test_only_case_c_routes_new_phases_and_does_not_reopen(self):
        java=self.text('src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperQa.java')
        case=java[java.index('private static void stepCanceledAbsent('):java.index('private static void requireSettledNow(')]
        self.assertNotIn('RESTART',case);self.assertNotIn('reconcileWorker(',case);self.assertNotIn('setItem(',case)
        self.assertEqual(case.count('transfer(WORKERS.get(helper)'),2)
        self.assertEqual(case.count('releaseHoldingTicket(server)'),2)
        self.assertIn('terminalOffline.equals',java);self.assertIn('server.getAllLevels()',case)
        self.assertIn('tick - finalizedOfflineAt < 100',case)

if __name__=='__main__':unittest.main()
