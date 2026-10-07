#!/usr/bin/env python3
"""Reuse exact unmodified Small Ships defaults with a new isolated QA mode only."""
import importlib.util
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('native_defaults', ROOT / 'scripts/prepare-native-client-defaults.py')
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)


def prepare(root=ROOT):
    script = (root / 'gradle/native-earthworks-qa.gradle').read_text()
    pins = re.findall(r"nativeEarthworksQaCompanions\s+fg\.deobf\('([^']*small-ships[^']*)'\)", script)
    if pins != [helper.COORDINATE]:
        raise ValueError('Earthworks QA changed its pinned Small Ships companion')
    helper.GAME_DIRECTORIES['earthworks-one-fill'] = 'build/native-earthworks-qa/client'
    return helper.prepare(root, 'earthworks-one-fill', helper.VERSION)


if __name__ == '__main__':
    print(prepare())
