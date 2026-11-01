#!/usr/bin/env python3
"""Host tests for the static coverage auditor, against an explicit pinned Orca checkout."""
import argparse
import importlib.util
import json
import tempfile
from pathlib import Path

project = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--orca', type=Path, required=True, help='Pinned OrcaSlicer source checkout')
args = parser.parse_args()
spec = importlib.util.spec_from_file_location('checker', project/'tools/check_settings_coverage.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)
schema = project/'app/src/main/assets/orca_settings_schema.json'
audit = project/'tools/settings_dynamic_audit.json'
report = checker.check(args.orca, schema, None, audit)
assert report['status'] == 'incomplete'
assert report['audited_dynamic_calls'] == 42 and report['audited_dynamic_definitions'] == 7
assert not report['unexplained_dynamic_calls'] and not report['unexplained_dynamic_definitions']
assert not report['unmapped_needing_scope_review']
assert report['missing_ui_keys'] is None
assert {r['key'] for r in report['additional_scope_calls'] if not r['present_in_preset_schema']} == {
    'curr_bed_type', 'first_layer_sequence_choice', 'other_layers_sequence_choice'
}
data = json.loads(schema.read_text(encoding='utf8'))
with tempfile.TemporaryDirectory() as tmp:
    tmp = Path(tmp)
    stale_audit = json.loads(audit.read_text(encoding='utf8'))
    stale_audit['sources']['src/libslic3r/PublishSettings.cpp'] = '0' * 64
    (tmp/'stale.json').write_text(json.dumps(stale_audit), encoding='utf8')
    stale = checker.check(args.orca, schema, None, tmp/'stale.json')
    assert stale['audit_error'] and len(stale['unexplained_dynamic_calls']) == 42
    ui = {'sections': {kind:[r['key'] for r in rows] for kind,rows in data['sections'].items()}}
    ui['sections']['filament'].remove('filament_retraction_length')
    (tmp/'ui.json').write_text(json.dumps(ui), encoding='utf8')
    missing = checker.check(args.orca, schema, tmp/'ui.json', audit)
    assert {'section':'filament','key':'filament_retraction_length'} in missing['missing_ui_keys']
print('Settings coverage host checks passed; overall audit remains incomplete')
