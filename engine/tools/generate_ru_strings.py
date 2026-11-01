#!/usr/bin/env python3
"""Export non-fuzzy, context-free singular translations from pinned Orca PO."""
import ast, json, pathlib, re, sys
if len(sys.argv) != 3:
    raise SystemExit('usage: generate_ru_strings.py ORCA_CHECKOUT OUTPUT_JSON')
po = (pathlib.Path(sys.argv[1]) / 'localization/i18n/ru/OrcaSlicer_ru.po').read_text(encoding='utf-8-sig')
result = {}
for block in po.split('\n\n'):
    if re.search(r'^#,.*\bfuzzy\b', block, re.M) or '\nmsgctxt ' in '\n' + block or '\nmsgid_plural ' in '\n' + block:
        continue
    fields = {}; current = None
    try:
        for line in block.splitlines():
            if line.startswith('msgid ') or line.startswith('msgstr '):
                current, value = line.split(' ', 1); fields[current] = ast.literal_eval(value)
            elif line.startswith('"') and current:
                fields[current] += ast.literal_eval(line)
            elif line.startswith('msg'):
                current = None
        src, translated = fields.get('msgid'), fields.get('msgstr')
        if src and translated:
            result.setdefault(src, translated)
    except (SyntaxError, ValueError):
        continue
pathlib.Path(sys.argv[2]).write_text(json.dumps(result, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
print(f'{len(result)} Russian upstream strings')
