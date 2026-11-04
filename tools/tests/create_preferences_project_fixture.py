#!/usr/bin/env python3
"""Generate a tiny labelled 3MF test project; no user model or print target.

The cube mesh is generated here. XML structures follow the pinned upstream
bbs_3mf writer/reader contract; all settings are deliberately test values.
Successful native import/slicing must be verified separately on Android.
"""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

CORE = 'http://schemas.microsoft.com/3dmanufacturing/core/2015/02'


def build_model() -> str:
    vertices = [(0, 0, 0), (10, 0, 0), (10, 10, 0), (0, 10, 0),
                (0, 0, 10), (10, 0, 10), (10, 10, 10), (0, 10, 10)]
    triangles = [(3, 2, 1), (1, 0, 3), (4, 5, 6), (6, 7, 4), (0, 1, 5), (5, 4, 0),
                 (1, 2, 6), (6, 5, 1), (2, 3, 7), (7, 6, 2), (3, 0, 4), (4, 7, 3)]
    mesh = '<mesh><vertices>' + ''.join(f'<vertex x="{x}" y="{y}" z="{z}"/>' for x, y, z in vertices)
    mesh += '</vertices><triangles>' + ''.join(f'<triangle v1="{a}" v2="{b}" v3="{c}"/>' for a, b, c in triangles) + '</triangles></mesh>'
    objects = ''.join(f'<object id="{i}" type="model">{mesh}</object>' for i in (1, 2))
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<model unit="millimeter" xml:lang="en-US" xmlns="{CORE}" xmlns:BambuStudio="http://schemas.bambulab.com/package/2021">
<metadata name="Application">OrcaSlicer-2.5.0-dev</metadata>
<metadata name="BambuStudio:3mfVersion">1</metadata>
<metadata name="Title">Kepler native preferences validation - generated cubes</metadata>
<resources>{objects}</resources><build>
<item objectid="1" transform="1 0 0 0 1 0 0 0 1 20 20 0"/>
<item objectid="2" transform="1 0 0 0 1 0 0 0 1 50 20 0"/>
</build></model>'''


def model_settings() -> str:
    objects = ''
    for index in (1, 2):
        objects += f'''<object id="{index}"><metadata key="name" value="Validation PLA cube {index}"/>
<metadata key="extruder" value="{index}"/><metadata key="wall_loops" value="5"/>
<part id="{index}" subtype="normal_part"><metadata key="name" value="Generated cube {index}"/>
<metadata key="extruder" value="{index}"/></part></object>'''
    instances = ''.join(f'''<model_instance><metadata key="object_id" value="{i}"/><metadata key="instance_id" value="0"/>
<metadata key="identify_id" value="{40+i}"/></model_instance>''' for i in (1, 2))
    return f'''<?xml version="1.0" encoding="UTF-8"?><config>{objects}<plate>
<metadata key="plater_id" value="1"/><metadata key="plater_name" value="Generated validation plate"/>
<metadata key="locked" value="false"/><metadata key="filament_maps" value="1 2"/>
{instances}</plate></config>'''


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    target = args.output.resolve()
    if target.exists():
        raise ValueError('Refusing to overwrite an existing fixture')
    target.parent.mkdir(parents=True, exist_ok=True)
    config = {
        'name': 'project_settings', 'version': '2.5.0', 'from': 'project',
        'printer_settings_id': 'Kepler validation printer 0.6',
        'print_settings_id': 'Kepler validation 0.24',
        'filament_settings_id': ['Kepler validation PLA black', 'Kepler validation PLA white'],
        'printer_technology': 'FFF', 'nozzle_diameter': ['0.6'],
        'printable_area': ['0x0', '250x0', '250x220', '0x220'], 'printable_height': '100',
        'layer_height': '0.24', 'initial_layer_print_height': '0.24',
        'filament_type': ['PLA', 'PLA'], 'filament_colour': ['#000000', '#FFFFFF'],
        'filament_diameter': ['1.75', '1.75'], 'filament_is_support': ['0', '0'],
        'nozzle_temperature': ['215', '215'], 'nozzle_temperature_initial_layer': ['215', '215'],
        'flush_volumes_matrix': ['0', '300', '400', '0'], 'flush_volumes_vector': ['70', '70', '70', '70'],
        # Define the tower explicitly rather than inheriting a location from
        # the selected process that could lie outside the chosen print bed.
        'print_sequence': 'by layer', 'enable_prime_tower': '1',
        'wipe_tower_x': ['150'], 'wipe_tower_y': ['100'],
        'prime_tower_width': '60', 'prime_tower_brim_width': '3',
    }
    contents = {
        '[Content_Types].xml': '''<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="model" ContentType="application/vnd.ms-package.3dmanufacturing-3dmodel+xml"/><Default Extension="config" ContentType="application/octet-stream"/></Types>''',
        '_rels/.rels': '''<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Target="/3D/3dmodel.model" Id="rel0" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/></Relationships>''',
        '3D/3dmodel.model': build_model(),
        'Metadata/model_settings.config': model_settings(),
        'Metadata/project_settings.config': json.dumps(config, ensure_ascii=False, indent=2),
    }
    with zipfile.ZipFile(target, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, text in contents.items():
            entry = zipfile.ZipInfo(name, (2026, 11, 2, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, text.encode('utf-8'))
    with zipfile.ZipFile(target) as archive:
        if archive.testzip() is not None:
            raise ValueError('Fixture CRC failed')
    manifest = {'fixture': target.name, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
                'bytes': target.stat().st_size, 'origin': 'Generated by tools/tests/create_preferences_project_fixture.py',
                'upstream_contract_revision': '6d2869c430284714f0a22570c38d3b359965a802', 'generator_version': 2,
                'model': 'Two generated 10 mm cubes, 24 triangles total; one plate; two PLA material slots',
                'test_values_not_a_printer_profile': True, 'native_runtime_verified': False,
                'physical_print_verified': False}
    target.with_suffix('.provenance.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print(json.dumps(manifest))


if __name__ == '__main__':
    main()
