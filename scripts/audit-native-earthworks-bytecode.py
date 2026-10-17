#!/usr/bin/env python3
"""Read-only bytecode receipt for the loaded pinned inventory and unary block parser."""
import importlib.util
from pathlib import Path

spec = importlib.util.spec_from_file_location('native_bytecode', Path(__file__).with_name('audit-native-inventory-bytecode.py'))
helper = importlib.util.module_from_spec(spec)
spec.loader.exec_module(helper)
helper.TARGETS['workers'][1].append('com.talhanation.workers.world.BuildBlockParse')
if __name__ == '__main__':
    helper.main()
