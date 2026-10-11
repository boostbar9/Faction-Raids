#!/usr/bin/env python3
"""Strict, separate oracle for cancellation while the original helper is disk-unloaded."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import uuid

ROOT = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('active_helper_baseline', ROOT/'verify-native-active-helper.py')
BASE = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(BASE)
require, integer, uid, inventory, cargo, materials, ordered = (BASE.require, BASE.integer, BASE.uid, BASE.inventory, BASE.cargo, BASE.materials, BASE.ordered)
HAND = 'SiegeProtectedHandRefreshPending'
REBINDS = 'SiegeProtectedHandRebinds'
ASSOCIATIONS = {'SiegeWholePerimeter','SiegeProtectedAreaReceipt','SiegeProtectedLedgerGeneration','SiegePlayerFortificationAreaId','SiegePlayerFortificationOwner','SiegePlayerFortificationPos','SiegePlayerFortificationMisses','SiegeWallGroundRecovery'}


def compound(text):
    """Bounded top-level canonical SNBT comparison; nested values remain exact strings."""
    require(isinstance(text, str) and 2 <= len(text) <= 256*1024 and text[0]=='{' and text[-1]=='}', 'Malformed persistent compound')
    chunks=[]; start=1; stack=[]; quote=False; escaped=False
    for index,char in enumerate(text[1:-1],1):
        if quote:
            if escaped: escaped=False
            elif char=='\\': escaped=True
            elif char=='"': quote=False
        elif char=='"': quote=True
        elif char in '{[': stack.append(char); require(len(stack)<=64,'Persistent nesting exceeds bound')
        elif char in '}]': require(stack and stack.pop()==('{' if char=='}' else '['),'Malformed persistent nesting')
        elif char==',' and not stack: chunks.append(text[start:index]); start=index+1
    require(not quote and not escaped and not stack,'Unclosed persistent value')
    chunks.append(text[start:-1]); result={}
    if chunks==['']: return result
    for entry in chunks:
        key,sep,value=entry.partition(':')
        require(sep and re.fullmatch(r'[A-Za-z_][A-Za-z_0-9]*',key) and value and key not in result,'Malformed/duplicate persistent field')
        result[key]=value
    return result


def compound_list(text):
    require(isinstance(text,str) and text.startswith('[') and text.endswith(']') and len(text)<=256*1024,'Malformed compound list')
    if text=='[]':return []
    # Reuse the canonical compound scanner with generated top-level keys.
    result=[];start=1;depth=0;quote=False;escape=False
    for index,char in enumerate(text[1:-1],1):
        if quote:
            if escape:escape=False
            elif char=='\\':escape=True
            elif char=='"':quote=False
        elif char=='"':quote=True
        elif char in '{[':depth+=1
        elif char in '}]':depth-=1
        elif char==',' and depth==0:result.append(compound(text[start:index]));start=index+1
        require(0<=depth<=64,'Malformed compound-list nesting')
    require(not quote and depth==0,'Unclosed compound list');result.append(compound(text[start:-1]));require(len(result)<=256,'Unbounded stages')
    return result


def nbt_uuid(text):
    require(isinstance(text,str) and re.fullmatch(r'\[I;[+-]?\d+(?:,[+-]?\d+){3}\]',text),'Malformed UUID tag')
    parts=[int(value) for value in text[3:-1].split(',')]
    require(all(-(2**31)<=value<2**31 for value in parts),'UUID integer outside bound')
    return str(uuid.UUID(int=sum((value & 0xffffffff) << (96-32*index) for index,value in enumerate(parts))))


def verify_original_hand(text,receipt,helper,owner,area,generation):
    value=compound(text)
    require(set(value)=={'Version','Receipt','Builder','Owner','Area','Generation'} and value['Version']=='1','Malformed original hand receipt')
    for key,expected in [('Receipt',receipt),('Builder',helper),('Owner',owner),('Area',area),('Generation',generation)]:
        require(nbt_uuid(value[key])==expected,'Original hand identity differs')


def terminal_seal(value):
    lines=['perimeter-terminal-v1',nbt_uuid(value['Project']),value['Generation'][:-1],json.loads(value['Hash']),value['Revision'][:-1],
           json.loads(value['State']),nbt_uuid(value['Owner']),nbt_uuid(value['Builder']),nbt_uuid(value['LedgerGeneration']),
           json.loads(value['ReservationRetired']),json.loads(value['BuilderDetached']),json.loads(value['StagesCleaned']),
           json.loads(value['CoreKey']),json.loads(value['Faction']),':'.join(value[key] for key in ('FeeVersion','Price','Active','Verified','ClaimChunks'))]
    paid=compound(value['Payment'])
    lines.append('paid:'+':'.join((nbt_uuid(paid['Project']),paid['Generation'][:-1],json.loads(paid['Hash']),paid['FeeVersion'],paid['Price'],paid['Debited'],'false' if paid['Creative']=='0b' else 'true')))
    lines.extend(':'.join((stage['Index'],nbt_uuid(stage['Area']),json.loads(stage['Digest']),stage['Targets'])) for stage in compound_list(value['Stages']))
    return hashlib.sha256(('\n'.join(lines)+'\n').encode()).hexdigest()


def verify_canceled_authority(data,canceled,terminal):
    original=compound(data.get('acceptedProject'));raw=compound(terminal)
    for key in ('Project','Generation','Hash','Owner','Builder','CoreKey','Faction','FeeVersion','Price','Payment'):
        require(original.get(key)==raw.get(key),'Original accepted paid identity differs from terminal')
    require(original.get('State')=='"RUNNING"' and original.get('Active')=='0' and original.get('Receipts')=='[]'
            and re.fullmatch(r'[1-9]\d*L',original.get('Revision','')),'Original accepted project is not unfinished RUNNING')
    original_stages=compound_list(original.get('Stages'));terminal_stages=compound_list(raw['Stages'])
    require(len(original_stages)==len(terminal_stages),'Original paid stage count differs')
    for paid_stage,terminal_stage in zip(original_stages,terminal_stages):
        require(all(paid_stage.get(key)==terminal_stage[key] for key in ('Index','Area','Digest')),'Original paid stage identity differs from terminal')
    actual=compound(canceled.get('authority'))
    if canceled['source']=='compact-terminal': require(actual==raw,'Authoritative compact cancellation differs from sealed terminal')
    else:
        require(set(actual)==set(original) and actual.get('State')=='"CANCELED"' and actual.get('Active')=='0' and actual.get('Receipts')=='[]'
                and re.fullmatch(r'[1-9]\d*L',actual.get('Revision','')) and int(actual['Revision'][:-1])>int(original['Revision'][:-1]),'Invalid authoritative active cancellation')
        require({k:v for k,v in actual.items() if k not in ('State','Revision','Blocker')}=={k:v for k,v in original.items() if k not in ('State','Revision','Blocker')},'Cancellation changed immutable original project')


def verify_frozen_associations(data):
    frozen=compound(inventory(data['finalizedOfflineInventory'])['persistent'])
    require(compound(frozen.get('SiegeProtectedHandLifecycle'))==compound(data['originalHelperHandReceipt']),'Frozen hand provenance differs')
    for key,expected in [('SiegeProtectedAreaReceipt',data['firstStageArea']),('SiegeProtectedLedgerGeneration',data['ledgerGeneration']),
                         ('SiegePlayerFortificationAreaId',data['firstStageArea']),('SiegePlayerFortificationOwner',data['owner'])]:
        require(nbt_uuid(frozen.get(key))==expected,'Frozen stale association is foreign or missing')
    link=compound(frozen.get('SiegeWholePerimeter'))
    require(set(link)=={'Version','Project','Generation','Hash','Core','LedgerGeneration'} and link['Version']=='1'
            and nbt_uuid(link['Project'])==data['projectId'] and link['Generation']==str(data['projectGeneration'])+'L'
            and json.loads(link['Hash'])==data['manifestHash'] and json.loads(link['Core'])==data['coreKey']
            and nbt_uuid(link['LedgerGeneration'])==data['ledgerGeneration'],'Frozen whole-project selector differs')
    return frozen


def verify_raw_terminal(text,project,owner,coordinator,generation,ledger,core,manifest,summary):
    value=compound(text)
    require(set(value)=={'Version','Project','Generation','Hash','Owner','Builder','CoreKey','Faction','Revision','State','Active','Verified','ClaimChunks',
        'LedgerGeneration','FeeVersion','Price','ReceiptHash','ReservationRetired','BuilderDetached','StagesCleaned','Stages','Payment'},'Malformed raw terminal schema')
    for key,expected in [('Project',project),('Owner',owner),('Builder',coordinator),('LedgerGeneration',ledger)]:require(nbt_uuid(value[key])==expected,'Raw terminal identity differs')
    for key,expected in [('Hash',manifest),('CoreKey',core),('State','CANCELED')]:require(json.loads(value[key])==expected,'Raw terminal summary differs')
    require(value['Version']=='1' and value['Generation']==str(generation)+'L' and value['Active']==value['Verified']=='0'
            and value['ClaimChunks']=='1' and value['Price']=='64' and value['FeeVersion']==str(summary['feeVersion']),'Changed raw terminal paid counters')
    for key in ('ReceiptHash','ReservationRetired','BuilderDetached','StagesCleaned'):require(re.fullmatch('[0-9a-f]{64}',json.loads(value[key])),'Missing sealed terminal cleanup digest')
    stages=compound_list(value['Stages']);require(len(stages)==len(summary['stages']),'Raw terminal stages differ')
    for actual,expected in zip(stages,summary['stages']):
        index,area,digest,targets=expected.split(':')
        require(set(actual)=={'Index','Area','Digest','Targets'} and actual['Index']==index and nbt_uuid(actual['Area'])==area
                and json.loads(actual['Digest'])==digest and actual['Targets']==targets,'Raw terminal stage identity differs')
    require(re.fullmatch(r'[1-9]\d*L',value['Revision']),'Missing terminal revision')
    require(core=='team:'+json.loads(value['Faction']) and json.loads(value['ReceiptHash'])==terminal_seal(value),'Changed faction/core or terminal seal')
    paid=compound(value['Payment']);require(set(paid)=={'Project','Generation','Hash','FeeVersion','Price','Debited','Creative'},'Malformed payment receipt')
    require(nbt_uuid(paid['Project'])==project and paid['Generation']==value['Generation'] and paid['Hash']==value['Hash']
            and paid['FeeVersion']==value['FeeVersion'] and paid['Price']==paid['Debited']=='64' and paid['Creative']=='0b','Unpaid/refunded/foreign terminal receipt')


def only_hand_arm(before, after):
    first,second=compound(before),compound(after)
    return HAND not in first and second=={**first,HAND:'1b'}


def verify_transfers(data, helper, owner):
    events = data.get('activeHelperTransitEvents'); ordered(events, 'ordinal', 128)
    before = [e for e in events if e.get('kind') == 'transfer-before']
    require(len(before) == 2, 'Exactly two supported native transfers required')
    expected = [('minecraft:overworld', 'minecraft:the_nether'), ('minecraft:the_nether', 'minecraft:overworld')]
    allowed = {'transfer-before', 'join-high', 'join-low', 'dimension-source-leave', 'transfer-settled', 'leave-observed-not-final-removal'}
    require(all(e.get('kind') in allowed and e.get('uuid') == helper for e in events), 'Unexpected transfer/failure/actor evidence')
    highs=[e for e in events if e['kind']=='join-high']; lows=[e for e in events if e['kind']=='join-low']
    joins=data['nativeWorkerJoins']; arms=[e for e in data['events'] if e.get('kind')=='load-mirror-armed']
    require(len(highs)==len(lows)==len(joins)==len(arms)==3,'Extra/missing utility admission')
    for index,(high,low,join,arm) in enumerate(zip(highs,lows,joins,arms)):
        require(high['ordinal']<low['ordinal'] and (index==2 or low['ordinal']<highs[index+1]['ordinal'])
                and high.get('loadedFromDisk') is join.get('loadedFromDisk') and low.get('loadedFromDisk') is join.get('loadedFromDisk')
                and high.get('canceled') is False and low.get('canceled') is False
                and high.get('dimension')==low.get('dimension')==join.get('dimension'),'Canceled/reordered/wrong utility admission')
        require(high.get('inventory')==join['inventory'] and low.get('inventory')==arm['armed']
                and inventory(high['inventory'])['beforeItemSlot']==-1 and only_hand_arm(high['inventory']['persistent'],low['inventory']['persistent']),
                'Utility/raw/LOWEST mirror evidence disagrees')
        for event in (high,low):require(event.get('owner')==owner and event.get('followState')==6 and event.get('alive') is True
                and event.get('removed') is False and event.get('neededItems')==0,'Disk/transfer admission lost closed exact ownership')
    for index, start in enumerate(before):
        end = before[index+1]['ordinal'] if index+1 < len(before) else 2**63
        group = [e for e in events if start['ordinal'] <= e['ordinal'] < end]
        # A real disk join is separate from native non-disk transfer admission.
        transfer = [e for e in group if e['kind'] in {'transfer-before', 'dimension-source-leave', 'transfer-settled'}
                    or e['kind'] in {'join-high', 'join-low'} and e.get('loadedFromDisk') is False]
        require([e['kind'] for e in transfer] == ['transfer-before', 'join-high', 'join-low', 'dimension-source-leave', 'transfer-settled'], 'Missing/reordered native callback custody')
        first, high, low, leave, settled = transfer
        require(first['dimension'] == expected[index][0] and high['dimension'] == low['dimension'] == settled['dimension'] == expected[index][1], 'Wrong transfer route')
        for event in (first, high, low, settled):
            require(event.get('owner') == owner and event.get('followState') == 6 and event.get('alive') is True
                    and event.get('removed') is False and event.get('neededItems') == 0, 'Transfer lost ordinary live owner/closed request state')
            require(inventory(event.get('inventory'))['beforeItemSlot'] == -1, 'Noncanonical borrowed hand at transfer')
        require(high.get('canceled') is False and low.get('canceled') is False, 'Canceled transfer admission')
        require(first['inventory'] == high['inventory'] and cargo(first['inventory']) == cargo(low['inventory'])
                and low['inventory'] == settled['inventory'], 'Native transfer changed cargo or raw/settled receipts')
        require(only_hand_arm(first['inventory']['persistent'], low['inventory']['persistent']), 'Transfer admission changed more than exact hand arm')
        require(leave.get('sourceInventoryResampled') is False and leave.get('dimension') == expected[index][0], 'Removed source was counted as custody')


def verify_restored_custody(before,after,split,old_rebinds,new_rebinds,index):
    require(cargo(before)==cargo(after),'Load restoration changed complete native cargo')
    expected=compound(before['persistent']);actual=compound(after['persistent'])
    require(expected.pop(HAND,None)=='1b' and HAND not in actual,'Restoration did not close exact pending arm')
    expected.pop('SiegeConstructionPause',None)
    require(new_rebinds==old_rebinds+int(split),'Unexpected restoration rebind delta')
    require(int(expected.get(REBINDS,'0'))==old_rebinds,'Saved rebind counter differs')
    if split:expected[REBINDS]=str(new_rebinds)
    candidates=[expected]
    if index==1:
        # Only this authenticated terminal disk load may finish exact same-tick cleanup.
        candidates.append({key:value for key,value in expected.items() if key not in ASSOCIATIONS})
    require(actual in candidates,'Restoration changed foreign persistent data or partial terminal association')
    if index==2:require(not set(actual).intersection(ASSOCIATIONS),'Detached return regained work association')


def verify_mirrors(data):
    events, joins = data['events'], data['nativeWorkerJoins']
    armed = [event for event in events if event.get('kind') == 'load-mirror-armed']
    restored = [event for event in events if event.get('kind') == 'load-mirror-restored']
    require(len(armed) == len(restored) == len(joins) == 3, 'Missing observed native load-mirror lifecycle')
    for actor in data['workers']:
        admissions = [event for event in joins if event['worker'] == actor]
        starts = [event for event in armed if event.get('worker') == actor]
        ends = [event for event in restored if event.get('worker') == actor]
        require(len(admissions) == len(starts) == len(ends), 'Wrong actor mirror custody')
        for index, (join, before, after) in enumerate(zip(admissions, starts, ends)):
            require(join['sequence'] < before['sequence'] < after['sequence']
                    and (index + 1 == len(starts) or after['sequence'] < starts[index+1]['sequence']), 'Overlapping or reordered mirror custody')
            require(inventory(before.get('raw')) == inventory(join.get('inventory'))
                    and cargo(before['raw']) == cargo(before.get('armed'))
                    and inventory(after.get('before')) == before['armed'], 'Load-mirror comparison lost raw custody')
            require(type(before.get('split')) is bool and after.get('split') is before['split']
                    and after.get('mainAliasesSlot5') is True, 'Production did not restore canonical hand identity')
            require(integer(after.get('rebindsAfter')) >= integer(after.get('rebindsBefore')) + int(before['split']), 'Missing ordinary production mirror rebind')
            inventory(after.get('after'))
            verify_restored_custody(before['armed'],after['after'],before['split'],integer(after['rebindsBefore']),integer(after['rebindsAfter']),index)
            require(only_hand_arm(before['raw']['persistent'], before['armed']['persistent']), 'Load admission changed more than exact hand arm')
    require(restored[0]['after']==data['finalizedOfflineInventory'],'Finalized offline custody changed after outbound restoration')
    detached=compound(restored[1]['after']['persistent'])
    for key in ASSOCIATIONS|{'SiegeConstructionPause'}:detached.pop(key,None)
    require(cargo(restored[1]['after'])==cargo(joins[2]['inventory']) and compound(joins[2]['inventory']['persistent'])==detached,
            'Detached return lost exact disk-restored custody or retained foreign association')


def verify_tickets(data):
    owner = uid(data.get('activeHelperTicketOwner')); require(owner not in data['workers'], 'Ticket owner aliases worker')
    values = data.get('activeHelperTicketEvents'); ordered(values, 'ordinal', 10000)
    key = 'minecraft:the_nether/siegeoverhaul/0/TickingEntities/' + owner
    allowed = {'add-pending', 'add-return', 'remove-pending', 'remove-return', 'zero-owned-verified'}
    state, baseline, cycles = 'zero', None, 0
    for event in values:
        require(event.get('kind') in allowed and event.get('owner') == owner and event.get('dimension') == 'minecraft:the_nether'
                and event.get('mod') == 'siegeoverhaul' and event.get('chunkX') == 0 and event.get('chunkZ') == 0
                and event.get('ticking') is True, 'Failed/unapproved persistent ticket event')
        census = event.get('census'); require(isinstance(census, dict), 'Missing exact ticket census')
        own = [k for k in census if k.endswith('/' + owner)]
        require(own in ([], [key]), 'Wrong/multiple owned ticket tuple')
        kind = event['kind']
        if kind == 'add-pending':
            require(state == 'zero' and not own, 'Add lacks absent preflight'); baseline = census; state = 'adding'
        elif kind == 'add-return':
            require(state == 'adding' and event.get('nativeReturn') is True and own == [key]
                    and {k:v for k,v in census.items() if k != key} == baseline, 'Partial/unmatched native ticket add'); state = 'held'
        elif kind == 'remove-pending':
            require(state == 'held' and own == [key] and {k:v for k,v in census.items() if k != key} == baseline, 'Removal changed unrelated census'); state = 'removing'
        elif kind == 'remove-return':
            require(state == 'removing' and event.get('nativeReturn') is True and not own and census == baseline, 'Unverified exact native removal'); state = 'zero'; cycles += 1
        else:
            require(state == 'zero' and not own, 'Zero-ticket claim while intent/tuple remains')
    require(state == 'zero' and cycles == 2 and data.get('finalOwnedTicketCount') == 0, 'Missing two exact add/remove cycles or final zero')



def verify_utility_chronology(data):
    tickets=data['activeHelperTicketEvents'];transfers=data['activeHelperTransitEvents'];combined=tickets+transfers
    require(len({e['ordinal'] for e in combined})==len(combined),'Aliased utility ticket/transit ordinals')
    for event in combined:
        if event['kind']!='dimension-source-leave':integer(event.get('nanoTime'),1)
    timed=[e for e in sorted(combined,key=lambda e:e['ordinal']) if 'nanoTime' in e]
    require(all(integer(b['nanoTime'],1)>integer(a['nanoTime'],1) for a,b in zip(timed,timed[1:])),'Utility chronology reversed or collapsed')
    adds=[e for e in tickets if e['kind']=='add-return'];removes=[e for e in tickets if e['kind']=='remove-return']
    calls=[e for e in transfers if e['kind']=='transfer-before'];disk=[e for e in transfers if e['kind']=='join-high' and e.get('loadedFromDisk') is True]
    require(len(adds)==len(removes)==len(calls)==2 and len(disk)==1,'Wrong utility lifecycle closure')
    require(adds[0]['ordinal']<calls[0]['ordinal']<removes[0]['ordinal']<adds[1]['ordinal']<disk[0]['ordinal']<calls[1]['ordinal']<removes[1]['ordinal'],
            'Ticket add/load/transfer order differs from exact two cycles')


def verify_release_order(data, helper, first, returned):
    events=data['events']; tickets=data['activeHelperTicketEvents']; transfers=data['activeHelperTransitEvents']
    settled=[e for e in events if e.get('kind')=='ordinary-end-settled-custody']
    sync=[e for e in transfers if e.get('kind')=='transfer-settled']
    removes=[e for e in tickets if e['kind']=='remove-pending']; removed=[e for e in tickets if e['kind']=='remove-return']
    require(len(settled)==len(sync)==len(removes)==len(removed)==2,'Missing exact later-END custody/ticket boundaries')
    for index,(kind,before,phase,dimension) in enumerate([
            ('c-outbound-ticket-released',first,'C_SETTLE','minecraft:the_nether'),
            ('c-return-ticket-released',returned,'C_RETURN_SETTLE','minecraft:overworld')]):
        matches=[e for e in events if e.get('kind')==kind]; require(len(matches)==1,'Missing/duplicate settled release')
        release=matches[0]; end=settled[index]
        require(end.get('worker')==helper and end.get('phase')==release.get('phase')==phase and end.get('dimension')==dimension,
                'Wrong settled custody actor/phase/dimension')
        require(integer(before.get('gameTime'))==integer(end.get('transferGameTime'))==integer(release.get('transferGameTime'))
                < integer(end.get('gameTime'))==integer(release.get('settledGameTime'))==integer(release.get('gameTime')),
                'Same-tick or stale ticket-release proof')
        require(before['sequence']<end['sequence']<release['sequence'],'Release precedes later END')
        call_ns,end_ns,pending_ns,removed_ns,release_ns=[integer(e.get('nanoTime'),1) for e in (sync[index],end,removes[index],removed[index],release)]
        require(call_ns<end_ns<pending_ns<removed_ns<release_ns,'Holding ticket removed before mandatory settled custody')
        require(any(e['kind']=='zero-owned-verified' and removed_ns<integer(e.get('nanoTime'),1)<release_ns for e in tickets),
                'Ticket release lacks subsequent exact zero proof')


def verify_report(data):
    require(isinstance(data,dict) and type(data.get('formatVersion')) is int and data['formatVersion']==1 and data.get('mode')=='active-helper-cancel-while-absent'
            and data.get('status')=='passed' and data.get('phase')=='FINISHED','Canceled-absent scenario did not pass')
    require('failure' not in data and 'ticketCleanupFailure' not in data,'Failed fixture presented as accepted')
    for key in ('boundedScenarioVerified','canceledWhileAbsentVerified','helperDiskEvictionVerified','terminalCleanupOnlyReturnVerified'):
        require(data.get(key) is True,'Missing Case C proof: '+key)
    for key in ('sameStageReturnVerified','coordinatorFirstDiskOrderVerified','stageSkippedReturnVerified','wholeSiteEvictionVerified','wholePlanCompleted','nativeWorkerChunkLoading'):
        require(data.get(key) is False,'Unproved Case C coverage: '+key)
    require(data.get('postDiskHelperPlacementVerified') in (None,False),'Cleanup-only case claims post-disk work')
    for key,value in [('totalLimitSeconds',600),('phaseLimitTicks',1200),('evictionLimitTicks',1800),('finalizationLimitTicks',200),
                      ('stableGroundedEndTicks',5),('absenceObservationTicks',100),('paidFee',64)]:
        require(type(data.get(key)) is int and data[key]==value,'Changed fixed Case C bound: '+key)
    require(type(data.get('elapsedSeconds')) in (int,float) and 0<data['elapsedSeconds']<600 and data.get('ownerMode')=='non-op Survival','Invalid runtime/owner')
    ids=data.get('workers'); require(isinstance(ids,list) and len(ids)==len(set(ids))==2,'Wrong original actor count')
    coordinator,helper=[uid(x) for x in ids];owner=uid(data.get('owner'));area=uid(data.get('firstStageArea'))
    project=uid(data.get('projectId'));generation=uid(data.get('ledgerGeneration'))
    require(owner not in ids and re.fullmatch('[0-9a-f]{64}',data.get('manifestHash','')),'Invalid paid identity')
    require(data.get('syntheticPartition') is True and data.get('qaSectionTargetCap')==192
            and data.get('materialCounts')=={'minecraft:cobblestone':352,'minecraft:oak_planks':220},'Changed reviewed geometry/stock setup')
    targets=data.get('firstStageTargets'); require(isinstance(targets,list) and len(targets)==len(set(targets))==139,'Changed first section')
    target_materials=data.get('firstStageMaterials'); require(isinstance(target_materials,dict) and set(target_materials)=={str(x) for x in targets}
            and set(target_materials.values())<= {'minecraft:cobblestone','minecraft:oak_planks'},'Invalid target recipe')
    events,work,joins=data.get('events'),data.get('nativeWork'),data.get('nativeWorkerJoins')
    for items,cap in ((events,4096),(work,1024),(joins,16)): ordered(items,'sequence',cap)
    seq=[e['sequence'] for items in (events,work,joins) for e in items];require(len(seq)==len(set(seq)),'Aliased evidence sequence')
    def one(kind):
        matches=[e for e in events if e.get('kind')==kind];require(len(matches)==1,'Missing/duplicate Case C boundary: '+kind);return matches[0]
    kinds=['commission','c-before-outbound','c-disk-unloaded','c-absent-progress','c-authoritative-canceled','c-compacted-while-absent',
           'c-canceled-offline-stable','c-terminal-disk-join','c-cleanup-resolved-in-holding','c-before-detached-return','c-detached-return-settled','c-final-loaded-stable']
    bounds={k:one(k) for k in kinds};require([bounds[k]['sequence'] for k in kinds]==sorted(bounds[k]['sequence'] for k in kinds),'Case C boundaries reordered')
    canceled=bounds['c-authoritative-canceled'];counts={coordinator:0,helper:0};supplied=set();seen=set()
    for event in work:
        actor=event.get('worker');require(actor in ids and event.get('area')==area and event.get('authorized') is True
                and event['sequence']<canceled['sequence'],'Post-cancel/foreign native work')
        require(actor!=helper or event['sequence']<bounds['c-before-outbound']['sequence'],'Absent/returned canceled helper performed work')
        before,after=event.get('before'),event.get('after');require(isinstance(before,list) and isinstance(after,list) and len(before)==len(after)==4,'Missing finite debit')
        for number in before+after:integer(number,0,572)
        cells=event.get('cells');require(isinstance(cells,list) and len(cells)==len(set(cells)),'Invalid native target receipt')
        cb,oak=integer(event.get('placedCobble'),0,139),integer(event.get('placedOak'),0,139)
        if event.get('kind')=='native-placement':
            require(cells and set(cells)<=set(targets) and not seen.intersection(cells) and event.get('nativeState')=='PLACE_BLOCKS'
                    and cb==sum(target_materials[str(cell)]=='minecraft:cobblestone' for cell in cells) and cb+oak==len(cells)
                    and before[0]-after[0]==cb and before[1]-after[1]==oak and before[2:]==after[2:],'Unmatched/duplicate placement debit')
            seen.update(cells);counts[actor]+=len(cells)
        else:
            require(event.get('kind')=='native-supply' and not cells and cb==oak==0 and after[0]>=before[0] and after[1]>=before[1]
                    and after[:2]!=before[:2] and after[0]-before[0]==before[2]-after[2] and after[1]-before[1]==before[3]-after[3], 'Unmatched source withdrawal')
            supplied.add(actor)
    require(supplied==set(ids) and all(counts.values()) and data.get('placementCounts')==counts and data.get('retainedPlacedBlocks')==len(seen)<139,'Original contributions or unfinished retention missing')
    first=bounds['c-before-outbound']; require(all(first.get('placementCounts',{}).values())
            and {e['worker'] for e in work if e['kind']=='native-supply' and e['sequence']<first['sequence']}==set(ids),'Both original workers did not contribute/withdraw before absence')
    for kind,boundary in bounds.items():
        integer(boundary.get('gameTime'))
        calculated={actor:sum(len(e['cells']) for e in work if e['kind']=='native-placement' and e['worker']==actor and e['sequence']<boundary['sequence']) for actor in ids}
        require(boundary.get('placementCounts')==calculated and boundary.get('pendingTargets')==572-sum(calculated.values()),'Boundary progress changed')
        materials(boundary.get('materials'))
        for name,key in [('cobble','placedCobble'),('oak','placedOak')]:
            require(boundary['materials'][name]['built']==sum(e[key] for e in work if e['kind']=='native-placement' and e['sequence']<boundary['sequence']),'Built material/receipts differ')
        if kind=='c-authoritative-canceled': continue
        require(boundary.get('ownerMode')=='non-op Survival' and isinstance(boundary.get('workers'),dict) and set(boundary['workers'])==set(ids)
                and isinstance(boundary.get('claims'),list),'Missing exact boundary schema')
        for actor,value in boundary['workers'].items():
            inventory(value.get('inventory'));integer(value.get('entityTick'));integer(value.get('handRebindCount'))
            require(type(value.get('removed')) is bool and type(value.get('handPending')) is bool and type(value.get('mainAliasesSlot5')) is bool,'Malformed worker boundary')
        if boundary['sequence']<canceled['sequence']:
            require(boundary.get('projectState')=='RUNNING' and boundary.get('stage')==0,'Stage advanced before absent cancellation')
        else:
            require('projectState' not in boundary and 'stage' not in boundary,'Live project coexists with compact terminal')
            require(not boundary['claims'] and boundary.get('terminalReservationsAbsent') is True and boundary.get('terminalMarkersAbsent') is True,'Terminal retained authority or markers')
    proof=data.get('offlineRemovalProof');require(isinstance(proof,dict) and proof.get('reason')=='UNLOADED_TO_CHUNK' and proof.get('shouldDestroy') is False
            and proof.get('shouldSave') is True and proof.get('chunkAvailable') is False and proof.get('registered') is False and proof.get('allDimensionsAbsent') is True
            and 0<=integer(proof.get('gameTime'))-integer(proof.get('unavailableAt'))<=200,'Missing finalized disk absence')
    unloaded=bounds['c-disk-unloaded'];absent=bounds['c-absent-progress'];
    release=one('c-outbound-ticket-released');require(0 < proof['gameTime']-integer(release.get('gameTime'))<=1800,'Eviction outside declared released-ticket window')
    require(proof['gameTime']==unloaded['gameTime'] and absent['gameTime']-unloaded['gameTime']>=100
            and absent['placementCounts'][coordinator]>unloaded['placementCounts'][coordinator],'Missing coordinator work during genuine offline window')
    for b in (unloaded,absent):require(b.get('helperSuspended') is True and b.get('helperActive') is False and b.get('helperGranted') is False,'Offline helper retained live grant')
    require(len(joins)==3 and all(e.get('worker')==helper for e in joins) and [e.get('loadedFromDisk') for e in joins]==[False,True,False]
            and [e.get('dimension') for e in joins]==['minecraft:the_nether','minecraft:the_nether','minecraft:overworld'],'Wrong exact helper admission lifecycle')
    require(inventory(data.get('finalizedOfflineInventory'))==inventory(joins[1].get('inventory'))
            and bounds['c-canceled-offline-stable']['sequence']<joins[1]['sequence']<bounds['c-terminal-disk-join']['sequence'],'Premature or changed terminal disk admission')
    compact=bounds['c-compacted-while-absent'];stable=bounds['c-canceled-offline-stable'];returned=bounds['c-detached-return-settled'];final=bounds['c-final-loaded-stable']
    require(integer(data.get('canceledOfflineStableTicks'),100,1200)==stable['gameTime']-compact['gameTime']
            and integer(data.get('cancellationStableTicks'),100,1200)==final['gameTime']-returned['gameTime'],'Missing offline/final canceled stability')
    require(data.get('canceledAuthorityGameTime')==canceled['gameTime'] and canceled.get('source') in ('active-canceled','compact-terminal'),'Missing server-authoritative cancellation cut')
    terminal=compact.get('terminalReceipt');require(isinstance(terminal,str) and terminal,'Missing compact receipt')
    original_hand=data.get('originalHelperHandReceipt');require(isinstance(original_hand,str) and compound(original_hand),'Missing frozen hand receipt')
    hand_id=uid(data.get('originalHelperHandReceiptId')); integer(data.get('projectGeneration'),1)
    require(isinstance(data.get('coreKey'),str) and data['coreKey'],'Missing exact original core')
    verify_original_hand(original_hand,hand_id,helper,owner,area,generation)
    summary=data.get('expectedCanceledSummary'); require(isinstance(summary,dict) and summary.get('state')=='CANCELED'
            and summary.get('activeStage')==summary.get('verifiedStages')==0 and summary.get('targetCount')==572
            and summary.get('claimChunks')==1 and summary.get('quotedPrice')==64 and integer(summary.get('feeVersion'),1)
            and isinstance(summary.get('payment'),str) and summary['payment'] and isinstance(summary.get('stages'),list)
            and len(summary['stages'])==4 and summary['stages'][0].startswith('0:'+area+':') and summary['stages'][0].endswith(':139'),'Missing exact paid terminal summary')
    for key in ('activeStage','verifiedStages','targetCount','claimChunks','feeVersion','quotedPrice'):integer(summary.get(key))
    stage_areas=set()
    for index,(stage,count) in enumerate(zip(summary['stages'],(139,147,147,139))):
        parts=stage.split(':');require(len(parts)==4 and parts[0]==str(index) and parts[3]==str(count)
                and re.fullmatch('[0-9a-f]{64}',parts[2]) and uid(parts[1]) not in stage_areas,'Changed fixed first-case partition')
        stage_areas.add(parts[1])
    verify_raw_terminal(terminal,project,owner,coordinator,data['projectGeneration'],generation,data['coreKey'],data['manifestHash'],summary)
    verify_canceled_authority(data,canceled,terminal);verify_frozen_associations(data)
    stock=canceled.get('stock');world=canceled.get('targetStates')
    require(isinstance(stock,dict) and set(stock)==set(ids)|{'chest'} and isinstance(world,dict) and len(world)==572,'Missing authority-bound target/individual stock freeze')
    for pair in stock.values():require(isinstance(pair,list) and len(pair)==2 and all(type(n) is int and 0<=n<=572 for n in pair),'Malformed stock freeze')
    for kind in kinds[kinds.index('c-compacted-while-absent'):]:
        b=bounds[kind];require(b.get('terminalReceipt')==terminal and b.get('stock')==stock and b.get('targetStates')==world,'Terminal receipt/target/individual stock changed')
        for index,name in enumerate(('cobble','oak')):
            require(b['materials'][name]['chest']==stock['chest'][index] and b['materials'][name]['workerCustody']==sum(stock[actor][index] for actor in ids),'Offline/live custody double-counted or lost')
        state=b.get('terminalProofState');expected='STALE_UNRESOLVED' if kind in ('c-compacted-while-absent','c-canceled-offline-stable') else 'DETACHED_RESOLVED'
        if kind=='c-terminal-disk-join': require(state in ('STALE_UNRESOLVED','DETACHED_RESOLVED'),'Invalid immediate disk cleanup state')
        else:require(state==expected,'Early or missing terminal resolution')
        require(b.get('helperUnresolved')==(project if state=='STALE_UNRESOLVED' else 'null'),'Unresolved association differs from actual cleanup state')
        verify_terminal_proof(b.get('terminalProof'),state,helper,owner,project,generation,area,data['manifestHash'],hand_id,
                              data['projectGeneration'],data['coreKey'],coordinator,summary)
    brackets=[e for e in events if e.get('kind')=='terminal-native-bracket'];require(len(brackets)==integer(data.get('terminalObservedBrackets'),100,9999),'Missing continuous post-cancel observer')
    observations={}
    for e in brackets:
        actor=e.get('worker');tick=integer(e.get('gameTime'))
        require(e['sequence']>canceled['sequence'] and e['sequence']<final['sequence'] and actor in ids and e.get('authorized') is False and type(e.get('changedTargets')) is int and e['changedTargets']==0
                and e.get('targetStatesUnchanged') is True and e.get('before')==e.get('after'),'Unauthorized postcancel work/custody bracket')
        require(canceled['gameTime']<=tick<=final['gameTime'] and (actor,tick) not in observations,'Missing/duplicate/collapsed terminal tick')
        require(e['before']==stock[actor]+stock['chest'] and all(type(value) is int for value in e['before']),'Terminal bracket differs from frozen individual custody')
        if actor==helper:require(e['sequence']>joins[2]['sequence'],'Absent helper produced terminal tick')
        observations[actor,tick]=e
    for tick in range(canceled['gameTime']+1,final['gameTime']+1):
        require((coordinator,tick) in observations,'Coordinator terminal observer gap before/after compaction')
    for tick in range(returned['gameTime']+1,final['gameTime']+1):
        require((helper,tick) in observations and observations[helper,tick]['sequence']>returned['sequence'],'Final original helper terminal observer gap')
    verify_transfers(data,helper,owner);verify_mirrors(data);verify_tickets(data);verify_utility_chronology(data);verify_release_order(data,helper,first,bounds['c-before-detached-return'])
    materials(data.get('finalMaterials'));require(data['finalMaterials']==final['materials'],'Final material receipt differs')
    initial,last=data.get('auxiliaryIsolationInitial'),data.get('auxiliaryIsolationFinal')
    require(isinstance(initial,dict) and isinstance(last,dict) and {k:v for k,v in initial.items() if k!='gameTime'}=={k:v for k,v in last.items() if k!='gameTime'}
            and integer(last.get('gameTime'))>integer(initial.get('gameTime')) and initial.get('feetY')==48 and initial.get('standingVerticalRange')==12
            and isinstance(initial.get('auxiliaries'),list) and 1<=len(initial['auxiliaries'])<=4 and len(initial.get('baseline',[]))==len(initial['auxiliaries'])*45,'Auxiliary isolation changed')
    treasury=data.get('treasuryAccounting');require(isinstance(treasury,dict) and treasury.get('startingFunds')==2000 and treasury.get('commissionDebit')==64
            and treasury.get('initialPaidBalance')==1936 and treasury.get('singleNegativeLedgerDelta')==-64
            and treasury.get('expectedFinalBalance')==1936+integer(treasury.get('interestCredits'))+integer(treasury.get('taxCredits')),'Extra fee/refund or unexplained Treasury change')
    require(data.get('activeHelperHoldingSetup',{}).get('synthetic') is True and data['activeHelperHoldingSetup'].get('dimension')=='minecraft:the_nether','Wrong holding setup')
    require(data.get('screenshots')==['03-authenticated-cancel.png','04-helper-lifecycle-complete.png'],'Missing actual frame evidence')
    return True


def verify_terminal_proof(value,state,helper,owner,project,generation,area,manifest,hand,project_generation,core,coordinator,summary):
    require(isinstance(value,dict) and value.get('state')==state and value.get('helper')==helper and value.get('owner')==owner
            and value.get('project')==project and value.get('ledgerGeneration')==generation and value.get('manifestHash')==manifest
            and value.get('originalHandReceipt')==hand and value.get('handArea')==area and value.get('cleanupAreas')==[area]
            and value.get('generation')==project_generation and value.get('core')==core and value.get('coordinator')==coordinator
            and value.get('terminal')==summary and value.get('admissionArea')==area, 'Foreign/malformed terminal provenance')
    resolved=state=='DETACHED_RESOLVED'
    require(value.get('resolved') is resolved and value.get('unresolvedProject')==('' if resolved else project), 'Terminal resolved state differs')
    expected=dict(admittedStage=0,active=False,ready=True,withdrawn=False,retiredStage=0 if resolved else -1,
                  suspended=not resolved,rejoinStage=-1,grantedStages=[0],legacyEnd=-1,resolved=resolved,destroyed=False)
    member=value.get('member');require(isinstance(member,dict) and member==expected,'Changed terminal admission/grant/destruction history')
    for key in ('active','ready','withdrawn','suspended','resolved','destroyed'):require(type(member[key]) is bool,'Malformed saved member flag')
    for key in ('admittedStage','retiredStage','rejoinStage','legacyEnd'):require(type(member[key]) is int,'Malformed saved member integer')
    require(type(value.get('pendingHand')) is bool and type(value.get('generation')) is int,'Malformed terminal provenance field');integer(value.get('rebinds'))
    for key in ('reservationsAbsent','destructionAbsent','originalHandExact','frozenPersistentExact'): require(value.get(key) is True,'Missing exact terminal provenance: '+key)
    association=value.get('association');require(isinstance(association,dict) and set(association)=={'wholeSelector','protectedChild','protectedGeneration','nativeArea','nativeOwner','currentArea'},'Missing association channel')
    for key in ('wholeSelector','protectedChild','protectedGeneration','nativeArea','nativeOwner'):
        require(association[key] is (not resolved),'Foreign/missing terminal association')
    require(association['currentArea'] in (('',) if resolved else ('',area)),'Wrong native current area')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('evidence',type=Path);parser.add_argument('runtime_manifest',type=Path);args=parser.parse_args()
    try:
        path=args.evidence/'result.json';require(path.stat().st_size<=32*1024*1024,'Result exceeds bound')
        data=BASE.GATE.CAPTURE.parse_unique(path.read_bytes());verify_report(data)
        gate_path=args.evidence/'api-gate/gate.json';gate=BASE.GATE.verify_receipt(args.runtime_manifest,gate_path)
        for field,file in [('sourceCommit','source-commit.txt'),('sourceTree','source-tree.txt')]:require(data.get(field)==gate[field]==(args.evidence/file).read_text().strip(),'Source/prelaunch identity differs')
        parity=data.get('loadedApiParity');require(isinstance(parity,dict) and parity.get('passed') is True
                and parity.get('gateSha256')==hashlib.sha256(gate_path.read_bytes()).hexdigest() and parity.get('contractSha256')==gate['reviewedGateContractSha256'],'Missing loaded parity')
        expected={c['name']:{'provider':c['supplierCanonicalPath'],'supplierSha256':c['supplierSha256'],'rawResourceSha256':c['classSha256']} for c in gate['classes']}
        require(parity.get('classes')==expected,'Loaded class closure differs')
        for name in data['screenshots']:
            image=args.evidence/name;require(image.is_file() and image.stat().st_size>4096 and image.read_bytes()[:8]==b'\x89PNG\r\n\x1a\n','Missing actual PNG')
    except (ValueError,KeyError,TypeError,OSError) as error:parser.exit(1,f'Absent-helper cancellation rejected: {error}\n')
    print('Verified bounded cancellation during helper disk absence and exact cleanup-only return; other lifecycle variants remain separate.')


if __name__=='__main__':main()
