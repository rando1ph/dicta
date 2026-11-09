"""M0 executable contract spike; no Android UI and no network unless --live."""
import argparse
import asyncio
import json
import time
from datetime import datetime, timezone
from pathlib import Path

import httpx
from jsonschema import Draft7Validator

ROOT = Path(__file__).resolve().parents[2]
PROFILE = ROOT / 'profiles/default'
MAX_BYTES = 131072
KEYWORDS = {'$schema', 'title', 'description', 'type', 'properties', 'required',
            'additionalProperties', 'items', 'enum', 'minLength', 'maxLength',
            'minItems', 'maxItems', 'minimum', 'maximum'}

class Failure(Exception):
    """Safe error code only: never include response, query, headers or key."""

def load(path):
    return strict_json(Path(path).read_text())

def strict_json(raw):
    def pairs(xs):
        out = {}
        for k, v in xs:
            if k in out:
                raise Failure('duplicate_key')
            out[k] = v
        return out
    def invalid(_):
        raise Failure('invalid_number')
    try:
        return json.loads(raw, object_pairs_hook=pairs, parse_constant=invalid)
    except (ValueError, RecursionError):
        raise Failure('invalid_json') from None

def check_schema(schema, depth=0):
    if depth > 12 or not isinstance(schema, dict) or set(schema) - KEYWORDS:
        raise Failure('unsupported_schema')
    Draft7Validator.check_schema(schema)
    kind = schema.get('type')
    if kind not in ('object', 'array', 'string', 'number', 'integer', 'boolean', 'null', ['string', 'null']):
        raise Failure('unsupported_type')
    if schema.get('$schema', 'http://json-schema.org/draft-07/schema#') != 'http://json-schema.org/draft-07/schema#':
        raise Failure('unsupported_draft')
    if schema.get('type') == 'object':
        if schema.get('additionalProperties') is not False:
            raise Failure('unbounded_object')
        for child in schema.get('properties', {}).values():
            check_schema(child, depth + 1)
    if schema.get('type') == 'array':
        if not isinstance(schema.get('maxItems'), int) or schema['maxItems'] > 20:
            raise Failure('unbounded_array')
        check_schema(schema.get('items'), depth + 1)

def tokens(pointer):
    if not pointer.startswith('/'):
        raise Failure('invalid_binding')
    return [x.replace('~1', '/').replace('~0', '~') for x in pointer[1:].split('/')]

def at_schema(schema, pointer):
    for key in tokens(pointer):
        if schema.get('type') == 'array' and key.isdigit():
            if int(key) >= schema.get('minItems', 0):
                raise Failure('optional_binding')
            schema = schema['items']
        elif schema.get('type') == 'object' and key in schema['properties']:
            if key not in schema.get('required', []):
                raise Failure('optional_binding')
            schema = schema['properties'][key]
        else:
            raise Failure('unmapped_binding')
    return schema

def at(data, pointer):
    try:
        for key in tokens(pointer):
            data = data[int(key)] if isinstance(data, list) else data[key]
        return data
    except (KeyError, IndexError, TypeError, ValueError):
        raise Failure('missing_binding') from None

def is_text(schema):
    t = schema.get('type')
    return t == 'string' or t == ['string', 'null']

def validate_template(template, schema):
    spec = load(PROFILE / 'template.schema.json')
    if list(Draft7Validator(spec).iter_errors(template)):
        raise Failure('invalid_template')
    count = 0
    def walk(nodes, scope, depth=0):
        nonlocal count
        if depth > 12:
            raise Failure('template_too_deep')
        for node in nodes:
            count += 1
            if count > 128:
                raise Failure('template_too_large')
            kind = node['type']
            target = at_schema(scope, node['bind']) if 'bind' in node else scope
            if kind in ('text', 'badge') and not is_text(target):
                raise Failure('binding_type')
            if kind in ('repeat', 'chips') and target.get('type') != 'array':
                raise Failure('binding_type')
            if kind == 'chips' and not is_text(target['items']):
                raise Failure('binding_type')
            if 'children' in node:
                walk(node['children'], target['items'] if kind == 'repeat' else scope, depth + 1)
    walk(template['nodes'], schema)
    for pointer in [*template['summary'].values(), template['speech']]:
        if not is_text(at_schema(schema, pointer)):
            raise Failure('binding_type')

def render(template, data):
    """Non-visual tree proving generic bindings, scopes and ordered field rendering."""
    def walk(nodes, scope):
        output = []
        for node in nodes:
            kind = node['type']
            value = at(scope, node['bind']) if 'bind' in node else None
            if node.get('hideIfEmpty') and value in (None, '', []):
                continue
            view = {k: v for k, v in node.items() if k not in ('bind', 'children', 'hideIfEmpty')}
            if kind == 'repeat':
                view['items'] = [walk(node['children'], item) for item in value]
            elif 'children' in node:
                view['children'] = walk(node['children'], scope)
            else:
                view['value'] = value  # plain text; never evaluate scripts or HTML
            output.append(view)
        return output
    return {'summary': {k: at(data, v) for k, v in template['summary'].items()},
            'nodes': walk(template['nodes'], data), 'speech': at(data, template['speech'])}

def decode(envelope, schema):
    try:
        choice = envelope['choices'][0]
        if choice['finish_reason'] != 'stop':
            raise Failure('incomplete_response')
        raw = choice['message']['content']
        if not isinstance(raw, str) or not raw.strip():
            raise Failure('empty_content')
        if len(raw.encode()) > MAX_BYTES:
            raise Failure('response_too_large')
        entry = strict_json(raw)
    except (KeyError, IndexError, TypeError):
        raise Failure('invalid_envelope') from None
    if list(Draft7Validator(schema).iter_errors(entry)):
        raise Failure('schema_invalid')
    return entry

def configuration():
    profile = load(PROFILE / 'profile.json')
    schema = load(PROFILE / profile['schema'])
    template = load(PROFILE / profile['template'])
    check_schema(schema)
    validate_template(template, schema)
    example = load(PROFILE / profile['example'])
    if list(Draft7Validator(schema).iter_errors(example)):
        raise Failure('example_invalid')
    return profile, schema, template, example

def request_body(profile, schema, example, query, mode='lookup'):
    if mode not in ('lookup', 'daily') or not isinstance(query, str) or len(query) > 100 or (mode == 'lookup' and not query.strip()):
        raise Failure('invalid_input')
    prompt = (PROFILE / profile['prompt']).read_text()
    for name, value in [('schema_json', schema), ('example_json', example)]:
        prompt = prompt.replace('{{' + name + '}}', json.dumps(value, ensure_ascii=False))
    if '{{' in prompt:
        raise Failure('unbound_prompt_variable')
    return {'model':profile['provider']['model'], 'thinking':{'type':'disabled'},
            'messages':[{'role':'system','content':prompt},
                        {'role':'user','content':json.dumps({'mode':mode,'query':query,'exclude':[]}, ensure_ascii=False)}],
            'response_format':{'type':'json_object'},'max_tokens':profile['provider']['maxTokens'],'stream':False}

async def post(client, url, body, key, total=60):
    async def request():
        async with client.stream('POST', url, json=body, headers={'Authorization':'Bearer '+key}) as r:
            if r.status_code != 200:
                # Never retain raw error text; it may echo credentials/input.
                raise Failure('http_' + str(r.status_code))
            chunks = bytearray()
            async for chunk in r.aiter_bytes():
                chunks.extend(chunk)
                if len(chunks) > MAX_BYTES:
                    raise Failure('response_too_large')
            return strict_json(chunks.decode('utf-8'))
    try:
        return await asyncio.wait_for(request(), timeout=total)
    except (asyncio.TimeoutError, httpx.TimeoutException):
        raise Failure('timeout') from None
    except httpx.RequestError:
        raise Failure('network') from None
    except UnicodeDecodeError:
        raise Failure('invalid_encoding') from None

def save(path, data):
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n')

async def live():
    profile, schema, template, example = configuration()
    secret_file = Path.home()/'.config/dicta/deepseek_api_key'
    if secret_file.stat().st_mode & 0o777 != 0o600 or secret_file.parent.stat().st_mode & 0o777 != 0o700:
        raise Failure('credential_permissions')
    key = secret_file.read_text().strip()
    if not key:
        raise Failure('credential_empty')
    report = {'timeUtc':datetime.now(timezone.utc).isoformat(),'provider':profile['provider'],'tests':[]}
    url = 'https://api.deepseek.com/chat/completions'  # deliberately fixed: never send key to imported endpoint
    async with httpx.AsyncClient(timeout=httpx.Timeout(40, connect=10), follow_redirects=False) as client:
        for query in ['luminous','ephemeral','qzxvplmnonce']:
            started=time.monotonic()
            row={'case':query}
            try:
                response=await post(client,url,request_body(profile,schema,example,query),key)
                entry=decode(response,schema)
                preview=render(template,entry)
                # Public synthetic queries only; exact-secret guard before any content is persisted.
                if key in json.dumps([entry,preview],ensure_ascii=False):
                    raise Failure('secret_in_response')
                save(ROOT/f'docs/m0/examples/{query}.json',entry)
                save(ROOT/f'docs/m0/evidence/{query}.render.json',preview)
                row.update({'outcome':'valid_and_mapped','finishReason':response['choices'][0]['finish_reason'],
                            'model':response.get('model'),'usage':response.get('usage')})
            except Failure as e:
                row['outcome']=str(e)
            row['seconds']=round(time.monotonic()-started,3)
            report['tests'].append(row)
            print(json.dumps(row))
        # One tiny capability probe; no implicit downgrade or repeated billable retries.
        body={'model':profile['provider']['model'],'thinking':{'type':'disabled'},'messages':[{'role':'user','content':'Return JSON: {"ok":true}'}],
              'response_format':{'type':'json_schema','json_schema':{'name':'probe','strict':True,'schema':{'type':'object','properties':{'ok':{'type':'boolean'}},'required':['ok'],'additionalProperties':False}}},'max_tokens':32}
        for label, payload, credential in [('json_schema_capability',body,key),('invalid_auth',request_body(profile,schema,example,'test'),'dicta-intentionally-invalid')]:
            try:
                response=await post(client,url,payload,credential)
                outcome='http_200_not_proof_of_schema_enforcement'
            except Failure as e:
                outcome=str(e)
            report['tests'].append({'case':label,'outcome':outcome})
            print(label,outcome)
    save(ROOT/'docs/m0/evidence/deepseek-live.json',report)
    if not all(t['outcome']=='valid_and_mapped' for t in report['tests'][:3]):
        raise Failure('live_validation_failed')

if __name__=='__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--live',action='store_true',help='Makes five limited real API calls; reads external key without logging it.')
    args=parser.parse_args()
    if args.live:
        asyncio.run(live())
    else:
        _,schema,template,example=configuration()
        render(template,example)
        for path in sorted((ROOT/'docs/m0/examples').glob('*.json')):
            entry=load(path)
            if list(Draft7Validator(schema).iter_errors(entry)):
                raise Failure('saved_example_invalid')
            render(template,entry)
        print('PASS: default profile, schema, template bindings and saved examples')
