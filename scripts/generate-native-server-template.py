#!/usr/bin/env python3
"""Deterministic, empty 16x8x16 GameTest structure; no vendor assets or saved world."""
import gzip
from pathlib import Path
import struct
import sys


def string(value):
    encoded = value.encode('utf-8')
    return struct.pack('>H', len(encoded)) + encoded


def named(kind, name, payload):
    return bytes([kind]) + string(name) + payload


def integer(value):
    return struct.pack('>i', value)


def structure():
    body = named(3, 'DataVersion', integer(3465))
    body += named(9, 'size', bytes([3]) + integer(3) + b''.join(map(integer, (16, 8, 16))))
    palette = named(8, 'Name', string('minecraft:air')) + b'\0'
    body += named(9, 'palette', bytes([10]) + integer(1) + palette)
    body += named(9, 'blocks', bytes([10]) + integer(0))
    body += named(9, 'entities', bytes([10]) + integer(0))
    compressed = bytearray(gzip.compress(named(10, '', body + b'\0'), mtime=0))
    compressed[9] = 255  # Normalize gzip OS byte across Python/platform versions.
    return bytes(compressed)


if __name__ == '__main__':
    target = Path(__file__).resolve().parents[1] / 'src/nativeServerQa/resources/data/siegeoverhaul/structures/native_server_empty.nbt'
    expected = structure()
    if sys.argv[1:] == ['--check']:
        assert target.read_bytes() == expected, 'Generated GameTest template is stale'
        print(f'Verified deterministic empty GameTest template ({len(expected)} bytes)')
    elif not sys.argv[1:]:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(expected)
        print(f'Generated {target}')
    else:
        raise SystemExit('Usage: generate-native-server-template.py [--check]')
