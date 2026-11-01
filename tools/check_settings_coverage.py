#!/usr/bin/env python3
"""Independently audit Orca Tab.cpp source references, inventory and UI keys.

This is a static *lower bound*, not a runtime coverage proof. Dynamic expressions,
conditional visibility and get_option attachment are reported as unknown. Pass a
separately exported --ui-keys JSON to compare actual UI-accessible keys; the
schema file by itself is never treated as evidence that a row is displayed.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

PROJECT=Path(__file__).resolve().parents[1]
WORKSPACE=PROJECT.parents[1]
TAB='src/slic3r/GUI/Tab.cpp'
METHODS={'process':['TabPrint::build'],
         'filament':['TabFilament::build','TabFilament::add_filament_overrides_page'],
         'printer':['TabPrinter::build_fff','TabPrinter::build_sla','TabPrinter::build_unregular_pages','TabPrinter::build_kinematics_page']}
CALLS={'append_single_option_line':0,'get_option':0,'append_option_line':1,'create_line_with_widget':1,'create_single_option_line':0}
EXTRA_METHODS={'process':['TabPrintModel::build','TabPrintPlate::build','TabSLAPrint::build'],
               'filament':['TabSLAMaterial::build'],'printer':['TabPrinter::build']}
CALL_RE=re.compile(r'\b('+ '|'.join(CALLS) +r')\s*\(')
STRING=re.compile(r'"(?:\\.|[^"\\])*"')

def mask(src,comments=True,strings=False):
    out=list(src);i=0
    while i<len(src):
        start=i
        if src.startswith('//',i):
            i=src.find('\n',i)
            if i<0:i=len(src)
            if comments:
                for j in range(start,i):out[j]=' '
        elif src.startswith('/*',i):
            end=src.find('*/',i+2);i=len(src) if end<0 else end+2
            if comments:
                for j in range(start,i):
                    if out[j]!='\n':out[j]=' '
        elif src[i] in ('"',"'"):
            quote=src[i];i+=1
            while i<len(src):
                if src[i]=='\\':i+=2;continue
                if src[i]==quote:i+=1;break
                i+=1
            if strings:
                for j in range(start,min(i,len(src))):
                    if out[j]!='\n':out[j]=' '
        else:i+=1
    return ''.join(out)

def close_pair(masked,start,op,cl):
    level=0
    for i in range(start,len(masked)):
        if masked[i]==op:level+=1
        elif masked[i]==cl:
            level-=1
            if level==0:return i
    raise ValueError('Unbalanced '+op+' at offset '+str(start))

def regions(masked):
    found={}
    for section,names in METHODS.items():
        for name in names:
            pattern=re.compile(r'\b'+re.escape(name)+r'\s*\(');m=pattern.search(masked)
            if not m:raise ValueError('Method not found: '+name)
            op=masked.find('(',m.start());close=close_pair(masked,op,'(',')');brace=masked.find('{',close+1)
            if brace<0:raise ValueError('No body: '+name)
            between=masked[close+1:brace]
            if ';' in between:raise ValueError('Found declaration instead of definition: '+name)
            found[name]=(section,brace+1,close_pair(masked,brace,'{','}'))
    return found

def arguments(clean,masked,op,close):
    out=[];a=op+1;depth={'(':0,'[':0,'{':0};pairs={')':'(',']':'[','}':'{'}
    for i in range(a,close):
        ch=masked[i]
        if ch in depth:depth[ch]+=1
        elif ch in pairs:depth[pairs[ch]]-=1
        elif ch==',' and not any(depth.values()):out.append(clean[a:i].strip());a=i+1
    if clean[a:close].strip():out.append(clean[a:close].strip())
    return out

def string_literal(arg):
    # Keys in these APIs are ordinary C++ string literals. A variable, concat,
    # conditional, macro or indexed call is intentionally left unresolved.
    matches=list(STRING.finditer(arg))
    if not matches:return None
    if STRING.sub('',arg).strip():return None
    import ast
    try:return ''.join(ast.literal_eval(m.group()) for m in matches)
    except (ValueError,SyntaxError):return None

def extract(src):
    clean=mask(src,comments=True,strings=False);scan=mask(clean,comments=False,strings=True);scope=regions(scan);hits=[];dynamic=[]
    line_starts=[0]+[m.end() for m in re.finditer('\n',src)]
    import bisect
    for builder,(section,start,end) in scope.items():
        for m in CALL_RE.finditer(scan,start,end):
            method=m.group(1);op=scan.find('(',m.start());close=close_pair(scan,op,'(',')')
            if close>end:raise ValueError('Call escapes method scope at '+str(m.start()))
            a=arguments(clean,scan,op,close);which=CALLS[method];expression=a[which] if len(a)>which else ''
            line=bisect.bisect_right(line_starts,m.start());key=string_literal(expression)
            item={'section':section,'file':TAB,'line':line,'method':method,'builder':builder,'expression':expression}
            if key is None:dynamic.append(item)
            else:item['key']=key;item['evidence']='reference_only' if method=='get_option' else 'direct_call';hits.append(item)
    return hits,dynamic

def read_ui(path):
    doc=json.loads(path.read_text(encoding='utf-8-sig'));sections=doc.get('sections')
    if not isinstance(sections,dict):raise ValueError('UI key export must contain object sections')
    result={}
    for section in METHODS:
        rows=sections.get(section)
        if not isinstance(rows,list):raise ValueError('Missing UI key array: '+section)
        keys=set()
        for row in rows:
            if isinstance(row,str):keys.add(row)
            elif isinstance(row,dict) and isinstance(row.get('key'),str):keys.add(row['key'])
            else:raise ValueError('Invalid UI key in '+section)
        result[section]=keys
    return result

def validate_absence_checks(checkout,audit):
    # This establishes only literal identifier occurrences in checked source
    # extensions, not absence of names built dynamically or used by plugins.
    checks=audit.get('absence_checks',[])
    if not checks:return None
    src=checkout/'src'
    for check in checks:
        symbol=check.get('symbol')
        if not isinstance(symbol,str) or not re.fullmatch(r'[A-Za-z_]\w*',symbol):return 'Invalid static-occurrence check'
        suffixes=check.get('extensions',[])
        if not suffixes or any(not re.fullmatch(r'\.[a-z]+',x) for x in suffixes):return 'Invalid source extensions'
        expected={(x['file'],x['line']) for x in check.get('expected',[])}
        found=set();pattern=re.compile(r'\b'+re.escape(symbol)+r'\b')
        for file in src.rglob('*'):
            if not file.is_file() or file.suffix.lower() not in suffixes:continue
            for number,line in enumerate(file.read_text(encoding='utf8',errors='replace').splitlines(),1):
                if pattern.search(line):found.add((file.relative_to(checkout).as_posix(),number))
        if found!=expected:return 'Static occurrences changed for '+symbol
    return None

def check(checkout,schema_path,ui_path,audit_path=None):
    data=(checkout/TAB).read_bytes();source=data.decode('utf-8-sig');hits,dynamic=extract(source)
    global METHODS
    selected_methods=METHODS
    try:
        METHODS=EXTRA_METHODS
        extra_hits,extra_dynamic=extract(source)
    finally: METHODS=selected_methods
    schema=json.loads(schema_path.read_text(encoding='utf-8-sig'));groups=schema.get('sections')
    if not isinstance(groups,dict):raise ValueError('Invalid schema sections')
    expected_sha=hashlib.sha256(data).hexdigest();recorded_sha=schema.get('sources',{}).get('tab',{}).get('sha256')
    expected_sources={'tab':TAB,'definitions':'src/libslic3r/PrintConfig.cpp','definition_header':'src/libslic3r/Config.hpp','presets':'src/libslic3r/Preset.cpp'}
    stale_sources=[]
    for name,path in expected_sources.items():
        stored=schema.get('sources',{}).get(name,{})
        if stored.get('path')!=path or stored.get('sha256')!=hashlib.sha256((checkout/path).read_bytes()).hexdigest():stale_sources.append(name)
    stale=bool(stale_sources)
    keys={};placements=set()
    for section in METHODS:
        rows=groups.get(section)
        if not isinstance(rows,list):raise ValueError('Missing schema section '+section)
        keys[section]=set()
        for row in rows:
            key=row.get('key');
            if not isinstance(key,str) or key in keys[section]:raise ValueError('Missing/duplicate key in '+section)
            keys[section].add(key)
            for p in row.get('placements',[]):placements.add((section,key,p.get('file'),p.get('line'),p.get('method')))
    missing_keys=sorted({(h['section'],h['key']) for h in hits if h['key'] not in keys[h['section']]})
    missing_placements=[h for h in hits if (h['section'],h['key'],h['file'],h['line'],h['method']) not in placements]
    dynamic_schema={(d.get('section'),d.get('file'),d.get('line'),d.get('method')) for d in schema.get('diagnostics',{}).get('dynamic_references',[])}
    missing_dynamic=[d for d in dynamic if (d['section'],d['file'],d['line'],d['method']) not in dynamic_schema]
    # Manual resolutions are accepted only for byte-identical upstream files and
    # exact callsites/expressions; coverage is still reported as incomplete.
    audit=None;audit_error=None
    if audit_path:
        audit=json.loads(audit_path.read_text(encoding='utf-8-sig'))
        for path,digest in audit.get('sources',{}).items():
            if Path(path).is_absolute() or '..' in Path(path).parts:raise ValueError('Invalid audit source path')
            if hashlib.sha256((checkout/path).read_bytes()).hexdigest()!=digest:audit_error='Audit source changed: '+path;break
        if audit.get('schema_version')!=1 or not {TAB,'src/libslic3r/PrintConfig.cpp','src/libslic3r/PublishSettings.cpp'}.issubset(audit.get('sources',{})):audit_error='Invalid audit format or missing source hashes'
        for path,digest in audit.get('android_sources',{}).items():
            if Path(path).is_absolute() or '..' in Path(path).parts:raise ValueError('Invalid Android audit source path')
            if hashlib.sha256((PROJECT/path).read_bytes()).hexdigest()!=digest:audit_error='Android audit source changed: '+path;break
        if audit_error is None:audit_error=validate_absence_checks(checkout,audit)
    resolutions={}
    if audit is not None and audit_error is None:
        for d in audit.get('dynamic_calls',[]):
            key=(d.get('section'),d.get('file'),d.get('line'),d.get('method'),d.get('builder'),d.get('expression'))
            if key in resolutions:raise ValueError('Duplicate dynamic audit call')
            if not isinstance(d.get('keys'),list) or not d['keys'] or not d.get('evidence'):raise ValueError('Missing dynamic resolution evidence')
            resolutions[key]=d
    explained=[];unexplained=[];missing_dynamic_keys=[]
    for d in dynamic:
        key=tuple(d.get(k) for k in ('section','file','line','method','builder','expression'))
        r=resolutions.get(key)
        if r is None:unexplained.append(d);continue
        explained.append(r)
        if r.get('scope')!='ui_pseudo':
            missing_dynamic_keys.extend({'section':d['section'],'key':k,'line':d['line']} for k in r['keys'] if k not in keys[d['section']])
    dynamic_definitions=schema.get('diagnostics',{}).get('dynamic_definitions',[])
    def_resolutions={(d.get('line'),d.get('expression')):d for d in (audit.get('dynamic_definitions',[]) if audit and not audit_error else [])}
    unexplained_definitions=[d for d in dynamic_definitions if (d.get('line'),d.get('expression')) not in def_resolutions]
    schema_unmapped={d['key'] for d in schema.get('unmapped_definitions',[])}
    reviewed_unmapped={d['key'] for d in (audit.get('unmapped_definitions_review',[]) if audit and not audit_error else [])}
    unreviewed_unmapped=sorted(schema_unmapped-reviewed_unmapped)
    needs_scope_review=[d for d in (audit.get('unmapped_definitions_review',[]) if audit and not audit_error else []) if d.get('disposition')=='needs_scope_and_usage_review']
    ui=read_ui(ui_path) if ui_path else None
    missing_ui=sorted({(h['section'],h['key']) for h in hits if ui is not None and h['key'] not in ui[h['section']]} |
                      {(d['section'],k) for d in explained if ui is not None and d.get('scope')!='ui_pseudo' for k in d['keys'] if k not in ui[d['section']]})
    extra=[]
    for h in extra_hits:
        scope={'TabPrintModel::build':'object','TabPrintPlate::build':'plate','TabSLAPrint::build':'sla_process','TabSLAMaterial::build':'sla_material','TabPrinter::build':'printer_dispatch'}[h['builder']]
        extra.append(dict(h,scope=scope,present_in_preset_schema=h['key'] in keys[h['section']]))
    return {'status':'incomplete','source_sha256':expected_sha,'schema_sha256':recorded_sha,'schema_stale':stale,'stale_sources':stale_sources,
            'source_literal_calls':len(hits),'direct_literal_calls':sum(h['evidence']=='direct_call' for h in hits),'reference_only_calls':sum(h['evidence']=='reference_only' for h in hits),
            'missing_schema_keys':[{'section':s,'key':k} for s,k in missing_keys],'missing_schema_placements':missing_placements,'unrecorded_dynamic':missing_dynamic,
            'dynamic_calls':dynamic,'audited_dynamic_calls':len(explained),'unexplained_dynamic_calls':unexplained,'missing_dynamic_keys':missing_dynamic_keys,
            'audited_dynamic_definitions':len(dynamic_definitions)-len(unexplained_definitions),'unexplained_dynamic_definitions':unexplained_definitions,'audit_error':audit_error,
            'additional_scope_calls':extra,'additional_scope_dynamic':extra_dynamic,'unreviewed_unmapped_definitions':unreviewed_unmapped,'unmapped_needing_scope_review':needs_scope_review,
            'static_occurrence_checks':audit.get('absence_checks',[]) if audit and not audit_error else [],
            'ui_keys_supplied':ui is not None,'missing_ui_keys':None if ui is None else [{'section':s,'key':k} for s,k in missing_ui],
            'unknowns':['The selected methods are a lower bound; other helper methods, native actions and dynamically constructed keys are not exhaustively proven.',
                        'Literal get_option is a reference only; attachment and runtime visibility are unknown.',
                        'Conditions, compile flags, printer technology and indexed/extruder runtime variations are not evaluated.',
                        'A separate UI-key export demonstrates declared availability only, not working editors or desktop parity.']}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--orca',type=Path,default=WORKSPACE/'work'/'OrcaSlicer');parser.add_argument('--schema',type=Path,default=PROJECT/'app'/'src'/'main'/'assets'/'orca_settings_schema.json');parser.add_argument('--ui-keys',type=Path);parser.add_argument('--audit',type=Path,default=PROJECT/'tools'/'settings_dynamic_audit.json');parser.add_argument('--json',action='store_true');args=parser.parse_args()
    try:report=check(args.orca,args.schema,args.ui_keys,args.audit)
    except (OSError,ValueError,KeyError,json.JSONDecodeError) as e:print('Cannot audit coverage:',e,file=sys.stderr);return 2
    if args.json:print(json.dumps(report,ensure_ascii=False,indent=2))
    else:
        print('Static source calls:',report['source_literal_calls'],'(direct:',report['direct_literal_calls'],'reference-only:',report['reference_only_calls'],')')
        for name in ('missing_schema_keys','missing_schema_placements','unrecorded_dynamic','unexplained_dynamic_calls','unexplained_dynamic_definitions','missing_dynamic_keys','additional_scope_calls','additional_scope_dynamic','unreviewed_unmapped_definitions','unmapped_needing_scope_review','missing_ui_keys'):
            rows=report[name];print(name+':',len(rows) if rows is not None else 'UNKNOWN (no UI evidence)')
            for row in rows or []:print(' ',json.dumps(row,ensure_ascii=False))
        print('Audited dynamic calls:',report['audited_dynamic_calls'],'definitions:',report['audited_dynamic_definitions'],'audit error:',report['audit_error'])
        print('Schema stale:',report['schema_stale'],report['stale_sources']);print('UI key export:', 'provided' if report['ui_keys_supplied'] else 'UNKNOWN (not provided)')
        for value in report['unknowns']:print('UNKNOWN:',value)
    # Even if literal matches are perfect, dynamic/runtime uncertainty means
    # zero unexplained omissions has not been established.
    return 1
if __name__=='__main__':raise SystemExit(main())
