#!/usr/bin/env python3
"""Extract literal option placements from pinned upstream GUI TabPrint::build.

Dynamic keys and options not present in the six desktop pages are deliberately
left unclassified; the mobile metadata bridge displays them under Others.
"""
import pathlib, re, sys

if len(sys.argv) != 3:
    raise SystemExit('usage: generate_tab_groups.py ORCA_CHECKOUT OUTPUT_INC')
source = (pathlib.Path(sys.argv[1]) / 'src/slic3r/GUI/Tab.cpp').read_text(encoding='utf-8')
source = source.split('void TabPrint::build()', 1)[1].split('void TabPrint::reload_config()', 1)[0]
source = re.sub(r'/\*.*?\*/', '', source, flags=re.S)
pages = {'Quality', 'Strength', 'Speed', 'Support', 'Multimaterial', 'Others'}
page = group = None
entries = {}
for line in source.splitlines():
    line = line.split('//', 1)[0]
    found = re.search(r'add_options_page\s*\(\s*L\("([^"]+)"\)', line)
    if found:
        page = found.group(1) if found.group(1) in pages else None
        group = None
    found = re.search(r'new_optgroup\s*\(\s*L\("([^"]+)"\)', line)
    if found:
        group = found.group(1)
    if page:
        for key in re.findall(r'(?:append_single_option_line|get_option)\s*\(\s*"([a-zA-Z0-9_]+)"', line):
            entries.setdefault(key, (page, group or page))
def literal(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'
out = pathlib.Path(sys.argv[2])
out.write_text('// Generated from pinned upstream TabPrint::build; see tools/generate_tab_groups.py.\n' +
               ''.join('{' + ', '.join(map(literal, (key, page, group))) + '},\n'
                       for key, (page, group) in entries.items()), encoding='utf-8')
print(f'{len(entries)} literal option mappings across {len({v[0] for v in entries.values()})} pages')
