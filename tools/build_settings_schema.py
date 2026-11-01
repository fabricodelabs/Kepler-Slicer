#!/usr/bin/env python3
"""Generate a conservative, source-traceable Orca settings inventory.

No C++ is executed: runtime defaults, computed expressions, templates, feature
flags and whether a row is attached/visible remain unknown until reconciled
with the actual upstream configuration and the mobile editor.  See the
embedded diagnostics; this file is NOT proof that a setting is editable.
"""
from __future__ import annotations
import argparse
import ast
import bisect
import hashlib
import json
import re
import sys
from pathlib import Path

EXPECTED_REVISION = '6d2869c430284714f0a22570c38d3b359965a802'
ROOT = Path(__file__).resolve().parents[1]
WORKSPACE = ROOT.parents[1]
TAB = 'src/slic3r/GUI/Tab.cpp'
CONFIG = 'src/libslic3r/PrintConfig.cpp'
CONFIG_HEADER = 'src/libslic3r/Config.hpp'
PRESET = 'src/libslic3r/Preset.cpp'
SCOPE = {
    'process': ('TabPrint::build',),
    'filament': ('TabFilament::build', 'TabFilament::add_filament_overrides_page'),
    'printer': ('TabPrinter::build_fff', 'TabPrinter::build_sla', 'TabPrinter::build_unregular_pages', 'TabPrinter::build_kinematics_page'),
}
# Argument position is zero-based; the first argument of the two helper APIs is a group.
CALLS = {'append_single_option_line': 0, 'get_option': 0,
         'append_option_line': 1, 'create_line_with_widget': 1,
         'create_single_option_line': 0}
TOKEN = re.compile(r'''(?P<comment>//[^\n]*|/\*[\s\S]*?\*/)|(?P<string>(?:u8|u|U|L)?"(?:\\.|[^"\\])*")|(?P<char>(?:u8|u|U|L)?'(?:\\.|[^'\\])*')|(?P<id>[A-Za-z_]\w*)|(?P<number>(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?[A-Za-z0-9_]*)|(?P<space>\s+)|(?P<punct>::|->|&&|\|\||==|!=|<=|>=|\+\+|--|.)''')

class Tok:
    __slots__ = ('kind','value','start','end','line')
    def __init__(self,kind,value,start,end,line): self.kind,self.value,self.start,self.end,self.line=kind,value,start,end,line

def lex(src):
    result=[]; line=1
    for m in TOKEN.finditer(src):
        kind=m.lastgroup; value=m.group();
        if kind not in ('space','comment'): result.append(Tok(kind,value,m.start(),m.end(),line))
        line += value.count('\n')
    return result

def pair_indices(tokens):
    pairs={}; stack=[]; opening={'(':')','[':']','{':'}'}; closing=set(opening.values())
    for i,t in enumerate(tokens):
        if t.value in opening:stack.append((t.value,i))
        elif t.value in closing:
            if not stack or opening[stack[-1][0]]!=t.value:
                # Preprocessor alternatives can contain unmatched syntax. Keep
                # scanning, but surface unpaired calls instead of guessing.
                continue
            _,j=stack.pop();pairs[j]=i;pairs[i]=j
    return pairs

def call_args(tokens,op,cl):
    args=[]; begin=op+1; depth=0
    for k in range(begin,cl):
        v=tokens[k].value
        if v in ('(', '[', '{'):depth+=1
        elif v in (')',']','}'):depth-=1
        elif v==',' and depth==0:args.append((begin,k));begin=k+1
    if begin<cl:args.append((begin,cl))
    return args

def literal_string(tokens,start,end):
    if start>=end:return None
    items=tokens[start:end]
    # Accept an explicitly marked localization call around string literals.
    if len(items)>=3 and items[0].value in ('L','_L','_u8L') and items[1].value=='(' and items[-1].value==')':items=items[2:-1]
    if not items or any(t.kind!='string' for t in items):return None
    out=[]
    for t in items:
        raw=re.sub(r'^(?:u8|u|U|L)(?=")','',t.value)
        try:out.append(ast.literal_eval(raw))
        except (ValueError,SyntaxError):return None
    return ''.join(out)

def text_expr(src,tokens,start,end):
    return src[tokens[start].start:tokens[end-1].end].strip() if start<end else ''

def ref(path,line):return {'file':path,'line':line}

def parse_literal(src,tokens,start,end):
    s=literal_string(tokens,start,end)
    if s is not None:return {'value':s,'expression':text_expr(src,tokens,start,end),'resolved':True}
    expression=text_expr(src,tokens,start,end)
    if expression in ('true','false'):return {'value':expression=='true','expression':expression,'resolved':True}
    if re.fullmatch(r'[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?[fFlL]?',expression):
        try:
            n=expression.rstrip('fFlL');return {'value':float(n) if any(x in n for x in '.eE') else int(n),'expression':expression,'resolved':True}
        except ValueError:pass
    return {'value':None,'expression':expression,'resolved':False}

def parse_default(src,tokens,start,end,pairs):
    """Resolve only literal ConfigOption constructor arguments, never execute C++."""
    original=text_expr(src,tokens,start,end)
    if start+2>=end or tokens[start].value!='new' or not tokens[start+1].value.startswith('ConfigOption'):
        return parse_literal(src,tokens,start,end)
    typename=tokens[start+1].value; opening=start+2
    # Template parameters may identify an enum, but are not evaluated here.
    if tokens[opening].value=='<':
        depth=1;opening+=1
        while opening<end and depth:
            if tokens[opening].value=='<':depth+=1
            elif tokens[opening].value=='>':depth-=1
            opening+=1
    if opening>=end or tokens[opening].value not in ('(', '{') or pairs.get(opening)!=end-1:
        return {'value':None,'expression':original,'resolved':False}
    args=call_args(tokens,opening,end-1)
    if not args:return {'value':None,'expression':original,'resolved':False}
    parsed=[parse_literal(src,tokens,a,b) for a,b in args]
    if not all(v['resolved'] for v in parsed):return {'value':None,'expression':original,'resolved':False}
    values=[v['value'] for v in parsed]
    is_vector=typename in ('ConfigOptionFloats','ConfigOptionInts','ConfigOptionBools','ConfigOptionStrings','ConfigOptionPercents','ConfigOptionFloatOrPercents')
    if not is_vector and len(values)!=1:return {'value':None,'expression':original,'resolved':False}
    return {'value':values if is_vector else values[0],'expression':original,'resolved':True}

def semicolon(tokens,start,end):
    depth=0
    for j in range(start,end):
        v=tokens[j].value
        if v in ('(','[','{'):depth+=1
        elif v in (')',']','}'):depth-=1
        if v==';' and depth==0:return j
    return end

def parse_definitions(src,tokens,pairs):
    starts=[];preproc=preprocess_conditions(src)
    for i in range(len(tokens)-7):
        if [t.value for t in tokens[i:i+4]]!=['def','=','this','->'] or tokens[i+4].value not in ('add','add_nullable') or tokens[i+5].value!='(':continue
        close=pairs.get(i+5)
        if close is None:continue
        a=call_args(tokens,i+5,close)
        if len(a)<2:continue
        key=literal_string(tokens,*a[0]);type_expression=text_expr(src,tokens,*a[1]);starts.append((i,close,key,type_expression))
    definitions={};unresolved=[]
    for n,(start,close,key,typ) in enumerate(starts):
        end=starts[n+1][0] if n+1<len(starts) else len(tokens)
        if key is None or tokens[start+4].value!='add':
            unresolved.append({'file':CONFIG,'line':tokens[start].line,'kind':'dynamic_definition_key','expression':text_expr(src,tokens,start,close+1)});continue
        row={'key':key,'type':typ,'source':ref(CONFIG,tokens[start].line),'fields':{},'field_assignments':{},'enums':{'values':[],'labels':[],'keys_map':None,'values_complete':True,'labels_complete':True,'source_assignments':[]}}
        conditions=[]
        for k in range(close+1,end):
            if tokens[k].value=='else' and k+1<end:
                body=k+1;stop=pairs.get(body,end) if tokens[body].value=='{' else semicolon(tokens,body,end)
                conditions.append((body,stop,'else (preceding condition not statically resolved)'));continue
            if tokens[k].value not in ('if','for','while','switch') or k+1>=end or tokens[k+1].value!='(' or k+1 not in pairs:continue
            last=pairs[k+1];body=last+1
            if body>=end:continue
            stop=pairs.get(body,end) if tokens[body].value=='{' else semicolon(tokens,body,end)
            conditions.append((body,stop,text_expr(src,tokens,k,last+1)))
        def add_field(field,value,index):
            entry=dict(value);entry['source']=ref(CONFIG,tokens[index].line)
            entry['conditions']=[text for begin,stop,text in conditions if begin<=index<=stop]
            entry['preprocessor']=preproc.get(tokens[index].line,[])
            row['field_assignments'].setdefault(field,[]).append(entry)
        j=close+1
        while j+4<end:
            if tokens[j].value!='def' or tokens[j+1].value!='->' or tokens[j+2].kind!='id':j+=1;continue
            field=tokens[j+2].value
            if tokens[j+3].value=='=':
                e=semicolon(tokens,j+4,end);value=parse_literal(src,tokens,j+4,e)
                if field in ('enum_values','enum_labels'):
                    target='values' if field=='enum_values' else 'labels';expr=text_expr(src,tokens,j+4,e)
                    is_list=j+4<e and tokens[j+4].value=='{' and pairs.get(j+4)==e-1
                    parts=call_args(tokens,j+4,e-1) if is_list else []
                    vals=[literal_string(tokens,*part) for part in parts]
                    row['enums'][target]=vals;row['enums'][target+'_complete']=is_list and all(v is not None for v in vals)
                    row['enums']['source_assignments'].append({'field':field,'expression':expr,'source':ref(CONFIG,tokens[j].line)})
                elif field=='enum_keys_map':row['enums']['keys_map']=value['expression']
                else:add_field(field,value,j)
                j=e+1;continue
            if field in ('enum_values','enum_labels') and j+5<end and tokens[j+3].value=='.' and tokens[j+4].value=='push_back' and tokens[j+5].value=='(':
                q=pairs.get(j+5);a=call_args(tokens,j+5,q) if q is not None else []
                if a:
                    target='values' if field=='enum_values' else 'labels';v=literal_string(tokens,*a[0]);row['enums'][target].append(v)
                    if v is None:row['enums'][target+'_complete']=False
                    row['enums']['source_assignments'].append({'field':field+'.push_back','expression':text_expr(src,tokens,*a[0]),'source':ref(CONFIG,tokens[j].line)})
                j=(q+1) if q is not None else j+1;continue
            if field=='set_default_value' and tokens[j+3].value=='(':
                q=pairs.get(j+3);a=call_args(tokens,j+3,q) if q is not None else []
                if a:add_field('default',parse_default(src,tokens,*a[0],pairs),j)
                j=(q+1) if q is not None else j+1;continue
            j+=1
        for field,assignments in row['field_assignments'].items():
            clean=[{k:v for k,v in a.items() if k not in ('source','conditions','preprocessor')} for a in assignments]
            if not any(a['conditions'] or a['preprocessor'] for a in assignments) and (len(clean)==1 or all(v==clean[0] for v in clean)):row['fields'][field]=clean[0]
        if row['enums']['keys_map'] and not row['enums']['values']:row['enums']['values_complete']=False
        definitions.setdefault(key,[]).append(row)
    return definitions,unresolved

def method_ranges(tokens,pairs):
    result={}
    wanted={m for group in SCOPE.values() for m in group}
    for i in range(len(tokens)-5):
        if tokens[i].value not in ('TabPrint','TabFilament','TabPrinter') or tokens[i+1].value!='::':continue
        name=tokens[i].value+'::'+tokens[i+2].value
        if name not in wanted or tokens[i+3].value!='(':continue
        close=pairs.get(i+3)
        if close is None or close+1>=len(tokens) or tokens[close+1].value!='{':continue
        end=pairs.get(close+1)
        if end is not None:result[name]=(close+2,end)
    missing=sorted(wanted-set(result));
    if missing:raise ValueError('Cannot locate upstream methods: '+', '.join(missing))
    return result

def preprocess_conditions(src):
    result={};stack=[]
    for line,text in enumerate(src.splitlines(),1):
        m=re.match(r'^\s*#\s*(if|ifdef|ifndef|elif|else|endif)\b(.*)',text)
        if m:
            cmd,expr=m.group(1),m.group(2).strip()
            if cmd in ('if','ifdef','ifndef'):stack.append('#'+cmd+' '+expr)
            elif cmd in ('elif','else') and stack:stack[-1]='#'+cmd+' '+expr
            elif cmd=='endif' and stack:stack.pop()
        result[line]=list(stack)
    return result

def parse_placements(src,tokens,pairs):
    methods=method_ranges(tokens,pairs);preproc=preprocess_conditions(src);out={k:[] for k in SCOPE};dynamic=[]
    for section,names in SCOPE.items():
        for method in names:
            begin,end=methods[method];page=group=None;page_expr=group_expr=None;scopes=[];block_conditions={};single_conditions=[]
            # The conditions are preserved as source expressions, not evaluated.
            for i in range(begin,end):
                if tokens[i].value in ('if','for','while','switch') and i+1<end and tokens[i+1].value=='(' and i+1 in pairs:
                    close=pairs[i+1];after=close+1;condition=text_expr(src,tokens,i,close+1)
                    if after<end and tokens[after].value=='{':block_conditions[after]=condition
                    elif after<end:
                        last=semicolon(tokens,after,end);single_conditions.append((after,last,condition))
                elif tokens[i].value=='else' and i+1<end and tokens[i+1].value=='{':block_conditions[i+1]='else (preceding condition, not statically resolved)'
            i=begin
            while i<end:
                t=tokens[i]
                if t.value=='{':scopes.append(block_conditions.get(i));i+=1;continue
                if t.value=='}':
                    if scopes:scopes.pop()
                    i+=1;continue
                if t.kind!='id' or i+1>=end or tokens[i+1].value!='(' or i+1 not in pairs:i+=1;continue
                close=pairs[i+1]
                if close>end:i+=1;continue
                args=call_args(tokens,i+1,close);name=t.value
                conditions=[s for s in scopes if s]+[cond for a,b,cond in single_conditions if a<=i<=b]
                if name=='add_options_page' and args:
                    page=literal_string(tokens,*args[0]);page_expr=text_expr(src,tokens,*args[0]);group=None;group_expr=None
                elif name=='new_optgroup' and args:
                    group=literal_string(tokens,*args[0]);group_expr=text_expr(src,tokens,*args[0])
                elif name in CALLS:
                    which=CALLS[name];arg=args[which] if len(args)>which else None
                    val=literal_string(tokens,*arg) if arg else None
                    expr=text_expr(src,tokens,*arg) if arg else ''
                    index_arg=1 if name=='get_option' else (2 if name=='append_single_option_line' else None)
                    index_expr=text_expr(src,tokens,*args[index_arg]) if index_arg is not None and len(args)>index_arg else None
                    index=int(index_expr) if index_expr is not None and re.fullmatch(r'-?\d+',index_expr) else None
                    option_id=(val if index_expr is None or index==-1 else (val+'#'+str(index) if index is not None and index>=0 else None)) if val is not None else None
                    place={'file':TAB,'line':t.line,'method':name,'builder':method,'page':page,'page_expression':page_expr,'group':group,'group_expression':group_expr,
                           'base_key':val,'index':index,'index_expression':index_expr,'option_id':option_id,
                           'conditions':conditions,'preprocessor':preproc.get(t.line,[]),'evidence':'reference_only' if name=='get_option' else 'direct_call'}
                    if val is None:dynamic.append({'section':section,'file':TAB,'line':t.line,'method':name,'builder':method,'expression':expr,'conditions':conditions,'preprocessor':preproc.get(t.line,[])})
                    else:out[section].append((val,place))
                # Do not skip nested calls; e.g. create_single_option_line(get_option()).
                i+=1
    return out,dynamic

def parse_preset_membership(src,tokens,pairs):
    arrays={
        's_Preset_print_options':('process','fff'),
        's_Preset_sla_print_options':('process','sla'),
        's_Preset_filament_options':('filament','fff'),
        's_Preset_sla_material_options':('filament','sla'),
        's_Preset_printer_options':('printer','fff'),
        's_Preset_sla_printer_options':('printer','sla'),
        's_Preset_machine_limits_options':('printer','machine_limits'),
    }
    result={section:[] for section in SCOPE};found=set()
    for i,t in enumerate(tokens[:-1]):
        if t.value not in arrays or tokens[i+1].value!='{' or i+1 not in pairs:continue
        close=pairs[i+1];section,technology=arrays[t.value];found.add(t.value)
        for k in range(i+2,close):
            if tokens[k].kind!='string':continue
            key=literal_string(tokens,k,k+1)
            if key is not None:result[section].append((key,{'file':PRESET,'line':tokens[k].line,'array':t.value,'technology':technology}))
    missing=sorted(set(arrays)-found)
    if missing:raise ValueError('Cannot locate upstream preset arrays: '+', '.join(missing))
    return result

def parse_extruder_membership(tokens,pairs):
    for i in range(len(tokens)-2):
        if tokens[i].value=='m_extruder_option_keys' and tokens[i+1].value=='=' and tokens[i+2].value=='{' and i+2 in pairs:
            close=pairs[i+2];result=[]
            for k in range(i+3,close):
                if tokens[k].kind=='string':
                    key=literal_string(tokens,k,k+1)
                    if key is not None:result.append((key,{'file':CONFIG,'line':tokens[k].line,'array':'m_extruder_option_keys','technology':'nozzle'}))
            return result
    raise ValueError('Cannot locate upstream extruder option keys')

def record(key,placements,definitions,memberships=(),default_mode=None):
    candidates=definitions.get(key,[])
    fields={}
    for field in ('label','tooltip','sidetext','min','max','mode','default'):
        vals=[d['fields'][field] for d in candidates if field in d['fields']]
        fields[field]=vals[0] if len(vals)==1 else (vals[0] if vals and all(v==vals[0] for v in vals) else None)
    types=sorted({d['type'] for d in candidates});type_value=types[0] if len(types)==1 else None
    enum=candidates[0]['enums'] if len(candidates)==1 else None
    primary=placements[0] if placements else {}
    mode=None;mode_source=None;valid_modes=('comSimple','comAdvanced','comExpert','comDevelop')
    if len(candidates)==1:
        assignments=candidates[0]['field_assignments'].get('mode',[])
        if not assignments and default_mode:
            mode=default_mode['value'];mode_source={'kind':'default_initializer','source':default_mode['source']}
        elif fields['mode'] and fields['mode']['expression'] in valid_modes:
            mode=fields['mode']['expression'];mode_source={'kind':'unconditional_assignment','source':assignments[0]['source']}
    field_resolution={}
    for name,value in fields.items():
        if not candidates:field_resolution[name]='definition_missing'
        elif name=='mode' and mode is not None:field_resolution[name]=mode_source['kind']
        elif value is None:field_resolution[name]='not_declared_or_ambiguous'
        elif value['resolved']:field_resolution[name]='source_literal'
        else:field_resolution[name]='expression_unresolved'
    match=re.fullmatch(r'(.+)#(\d+)',key);base_key=match.group(1) if match else key;index=int(match.group(2)) if match else None
    return {'key':key,'base_key':base_key,'index':index,'label':fields['label']['value'] if fields['label'] and fields['label']['resolved'] else None,
            'tooltip':fields['tooltip']['value'] if fields['tooltip'] and fields['tooltip']['resolved'] else None,
            'type':type_value,'units':fields['sidetext']['value'] if fields['sidetext'] and fields['sidetext']['resolved'] else None,
            'min':fields['min']['value'] if fields['min'] and fields['min']['resolved'] else None,'max':fields['max']['value'] if fields['max'] and fields['max']['resolved'] else None,
            'enums':enum,'mode':mode,'mode_source':mode_source,
            'page':primary.get('page'),'group':primary.get('group'),'default':fields['default'],
            'source':{'definition':[d['source'] for d in candidates],'tab':ref(TAB,primary['line']) if primary else None},
            'availability':{'conditions':primary.get('conditions',[]),'preprocessor':primary.get('preprocessor',[]),'runtime_visibility':'unknown'},
            'placements':placements,'preset_membership':list(memberships),'definition_candidates':candidates,
            'resolution':{'definition_count':len(candidates),'ambiguous_definition':len(candidates)>1,'fields':field_resolution,'schema_presence_is_not_ui_editability':True},
            'ui_accessibility':{'status':'unknown','reason':'Static schema presence does not establish that a mobile UI exposes or can edit this option.'}}

def apply_reviewed_dynamic_definitions(sections,definitions,default_mode,checkout):
    """Expand only manually reviewed expressions against their exact source hashes."""
    audit_path=ROOT/'tools'/'settings_dynamic_audit.json'
    audit=json.loads(audit_path.read_text(encoding='utf8'))
    for path,digest in audit['sources'].items():
        if hashlib.sha256((checkout/path).read_bytes()).hexdigest()!=digest:
            raise ValueError('Reviewed dynamic definitions are stale for '+path)
    audited={item['line']:item for item in audit['dynamic_definitions']}
    if set(audited)!={1305,5388,5406,5424,7772,8785,13307}:raise ValueError('Unexpected dynamic definition audit')
    rows={r['key']:r for group in sections.values() for r in group}
    for line in (1305,5388,5406,5424,7772,8785):
        item=audited[line]
        for key in item['keys']:
            if key not in rows:raise ValueError('Reviewed dynamic key missing from schema: '+key)
            row=rows[key]
            if row['type'] is not None:raise ValueError('Dynamic key unexpectedly already defined: '+key)
            row['source']['definition'].append(ref(CONFIG,line))
            row['resolution']['dynamic_definition']={'source':ref(CONFIG,line),'evidence':item['evidence'],'reviewed':True}
            row['resolution']['fields']['mode']='reviewed_dynamic_source'
            mode_line={1305:1311,5388:5403,5406:5421,5424:5439,7772:7779,8785:8800}[line]
            row['mode_source']={'kind':'reviewed_dynamic_source','source':ref(CONFIG,mode_line)}
            if line==1305:
                row.update(type='coString',label='Capabilities',mode='comAdvanced')
                row['resolution']['dynamic_definition']['desktop_control']='plugin_config dialog'
            elif line in (5388,5406,5424):
                axis=key[-1].upper()
                category={5388:'speed',5406:'acceleration',5424:'jerk'}[line]
                row.update(type='coFloats',label='Maximum '+category+' '+axis,mode='comSimple',
                           units='mm/s²' if line==5406 else 'mm/s',min=0)
                # Values are copied from the literal axis table at PrintConfig.cpp:5379-5384.
                defaults={'x':([500,200],[1000,1000],[10,10]),'y':([500,200],[1000,1000],[10,10]),
                          'z':([12,12],[500,200],[.2,.4]),'e':([120,120],[5000,5000],[2.5,2.5])}
                row['default']={'value':defaults[key[-1]][{5388:0,5406:1,5424:2}[line]],
                                'expression':'axis.'+{5388:'max_feedrate',5406:'max_acceleration',5424:'max_jerk'}[line],
                                'resolved':True,'source':ref(CONFIG,{'x':5380,'y':5381,'z':5382,'e':5383}[key[-1]])}
            elif line==7772:
                axis=key[-1].upper()
                row.update(type='coEnum',label='G-code remap '+axis,mode='comDevelop',
                           enums={'values':['pos_x','pos_y','pos_z','neg_x','neg_y','neg_z','rev_x','rev_y','rev_z'],
                                  'labels':['+X','+Y','+Z','-X','-Y','-Z','Rev X','Rev Y','Rev Z'],
                                  'keys_map':'ConfigOptionEnum<RemapAxis>::get_enum_values()',
                                  'values_complete':True,'labels_complete':True,'source_assignments':[]})
            else:
                base=key[len('filament_'):]
                original=record(base,[],definitions,default_mode=default_mode)
                if original['type'] is None:raise ValueError('Cannot resolve inherited type of '+key)
                for field in ('type','label','tooltip','units','min','max','enums'):
                    row[field]=original[field]
                row['mode']='comSimple' if key in ('filament_retraction_length','filament_z_hop','filament_long_retractions_when_cut','filament_retraction_distances_when_cut') else 'comAdvanced'
                row['mode_source']['source']=ref(CONFIG,8800 if row['mode']=='comSimple' else 8802)
                row['nullable']=True
                row['resolution']['dynamic_definition']['inherits_definition']=base
                row['source']['definition'].extend(original['source']['definition'])
                # The upstream default is constructed from the base at runtime;
                # do not invent a value for a nullable vector.
                row['default']={'value':None,'expression':'nullable copy of '+base+' default','resolved':False}

def build(checkout):
    config_bytes=(checkout/CONFIG).read_bytes();tab_bytes=(checkout/TAB).read_bytes();preset_bytes=(checkout/PRESET).read_bytes();header_bytes=(checkout/CONFIG_HEADER).read_bytes()
    config=config_bytes.decode('utf-8-sig');tab=tab_bytes.decode('utf-8-sig');preset=preset_bytes.decode('utf-8-sig');header=header_bytes.decode('utf-8-sig')
    default_match=re.search(r'\bConfigOptionMode\s+mode\s*=\s*(comSimple|comAdvanced|comExpert|comDevelop)\s*;',header)
    if default_match is None:raise ValueError('Cannot establish ConfigOptionDef mode initializer')
    default_mode={'value':default_match.group(1),'source':ref(CONFIG_HEADER,header.count('\n',0,default_match.start())+1)}
    ct=lex(config);tt=lex(tab);pt=lex(preset);cp=pair_indices(ct);defs,unknown_defs=parse_definitions(config,ct,cp);placements,dynamic=parse_placements(tab,tt,pair_indices(tt));membership=parse_preset_membership(preset,pt,pair_indices(pt));membership['printer'].extend(parse_extruder_membership(ct,cp));sections={}
    for section,rows in placements.items():
        ordered={};members={}
        for key,place in rows:ordered.setdefault(key,[]).append(place)
        for key,member in membership[section]:ordered.setdefault(key,[]);members.setdefault(key,[]).append(member)
        sections[section]=[record(key,places,defs,members.get(key,[]),default_mode) for key,places in ordered.items()]
    apply_reviewed_dynamic_definitions(sections,defs,default_mode,checkout)
    mapped={x['key'] for rows in sections.values() for x in rows};unmapped=sorted(set(defs)-mapped)
    return {'schema_version':1,'upstream_revision':EXPECTED_REVISION,'revision_verification':'not performed; compare the source checkout independently',
            'sources':{'tab':{'path':TAB,'sha256':hashlib.sha256(tab_bytes).hexdigest()},'definitions':{'path':CONFIG,'sha256':hashlib.sha256(config_bytes).hexdigest()},'definition_header':{'path':CONFIG_HEADER,'sha256':hashlib.sha256(header_bytes).hexdigest()},'presets':{'path':PRESET,'sha256':hashlib.sha256(preset_bytes).hexdigest()}},
            'sections':sections,'unmapped_definitions':[record(key,[],defs,default_mode=default_mode) for key in unmapped],
            'diagnostics':{'dynamic_references':dynamic,'dynamic_definitions':unknown_defs,'unmapped_definition_keys':unmapped,
            'limitations':['Static source extraction only: no C++ evaluation, preprocessing, or runtime UI visibility proof.',
            'get_option references can be unattached; direct calls can be conditional; multiline/control-flow scope is conservative.',
            'Nonliteral defaults, labels, enum maps and bounds retain source expressions, not invented values.',
            'Definitions outside PrintConfig.cpp, helper pages not in the declared method list, runtime option generation and desktop fields without literal option keys need independent review.',
            'Schema entries do not mean the current mobile UI can edit them or that changing them is supported by JNI.']}}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--orca',type=Path,default=WORKSPACE/'work'/'OrcaSlicer');p.add_argument('--output',type=Path,default=ROOT/'app'/'src'/'main'/'assets'/'orca_settings_schema.json');p.add_argument('--runtime-output',type=Path,default=ROOT/'app'/'src'/'main'/'assets'/'orca_settings_runtime.json');p.add_argument('--dynamic-report',type=Path,default=ROOT/'tools'/'SETTINGS_DYNAMIC_EXTRACTED.md');args=p.parse_args()
    try:
        doc=build(args.orca);payload=json.dumps(doc,ensure_ascii=False,separators=(',',':'))+'\n';args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(payload,encoding='utf-8',newline='\n')
        runtime={'schema_version':doc['schema_version'],'upstream_revision':doc['upstream_revision'],'source_hashes':{name:source['sha256'] for name,source in doc['sources'].items()},'sections':{}}
        for section,rows in doc['sections'].items():
            compact=[]
            for r in rows:
                option={k:r[k] for k in ('key','base_key','index','label','tooltip','type','units','min','max','mode','page','group')}
                option['nullable']=r.get('nullable')
                option['source']=r['source']
                enum=r['enums'];option['enums']={k:enum[k] for k in ('values','labels','values_complete','labels_complete','keys_map')} if enum else None
                default=r['default'];option['default']={'value':default['value'],'resolved':default['resolved']} if default else None
                compact.append(option)
            runtime['sections'][section]=compact
        args.runtime_output.parent.mkdir(parents=True,exist_ok=True);args.runtime_output.write_text(json.dumps(runtime,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8',newline='\n')
        def cell(s):return str(s).replace('|','\\|').replace('`','\\`').replace('\n',' ')
        lines=['# Orca dynamic settings: unresolved source calls','',
               'Generated static review queue, not a claim that the desktop UI omits these options. '
               'No code is executed. Resolve each source expression and attachment against the pinned Orca checkout and active runtime.', '',
               '| Section | Builder | Source line | Call | Expression | Note |','|---|---|---:|---|---|---|']
        for d in doc['diagnostics']['dynamic_references']:
            expr=d['expression'];note=('Nested wrapper: inspect its inner get_option and actual row attachment' if 'get_option' in expr else
                    'Local variable: trace assignment and attachment' if re.fullmatch(r'[A-Za-z_]\w*',expr) else
                    'Runtime expression: enumerate valid expansions without guessing')
            lines.append('| '+ ' | '.join(cell(x) for x in (d['section'],d['builder'],d['line'],d['method'],expr,note))+' |')
        lines += ['','## Dynamic definitions','','These are source expressions, not fully resolved option definitions.','',
                  '| Source line | Expression |','|---:|---|']
        for d in doc['diagnostics']['dynamic_definitions']:lines.append('| '+cell(d['line'])+' | '+cell(d['expression'])+' |')
        lines += ['',f"Unmapped definitions: {len(doc['diagnostics']['unmapped_definition_keys'])}; see `unmapped_definitions` in the JSON. Unmapped does not mean missing from desktop UI.", '']
        args.dynamic_report.parent.mkdir(parents=True,exist_ok=True);args.dynamic_report.write_text('\n'.join(lines),encoding='utf-8',newline='\n')
    except (OSError,ValueError) as e:print('Cannot generate settings inventory:',e,file=sys.stderr);return 2
    print('Generated:',args.output)
    print('Counts:',', '.join(f'{k}={len(v)}' for k,v in doc['sections'].items()),'; dynamic references=',len(doc['diagnostics']['dynamic_references']),'; unmapped definitions=',len(doc['diagnostics']['unmapped_definition_keys']))
    return 0
if __name__=='__main__':raise SystemExit(main())
