#!/usr/bin/env python3
"""Index instantiated upstream process/filament profiles from the bundled upstream ZIP.

This is a discovery index, not a compatibility evaluator: inheritance, includes,
and upstream condition expressions are resolved by libslic3r at slicing time.
"""
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ZIP = ROOT / 'engine_assets/src/main/assets/kepler_profiles.zip'
OUT = ROOT / 'app/src/main/assets/kepler_print_profile_catalog.json'
REV = '6d2869c430284714f0a22570c38d3b359965a802'


def build():
    rows = []
    with zipfile.ZipFile(ZIP) as archive:
        entries = set(archive.namelist())
        for manifest in sorted(n for n in entries if '/' not in n and n.endswith('.json')):
            vendor = manifest[:-5]
            data = json.loads(archive.read(manifest))
            for kind, key in [('process', 'process_list'), ('filament', 'filament_list')]:
                for item in data.get(key, []):
                    path = f"{vendor}/{item['sub_path']}"
                    if path not in entries:
                        raise ValueError(f'Manifest references missing file: {path}')
                    profile = json.loads(archive.read(path))
                    if str(profile.get('instantiation', '')).lower() != 'true':
                        continue
                    if profile.get('type') != kind or not profile.get('name'):
                        raise ValueError(f'Invalid instantiated profile: {path}')
                    if profile['name'] != item['name']:
                        raise ValueError(f'Name mismatch: {path}')
                    explicit = profile.get('compatible_printers', [])
                    if not isinstance(explicit, list):
                        raise ValueError(f'Invalid compatible_printers: {path}')
                    rows.append({'kind': kind, 'vendor': vendor, 'name': profile['name'],
                                 'path': path, 'printers': explicit})
    OUT.write_text(json.dumps({'revision': REV, 'profiles': rows}, ensure_ascii=False,
                              separators=(',', ':')), encoding='utf-8')
    print(f'{len(rows)} instantiated profiles written to {OUT}')


if __name__ == '__main__':
    try:
        build()
    except (OSError, ValueError, KeyError, zipfile.BadZipFile) as exc:
        print(exc, file=sys.stderr)
        raise SystemExit(1)
