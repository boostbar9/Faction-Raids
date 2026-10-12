"""Synthetic oracle/source boundaries only; these tests do not emulate native gameplay."""
import copy
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('active_helper_oracle', ROOT/'scripts/verify-native-active-helper.py')
VERIFY = importlib.util.module_from_spec(SPEC); SPEC.loader.exec_module(VERIFY)
C, H, O, A, P, G, T = [f'00000000-0000-0000-0000-{n:012d}' for n in range(1, 8)]


def inv():
    return {'slots': ['{}']*6, 'main': '{}', 'off': '{}', 'beforeItemSlot': -1, 'persistent': '{}'}


def fixture():
    data = dict(formatVersion=1, status='passed', phase='FINISHED', mode='active-helper-same-stage-disk',
                boundedScenarioVerified=True, sameStageReturnVerified=True, helperDiskEvictionVerified=True,
                coordinatorFirstDiskOrderVerified=True, postDiskHelperPlacementVerified=True,
                stageSkippedReturnVerified=False, canceledWhileAbsentVerified=False, wholeSiteEvictionVerified=False,
                wholePlanCompleted=False, nativeWorkerChunkLoading=False, totalLimitSeconds=600, phaseLimitTicks=1200,
                evictionLimitTicks=1800, finalizationLimitTicks=200, stableGroundedEndTicks=5, absenceObservationTicks=100,
                paidFee=64, elapsedSeconds=150.0, cancellationStableTicks=100, ownerMode='non-op Survival',
                workers=[C,H], owner=O, projectId=P, ledgerGeneration=G, firstStageArea=A,
                manifestHash='a'*64, firstStageTargets=list(range(139)), firstStageMaterials={str(i):'minecraft:cobblestone' for i in range(139)}, events=[], nativeWork=[], nativeWorkerJoins=[])
    counts, stock, chest, sequence, cell = {C:0,H:0}, {C:0,H:0}, 352, 0, 0
    def census():
        return dict(cobble=dict(built=sum(counts.values()), chest=chest, workerCustody=sum(stock.values()), owner=0, loose=0, total=352),
                    oak=dict(built=0,chest=220,workerCustody=0,owner=0,loose=0,total=220))
    def event(kind, tick, suspended=False, active=False, boundary=True, **extra):
        nonlocal sequence
        sequence += 1
        value = dict(sequence=sequence, kind=kind, gameTime=tick)
        if boundary:
            value.update(placementCounts=counts.copy(), pendingTargets=572-sum(counts.values()), ownerMode='non-op Survival', materials=census(),
                         projectState='RUNNING',stage=0,helperSuspended=suspended,helperActive=active,helperGranted=active,helperRejoining=False)
        if boundary:
            value.update(workers={actor:dict(dimension='minecraft:the_nether' if actor==H and suspended else 'minecraft:overworld',
                                            removed=False,inventory=inv(),handPending=False,mainAliasesSlot5=True,nativeState='PLACE_BLOCKS',entityTick=tick,handRebindCount=0)
                                  for actor in (C,H)},claims=[])
        value.update(extra); data['events'].append(value); return value
    def work(actor, supply=False):
        nonlocal sequence, cell, chest
        before = [stock[actor],0,chest,220]; sequence += 1
        if supply: stock[actor] += 64; chest -= 64; cells=[]
        else: stock[actor] -= 1; counts[actor]+=1; cells=[cell]; cell += 1
        data['nativeWork'].append(dict(sequence=sequence,worker=actor,area=A,kind='native-supply' if supply else 'native-placement',
            gameTime=sequence,cells=cells,before=before,after=[stock[actor],0,chest,220],placedCobble=len(cells),placedOak=0,
            authorized=True,nativeState='TAKE_NEEDED_ITEMS' if supply else 'PLACE_BLOCKS'))
    def join(actor, disk, dimension):
        nonlocal sequence
        sequence += 1; data['nativeWorkerJoins'].append(dict(sequence=sequence,worker=actor,loadedFromDisk=disk,dimension=dimension,inventory=inv()))
        armed=inv();armed['persistent']='{SiegeProtectedHandRefreshPending:1b}'
        event('load-mirror-armed',sequence,boundary=False,worker=actor,raw=inv(),armed=armed,split=True)
        event('load-mirror-restored',sequence,boundary=False,worker=actor,before=armed,after=inv(),split=True,
              mainAliasesSlot5=True,rebindsBefore=0,rebindsAfter=1)
    event('commission',0)
    work(C,True); work(H,True); work(C); work(H); event('before-first-transfer',10,active=True)
    join(H,False,'minecraft:the_nether'); event('ordinary-end-settled-custody',11,boundary=False)
    work(C); event('same-stage-absence',110,suspended=True)
    join(H,False,'minecraft:overworld'); event('first-native-return',111,suspended=True)
    event('ordinary-end-settled-custody',112,boundary=False); work(H); event('same-stage-resumed',120,active=True)
    event('before-disk-transfer',120,active=True)
    join(H,False,'minecraft:the_nether')
    event('ordinary-end-settled-custody',121,boundary=False,worker=H,dimension='minecraft:the_nether',phase='SECOND_SETTLE',transferGameTime=120,nanoTime=320)
    event('eviction-ticket-released-after-settled',121,boundary=False,worker=H,phase='SECOND_SETTLE',transferGameTime=120,settledGameTime=121,nanoTime=360)
    event('phase',121,boundary=False,phase='EVICT')
    event('genuine-helper-disk-unload',500,suspended=True)
    event('actual-server-stop',501,boundary=False,authority='paid',ledger='saved',coordinator=inv(),offlineHelper=inv())
    event('actual-server-reopen-before-ticks',501,boundary=False,authority='paid',ledger='saved')
    join(C,True,'minecraft:overworld'); event('coordinator-first-disk-reopen',502,suspended=True)
    work(C); event('coordinator-native-progress-while-helper-offline',602,suspended=True)
    join(H,True,'minecraft:the_nether'); event('later-helper-disk-join',603,suspended=True)
    join(H,False,'minecraft:overworld'); event('disk-native-return',603,suspended=True)
    event('ordinary-end-settled-custody',604,boundary=False)
    work(H); event('disk-return-resumed',610,active=True)
    event('loaded-workers-canceled',620)
    data.update(placementCounts=counts,retainedPlacedBlocks=sum(counts.values()),finalMaterials=census(),finalizedOfflineInventory=inv(),
                offlineRemovalProof=dict(reason='UNLOADED_TO_CHUNK',shouldDestroy=False,shouldSave=True,chunkAvailable=False,registered=False,gameTime=500,unavailableAt=499))
    events=[]; ordinal=0
    for transfer_index,(source,destination) in enumerate([('minecraft:overworld','minecraft:the_nether'),('minecraft:the_nether','minecraft:overworld')]*2):
        for kind in ['transfer-before','join-high','join-low','dimension-source-leave','transfer-settled']:
            ordinal+=1
            event=dict(ordinal=ordinal,kind=kind,uuid=H,owner=O,dimension=source if kind in ('transfer-before','dimension-source-leave') else destination,
                       alive=True,removed=False,neededItems=0,followState=6,inventory=inv(),nanoTime=[100,200,300,500][transfer_index]+(ordinal-1)%5+1)
            if kind in ('join-low','transfer-settled'): event['inventory']['persistent']='{SiegeProtectedHandRefreshPending:1b}'
            if kind.startswith('join-'): event.update(loadedFromDisk=False,canceled=False)
            if kind=='dimension-source-leave': event['sourceInventoryResampled']=False
            events.append(event)
    data['activeHelperTransitEvents']=events
    ticket_key='minecraft:the_nether/siegeoverhaul/0/TickingEntities/'+T
    baseline={'minecraft:overworld/Forced':'[L;]','minecraft:the_nether/Forced':'[L;]','minecraft:the_end/Forced':'[L;]'}
    tickets=[]; ordinal=0
    for cycle in range(3):
        for kind in ['zero-owned-verified','add-pending','add-return','remove-pending','remove-return','zero-owned-verified']:
            ordinal+=1; census=baseline.copy()
            if kind in ('add-return','remove-pending'): census[ticket_key]='[I;0,0,0,7]'
            event=dict(ordinal=ordinal,kind=kind,owner=T,dimension='minecraft:the_nether',mod='siegeoverhaul',chunkX=0,chunkZ=0,ticking=True,census=census,
                       nanoTime=[[50,51,52,210,211,212],[249,250,251,330,340,350],[449,450,451,510,511,512]][cycle][(ordinal-1)%6])
            if kind in ('add-return','remove-return'): event['nativeReturn']=True
            tickets.append(event)
    data.update(activeHelperTicketOwner=T,activeHelperTicketEvents=tickets,finalOwnedTicketCount=0)
    initial=dict(gameTime=1,feetY=48,standingVerticalRange=12,auxiliaries=[{'uuid':'auxiliary-fixture'}],baseline=[{}]*45)
    data.update(auxiliaryIsolationInitial=initial,auxiliaryIsolationFinal={**initial,'gameTime':720},
                treasuryAccounting=dict(startingFunds=2000,commissionDebit=64,initialPaidBalance=1936,singleNegativeLedgerDelta=-64,
                                        expectedFinalBalance=1936,interestCredits=0,taxCredits=0),
                activeHelperHoldingSetup=dict(synthetic=True,dimension='minecraft:the_nether'),
                screenshots=['03-authenticated-cancel.png','04-helper-lifecycle-complete.png'])
    return data


class HelperOracleTests(unittest.TestCase):
    def reject(self, mutate):
        value=fixture(); mutate(value)
        with self.assertRaises((ValueError, KeyError, TypeError)): VERIFY.verify_report(value)

    def test_exact_synthetic_proof_is_accepted(self): self.assertTrue(VERIFY.verify_report(fixture()))
    def test_flags_are_not_enough(self): self.reject(lambda d:d['events'].clear())
    def test_partial_scope_never_claims_stage_skipped_or_canceled_absence(self):
        for field in ('stageSkippedReturnVerified','canceledWhileAbsentVerified','wholeSiteEvictionVerified','wholePlanCompleted'):
            with self.subTest(field=field): self.reject(lambda d:d.update({field:True}))
    def test_runtime_and_owner_limits_are_fixed(self):
        for field,value in [('totalLimitSeconds',601),('evictionLimitTicks',1801),('elapsedSeconds',600),('ownerMode','Spectator')]:
            with self.subTest(field=field): self.reject(lambda d:d.update({field:value}))
    def test_no_extra_actor_or_hire(self): self.reject(lambda d:d['workers'].append(O))
    def test_missing_supply_or_fake_owner_materials_fails(self):
        self.reject(lambda d:d['nativeWork'].__setitem__(0,{**d['nativeWork'][0],'authorized':False}))
        self.reject(lambda d:d['finalMaterials']['cobble'].update(owner=1,chest=d['finalMaterials']['cobble']['chest']-1))
    def test_bad_native_debit_or_duplicate_cell_fails(self):
        self.reject(lambda d:d['nativeWork'][2]['after'].__setitem__(0,64))
        self.reject(lambda d:d['nativeWork'][3].update(cells=d['nativeWork'][2]['cells']))
    def test_required_boundary_schema_cannot_be_omitted(self):
        for kind in ('commission','loaded-workers-canceled','same-stage-absence'):
            with self.subTest(kind=kind): self.reject(lambda d:next(e for e in d['events'] if e['kind']==kind).pop('placementCounts'))
    def test_world_built_counts_must_match_native_receipts(self):
        def mutate(d):
            value=next(e for e in d['events'] if e['kind']=='same-stage-absence')['materials']['cobble']
            value['built']+=1; value['chest']-=1
        self.reject(mutate)
    def test_helper_must_not_work_inside_absence_windows(self):
        bounds={kind:{'sequence':tick} for kind,tick in [('before-first-transfer',10),('first-native-return',20),('before-disk-transfer',30),('disk-native-return',40)]}
        VERIFY.verify_absence_work([{'worker':C,'sequence':15},{'worker':H,'sequence':25}],bounds,H)
        for tick in (11,19,31,39):
            with self.subTest(tick=tick),self.assertRaisesRegex(ValueError,'Absent helper'):
                VERIFY.verify_absence_work([{'worker':H,'sequence':tick}],bounds,H)
    def test_absence_cannot_keep_stage_grant(self):
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='same-stage-absence').update(helperGranted=True))
    def test_stage_advance_is_not_same_stage_rejoin(self):
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='disk-return-resumed').update(stage=1))
    def test_transfer_cargo_and_raw_receipts_are_exact(self):
        self.reject(lambda d:d['activeHelperTransitEvents'][1]['inventory']['slots'].__setitem__(0,'{Count:1b}'))
        self.reject(lambda d:d['activeHelperTransitEvents'][1]['inventory'].update(persistent='{foreign:1b}'))
    def test_canceled_join_or_missing_registered_custody_fails(self):
        self.reject(lambda d:d['activeHelperTransitEvents'][1].update(canceled=True))
        self.reject(lambda d:d['activeHelperTransitEvents'].pop(4))
    def test_eviction_requires_post_settled_release_and_clock(self):
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='eviction-ticket-released-after-settled').update(gameTime=120))
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='eviction-ticket-released-after-settled').update(settledGameTime=120))
        self.reject(lambda d:[e for e in d['events'] if e['kind']=='ordinary-end-settled-custody'][2].update(gameTime=120))
        self.reject(lambda d:[e for e in d['activeHelperTicketEvents'] if e['kind']=='remove-pending'][1].update(nanoTime=310))
        self.reject(lambda d:next(e for e in d['events'] if e.get('phase')=='EVICT').update(gameTime=120))
        self.reject(lambda d:d['offlineRemovalProof'].update(gameTime=220,unavailableAt=219))
    def test_eviction_custody_cannot_use_wrong_actor_phase_or_dimension(self):
        for change in ({'worker':C},{'phase':'SECOND_OUT'},{'dimension':'minecraft:overworld'}):
            with self.subTest(change=change):
                self.reject(lambda d:[e for e in d['events'] if e['kind']=='ordinary-end-settled-custody'][2].update(change))
    def test_eviction_requires_new_release_and_zero_ticket_proof(self):
        self.reject(lambda d:d['events'].remove(next(e for e in d['events'] if e['kind']=='eviction-ticket-released-after-settled')))
        self.reject(lambda d:d['activeHelperTicketEvents'].__setitem__(11,{**d['activeHelperTicketEvents'][11],'nanoTime':370}))
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='eviction-ticket-released-after-settled').update(sequence=1))
    def test_load_mirror_must_be_observed_exact_and_restored(self):
        self.reject(lambda d:d['events'].remove(next(e for e in d['events'] if e['kind']=='load-mirror-armed')))
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='load-mirror-restored').update(mainAliasesSlot5=False))
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='load-mirror-restored').update(rebindsAfter=0))
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='load-mirror-armed')['armed']['slots'].__setitem__(0,'{Count:1b}'))
    def test_removed_source_never_counted(self):
        self.reject(lambda d:d['activeHelperTransitEvents'][3].update(sourceInventoryResampled=True))
    def test_final_native_unload_not_just_missing_lookup(self):
        for field,value in [('reason','CHANGED_DIMENSION'),('shouldDestroy',True),('shouldSave',False),('chunkAvailable',True),('registered',True),('unavailableAt',299)]:
            with self.subTest(field=field): self.reject(lambda d:d['offlineRemovalProof'].update({field:value}))
    def test_disk_join_order_and_cargo_fails_closed(self):
        self.reject(lambda d:next(e for e in d['nativeWorkerJoins'] if e['loadedFromDisk']).update(worker=H))
        self.reject(lambda d:d['finalizedOfflineInventory']['slots'].__setitem__(0,'{Count:1b}'))
    def test_wrong_receipt_reopen_fails(self):
        self.reject(lambda d:next(e for e in d['events'] if e['kind']=='actual-server-reopen-before-ticks').update(ledger='changed'))
    def test_missing_end_recheck_fails(self):
        self.reject(lambda d:d['events'].remove(next(e for e in d['events'] if e['kind']=='ordinary-end-settled-custody')))
    def test_ticket_intent_return_and_cleanup_are_mandatory(self):
        self.reject(lambda d:d['activeHelperTicketEvents'].pop(1))
        self.reject(lambda d:d['activeHelperTicketEvents'][2].update(nativeReturn=False))
        self.reject(lambda d:d.update(finalOwnedTicketCount=1))
    def test_ticket_owner_wrong_chunk_and_unrelated_change_fail(self):
        self.reject(lambda d:d['activeHelperTicketEvents'][2].update(chunkX=1))
        self.reject(lambda d:d['activeHelperTicketEvents'][2]['census'].update(unrelated='changed'))
        self.reject(lambda d:d['activeHelperTicketEvents'][2].update(kind='cleanup-original-census-diverged'))
    def test_auxiliary_baseline_and_extra_fee_fail(self):
        self.reject(lambda d:d['auxiliaryIsolationFinal'].update(feetY=65))
        self.reject(lambda d:d['treasuryAccounting'].update(singleNegativeLedgerDelta=-128))
    def test_boolean_and_float_integer_substitution_fail(self):
        self.reject(lambda d:d.update(formatVersion=True))
        self.reject(lambda d:d.update(phaseLimitTicks=1200.0))
        self.reject(lambda d:d['nativeWork'][0]['before'].__setitem__(0,False))


class HelperSourceContracts(unittest.TestCase):
    def text(self,path): return (ROOT/path).read_text()
    def test_separate_flags_and_directory_do_not_activate_baseline_driver(self):
        java=self.text('src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeStagedHandoffQa.java')
        self.assertIn('&& !Boolean.getBoolean("siegeoverhaul.nativeQa.activeHelper")',java)
        gradle=self.text('build.gradle')
        self.assertIn("if (activeHelperQa) nativeQaDirectory = 'build/native-helper-qa/client'",gradle)
        self.assertIn("if (activeHelperQa && (!sharedCrewQa || nativeQaMode != 'staged-handoff'))",gradle)
    def test_same_source_prelaunch_gate_is_mandatory_and_readonly(self):
        workflow=self.text('.github/workflows/native-active-helper-qa.yml')
        self.assertLess(workflow.index('prepareNativeActiveHelperApiEvidence'),workflow.index('scripts/verify-native-helper-api.py'))
        self.assertLess(workflow.index('scripts/verify-native-helper-api.py'),workflow.index(' runClient'))
        self.assertIn('timeout --signal=KILL 600s',workflow)
        self.assertIn('contents: read',workflow);self.assertIn('persist-credentials: false',workflow)
        self.assertIn('github.event.pull_request.number == 295',workflow)
        self.assertIn("'native-active-helper-qa'",workflow)
        self.assertNotIn('pull_request_target',workflow);self.assertNotIn('secrets.',workflow)
        self.assertNotIn('workflow_dispatch',workflow)
        self.assertIn('onboardAccessibility:false\\ntutorialStep:none',workflow)
        self.assertIn('> build/native-helper-qa/client/options.txt',workflow)
        self.assertIn('./gradlew --no-daemon --offline --console=plain',workflow)
        self.assertIn('glxinfo -B | tee build/native-helper-qa/evidence/opengl.txt',workflow)
        gradle=self.text('gradle/native-active-helper-qa.gradle');self.assertIn("'--verify-receipt'",gradle)
        self.assertIn("tasks.matching { it.name == 'runClient' }.configureEach",gradle)
    def test_driver_has_no_membership_or_native_tick_repair(self):
        java=self.text('src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperQa.java')
        for prohibited in ('ledger.suspendCrew(', 'ledger.activateCrew(', '.reconcileWorker(', '.beforeNativeTick(',
                           '.setGameMode(', '.setGameTime(', '.setDeltaMovement(', '.setItem(', '.setPos(', 'worker.tick(', 'goal.tick(', 'delegate.tick('):
            self.assertNotIn(prohibited,java)
        self.assertEqual(java.count('.setNoAi(false)'),1)
        self.assertIn('Files.writeString(defaults.resolve("recruits-server.toml"), CONFIG, java.nio.file.StandardOpenOption.CREATE_NEW)',java)
        self.assertIn('receiveCanceled = true',java)
        self.assertIn('transit.assertSettled(transferredSource, transferredDestination)',java)
        self.assertIn('transit.cleanup(server); transit.assertNoTickets(server)',java)
        self.assertIn('Entity.RemovalReason.UNLOADED_TO_CHUNK',java)
        self.assertIn('tick - unavailableAt <= 200',java)
        first_out = java.split('case FIRST_OUT -> {',1)[1].split('case FIRST_ABSENCE',1)[0]
        self.assertIn('helperBefore = placements(helper); coordinatorBefore = placements(coordinator);',first_out)
        self.assertLess(first_out.index('helperBefore ='),first_out.index('boundary(server, "before-first-transfer")'))
        self.assertIn('PENDING_LOADS.get(worker)',java)
        self.assertIn('!DISK_JOINS.containsKey(coordinator)',java)
        tick = java.split('public static void serverTick',1)[1].split('private static void step',1)[0]
        self.assertLess(tick.index('Bounded helper phase timed out'),tick.index('phase == Phase.RELOADING && stopObserved'))
        self.assertLess(tick.index('flush(level)'),tick.index('phase == Phase.RELOADING && stopObserved'))
        self.assertIn('level.getEntity(coordinator) != WORKERS.get(coordinator) || !footprintReady(level)',java)
        readiness=java.split('private static boolean footprintReady',1)[1].split('private static Set<Long> pending',1)[0]
        self.assertIn('getChunkNow(chunk.x, chunk.z)',readiness)
        for prohibited in ('getBlockState(', 'getBlockEntity(', 'getChunk('): self.assertNotIn(prohibited,readiness)
        self.assertIn('Pending-target observation would load the footprint',java)
        self.assertIn('Material census would load its observation footprint',java)
        self.assertIn('Immutable audit would load its observation footprint',java)
        self.assertIn('ProtectedBuilderHandMirror.sameValue(slot, main)',java)
        self.assertIn('worker.getInventory().getItem(index) != slot && worker.getInventory().getItem(index) != main',java)
        self.assertIn('observed.slots().equals(pending.armed().slots())',java)
        self.assertIn('main == slot && ProtectedBuilderHandMirror.rebindCount',java)
        self.assertNotIn('ProtectedBuilderHandMirror.restore(',java)
    def test_second_out_retains_same_ticket_until_later_end_proof(self):
        java=self.text('src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperQa.java')
        second=java.split('case SECOND_OUT ->',1)[1].split('case SECOND_SETTLE ->',1)[0]
        self.assertIn('move(tick, Phase.SECOND_SETTLE)',second)
        self.assertNotIn('releaseHoldingTicket',second)
        self.assertNotIn('absenceAt =',second)
        end=java.split('if (settledEndPending) {',1)[1].split('ServerPlayer owner',1)[0]
        self.assertLess(end.index('tick > transferredAt'),end.index('transit.assertSettled('))
        self.assertLess(end.index('transit.assertSettled('),end.index('settledAt = tick'))
        self.assertIn('requireFirstStage(project); beginEviction(server, tick)',java)
    def test_provider_parity_includes_resources_and_inheritance_not_transformed_claim(self):
        java=self.text('src/nativeQa/java/com/devfarinsky/siegeoverhaul/nativecompat/NativeActiveHelperApiParity.java')
        for needed in ('getProtectionDomain()', 'getCodeSource()', 'getResourceAsStream(resource)', 'Class.forName(name, false',
                       'actualChain.equals(CHAIN)', 'supplierCanonicalPath', 'classSha256', 'transformed method bodies are not observed'):
            self.assertIn(needed,java)


if __name__=='__main__': unittest.main()
