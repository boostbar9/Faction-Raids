#!/usr/bin/env python3
"""Fail-closed checks for the bounded real-server economy receipt in Native Building QA."""
import json
from pathlib import Path
import sys
import uuid


def verify(data):
    gameplay = data['gameplay']
    assert data['status'] == gameplay['status'] == 'passed', data
    assert gameplay['manualTreasuryDebit'] == 8 and gameplay['manualPaidTreasury'] == 992, gameplay
    assert gameplay['perimeterTreasuryDebit'] == 64, gameplay
    assert gameplay['completedManualRestart']['treasury'] == 992, gameplay
    economy = gameplay['economy']
    assert economy['status'] == 'passed', economy
    assert 'Controlled cleared-wave states' in economy['scope'], economy
    assert 'production processRaid' in economy['scope'] and 'No raid combat was fought.' in economy['scope'], economy
    territory = economy['territory']
    assert territory['unownedRejected'] == territory['ownedRejected'] == [0, 1], territory
    assert territory['unavailableTreasuryDebit'] == 0 and territory['personalInventoryUnchanged'] is True, territory
    assert territory['fixtureFunding'] == 1500 and territory['workingPrices'] == [900, 600], territory
    assert territory['savedOwnershipMask'] == 15 and territory['activeCount'] == territory['retainedCount'] == 2, territory
    practice = economy['practice']
    assert practice == {'wave': 5, 'handlerPasses': 2, 'boxes': 0, 'treasuryDebitOrCredit': 0}, practice
    checkpoint = economy['checkpoint']
    assert checkpoint['wave'] == 5 and checkpoint['boxes'] == 1, checkpoint
    assert checkpoint['beforeAnyBallot'] is True and checkpoint['waitReplayNoDuplicate'] is True, checkpoint
    assert type(checkpoint['rolledTier']) is int and 0 <= checkpoint['rolledTier'] <= 3, checkpoint
    assert type(checkpoint['treasuryReward']) is int and checkpoint['treasuryReward'] >= 0, checkpoint
    assert checkpoint['treasury'] == 992 + checkpoint['treasuryReward'], checkpoint
    reload = economy['diskReload']
    assert reload['ownershipMask'] == 15 and reload['activeCount'] == reload['retainedCount'] == 2, reload
    for field in ['newSavedDataAndRaidInstances', 'exactPlayerInventory', 'exactCurrentWaveReceipt', 'replayNoDuplicate']:
        assert reload[field] is True, (field, reload)
    full = economy['fullInventory']
    assert full['wave'] == 6 and full['filledMainSlots'] == 36, full
    assert full['droppedBoxes'] == 1 and full['inventoryBoxes'] == 0 and full['replayNoDuplicate'] is True, full
    assert type(full['rolledTier']) is int and 0 <= full['rolledTier'] <= 3, full
    assert full['item'] == 'siegeoverhaul:loot_box_' + ['common', 'uncommon', 'rare', 'epic'][full['rolledTier']], full
    assert str(uuid.UUID(full['dropEntity'])) == full['dropEntity'], full
    return economy


if __name__ == '__main__':
    verify(json.loads(Path(sys.argv[1]).read_text()))
    print('Native economy receipt passed: controlled cleared-wave server cases, not a fought raid.')
