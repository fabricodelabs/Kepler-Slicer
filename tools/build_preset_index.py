"""Index original upstream preset JSON without changing it or resolving defaults."""
import argparse
import hashlib
import json
import pathlib
import zipfile


def build(archive: pathlib.Path, output: pathlib.Path):
    records, skipped = [], []
    with zipfile.ZipFile(archive) as source:
        for path in sorted(source.namelist()):
            parts = path.split('/')
            if len(parts) < 3 or parts[1] not in ('machine', 'process', 'filament') or not path.endswith('.json'):
                continue
            document = json.loads(source.read(path).decode('utf-8-sig'))
            if parts[-1] in ('filament_id_map.json', 'filament_name_map.json', 'support_recommended_params.json'):
                skipped.append(path)
                continue
            if document.get('type') not in ('machine', 'process', 'filament'):
                skipped.append(path)
                continue
            name = document.get('name')
            if not isinstance(name, str) or not name:
                raise ValueError(f'Preset name missing: {path}')
            parent = document.get('inherits', '')
            if not isinstance(parent, str):
                raise ValueError(f'Non-string inheritance requires explicit support: {path}')
            records.append(dict(kind='printer' if parts[1] == 'machine' else parts[1],
                                vendor=parts[0], name=name, path=path, parent=parent,
                                instantiated=str(document.get('instantiation', 'false')).lower() == 'true'))
    identities = [(row['kind'], row['vendor'], row['name']) for row in records]
    if len(identities) != len(set(identities)):
        raise ValueError('System preset identities must be unique within each vendor and kind')
    payload = archive.read_bytes()
    root = dict(version=1, revision='6d2869c430284714f0a22570c38d3b359965a802',
                upstream_version='02.08.01.55', archive_sha256=hashlib.sha256(payload).hexdigest(),
                archive_size=len(payload), profiles=records, non_preset_json=skipped)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(root, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8')
    print(f'Indexed {len(records)} original presets: {output}')


if __name__ == '__main__':
    base = pathlib.Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=pathlib.Path, default=base/'engine_assets/src/main/assets/kepler_profiles.zip')
    parser.add_argument('--output', type=pathlib.Path, default=base/'app/src/main/assets/kepler_preset_index.json')
    args = parser.parse_args()
    build(args.archive, args.output)
