#!/usr/bin/env python3
"""Fail closed on real owned-villager report transport and read-only lifecycle evidence."""
import json
from pathlib import Path
import sys
import uuid

FLAGS = ['serverEntityParity', 'productionWatchAndSnapshot', 'leaveClearsReport',
         'returnReceivesFreshReport', 'reopenReceivesFreshReport', 'ledgerUnchanged',
         'inventoryUnchanged', 'treasuryAndTaxesUnchanged']
SHOTS = ['19-live-core-civilians.png', '20-live-core-civilians-reopened.png']


def verify(data):
    assert data['status'] == data['gameplay']['status'] == 'passed'
    result = data['gameplay']['civilianReport']
    assert result['status'] == 'passed'
    assert all(result[key] is True for key in FLAGS), result
    assert result['recruitActions'] == 0
    assert 1 <= result['firstMenuId'] <= 100 and 1 <= result['reopenedMenuId'] <= 100
    assert result['firstMenuId'] != result['reopenedMenuId']
    rows = result['serverRows']
    assert result['residentCount'] == len(rows) and 0 < len(rows) <= 64
    assert len({str(uuid.UUID(row['uuid'])) for row in rows}) == len(rows)
    for row in rows:
        assert row['name'] and row['profession'] and row['type'] and 1 <= row['level'] <= 5, row
        assert row['status'] in ['Loaded', 'Stranded · taxes paused'], row
        assert type(row['nativeAiPaused']) is bool
    assert 'AI was already paused' in result['scope'] and 'tax accrual' in result['notCovered']
    assert all(name in data['screenshots'] for name in SHOTS)
    print('Verified real civilian S2C report parity, page/reopen lifecycle and unchanged ledger/inventory/Treasury.')
    return result


if __name__ == '__main__':
    verify(json.loads(Path(sys.argv[1] if len(sys.argv) > 1 else 'build/native-qa/evidence/result.json').read_text()))
