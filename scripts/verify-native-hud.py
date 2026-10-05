#!/usr/bin/env python3
"""Fail closed on incomplete actual HUD framebuffer/interaction receipts (stdlib only)."""
import json
from pathlib import Path
import struct
import sys


PAGES = ['army', 'loot', 'treasury', 'territory', 'building', 'civilians', 'intel']
PLANS = ['barricade', 'watchtower', 'gatehouse', 'wall', 'corner', 'stairs']
MATRICES = ['compact-scale2', 'compact-scale3', 'roomy-scale2', 'roomy-scale1']
STATES = ['army-unavailable', 'treasury-empty', 'construction-loading', 'construction-empty',
          'civilians-capacity', 'loot-confirmation', 'intel-keyboard-e', 'intel-no-results',
          'intel-scrolled', 'intel-keyboard-focus', 'codex-core', 'codex-how-to-play',
          'codex-journal', 'settings', 'settings-no-results', 'settings-scrolled', 'hero-visuals', 'native-inspection']


def expected_screenshots():
    names = []
    for prefix in MATRICES:
        names.extend(f'{prefix}-{page}.png' for page in PAGES)
        names.extend(f'{prefix}-{section}.png' for section in [
            'building-structures', 'building-construction', 'intel-enemy-lore', 'intel-how-to-play',
            'loot-gallery-epic', 'loot-gallery-last', 'loot-gallery-rare'])
        names.extend(f'{prefix}-plan-{plan}.png' for plan in PLANS)
    for prefix in MATRICES[:2]:
        names.extend(f'{prefix}-{state}.png' for state in STATES)
    names.extend(['roomy-scale1-native-inspection.png', 'resized-intel-preserved.png'])
    return set(names)


def verify(root):
    data = json.loads((root / 'result.json').read_text())
    assert data['mode'] == 'hud' and data['status'] == 'passed', data.get('failure', data)
    assert data['completedSteps'] == data['plannedSteps'] and data['plannedSteps'] > 100
    assert len(data['assertions']) >= 25, data['assertions']
    versions = {'minecraft': '1.20.1', 'forge': '47.4.16', 'workers': '2.0.3',
                'recruits': '1.15.2', 'smallships': '2.0.0-b1.4', 'siegeweapons': '0.2.5'}
    for mod, version in versions.items():
        assert data['loadedModVersions'][mod] == version, mod
    for mod in ['workers', 'recruits', 'smallships', 'siegeweapons']:
        artifact = data['loadedCompanionArtifacts'][mod]
        assert len(artifact['sha256']) == 64 and 'remapped' in artifact['kind'], mod
    assert data['notCovered'] and 'client-menu' in data['coverage']
    expected = expected_screenshots()
    assert len(data['screenshots']) == len(expected) and set(data['screenshots']) == expected, (
        'Missing', sorted(expected - set(data['screenshots'])), 'Extra', sorted(set(data['screenshots']) - expected))
    views = {view['screenshot']: view for view in data['views']}
    assert len(views) == len(expected) and set(views) == expected
    for name in sorted(expected):
        raw = (root / name).read_bytes()
        assert raw.startswith(b'\x89PNG\r\n\x1a\n') and raw[12:16] == b'IHDR' and len(raw) > 4096, name
        width, height = struct.unpack('>II', raw[16:24])
        view = views[name]
        viewport = view['viewport']
        size = (1440, 960) if name.startswith('roomy-') else (960, 720)
        scale = 1 if name.startswith('roomy-scale1') else 3 if 'scale3' in name or name.startswith('resized-') else 2
        assert (width, height) == size == (viewport['framebufferWidth'], viewport['framebufferHeight']), name
        assert (viewport['guiWidth'], viewport['guiHeight']) == (width // scale, height // scale), name
        assert viewport['requestedGuiScale'] == scale and view['nonblankSamples'] > 50, name
        assert view['fixture'] and view['widgets'], name
        for widget in view['widgets']:
            assert 0 <= widget['x'] < viewport['guiWidth'] and 0 <= widget['y'] < viewport['guiHeight'], (name, widget)
            assert widget['width'] > 0 and widget['height'] > 0, (name, widget)
            assert widget['x'] + widget['width'] <= viewport['guiWidth'] + .1, (name, widget)
            assert widget['y'] + widget['height'] <= viewport['guiHeight'] + .1, (name, widget)
            assert not widget['focused'] or widget['active'], (name, widget)
        for i, a in enumerate(view['widgets']):
            for b in view['widgets'][i + 1:]:
                assert (a['x'] >= b['x'] + b['width'] or b['x'] >= a['x'] + a['width']
                        or a['y'] >= b['y'] + b['height'] or b['y'] >= a['y'] + a['height']), (name, a, b)
        if name in {f'{prefix}-army.png' for prefix in MATRICES}:
            assert len(view['nativePortraits']) == 4, name
            assert all(value.startswith('com.talhanation.') for value in view['nativePortraits']), name
        if name.endswith('-loot-gallery-last.png'):
            tooltip = view['lootTooltipBounds']
            assert tooltip['x'] >= 4 and tooltip['y'] >= 4, name
            assert tooltip['x'] + tooltip['width'] <= viewport['guiWidth'] - 4, name
            assert tooltip['y'] + tooltip['height'] <= viewport['guiHeight'] - 4, name
            assert 0 < tooltip['scale'] <= 1, name
        if name.endswith('-territory.png'):
            assert view['territoryAvailability'] == {'ownershipMask': 5, 'active': 1, 'retained': 1, 'unavailableButtons': 2}, name
            unavailable = [widget for widget in view['widgets'] if widget['label'] == 'Unavailable']
            assert len(unavailable) == 2 and all(not widget['active'] for widget in unavailable), name
        if '-plan-' in name:
            assert view['selectedPlan'].lower() == name.removesuffix('.png').split('-plan-')[1], name
            cards = view['nativeBlueprintCards']
            assert any(card['plan'] == view['selectedPlan'] for card in cards), name
            assert all(card['sourceBlueprintMatches'] and card['blockModels'] > 0 and card['caption'] for card in cards), name
        if 'native-inspection' in name:
            assert view['nativeStructurePreviewCount'] == 1, name
    required = ['Native OS E key', 'Native Ctrl+Tab', 'Native Ctrl+Shift+Tab', 'Native Escape',
                'Actual 1440x960 to 960x720', 'Hired, unavailable, insufficient', 'without a purchase',
                'Codex Journal', 'focus across sync', 'Settings empty-filter']
    for phrase in required:
        assert any(phrase in assertion for assertion in data['assertions']), phrase
    source = (root / 'source-commit.txt').read_text().strip()
    assert len(source) == 40 and all(char in '0123456789abcdef' for char in source), source
    print(f'Verified {len(expected)} actual HUD framebuffers and bounded interaction receipts. Visual review remains required.')
    return data


if __name__ == '__main__':
    verify(Path(sys.argv[1] if len(sys.argv) > 1 else 'build/native-hud-qa/evidence'))
