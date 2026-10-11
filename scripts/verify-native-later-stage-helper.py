#!/usr/bin/env python3
"""Strict Case L oracle: fixed cap64 A0 disk absence, A2 return, and authentic cancellation.

Synthetic fixtures exercise rejection decisions only. Native acceptance additionally
requires the source/runtime gate and actual image files in main(). Existing oracles
are imported only for unchanged bounded parsers and exact utility custody checks.
"""
import argparse
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import re
import uuid

ROOT = Path(__file__).resolve().parent

def module(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / file)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value

C = module('later_stage_custody_utils', 'verify-native-absent-helper-cancel.py')
P = module('later_stage_partition_utils', 'verify-native-helper-partition.py')
BASE = C.BASE
require, integer, uid, inventory, cargo, materials, ordered = C.require, C.integer, C.uid, C.inventory, C.cargo, C.materials, C.ordered
compound, nbt_uuid = C.compound, C.nbt_uuid
COUNTS = [30,35,30,44,21,54,38,34,33,38,50,26,43,26,35,35]
BOUNDARIES = ['l-commission', 'l-before-outbound', 'l-outbound-settled', 'l-finalized-disk-removal',
 'l-stage-0-verified', 'l-stage-0-retired', 'l-stage-1-verified', 'l-stage-1-retired',
 'l-a2-ready-helper-offline', 'l-holding-disk-join', 'l-holding-cleanup-ready', 'l-before-native-return',
 'l-return-settled', 'l-helper-a2-active', 'l-helper-a2-placement', 'l-authoritative-canceled',
 'l-compacted-loaded', 'l-final-stable']
MATERIALS = P.MATERIALS


def snbt_list(text, maximum=4096):
    """Read exact compound elements without the utility's stage-only 256-element cap."""
    require(isinstance(text, str) and text.startswith('[') and text.endswith(']') and len(text) <= 256*1024, 'Invalid bounded SNBT list')
    if text == '[]': return []
    parts=[]; start=1; depth=0; quote=False; escaped=False
    for index, char in enumerate(text[1:-1], 1):
        if quote:
            if escaped: escaped=False
            elif char=='\\': escaped=True
            elif char=='"': quote=False
        elif char=='"': quote=True
        elif char in '{[': depth+=1
        elif char in '}]': depth-=1
        elif char==',' and depth==0: parts.append(text[start:index]); start=index+1
        require(0 <= depth <= 64, 'Invalid SNBT nesting')
    require(not quote and not escaped and depth==0, 'Unclosed SNBT list')
    parts.append(text[start:-1]); require(len(parts)<=maximum, 'SNBT list exceeds cap')
    return [compound(part) for part in parts]


def number(raw, suffix='', minimum=-(2**63), maximum=2**63-1):
    require(isinstance(raw,str) and re.fullmatch(r'-?(?:0|[1-9]\d*)'+suffix, raw), 'Wrong NBT integer type')
    return integer(int(raw[:-len(suffix)] if suffix else raw), minimum, maximum)


def longs(raw):
    require(isinstance(raw,str) and raw.startswith('[L;') and raw.endswith(']'), 'Missing long array')
    values=[] if raw=='[L;]' else [number(v,'L') for v in raw[3:-1].split(',')]
    require(len(values)<=4096, 'Oversized long array')
    return values


def string(raw):
    require(isinstance(raw,str) and raw.startswith('"'), 'Missing NBT string')
    value=json.loads(raw); require(isinstance(value,str), 'Invalid NBT string'); return value


def exact(a,b,message): P.exact(a,b,message)
def digest(text): return hashlib.sha256(text.encode()).hexdigest()
def tag_map(text): return compound(text)
def state(tag):
    require(set(tag)=={'Name'}, 'Unexpected state properties in fixed full-block fixture')
    name=string(tag['Name']); require(name in (*MATERIALS,'minecraft:air'), 'Unsupported fixture block state'); return name


def candidate(data):
    raw=P.CANDIDATE_PATH.read_bytes()
    require(hashlib.sha256(raw).hexdigest()==P.CANDIDATE_SHA256 and data.get('candidateSha256')==P.CANDIDATE_SHA256, 'Changed fixed candidate bytes')
    exact(data.get('candidatePartition'), BASE.GATE.CAPTURE.parse_unique(raw), 'Changed full historical candidate JSON')
    plan, partition=P.derive_expected(); exact(P.load_candidate(),partition,'Pinned partition differs')
    exact(data.get('nativePartition'),partition,'Observed native partition differs from immutable full ordered candidate')
    expected=[dict(ordinal=i,index=c['index'],layoutStageDigest=c['layoutStageDigest'],blueprintBlockCount=c['targetCount'],targetCount=c['targetCount'],nativeRoundTripPassed=True,problem=None) for i,c in enumerate(partition['validatorCalls'])]
    exact(data.get('nativePartitionRoundTrips'),expected,'Missing/changed/reordered native codec proposal or fallback')
    return plan,partition


def accepted_project(data, plan, partition):
    p=compound(data.get('acceptedProject'))
    required={'Version','Project','Generation','Owner','Builder','CoreKey','Core','Faction','Material','FeeVersion','Price','Review','Hash','Territory','State','Revision','Active','Blocker','Palette','Targets','Clearance','Columns','Min','Max','Layout','Stages','Receipts','Payment'}
    require(set(p)==required and p['Version']=='1' and p['State']=='"RUNNING"' and p['Active']=='0' and p['Receipts']=='[]', 'Invalid original paid project schema/state')
    for key,expected in [('Project',data['projectId']),('Owner',data['owner']),('Builder',data['workers'][0])]: require(nbt_uuid(p[key])==expected,'Foreign original project identity')
    require(number(p['Generation'],'L',1)==data['projectGeneration'] and string(p['Hash'])==data['manifestHash'] and string(p['CoreKey'])==data['coreKey'] and data['coreKey']=='team:'+string(p['Faction']), 'Changed original project identity')
    require(p['FeeVersion']=='1' and p['Price']=='64' and number(p['Material'],minimum=0,maximum=2)>=0 and re.fullmatch('[0-9a-f]{64}',string(p['Review'])) and number(p['Revision'],'L',1)>0,'Changed paid project counters')
    require(longs(p['Territory'])==[8] and string(p['Layout'])==P.LAYOUT_SHA256,'Changed fixed territory/layout')
    require(number(p['Min'],'L')==P.packed(*plan['min']) and number(p['Max'],'L')==P.packed(*plan['max']), 'Changed paid geometry envelope')
    palette=[state(v) for v in snbt_list(p['Palette'])]; require(len(palette)==len(set(palette))==3,'Unexpected fixed palette')
    target_rows=snbt_list(p['Targets']); clear_rows=snbt_list(p['Clearance']); columns=snbt_list(p['Columns'])
    target={}; before={}; clear={}
    for row in target_rows:
        require(set(row)=={'Pos','Target','Before'},'Malformed paid target')
        cell=number(row['Pos'],'L'); require(cell not in target,'Duplicate paid target')
        target[cell]=palette[number(row['Target'],minimum=0,maximum=len(palette)-1)]; before[cell]=palette[number(row['Before'],minimum=0,maximum=len(palette)-1)]
    for row in clear_rows:
        require(set(row)=={'Pos','Before'},'Malformed paid clearance')
        cell=number(row['Pos'],'L');require(cell not in clear,'Duplicate paid clearance');clear[cell]=palette[number(row['Before'],minimum=0,maximum=len(palette)-1)]
    require(target=={v['cell']:v['material'] for v in plan['targets']} and set(clear)==set(plan['clearance']) and set(before.values())==set(clear.values())=={'minecraft:air'},'Paid exact target/material/clearance map changed')
    require(len(columns)==220 and [number(v['Base'],'L') for v in columns]==[v+65 for v in plan['columns']],'Paid column order differs')
    for row in columns:
        require(set(row)=={'Base','Support','Inward','Component'},'Malformed paid column')
        x,_,z=P.coordinates(number(row['Base'],'L'))
        require(row['Support']=='0' and number(row['Inward'])==min(x-127,144-x,z+1,16-z) and row['Component']=='0','Changed fixed column contract')
    text='perimeter-project-v1\n'+'\n'.join([nbt_uuid(p['Project']),str(data['projectGeneration']),data['owner'],data['workers'][0],data['coreKey'],str(number(p['Core'],'L')),string(p['Faction']),p['Material'],string(p['Review']),p['FeeVersion']+':'+p['Price']])+'\n'
    text+=''.join(f'claim:{n}\n' for n in longs(p['Territory']))
    text+=''.join('column:'+':'.join([str(number(v['Base'],'L')),v['Support'],v['Inward'],v['Component']])+'\n' for v in columns)
    for label,values in [('target',target),('before',before),('clear',clear)]: text+=''.join(f'{label}:{cell}:{v}\n' for cell,v in sorted(values.items()))
    text+=string(p['Layout']); require(digest(text)==data['manifestHash'],'Raw paid manifest hash mismatch')
    stages=snbt_list(p['Stages']); require(len(stages)==16,'Changed stage count')
    for i,(s,expected) in enumerate(zip(stages,partition['stages'])):
        require(set(s)=={'Index','Area','Digest','LayoutDigest','Columns','Min','Max','Origin'} and s['Index']==str(i),'Malformed paid stage')
        require(longs(s['Columns'])==expected['columns'] and string(s['LayoutDigest'])==expected['layoutStageDigest'],'Paid stage columns/layout differ')
        require(number(s['Min'],'L')==P.packed(*expected['min']) and number(s['Max'],'L')==P.packed(*expected['max']) and number(s['Origin'],'L')==P.packed(expected['max'][0],expected['min'][1],expected['min'][2]),'Paid stage bounds differ')
        content='perimeter-stage-contract-v1\n'+data['manifestHash']+'\n'+expected['layoutStageDigest']+'\n'
        content+=''.join(f"{t['cell']}:{t['material']}:minecraft:air\n" for t in expected['targets'])
        content+=''.join(f'clear:{cell}:minecraft:air\n' for cell in expected['clearance'])
        require(string(s['Digest'])==digest(content),'Paid stage-contract digest mismatch')
        key=f"siege-perimeter-stage-v1:{data['projectId']}:{data['projectGeneration']}:{i}:{digest(content)}"
        require(nbt_uuid(s['Area'])==str(uuid.UUID(bytes=hashlib.md5(key.encode()).digest(),version=3)),'Paid derived stage UUID mismatch')
    paid=compound(p['Payment']); require(set(paid)=={'Project','Generation','Hash','FeeVersion','Price','Debited','Creative'},'Malformed payment')
    require(all(paid[k]==p[k] for k in ('Project','Generation','Hash','FeeVersion','Price')) and paid['Debited']=='64' and paid['Creative']=='0b','Changed single Survival payment')
    require(data['firstStageArea']==nbt_uuid(stages[0]['Area']),'Changed first stage identity')
    return p,stages,target


def project_snapshot(raw, original, stages, expected=None):
    p=compound(raw); mutable={'State','Revision','Active','Blocker','Receipts'}
    require(set(p)==set(original) and {k:v for k,v in p.items() if k not in mutable}=={k:v for k,v in original.items() if k not in mutable},'Live paid project identity/layout changed')
    number(p['Revision'],'L',1); active=number(p['Active'],minimum=0,maximum=2); receipts=snbt_list(p['Receipts'])
    require(len(receipts)<=2,'A2 verified or later-stage receipt observed')
    for i,r in enumerate(receipts):
        require(set(r)=={'Project','Generation','Hash','Index','Area','Digest','Targets'} and all(r[k]==original[k] for k in ('Project','Generation','Hash')) and r['Index']==str(i) and r['Area']==stages[i]['Area'] and r['Digest']==stages[i]['Digest'] and r['Targets']==str(COUNTS[i]),'Foreign/reordered/changed stage receipt')
    s=string(p['State']);require(s in ('RUNNING','STAGE_VERIFIED','WAITING_FOR_NEXT_STAGE','CANCELED'),'Unexpected live state')
    require(len(receipts)==active+(s=='STAGE_VERIFIED'),'Receipt prefix conflicts with active stage')
    if expected is not None:require((s,active,len(receipts)) in expected,'Wrong exact stage milestone')
    return p


def marker_receipt(raw,stage,original,partition,seen):
    """Authenticate the actual native receipt, not a QA completion boolean."""
    r=compound(raw); expected=partition['stages'][stage]; points=expected['targets']
    require(set(r)=={'Origin','Facing','Width','Depth','Height','Structure','Owner','Builder','CoreKey','CorePos','Before','Reservation','Completed','Cleared'},'Malformed native marker receipt')
    require(r['Owner']==original['Owner'] and r['Builder']==original['Builder'] and r['CoreKey']==original['CoreKey'] and r['CorePos']==original['Core'],'Foreign native marker ownership/core')
    lo,hi=expected['min'],expected['max']
    require(number(r['Origin'],'L')==P.packed(hi[0],lo[1],lo[2]) and r['Facing']=='"south"' and [number(r[k]) for k in ('Width','Height','Depth')]==[hi[a]-lo[a]+1 for a in range(3)],'Native marker envelope changed')
    structure=compound(r['Structure']); require(set(structure)=={'width','facing','blocks'} and structure['width']==r['Width'] and structure['facing']=='"south"','Malformed native blueprint')
    actual={}
    for row in snbt_list(structure['blocks']):
        require(set(row)=={'x','y','z','state'},'Malformed native block')
        cell=P.packed(*[lo[a]+number(row[k],minimum=0) for a,k in enumerate(('x','y','z'))]);require(cell not in actual,'Duplicate native block');actual[cell]=state(compound(row['state']))
    require(actual=={t['cell']:t['material'] for t in points},'Native recipe differs from exact captured stage')
    def states(raw):
        rows=snbt_list(raw); out={}
        for row in rows:
            require(set(row)=={'Pos','State'},'Malformed native before state');cell=number(row['Pos'],'L');require(cell not in out,'Duplicate native before cell');out[cell]=state(compound(row['State']))
        return out
    require(states(r['Before'])=={t['cell']:'minecraft:air' for t in points},'Native prior target state changed')
    reservation=compound(r['Reservation']);require(set(reservation)=={'Version','Cells','Clearance'} and reservation['Version']=='1','Malformed native reservation')
    cells=longs(reservation['Cells']); require(len(cells)==len(set(cells)) and set(cells)==set(actual)|set(expected['clearance']) and states(reservation['Clearance'])==dict.fromkeys(expected['clearance'],'minecraft:air'),'Native reservation differs from paid stage')
    completed=longs(r['Completed']);cleared=longs(r['Cleared'])
    require(len(completed)==len(set(completed)) and set(completed)==set(seen)&set(actual) and not cleared,'Unreceipted/fabricated captured marker completion')


def ledger_snapshot(raw,data,stages,grants,terminal=False,commission=False):
    ledger=compound(raw)
    require(set(ledger)=={'Sites','Retired','Generation','Invalid','ProjectLeases','HandLifecycles','BuilderAssignments','BuilderCrew'} and ledger['Invalid']=='0b' and nbt_uuid(ledger['Generation'])==data['ledgerGeneration'],'Invalid/foreign ledger')
    plan,partition=P.derive_expected()
    sites=snbt_list(ledger['Sites']);retired=snbt_list(ledger['Retired'])
    require(not retired,'Outstanding retired cleanup identifier')
    if terminal:require(not sites,'Terminal retained spatial reservation')
    else:
        require(len(sites)==1,'Missing/excess whole-project spatial reservation')
        site=sites[0];require(set(site)=={'Id','BuilderDestroyed','ReservationVersion','Cells','Edited'} and nbt_uuid(site['Id'])==data['projectId'] and site['BuilderDestroyed']=='0b' and site['ReservationVersion']=='1','Destroyed/foreign reservation')
        cells=longs(site['Cells']);edited=longs(site['Edited']);expected={t['cell'] for t in plan['targets']}|set(plan['clearance'])
        require(len(cells)==len(set(cells)) and set(cells)==expected and len(edited)==len(set(edited)) and set(edited)<=expected,'Spatial reservation or edit journal changed')
    hands=snbt_list(ledger['HandLifecycles']); matches=[h for h in hands if nbt_uuid(h.get('Builder'))==data['workers'][1]]
    require((commission and not matches) or len(matches)==1 and matches[0]==compound(data['originalHelperHandReceipt']),'Reminted/foreign ledger hand')
    assignments=compound(ledger['BuilderAssignments']);require(assignments=={'Version':'2','Projects':'[]'},'Original coordinator changed or helper became coordinator')
    crew=compound(ledger['BuilderCrew']);require(set(crew)=={'Version','LedgerGeneration','Projects'} and crew['Version']=='3' and crew['LedgerGeneration']==ledger['Generation'],'Malformed explicit crew ledger')
    entries=snbt_list(crew['Projects'])
    m=None
    if commission and not entries:
        require(not matches,'Unadmitted helper already has retained ledger hand')
    else:
        require(len(entries)==1,'Unexpected crew project')
        entry=entries[0];require(set(entry)=={'Project','Generation','Hash','Original','Members'} and nbt_uuid(entry['Project'])==data['projectId'] and number(entry['Generation'],'L')==data['projectGeneration'] and string(entry['Hash'])==data['manifestHash'] and nbt_uuid(entry['Original'])==data['workers'][0],'Foreign crew provenance')
        members=snbt_list(entry['Members']);require(len(members)==1,'Unexpected helper identity')
        m=members[0];require(set(m)=={'Worker','Active','Ready','Withdrawn','AdmittedStage','RetiredStage','Suspended','RejoinStage','GrantedStages','LegacyEnd','Resolved'} and nbt_uuid(m['Worker'])==data['workers'][1] and m['AdmittedStage']=='0' and m['LegacyEnd']=='-1' and m['Ready']=='1b' and m['Withdrawn']=='0b','Changed helper admission/legacy/destruction')
        require(longs(m['GrantedStages'])==[sum(1<<n for n in grants),0,0,0],'Missing/extra helper history grant')
        for k in ('Active','Ready','Withdrawn','Suspended','Resolved'):require(m[k] in ('0b','1b'),'Malformed member flag')
        number(m['RejoinStage'],minimum=-1,maximum=2);number(m['RetiredStage'],minimum=-1,maximum=2)
        if terminal:require(m['Active']=='0b' and m['Resolved']=='1b' and m['Suspended']=='0b' and m['RejoinStage']=='-1' and m['RetiredStage']=='2','Unresolved terminal helper')
    leases=compound(ledger['ProjectLeases']);require(set(leases)=={'Version','Projects'} and leases['Version']=='1','Malformed project leases')
    lease_rows=snbt_list(leases['Projects'])
    if terminal:require(not lease_rows,'Terminal lease remains')
    else:
        require(len(lease_rows)==1,'Missing/excess project lease');lease=lease_rows[0]
        require(set(lease)=={'Id','Generation','Hash','Stages','Active','Retired','Cells'} and nbt_uuid(lease['Id'])==data['projectId'] and number(lease['Generation'],'L')==data['projectGeneration'] and string(lease['Hash'])==data['manifestHash'],'Foreign lease')
        require([r.get('Id') for r in snbt_list(lease['Stages'])]==[s['Area'] for s in stages],'Foreign lease stage identities')
        index=number(lease['Active'],minimum=0,maximum=2);part=partition['stages'][index];cells=longs(lease['Cells'])
        require(lease['Retired'] in ('0b','1b') and len(cells)==len(set(cells)) and set(cells)=={t['cell'] for t in part['targets']}|set(part['clearance']),'Changed active child lease cells')
    return ledger,m


def journal_snapshot(raw,core,original,stages):
    require(core.get('PerimeterStageRuntime')==raw,'Journal snapshot differs from authoritative core')
    journal=compound(raw);require(set(journal)=={'Version','Entries'} and journal['Version']=='1','Malformed native stage journal')
    entries=snbt_list(journal['Entries']);require(len(entries)<=1,'Unexpected journal entry')
    if not entries:return []
    e=entries[0];require(set(e)=={'Id','Generation','Hash','Attempts'} and e['Id']==original['Project'] and e['Generation']==original['Generation'] and e['Hash']==original['Hash'],'Foreign stage journal')
    attempts=snbt_list(e['Attempts']);require(1<=len(attempts)<=3,'Missing or advanced native stage history')
    for i,a in enumerate(attempts):
        require(set(a)=={'Index','Area','Marker','State'} and a['Index']==str(i) and a['Area']==stages[i]['Area'] and a['Marker']==attempts[0]['Marker'] and a['State'] in ('"INTENT"','"LIVE"','"RETIRED"'),'Malformed native attempt')
        number(a['Marker'],'L');require(i==len(attempts)-1 or a['State']=='"RETIRED"','Unretired previous native stage')
    return attempts


def stack_contents(raw):
    """Read complete ItemStack SNBT while retaining opaque item data for custody."""
    tag=compound(raw)
    if not tag:return ('minecraft:air',0)
    require({'id','Count'}<=set(tag) and set(tag)<={'id','Count','tag','ForgeCaps'},'Malformed serialized native stack')
    item=string(tag['id']);require(re.fullmatch(r'[a-z0-9_.-]+:[a-z0-9_./-]+',item),'Malformed native item identifier')
    count=number(tag['Count'],'b',0,64)
    require((item=='minecraft:air')==(count==0),'Noncanonical empty native stack')
    return item,count


def inventory_stock(value):
    """Pinned Recruits main/slot5 value contract; bounded Case L offhand stays nonconstruction.

    Ordinary borrowed hands may change beforeItemSlot during work. No placement or
    supply is allowed while the serialized post-load mirror is still pending.
    Object alias restoration itself remains bound to the observed admission records.
    """
    inv=inventory(value);require(-1<=inv['beforeItemSlot']<len(inv['slots']),'Borrowed hand index outside native inventory')
    parsed=[stack_contents(s) for s in inv['slots']]
    main=stack_contents(inv['main']);off=stack_contents(inv['off'])
    require(compound(inv['main'])==compound(inv['slots'][5]),'Native main hand differs from slot5 cargo')
    require(off[0] not in MATERIALS,'Bounded Case L offhand acquired construction stock')
    require(compound(inv['off'])==compound(inv['slots'][4]),'Native offhand differs from pinned slot4 cargo')
    return [sum(count for item,count in parsed if item==material) for material in MATERIALS]


def pending_flag(persistent):
    p=compound(persistent)
    require(C.HAND not in p or p[C.HAND]=='1b','Malformed or retained false pending-hand tag')
    return C.HAND in p


def rebind_count(persistent):
    return number(compound(persistent).get(C.REBINDS,'0'),minimum=0,maximum=2**31-1)


def closed_worker(worker, expected_owner, allow_pending=False):
    inv=inventory(worker.get('inventory'));inventory_stock(inv);p=compound(inv['persistent'])
    require(worker.get('owner')==expected_owner and worker.get('removed') is False,'Closed worker has foreign owner or is removed')
    require(worker.get('beforeItemSlot')==inv['beforeItemSlot']==-1 and type(worker.get('beforeItemSlot')) is int,'Closed worker retained borrowed hand')
    require(type(worker.get('neededItemCount')) is int and worker['neededItemCount']==0,'Closed worker retained native material request')
    pending=pending_flag(inv['persistent'])
    require(worker.get('handPending') is pending and (allow_pending or not pending),'Raw pending hand conflicts with closed-worker evidence')
    require(type(worker.get('mainAliasesSlot5')) is bool and (pending or worker['mainAliasesSlot5'] is True),'Closed nonpending hand lacks canonical identity')
    require('SiegeProtectedHandReviewRequired' not in p and 'SiegeProtectedInventoryCleanup' not in p,'Closed worker retained hand review or inventory cleanup')
    require(type(worker.get('handRebindCount')) is int and worker['handRebindCount']==rebind_count(inv['persistent']),'Raw hand rebind count differs from boundary')
    return inv


def marker_position(bounds):
    journal=compound(bounds['l-commission']['journalSnapshot'])
    entry=snbt_list(journal['Entries'])[0];attempt=snbt_list(entry['Attempts'])[0]
    return number(attempt['Marker'],'L')


def verify_boundary_inventory(data,b,actor,w,stock):
    inv=inventory(w.get('inventory'));require(inventory_stock(inv)==stock,'Raw native inventory differs from individual stock')
    require(w.get('owner')==data['owner'],'Raw boundary worker owner changed')
    current=w.get('currentArea');require('currentArea' in w and (current is None or uid(current)),'Missing/malformed native current-area pointer')
    persistent=compound(inv['persistent'])
    if current is not None:require(nbt_uuid(persistent.get('SiegePlayerFortificationAreaId'))==current,'Native current-area pointer contradicts persistent selector')
    offline=actor==data['workers'][1] and b['kind'] in BOUNDARIES[3:9]
    holding=actor==data['workers'][1] and b['kind'] in BOUNDARIES[2:12]
    require(w.get('removed') is offline and w.get('dimension')==('minecraft:the_nether' if holding else 'minecraft:overworld'),'Raw worker removal/dimension contradicts fixed native custody route')
    raw_pending=pending_flag(inv['persistent'])
    require(w.get('handPending') is raw_pending and w.get('beforeItemSlot')==inv['beforeItemSlot'] and type(w.get('beforeItemSlot')) is int,'Raw hand state differs from boundary')
    require(type(w.get('handRebindCount')) is int and w['handRebindCount']==rebind_count(inv['persistent']),'Raw rebind counter differs from boundary')
    if actor==data['workers'][1]:
        restored=[e for e in data['events'] if e.get('kind')=='load-mirror-restored' and e['sequence']<b['sequence']]
        expected=restored[-1]['rebindsAfter'] if restored else 0
        require(w['handRebindCount']==expected,'Boundary mirror counter escaped observed load chain')
        if raw_pending:
            arms=[e for e in data['events'] if e.get('kind')=='load-mirror-armed' and e['sequence']<b['sequence']]
            require(arms and inv==arms[-1]['armed'] and not any(e.get('kind')=='load-mirror-restored' and arms[-1]['sequence']<e['sequence']<b['sequence'] for e in data['events']),'Unobserved/already-restored pending mirror counted once')
    else:require(not raw_pending,'Original coordinator unexpectedly entered a load mirror')
    if not raw_pending:require(w.get('mainAliasesSlot5') is True,'Nonpending boundary main mirror is not canonical')


def associations(raw,data,area,active):
    p=compound(raw)
    require(compound(p.get('SiegeProtectedHandLifecycle'))==compound(data['originalHelperHandReceipt']),'Actor immutable A0 hand changed')
    whole=compound(p.get('SiegeWholePerimeter'))
    require(set(whole)=={'Version','Project','Generation','Hash','Core','LedgerGeneration'} and whole['Version']=='1' and nbt_uuid(whole['Project'])==data['projectId'] and number(whole['Generation'],'L')==data['projectGeneration'] and string(whole['Hash'])==data['manifestHash'] and string(whole['Core'])==data['coreKey'] and nbt_uuid(whole['LedgerGeneration'])==data['ledgerGeneration'],'Foreign/missing whole-project association')
    channels={'SiegeProtectedAreaReceipt':area,'SiegeProtectedLedgerGeneration':data['ledgerGeneration'],'SiegePlayerFortificationAreaId':area,'SiegePlayerFortificationOwner':data['owner']}
    if active:
        for key,expected in channels.items():require(nbt_uuid(p.get(key))==expected,'Foreign/missing live authority channel')
        number(p.get('SiegePlayerFortificationPos'),'L')
        require(C.HAND not in p,'Pending hand used as live work authority')
    return p


def verify_work(data,bounds,original,stages,partition):
    position=marker_position(bounds)
    ids=data['workers']; helper=ids[1]; counts=dict.fromkeys(ids,0);stock={actor:[0,0] for actor in ids};chest=[352,220];seen=set(); supplied=set();result=[]
    for e in data['nativeWork']:
        actor=e.get('worker');stage=integer(e.get('stage'),0,2);area=nbt_uuid(stages[stage]['Area'])
        require(actor in ids and e.get('area')==e.get('markerUuid')==area and e.get('stageDigest')==string(stages[stage]['Digest']) and e.get('authorized') is True,'Foreign/off-stage/native work')
        require(bounds['l-commission']['sequence']<e['sequence']<bounds['l-authoritative-canceled']['sequence'],'Precommission/post-cancel work')
        if actor==helper:
            require(stage in (0,2) and (e['sequence']<bounds['l-before-outbound']['sequence'] if stage==0 else e['sequence']>bounds['l-helper-a2-active']['sequence']),'Absent/A1/inactive helper work')
        for key in ('gameTime','startGameTime','endGameTime'):integer(e.get(key))
        require(e['startGameTime']==e['endGameTime']==e['gameTime'] and e.get('startDimension')==e.get('endDimension')=='minecraft:overworld','Placement bracket crossed a tick/world')
        project_snapshot(e.get('projectSnapshot'),original,stages,{('RUNNING',stage,stage)})
        auth=e.get('authority'); require(isinstance(auth,dict) and set(auth)=={'workerInventory','ledgerSnapshot','projectSnapshot','currentArea','owner'},'Missing exact pre-bracket authority')
        require(auth['projectSnapshot']==e['projectSnapshot'] and auth['owner']==data['owner'] and auth['currentArea']==area,'Foreign/cross-stage pre-bracket authority')
        work_grants=[0,2] if stage==2 and actor==helper else [0]
        if stage==2 and actor==ids[0]:
            crew=compound(compound(auth['ledgerSnapshot'])['BuilderCrew']);saved=snbt_list(snbt_list(crew['Projects'])[0]['Members'])[0]
            if longs(saved['GrantedStages'])==[5,0,0,0]:work_grants=[0,2]
        work_ledger,member=ledger_snapshot(auth['ledgerSnapshot'],data,stages,work_grants,commission=actor==ids[0] and stage==0 and e['sequence']<bounds['l-before-outbound']['sequence'])
        lease=snbt_list(compound(work_ledger['ProjectLeases'])['Projects'])[0]
        require(lease['Active']==str(stage) and lease['Retired']=='0b','Work authority uses retired/foreign active child lease')
        inv=inventory(auth['workerInventory']);p=compound(inv['persistent'])
        if actor==helper:
            associations(inv['persistent'],data,area,True)
            require(member['Active']=='1b' and member['Suspended']=='0b' and member['RejoinStage']=='-1' and member['Resolved']=='0b','Historical grant/pending intent used as live authority')
        else:
            for k,expected in [('SiegeProtectedAreaReceipt',area),('SiegeProtectedLedgerGeneration',data['ledgerGeneration']),('SiegePlayerFortificationAreaId',area),('SiegePlayerFortificationOwner',data['owner'])]:require(nbt_uuid(p.get(k))==expected,'Coordinator exact work links missing')
            require(C.HAND not in p,'Coordinator pending hand work')
            whole=compound(p.get('SiegeWholePerimeter'))
            require(set(whole)=={'Version','Project','Generation','Hash','Core','LedgerGeneration'} and whole['Version']=='1' and whole['Project']==original['Project'] and whole['Generation']==original['Generation'] and whole['Hash']==original['Hash'] and whole['Core']==original['CoreKey'] and nbt_uuid(whole['LedgerGeneration'])==data['ledgerGeneration'],'Coordinator whole-project authority differs')
        before,after=e.get('before'),e.get('after');require(isinstance(before,list) and isinstance(after,list) and len(before)==len(after)==4,'Missing exact finite stock debit')
        for n in before+after:integer(n,0,572)
        require(before==stock[actor]+chest,'Native work stock chain is discontinuous')
        require(inventory_stock(inv)==before[:2],'Raw pre-work inventory does not contain claimed construction stock')
        require(number(p.get('SiegePlayerFortificationPos'),'L')==position,'Pre-work native selector targets a foreign marker position')
        require('SiegeProtectedHandReviewRequired' not in p,'Reviewed hand used for native work')
        cells=e.get('cells');require(isinstance(cells,list) and len(cells)==len(set(cells)),'Duplicate/malformed target cells')
        targets={t['cell']:t['material'] for t in partition['stages'][stage]['targets']}
        cb,oak=integer(e.get('placedCobble'),0,64),integer(e.get('placedOak'),0,64)
        if e.get('kind')=='native-placement':
            require(cells and set(cells)<=set(targets) and not seen.intersection(cells) and e.get('nativeState')=='PLACE_BLOCKS' and cb==sum(targets[c]==MATERIALS[0] for c in cells) and cb+oak==len(cells) and before[0]-after[0]==cb and before[1]-after[1]==oak and before[2:]==after[2:],'Fabricated/duplicate/unreceipted placement debit')
            seen.update(cells);counts[actor]+=len(cells)
        else:
            require(e.get('kind')=='native-supply' and not cells and cb==oak==0 and after[0]>=before[0] and after[1]>=before[1] and after[:2]!=before[:2] and after[0]-before[0]==before[2]-after[2] and after[1]-before[1]==before[3]-after[3],'Supply lacks exact finite chest withdrawal')
            supplied.add((actor,stage))
        stock[actor]=after[:2];chest=after[2:]
        marker=compound(e['markerReceipt']);require(marker.get('SiegeConstructionCommissionPaid')=='1b' and marker.get('SiegePerimeterProjectRequired')=='1b','Unpaid/unbound actual marker')
        scope=compound(marker.get('SiegePerimeterProject'))
        require(set(scope)=={'Version','Project','Generation','Hash','Core','Stage','StageHash'} and scope['Version']=='1' and scope['Project']==original['Project'] and scope['Generation']==original['Generation'] and scope['Hash']==original['Hash'] and scope['Core']==original['CoreKey'] and scope['Stage']==str(stage) and scope['StageHash']==stages[stage]['Digest'],'Marker paid-stage selector changed')
        marker_receipt(marker.get('SiegeProtectedConstructionV1'),stage,original,partition,seen)
        result.append((e['sequence'],dict(counts),{k:list(v) for k,v in stock.items()}|{'chest':list(chest)},set(seen)))
    require({(actor,0) for actor in ids}<=supplied and all(counts.values()),'Both original A0 suppliers/contributors required')
    require(data.get('placementCounts')==counts and all(type(v) is int for v in data['placementCounts'].values()) and type(data.get('retainedPlacedBlocks')) is int and data.get('retainedPlacedBlocks')==len(seen) and 66<=len(seen)<=94,'Wrong bounded final partial build')
    for stage in (0,1):require({t['cell'] for t in partition['stages'][stage]['targets']}<=seen,'Incomplete naturally verified A0/A1')
    require(any(e['worker']==helper and e['stage']==2 and e['kind']=='native-placement' for e in data['nativeWork']),'Missing actual A2 helper placement')
    return result


def terminal_receipt(raw,data,original,stages):
    r=compound(raw)
    require(set(r)=={'Version','Project','Generation','Hash','Owner','Builder','CoreKey','Faction','Revision','State','Active','Verified','ClaimChunks','LedgerGeneration','FeeVersion','Price','ReceiptHash','ReservationRetired','BuilderDetached','StagesCleaned','Stages','Payment'},'Malformed compact terminal')
    for key in ('Project','Generation','Hash','Owner','Builder','CoreKey','Faction','FeeVersion','Price','Payment'):require(r[key]==original[key],'Terminal changed original paid identity')
    require(r['Version']=='1' and r['State']=='"CANCELED"' and r['Active']==r['Verified']=='2' and r['ClaimChunks']=='1' and nbt_uuid(r['LedgerGeneration'])==data['ledgerGeneration'],'Wrong fixed canceled A2/2 terminal')
    require(number(r['Revision'],'L')>number(original['Revision'],'L'),'Terminal revision did not advance')
    for key in ('ReceiptHash','ReservationRetired','BuilderDetached','StagesCleaned'):require(re.fullmatch('[0-9a-f]{64}',string(r[key])),'Missing terminal cleanup seal')
    actual=snbt_list(r['Stages']);require(len(actual)==16,'Terminal stage summary changed')
    for i,(a,s) in enumerate(zip(actual,stages)):require(set(a)=={'Index','Area','Digest','Targets'} and all(a[k]==s[k] for k in ('Index','Area','Digest')) and a['Targets']==str(COUNTS[i]),'Terminal paid-stage summary changed')
    require(string(r['ReceiptHash'])==C.terminal_seal(r),'Forged terminal receipt seal')
    return r


def verify_boundaries(data,bounds,original,stages,partition,target,work):
    helper=data['workers'][1]; previous_revision=number(original['Revision'],'L'); terminal=None; frozen=None
    for kind in BOUNDARIES:
        b=bounds[kind];integer(b.get('gameTime'));require(b.get('ownerMode')=='non-op Survival','Changed boundary owner mode')
        prior=[v for v in work if v[0]<b['sequence']]
        _,counts,stock,seen=prior[-1] if prior else (0,dict.fromkeys(data['workers'],0),{**{a:[0,0] for a in data['workers']},'chest':[352,220]},set())
        require(b.get('placementCounts')==counts and all(type(v) is int for v in b['placementCounts'].values()) and b.get('stock')==stock and b.get('pendingTargets')==572-len(seen),'Boundary stock/progress differs from continuous native work')
        require(all(isinstance(pair,list) and len(pair)==2 and all(type(n) is int for n in pair) for pair in b['stock'].values()),'Malformed per-actor stock counters')
        world={str(cell):'Block{'+(material if cell in seen else 'minecraft:air')+'}' for cell,material in target.items()}
        require(b.get('targetStates')==world,'World targets differ from unique native receipts')
        materials(b.get('materials'))
        for i,name in enumerate(('cobble','oak')):
            c=b['materials'][name];require(c['built']==sum(target[cell]==MATERIALS[i] for cell in seen) and c['chest']==stock['chest'][i] and c['workerCustody']==sum(stock[a][i] for a in data['workers']),'World/individual custody mismatch')
        core=compound(b.get('coreSnapshot'));store=compound(core.get('PerimeterProjects'));require(set(store)=={'Version','Entries','Terminals'} and store['Version']=='2','Malformed authoritative project store')
        attempts=journal_snapshot(b.get('journalSnapshot'),core,original,stages)
        require(all(number(a['Marker'],'L')==marker_position(bounds) for a in attempts),'Native marker position changed across authoritative snapshots')
        compact=kind in ('l-compacted-loaded','l-final-stable') or kind=='l-authoritative-canceled' and b.get('authoritySource')=='compact-terminal'
        grants=[0,2] if b['sequence']>=bounds['l-helper-a2-active']['sequence'] else [0]
        if kind=='l-return-settled':
            crew=compound(compound(b.get('ledgerSnapshot'))['BuilderCrew']);members=snbt_list(snbt_list(crew['Projects'])[0]['Members'])
            if longs(members[0]['GrantedStages'])==[5,0,0,0]:grants=[0,2]
        ledger,member=ledger_snapshot(b.get('ledgerSnapshot'),data,stages,grants,compact,kind=='l-commission')
        workers=b.get('workers');require(isinstance(workers,dict) and set(workers)==set(data['workers']),'Wrong original worker boundary')
        for actor,w in workers.items():
            inv=inventory(w.get('inventory'));integer(w.get('entityTick'));integer(w.get('handRebindCount'))
            require(w.get('dimension') in ('minecraft:overworld','minecraft:the_nether') and type(w.get('removed')) is bool and type(w.get('handPending')) is bool and type(w.get('mainAliasesSlot5')) is bool and isinstance(w.get('nativeState'),str),'Malformed worker boundary')
            verify_boundary_inventory(data,b,actor,w,stock[actor])
            if actor==helper and kind!='l-commission':require(compound(compound(inv['persistent']).get('SiegeProtectedHandLifecycle'))==compound(data['originalHelperHandReceipt']),'Boundary changed immutable A0 hand')
        claims=b.get('claims'); require(isinstance(claims,list) and len(claims)<=2 and len({c.get('target') for c in claims})==len(claims),'Duplicate/malformed native claims')
        if compact:
            require(b.get('projectSnapshot') is None and store['Entries']=='[]' and not attempts and not claims,'Terminal retained live project/journal/claims')
            terms=snbt_list(store['Terminals']);require(len(terms)==1 and terms[0]==compound(b.get('terminalReceipt')),'Terminal not authoritative')
            current=terminal_receipt(b['terminalReceipt'],data,original,stages)
            if terminal is None:terminal=current
            else:require(terminal==current,'Terminal receipt changed')
            require(b.get('terminalMarkersAbsent') is True and b.get('terminalReservationsAbsent') is True,'Incomplete native terminal cleanup')
            for w in workers.values():
                closed_worker(w,data['owner'])
                require(w.get('dimension')=='minecraft:overworld' and w.get('currentArea') is None,'Terminal worker is not loaded and detached in the build dimension')
                require(not set(compound(w['inventory']['persistent'])).intersection(C.ASSOCIATIONS),'Terminal worker retained work/storage selectors')
        else:
            p=project_snapshot(b.get('projectSnapshot'),original,stages)
            require(snbt_list(store['Entries'])==[p] and store['Terminals']=='[]','Boundary project not authoritative')
            revision=number(p['Revision'],'L'); require(revision>=previous_revision,'Project revision rewound');previous_revision=revision
            active=number(p['Active']); receipts=snbt_list(p['Receipts'])
            leases=snbt_list(compound(ledger['ProjectLeases'])['Projects']);require(len(leases)==1,'Missing live project lease')
            lease=leases[0];lease_index=number(lease['Active'])
            if p['State']=='"RUNNING"':require(lease_index==active and lease['Retired']=='0b' and len(attempts)==active+1 and attempts[active]['State']=='"LIVE"','Running project, native journal and live lease disagree')
            elif p['State']=='"STAGE_VERIFIED"':require(lease_index==active and len(attempts)==active+1,'Verified stage lease/journal mismatch')
            elif p['State']=='"WAITING_FOR_NEXT_STAGE"':require(lease_index==active-1 and lease['Retired']=='1b' and len(attempts)==active and attempts[-1]['State']=='"RETIRED"','Next-stage wait skipped retirement')
            if member is not None and kind!='l-authoritative-canceled':require(member['Resolved']=='0b' and member['RetiredStage']=='-1','Resolved/retired helper reactivation')
            require(b.get('receipts') is not None and [compound(r) for r in b['receipts']]==receipts,'Missing exact captured receipt prefix')
            for c in claims:require(c.get('worker') in data['workers'] and c.get('area')==nbt_uuid(stages[active]['Area']) and c.get('target') in {t['cell'] for t in partition['stages'][active]['targets']},'Foreign boundary claim')
            if kind in BOUNDARIES[:4]:require(p['State']=='"RUNNING"' and active==0 and not receipts,'A0 verified before finalized disk removal')
            elif kind.startswith('l-stage-'):
                i=int(kind.split('-')[2]);require(len(receipts)==i+1,'Wrong verification prefix')
                require({t['cell'] for t in partition['stages'][i]['targets']}<=seen,'Stage receipt lacks exact world completion')
                if kind.endswith('retired'):
                    require(len(attempts)>i and attempts[i]['State']=='"RETIRED"' and b.get('markerPresent') is False and b.get('leasePresent') is False,'Native retirement marker/lease/journal proof missing')
            elif kind=='l-authoritative-canceled':require(p['State']=='"CANCELED"' and active==2 and len(receipts)==2,'Cancellation missed unfinished A2')
            else:require(p['State']=='"RUNNING"' and active==2 and len(receipts)==2,'Load/return/work did not occur at unfinished RUNNING A2')
            if kind in BOUNDARIES[4:13] and grants==[0]:require(member['Active']=='0b' and member['Suspended']=='1b' and member['RejoinStage']=='-1' and member['Resolved']=='0b','Absent/holding helper was not suspended')
            if kind in ('l-helper-a2-active','l-helper-a2-placement') or kind=='l-return-settled' and grants==[0,2]:require(member['Active']=='1b' and member['Suspended']=='0b' and member['RejoinStage']=='-1','A2 authority was not finally activated')
            if kind in ('l-helper-a2-active','l-helper-a2-placement') or kind=='l-return-settled' and grants==[0,2]:
                helper_worker=workers[helper];area=nbt_uuid(stages[2]['Area'])
                persistent=associations(helper_worker['inventory']['persistent'],data,area,True)
                require(helper_worker['currentArea']==area and number(persistent['SiegePlayerFortificationPos'],'L')==marker_position(bounds),'Active A2 boundary lacks exact native current-area/marker authority')
            if kind=='l-return-settled' and grants==[0]:
                require(workers[helper]['currentArea'] is None and not set(compound(workers[helper]['inventory']['persistent'])).intersection(C.ASSOCIATIONS-{'SiegeWholePerimeter'}),'Suspended return retained partial A2 authority')
        if kind=='l-before-outbound':require(all(counts.values()),'Missing either original A0 contribution')
        if kind=='l-finalized-disk-removal':
            require(b.get('remainingA0Targets')==30-sum(cell in seen for cell in (t['cell'] for t in partition['stages'][0]['targets'])) and not any(c['worker']==helper for c in claims),'Finalized snapshot remaining count/absent claim mismatch')
        if kind=='l-authoritative-canceled':frozen=(stock,world)
        if compact:require(frozen==(stock,world),'Postcancel world/individual material changed before compaction')
    return terminal


def verify_mirrors(data):
    joins=data['nativeWorkerJoins'];events=data['events'];arms=[e for e in events if e.get('kind')=='load-mirror-armed'];ends=[e for e in events if e.get('kind')=='load-mirror-restored']
    require(len(arms)==len(ends)==len(joins)==3,'Missing exact observed mirror lifecycle')
    for i,(join,arm,end) in enumerate(zip(joins,arms,ends)):
        require(join['sequence']<arm['sequence']<end['sequence'] and (i==2 or end['sequence']<joins[i+1]['sequence']) and arm.get('worker')==end.get('worker')==data['workers'][1],'Missing/reordered actor mirror lifecycle')
        raw=inventory(arm.get('raw'));armed=inventory(arm.get('armed'));after=inventory(end.get('after'))
        require(raw==join['inventory'] and cargo(raw)==cargo(armed)==cargo(after) and end.get('before')==armed and C.only_hand_arm(raw['persistent'],armed['persistent']),'Changed raw/admitted/restored cargo')
        require(type(arm.get('split')) is bool and end.get('split') is arm['split'] and end.get('mainAliasesSlot5') is True and arm.get('thirdSlotAlias') is False,'Invalid mirror identity proof')
        require(raw['slots'][5]==raw['main'] and raw['beforeItemSlot']==-1,'Split unequal or borrowed hand mirror')
        stack=compound(raw['main']);count=number(stack['Count'],'b',0,64) if stack else 0
        require(arm.get('slot5Count')==count and integer(arm.get('slot5Maximum'),1,64)>=count,'Oversized/noncanonical hand mirror')
        old=integer(end.get('rebindsBefore'));new=integer(end.get('rebindsAfter'));require(new==old+int(arm['split']),'Missing/double mirror rebind')
        expected=compound(armed['persistent']);actual=compound(after['persistent']);require(expected.pop(C.HAND,None)=='1b' and C.HAND not in actual,'Missing mirror arm/restore')
        expected.pop('SiegeConstructionPause',None)
        if arm['split']:expected[C.REBINDS]=str(new)
        require(int(compound(raw['persistent']).get(C.REBINDS,'0'))==old,'Wrong saved mirror counter')
        candidates=[expected]
        if i==1:candidates.append({k:v for k,v in expected.items() if k not in C.ASSOCIATIONS-{'SiegeWholePerimeter'}})
        require(actual in candidates,'Restoration changed unrelated persistent data/partial associations')


def verify_helper_proof(data,b,stages):
    """Bind the pure Java historical decision to raw actor, member and paid history."""
    value=b.get('helperProof');require(isinstance(value,dict),'Missing historical helper decision')
    state_name=value.get('state');require(state_name in ('STALE_A0_SUSPENDED','DETACHED_SUSPENDED'),'Rejected/misused holding historical hand decision')
    if b['kind'] in ('l-holding-cleanup-ready','l-before-native-return'):require(state_name=='DETACHED_SUSPENDED','Native return preceded exact historical detachment')
    for key,expected in [('helper',data['workers'][1]),('owner',data['owner']),('project',data['projectId']),('generation',data['projectGeneration']),('manifestHash',data['manifestHash']),('ledgerGeneration',data['ledgerGeneration']),('core',data['coreKey']),('coordinator',data['workers'][0]),('originalHandReceipt',data['originalHelperHandReceiptId']),('handArea',data['firstStageArea']),('admissionArea',data['firstStageArea']),('unresolvedProject',data['projectId'])]:require(value.get(key)==expected,'Foreign historical proof identity: '+key)
    require(value.get('current')==dict(state='RUNNING',activeStage=2,verifiedStages=2),'Historical proof used outside RUNNING A2')
    active=state_name=='ACTIVE_A2';stale=state_name=='STALE_A0_SUSPENDED';grants=[0,2] if active else [0]
    require(value.get('cleanupAreas')==[V for i,V in enumerate([nbt_uuid(s['Area']) for s in stages]) if i in grants],'Wrong member-specific historical cleanup scope')
    for key in ('originalHandExact','frozenPersistentExact','pastRetired','a2Lease','destructionAbsent'):require(value.get(key) is True,'Missing historical exactness proof')
    require(value.get('currentStageGranted') is active,'Historical grant conflated with work authority')
    hand=dict(receipt=data['originalHelperHandReceiptId'],builder=data['workers'][1],owner=data['owner'],area=data['firstStageArea'],generation=data['ledgerGeneration'])
    for k in ('originalHand','actorHand','ledgerHand','frozenHand'):require(value.get(k)==hand,'Historical original/actor/ledger/frozen hand differs')
    require(type(value.get('pendingHand')) is bool and (not active or not value['pendingHand']),'Pending hand active authority')
    if b['kind'] in ('l-holding-cleanup-ready','l-before-native-return'):require(value['pendingHand'] is False,'Pending hand authorized native transfer')
    integer(value.get('rebinds'));m=value.get('member')
    expected=dict(admittedStage=0,active=active,ready=True,withdrawn=False,retiredStage=-1,suspended=not active,rejoinStage=-1,grantedStages=grants,legacyEnd=-1,resolved=False,destroyed=False)
    exact(m,expected,'Historical pure decision member differs')
    expected_receipts=[dict(project=data['projectId'],generation=data['projectGeneration'],manifestHash=data['manifestHash'],stageIndex=i,areaId=nbt_uuid(stages[i]['Area']),stageDigest=string(stages[i]['Digest']),verifiedTargets=COUNTS[i]) for i in (0,1)]
    exact(value.get('receipts'),expected_receipts,'Historical proof lacks original exact paid A0/A1 receipts')
    actor=b['workers'][data['workers'][1]];inv=closed_worker(actor,data['owner'],allow_pending=b['kind']=='l-holding-disk-join');p=compound(inv['persistent']);association=value.get('association')
    require(actor['handPending'] is value['pendingHand'] and actor['handRebindCount']==value['rebinds'],'Historical decision contradicts raw hand lifecycle')
    associations(inv['persistent'],data,None,False)
    require(actor.get('dimension')=='minecraft:the_nether','Historical holding evidence is in a different dimension')
    require(isinstance(association,dict) and set(association)=={'wholeSelector','protectedChild','protectedGeneration','nativeArea','nativeOwner','currentArea'},'Incomplete historical association channels')
    channels={'wholeSelector':'SiegeWholePerimeter','protectedChild':'SiegeProtectedAreaReceipt','protectedGeneration':'SiegeProtectedLedgerGeneration','nativeArea':'SiegePlayerFortificationAreaId','nativeOwner':'SiegePlayerFortificationOwner'}
    for k,key in channels.items():require(association[k] is (key in p),'Raw historical association contradicts pure decision')
    require(association['wholeSelector'] is True,'Historical continuation lost whole selector')
    require(association['currentArea']==(actor['currentArea'] or ''),'Historical decision contradicts raw current-area pointer')
    if stale or active:
        area=nbt_uuid(stages[2 if active else 0]['Area'])
        associations(actor['inventory']['persistent'],data,area,not value['pendingHand'])
        require(all(association[k] is True for k in channels),'Partial stale/live native association')
        for key,expected in [('SiegeProtectedAreaReceipt',area),('SiegeProtectedLedgerGeneration',data['ledgerGeneration']),('SiegePlayerFortificationAreaId',area),('SiegePlayerFortificationOwner',data['owner'])]:require(nbt_uuid(p.get(key))==expected,'Contradictory historical persistent channel')
        require(association['currentArea'] in ([area] if active else ['',area]),'Wrong historical transient area')
        require(number(p.get('SiegePlayerFortificationPos'),'L')==marker_position({e['kind']:e for e in data['events'] if e.get('kind') in BOUNDARIES}),'Historical native position differs from original marker')
    else:
        require(all(association[k] is False for k in channels if k!='wholeSelector') and association['currentArea']=='' and not set(p).intersection(C.ASSOCIATIONS-{'SiegeWholePerimeter'}),'Detached continuation has partial live authority')
    frozen=compound(data['finalizedOfflineInventory']['persistent']);allowed=C.ASSOCIATIONS|{C.HAND,C.REBINDS,'SiegeConstructionPause'}
    require({k:v for k,v in frozen.items() if k not in allowed}=={k:v for k,v in p.items() if k not in allowed},'Historical inspection changed unrelated persistent data')


def unchanged_inventory(before,after,allow_detach=False):
    """Full cargo/equipment and persistent continuity across nonworking custody."""
    require(cargo(before)==cargo(after),'Unexplained cross-boundary cargo/equipment change')
    first=compound(before['persistent']);last=compound(after['persistent']);allowed=[first]
    if allow_detach:
        detached={k:v for k,v in first.items() if k not in C.ASSOCIATIONS-{'SiegeWholePerimeter'}}
        detached.pop('SiegeConstructionPause',None);allowed.append(detached)
    require(last in allowed,'Cross-boundary persistent data changed outside exact historical detachment')


def verify_custody_chain(data,bounds):
    helper=data['workers'][1];joins=data['nativeWorkerJoins']
    restored=[e for e in data['events'] if e.get('kind')=='load-mirror-restored']
    require(len(restored)==3,'Missing cross-load restored custody chain')
    def snapshot(kind):return bounds[kind]['workers'][helper]['inventory']
    unchanged_inventory(snapshot('l-before-outbound'),joins[0]['inventory'])
    unchanged_inventory(restored[0]['after'],snapshot('l-outbound-settled'))
    unchanged_inventory(restored[0]['after'],data['finalizedOfflineInventory'])
    for kind in BOUNDARIES[3:9]:unchanged_inventory(data['finalizedOfflineInventory'],snapshot(kind))
    holding=snapshot('l-holding-disk-join')
    if pending_flag(holding['persistent']):
        armed=[e for e in data['events'] if e.get('kind')=='load-mirror-armed'][1]
        require(holding==armed['armed'],'Holding pending snapshot is not the exact recorded native admission')
    else:unchanged_inventory(restored[1]['after'],holding,allow_detach=True)
    unchanged_inventory(restored[1]['after'],snapshot('l-holding-cleanup-ready'),allow_detach=True)
    unchanged_inventory(snapshot('l-holding-cleanup-ready'),snapshot('l-before-native-return'))
    unchanged_inventory(snapshot('l-before-native-return'),joins[2]['inventory'])
    # END HIGHEST captures restoration before controller NORMAL activation.
    # resumeCrewMember/linkWorker add precisely these five A2 bindings; their
    # actual values/current pointer/active ledger are authenticated above.
    require(cargo(restored[2]['after'])==cargo(snapshot('l-return-settled')),'Returned settled custody changed restored cargo')
    first=compound(restored[2]['after']['persistent']);last=compound(snapshot('l-return-settled')['persistent'])
    if bounds['l-return-settled']['workers'][helper]['currentArea'] is not None:
        keys={'SiegeProtectedAreaReceipt','SiegeProtectedLedgerGeneration','SiegePlayerFortificationAreaId','SiegePlayerFortificationOwner','SiegePlayerFortificationPos'}
        require(not set(first).intersection(keys),'Return restoration was not detached before activation')
        first.update({key:last[key] for key in keys})
    require(last==first,'Settled return changed persistent data outside exact A2 activation bindings')
    for kind in ('l-before-outbound','l-holding-cleanup-ready','l-before-native-return'):
        closed_worker(bounds[kind]['workers'][helper],data['owner'])


def verify_helper_persistent_observations(data,bounds,stages):
    """Exact post-return projections, with only independently recorded storage writes.

    A zero-material storage callback is not emitted by this fixed driver schema.
    Its dirty-counter advance therefore fails closed; it is not a production bug
    or permission to ignore arbitrary operation/persistent changes.
    """
    helper=data['workers'][1];dirty='SiegeProtectedStorageDirtyWrites';cleanup='SiegeProtectedInventoryCleanup'
    restored=[e for e in data['events'] if e.get('kind')=='load-mirror-restored'][2]
    base=compound(restored['after']['persistent']);original=compound(data['acceptedProject'])
    require(cleanup not in base and 'SiegeConstructionPause' not in base,'Returned helper did not close its prior operation')
    initial_dirty=number(base.get(dirty,'0'),minimum=0,maximum=2**31-1)
    active=base|{'SiegeProtectedAreaReceipt':stages[2]['Area'],
        'SiegeProtectedLedgerGeneration':compound(data['originalHelperHandReceipt'])['Generation'],
        'SiegePlayerFortificationAreaId':stages[2]['Area'],'SiegePlayerFortificationOwner':original['Owner'],
        'SiegePlayerFortificationPos':str(marker_position(bounds))+'L'}
    terminal={key:value for key,value in base.items() if key!='SiegeWholePerimeter'}
    work=[e for e in data['nativeWork'] if e['worker']==helper and e['stage']==2]
    supplies=[e for e in work if e['kind']=='native-supply']
    def check(raw,sequence,projections,supply=False):
        actual=compound(raw);prior=sum(e['sequence']<sequence for e in supplies)
        extra={}
        if cleanup in actual:
            require(supply and compound(actual[cleanup])=={'Version':'1','NEEDED':'1'},'A2 cleanup is not an exact recorded live supply checkpoint')
            extra[cleanup]=actual[cleanup]
        expected=[]
        for projection in projections:
            value=projection|extra
            if dirty in base or prior:value[dirty]=str(min(2**31-1,initial_dirty+prior))
            expected.append(value)
        require(actual in expected,'A2/terminal persistent data changed outside authenticated operation and association deltas')
    for e in work:check(e['authority']['workerInventory']['persistent'],e['sequence'],[active],e['kind']=='native-supply')
    for kind in ('l-helper-a2-active','l-helper-a2-placement','l-authoritative-canceled','l-compacted-loaded','l-final-stable'):
        b=bounds[kind]
        # A live canceled cut can precede, follow, or occur between exact native
        # child detachment and whole-link clearing. No unrelated tag is exempted.
        projections=([active,base,terminal] if kind=='l-authoritative-canceled' and b.get('authoritySource')=='live-project'
            else [terminal] if kind in ('l-authoritative-canceled','l-compacted-loaded','l-final-stable') else [active])
        check(b['workers'][helper]['inventory']['persistent'],b['sequence'],projections)


def verify_release_order(data,bounds):
    events=data['events'];tickets=data['activeHelperTicketEvents'];transfers=data['activeHelperTransitEvents']
    ends=[e for e in events if e.get('kind')=='ordinary-end-settled-custody'];sync=[e for e in transfers if e['kind']=='transfer-settled'];remove=[e for e in tickets if e['kind']=='remove-pending'];removed=[e for e in tickets if e['kind']=='remove-return']
    require(len(ends)==len(sync)==len(remove)==len(removed)==2,'Missing exact two later-END custody cuts')
    for i,(before,kind,phase,dimension) in enumerate([('l-before-outbound','l-outbound-ticket-released','L_SETTLE','minecraft:the_nether'),('l-before-native-return','l-return-ticket-released','L_RETURN_SETTLE','minecraft:overworld')]):
        release=[e for e in events if e.get('kind')==kind];require(len(release)==1,'Missing release cut');release=release[0];end=ends[i]
        require(end.get('worker')==data['workers'][1] and end.get('dimension')==dimension and end.get('phase')==release.get('phase')==phase,'Wrong later-END custody')
        require(bounds[before]['gameTime']==end.get('transferGameTime')==release.get('transferGameTime')<end.get('gameTime')==release.get('settledGameTime')==release.get('gameTime') and bounds[before]['sequence']<end['sequence']<release['sequence'],'Same-tick/stale/reordered custody release')
        times=[integer(e.get('nanoTime'),1) for e in (sync[i],end,remove[i],removed[i],release)]; require(times==sorted(set(times)),'Release preceded later ordinary-END custody')
        require(any(e['kind']=='zero-owned-verified' and times[3]<integer(e.get('nanoTime'),1)<times[4] for e in tickets),'Release lacked exact subsequent zero ticket census')


def verify_report(data):
    require(isinstance(data,dict) and type(data.get('formatVersion')) is int and data['formatVersion']==1 and data.get('mode')=='active-helper-later-stage-disk' and data.get('status')=='passed' and data.get('phase')=='FINISHED','Case L did not pass')
    require('failure' not in data and 'ticketCleanupFailure' not in data,'Failure hidden by pass flag')
    for k in ('boundedScenarioVerified','laterStageReturnVerified','helperDiskEvictionVerified','postDiskHelperPlacementVerified','stageSkippedReturnVerified'):require(data.get(k) is True,'Missing bounded Case L success: '+k)
    for k in ('sameStageReturnVerified','coordinatorFirstDiskOrderVerified','canceledWhileAbsentVerified','wholeSiteEvictionVerified','wholePlanCompleted','nativeWorkerChunkLoading'):require(data.get(k) is False,'Unsupported coverage claim: '+k)
    for k,v in dict(totalLimitSeconds=600,phaseLimitTicks=1200,evictionLimitTicks=1800,finalizationLimitTicks=200,stableGroundedEndTicks=5,absenceObservationTicks=100,paidFee=64,qaSectionTargetCap=64).items():require(type(data.get(k)) is int and data[k]==v,'Changed fixed bound: '+k)
    require(data.get('syntheticPartition') is True and data.get('materialCounts')==dict(zip(MATERIALS,(352,220))) and data.get('ownerMode')=='non-op Survival','Changed bounded fixture')
    exact(data['materialCounts'],dict(zip(MATERIALS,(352,220))),'Malformed exact finite material counts')
    elapsed=data.get('elapsedSeconds');require(type(elapsed) in (int,float) and 0<elapsed<600 and math.isfinite(elapsed),'Changed runtime bound')
    for k in ('sourceCommit','sourceTree'):require(isinstance(data.get(k),str) and re.fullmatch('[0-9a-f]{40}',data[k]),'Missing exact source identity')
    ids=data.get('workers');require(isinstance(ids,list) and len(ids)==len(set(ids))==2,'Not exactly two original actors')
    for value in ids+[data.get('owner'),data.get('projectId'),data.get('ledgerGeneration'),data.get('firstStageArea'),data.get('originalHelperHandReceiptId')]:uid(value)
    require(data['owner'] not in ids and data['projectId'] not in ids and re.fullmatch('[0-9a-f]{64}',data.get('manifestHash','')),'Foreign/aliased paid identity')
    integer(data.get('projectGeneration'),1)
    C.verify_original_hand(data.get('originalHelperHandReceipt'),data['originalHelperHandReceiptId'],ids[1],data['owner'],data['firstStageArea'],data['ledgerGeneration'])
    initial=data.get('initialSetupAdmissions')
    require(isinstance(initial,list) and len(initial)==2 and {v.get('worker') for v in initial}==set(ids),'Missing exact synthetic precommission admissions')
    for v in initial:require(set(v)=={'worker','dimension','loadedFromDisk','syntheticPrecommission'} and v['dimension']=='minecraft:overworld' and v['loadedFromDisk'] is False and v['syntheticPrecommission'] is True,'Initial admission hidden or confused with postcommission lifecycle')
    plan,partition=candidate(data);original,stages,target=accepted_project(data,plan,partition)
    P.serializer_resource_names(data)
    exact(data.get('firstStageTargets'),[t['cell'] for t in partition['stages'][0]['targets']],'Changed first-stage targets')
    exact(data.get('firstStageMaterials'),{str(t['cell']):t['material'] for t in partition['stages'][0]['targets']},'Changed first-stage materials')
    for items,limit in ((data.get('events'),4096),(data.get('nativeWork'),1024),(data.get('nativeWorkerJoins'),16)):ordered(items,'sequence',limit)
    events,work,joins=data['events'],data['nativeWork'],data['nativeWorkerJoins'];all_events=events+work+joins
    sequences=[e['sequence'] for e in all_events];require(len(sequences)==len(set(sequences)),'Aliased evidence sequence')
    bounds={}
    for k in BOUNDARIES:
        found=[e for e in events if e.get('kind')==k];require(len(found)==1,'Missing/duplicate Case L boundary: '+k);bounds[k]=found[0]
    require([bounds[k]['sequence'] for k in BOUNDARIES]==sorted(bounds[k]['sequence'] for k in BOUNDARIES),'Reordered required Case L boundaries')
    timed=[e for e in sorted(all_events,key=lambda e:e['sequence']) if 'gameTime' in e]
    require(all(integer(b['gameTime'])>=integer(a['gameTime']) for a,b in zip(timed,timed[1:])),'Evidence game time reversed')
    phases=[e for e in events if e.get('kind')=='phase']
    expected_phases=['COMMISSION','BOTH_WORK','L_OUT','L_SETTLE','L_EVICT','L_OFFLINE_A0','L_OFFLINE_A1','L_WAIT_A2','L_LOAD','L_HOLDING','L_RETURN','L_RETURN_SETTLE','L_REJOIN','L_CANCEL_MENU','L_CANCEL_WAIT','L_FINAL_STABLE','FINISHED']
    require([e.get('phase') for e in phases]==expected_phases,'Missing/repeated/unapproved phase transition or timeout reset')
    for a,b in zip(phases,phases[1:]):
        require(0<=integer(b.get('gameTime'))-integer(a.get('gameTime'))<=(1800 if a.get('phase')=='L_EVICT' else 1200),'Phase exceeded unchanged timeout')
    trace=verify_work(data,bounds,original,stages,partition)
    terminal=verify_boundaries(data,bounds,original,stages,partition,target,trace)
    for k in ('l-holding-disk-join','l-holding-cleanup-ready','l-before-native-return'):verify_helper_proof(data,bounds[k],stages)
    removal=data.get('offlineRemovalProof');require(isinstance(removal,dict) and removal.get('reason')=='UNLOADED_TO_CHUNK' and removal.get('shouldDestroy') is False and removal.get('shouldSave') is True and removal.get('chunkAvailable') is False and removal.get('registered') is False and removal.get('allDimensionsAbsent') is True,'No finalized native nondestructive disk absence')
    start=bounds['l-finalized-disk-removal'];ready=bounds['l-a2-ready-helper-offline'];canceled=bounds['l-authoritative-canceled'];final=bounds['l-final-stable']
    require(removal.get('gameTime')==start['gameTime'] and 0<=integer(removal['gameTime'])-integer(removal.get('unavailableAt'))<=200,'Delayed/unfinalized immediate removal snapshot')
    releases=[e for e in events if e.get('kind')=='l-outbound-ticket-released'];require(len(releases)==1 and 0<start['gameTime']-releases[0]['gameTime']<=1800,'Expanded eviction window')
    require(100<=ready['gameTime']-start['gameTime'],'Genuine finalized disk absence shorter than 100 ticks')
    require(type(data.get('genuineDiskAbsenceTicks')) is int and data['genuineDiskAbsenceTicks']==ready['gameTime']-start['gameTime'] and type(data.get('remainingA0AtFinalizedRemoval')) is int and data['remainingA0AtFinalizedRemoval']==start['remainingA0Targets'],'Reported disk absence/remaining A0 coverage differs from raw cuts')
    adds=[e for e in data['activeHelperTicketEvents'] if e.get('kind')=='add-pending']
    require(len(adds)==2 and integer(start.get('nanoTime'),1)<integer(ready.get('nanoTime'),1)<integer(adds[1].get('nanoTime'),1),'Holding load requested before genuine A2 readiness')
    require(len(joins)==3 and all(e.get('worker')==ids[1] for e in joins) and [e.get('loadedFromDisk') for e in joins]==[False,True,False] and [e.get('dimension') for e in joins]==['minecraft:the_nether','minecraft:the_nether','minecraft:overworld'],'Wrong postcommission helper admissions/coordinator join')
    require(bounds['l-before-outbound']['sequence']<joins[0]['sequence']<bounds['l-outbound-settled']['sequence'] and ready['sequence']<joins[1]['sequence']<bounds['l-holding-disk-join']['sequence'] and bounds['l-before-native-return']['sequence']<joins[2]['sequence']<bounds['l-return-settled']['sequence'],'Early/late native helper admission')
    require(bounds['l-helper-a2-active']['sequence']>joins[2]['sequence'],'Activation before return admission')
    require(inventory(data.get('finalizedOfflineInventory'))==inventory(joins[1].get('inventory')),'Raw disk inventory/receipt changed')
    associations(data['finalizedOfflineInventory']['persistent'],data,data['firstStageArea'],True)
    require(bounds['l-helper-a2-placement']['placementCounts'][ids[1]]>bounds['l-helper-a2-active']['placementCounts'][ids[1]],'No new helper placement after A2 activation')
    require(integer(data.get('cancellationStableTicks'),100)==final['gameTime']-canceled['gameTime'] and data.get('canceledAuthorityGameTime')==canceled['gameTime'],'Stable clock did not start at first authoritative cancellation')
    require(canceled.get('authoritySource') in ('live-project','compact-terminal') and canceled.get('authority')==(canceled['projectSnapshot'] if canceled['authoritySource']=='live-project' else canceled.get('terminalReceipt')),'Missing first server-authoritative cancellation cut')
    brackets=[e for e in events if e.get('kind')=='terminal-native-bracket'];require(len(brackets)==integer(data.get('terminalObservedBrackets'),200,9999),'Missing continuous postcancel participant observer')
    observed=set()
    for e in brackets:
        actor=e.get('worker');tick=integer(e.get('gameTime'));require(actor in ids and (actor,tick) not in observed and canceled['gameTime']<=tick<=final['gameTime'] and canceled['sequence']<e['sequence']<final['sequence'],'Reordered/duplicate/unbounded terminal bracket')
        require(e.get('authorized') is False and type(e.get('changedTargets')) is int and e['changedTargets']==0 and e.get('targetStatesUnchanged') is True and e.get('before')==e.get('after')==canceled['stock'][actor]+canceled['stock']['chest'],'Postcancel supply/placement/per-actor custody changed')
        require(all(type(n) is int for n in e['before']+e['after']),'Malformed postcancel stock counters')
        observed.add((actor,tick))
    for tick in range(canceled['gameTime']+1,final['gameTime']+1):
        for actor in ids:require((actor,tick) in observed,'Participant observer gap before/after compaction')
    seal=data.get('evidenceSeal');require(isinstance(seal,dict) and set(seal)=={'sequence','gameTime','serverEnd'} and seal['serverEnd'] is True and seal['gameTime']==final['gameTime'] and integer(seal['sequence'],1)==max(sequences)+1,'Missing successful server-END seal or late evidence writes')
    C.verify_transfers(data,ids[1],data['owner']);C.verify_tickets(data);C.verify_utility_chronology(data);verify_release_order(data,bounds);verify_mirrors(data);verify_custody_chain(data,bounds)
    verify_helper_persistent_observations(data,bounds,stages)
    materials(data.get('finalMaterials'));require(data['finalMaterials']==final['materials'],'Final material receipt differs')
    a,b=data.get('auxiliaryIsolationInitial'),data.get('auxiliaryIsolationFinal')
    require(isinstance(a,dict) and isinstance(b,dict) and {k:v for k,v in a.items() if k!='gameTime'}=={k:v for k,v in b.items() if k!='gameTime'} and integer(b.get('gameTime'))>integer(a.get('gameTime')) and a.get('feetY')==48 and a.get('standingVerticalRange')==12 and isinstance(a.get('auxiliaries'),list) and 1<=len(a['auxiliaries'])<=4 and len(a.get('baseline',[]))==45*len(a['auxiliaries']),'Auxiliary fixture isolation changed')
    treasury=data.get('treasuryAccounting');require(isinstance(treasury,dict) and treasury.get('startingFunds')==2000 and treasury.get('commissionDebit')==64 and treasury.get('initialPaidBalance')==1936 and treasury.get('singleNegativeLedgerDelta')==-64 and treasury.get('expectedFinalBalance')==1936+integer(treasury.get('interestCredits'))+integer(treasury.get('taxCredits')),'Double fee/refund/changed Treasury')
    require(data.get('activeHelperHoldingSetup',{}).get('synthetic') is True and data['activeHelperHoldingSetup'].get('dimension')=='minecraft:the_nether','Changed reviewed holding dimension')
    require(data.get('screenshots')==['03-authenticated-cancel.png','04-helper-lifecycle-complete.png'],'Missing exact frame evidence')
    return True


def verify_runtime(data,evidence,runtime_manifest):
    gate_path=evidence/'api-gate/gate.json';gate=BASE.GATE.verify_receipt(runtime_manifest,gate_path)
    for field,file in [('sourceCommit','source-commit.txt'),('sourceTree','source-tree.txt')]:require(data.get(field)==gate[field]==(evidence/file).read_text().strip(),'Source/prelaunch identity differs')
    parity=data.get('loadedApiParity');require(isinstance(parity,dict) and parity.get('passed') is True and parity.get('gateSha256')==hashlib.sha256(gate_path.read_bytes()).hexdigest() and parity.get('contractSha256')==gate['reviewedGateContractSha256'],'Missing loaded API parity')
    expected={c['name']:{'provider':c['supplierCanonicalPath'],'supplierSha256':c['supplierSha256'],'rawResourceSha256':c['classSha256']} for c in gate['classes']}
    require(parity.get('classes')==expected,'Loaded runtime class/provider closure differs')
    P.verify_attestations(data,gate,hashlib.sha256(gate_path.read_bytes()).hexdigest())
    for name in data['screenshots']:
        image=evidence/name;require(image.is_file() and image.stat().st_size>4096 and image.read_bytes()[:8]==b'\x89PNG\r\n\x1a\n','Missing actual PNG evidence')


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('evidence',type=Path);parser.add_argument('runtime_manifest',type=Path);args=parser.parse_args()
    try:
        path=args.evidence/'result.json';require(path.stat().st_size<=32*1024*1024,'Result exceeds unchanged bound')
        data=BASE.GATE.CAPTURE.parse_unique(path.read_bytes());verify_report(data);verify_runtime(data,args.evidence,args.runtime_manifest)
    except (ValueError,KeyError,TypeError,OSError,IndexError) as error:parser.exit(1,f'Later-stage helper rejected: {error}\n')
    remaining=data['remainingA0AtFinalizedRemoval']
    scope='remaining A0 placement observed offline' if remaining else 'A0 completion-barrier-only interval'
    print(f'Verified bounded A0 disk absence ({remaining} A0 targets remaining: {scope}), exact A0/A1 retirement, A2 native return/work, and A2/2 cancellation; no whole-project completion claim.')


if __name__=='__main__':main()
