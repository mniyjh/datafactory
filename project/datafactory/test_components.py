import urllib.request, json, time

BASE = 'http://localhost:8082'

def api(m, p, d=None):
    u = f'{BASE}{p}'; b = json.dumps(d).encode() if d else None
    req = urllib.request.Request(u, data=b, method=m, headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req) as resp: return json.loads(resp.read())
    except Exception as e: return {'error': str(e)}

def test_dsl(name, dsl_content, version):
    r = api('POST', '/task-dsl/version', {'taskId':7, 'environment':'PROD', 'version':version, 'dslContent':json.dumps(dsl_content), 'changeLog':name+' test'})
    print(f'  DSL创建: {r.get("message")}')
    r = api('GET', '/task-dsl/7/versions')
    for v in r.get('data', []):
        if v.get('version') == version:
            dsl_id = v.get('id')
            api('POST', f'/task-dsl/version/{dsl_id}/publish', {})
            api('POST', f'/task-dsl/version/{dsl_id}/current', {})
            r3 = api('POST', '/tasks/7/execute', {'versionId': dsl_id})
            exec_id = r3.get('data')
            time.sleep(1)
            if exec_id:
                r4 = api('GET', f'/executor/log/detail/{exec_id}')
                d4 = r4.get('data',{})
                print(f'  Result: status={d4.get("status")} output={str(d4.get("outputResult",""))[:200]}')
                r5 = api('GET', f'/executor/log/nodes/{exec_id}')
                for n in r5.get('data', []):
                    print(f'    [{n.get("nodeType")}] {n.get("nodeName")}: {n.get("status")}')
            break

# Check all components
r = api('GET', '/component?current=1&size=20')
comps = r.get('data', {}).get('records', [])
print('=== 组件清单 ===')
for c in comps:
    print(f'  [{c.get("type"):15s}] id={c.get("id")} code={c.get("code"):15s} status={c.get("status")}')

# Test 1: DB
print()
print('=== 测试 DB ===')
test_dsl('DB', {
    'nodes': [
        {'id':'s1','type':'START','name':'S','position':{'x':100,'y':100}},
        {'id':'d1','type':'DB','name':'DB','componentId':3,'position':{'x':300,'y':100},
         'fieldValues':{'sql':'SELECT COUNT(*) AS cnt FROM component'}},
        {'id':'e1','type':'END','name':'E','position':{'x':500,'y':100}}
    ],
    'edges':[
        {'id':'e1','source':{'nodeId':'s1'},'target':{'nodeId':'d1'}},
        {'id':'e2','source':{'nodeId':'d1'},'target':{'nodeId':'e1'}}
    ]
}, 'v_db')

# Test 2: BRANCH
print()
print('=== 测试 BRANCH ===')
test_dsl('BRANCH', {
    'nodes': [
        {'id':'s1','type':'START','name':'S','position':{'x':100,'y':100}},
        {'id':'d1','type':'DB','name':'Count','componentId':3,'position':{'x':300,'y':100},
         'fieldValues':{'sql':'SELECT COUNT(*) AS cnt FROM component WHERE status=1'}},
        {'id':'b1','type':'BRANCH','name':'Branch','componentId':4,'position':{'x':500,'y':100},
         'fieldValues':{'expression':'cnt > 0','trueBranch':'e1','falseBranch':'e1'}},
        {'id':'e1','type':'END','name':'E','position':{'x':700,'y':100}}
    ],
    'edges':[
        {'id':'e1','source':{'nodeId':'s1'},'target':{'nodeId':'d1'}},
        {'id':'e2','source':{'nodeId':'d1'},'target':{'nodeId':'b1'}},
        {'id':'e3','source':{'nodeId':'b1'},'target':{'nodeId':'e1'}}
    ]
}, 'v_branch')

# Test 3: FILTER
print()
print('=== 测试 FILTER ===')
test_dsl('FILTER', {
    'nodes': [
        {'id':'s1','type':'START','name':'S','position':{'x':100,'y':100}},
        {'id':'d1','type':'DB','name':'DB','componentId':3,'position':{'x':300,'y':100},
         'fieldValues':{'sql':'SELECT component_type AS value, component_name AS label FROM component WHERE delete_flag=0 LIMIT 5'}},
        {'id':'f1','type':'FILTER','name':'Filter','componentId':8,'position':{'x':500,'y':100},
         'fieldValues':{'filterType':'COLUMN','columns':'value,label','sourceNodeId':'d1'}},
        {'id':'e1','type':'END','name':'E','position':{'x':700,'y':100}}
    ],
    'edges':[
        {'id':'e1','source':{'nodeId':'s1'},'target':{'nodeId':'d1'}},
        {'id':'e2','source':{'nodeId':'d1'},'target':{'nodeId':'f1'}},
        {'id':'e3','source':{'nodeId':'f1'},'target':{'nodeId':'e1'}}
    ]
}, 'v_filter')

# Test 4: API (direct URL, no Feign)
print()
print('=== 测试 API(直接URL) ===')
test_dsl('API', {
    'nodes': [
        {'id':'s1','type':'START','name':'S','position':{'x':100,'y':100}},
        {'id':'a1','type':'API','name':'API','componentId':7,'position':{'x':300,'y':100},
         'fieldValues':{'url':'https://httpbin.org/get','method':'GET'}},
        {'id':'e1','type':'END','name':'E','position':{'x':500,'y':100}}
    ],
    'edges':[
        {'id':'e1','source':{'nodeId':'s1'},'target':{'nodeId':'a1'}},
        {'id':'e2','source':{'nodeId':'a1'},'target':{'nodeId':'e1'}}
    ]
}, 'v_api')

# Test 5: PYTHON (SQL type script, no ProcessBuilder)
print()
print('=== 测试 PYTHON(SQL脚本) ===')
test_dsl('PYTHON', {
    'nodes': [
        {'id':'s1','type':'START','name':'S','position':{'x':100,'y':100}},
        {'id':'p1','type':'PYTHON','name':'Python','componentId':6,'position':{'x':300,'y':100},
         'fieldValues':{'scriptCode':'python','scriptType':'SQL'}},
        {'id':'e1','type':'END','name':'E','position':{'x':500,'y':100}}
    ],
    'edges':[
        {'id':'e1','source':{'nodeId':'s1'},'target':{'nodeId':'p1'}},
        {'id':'e2','source':{'nodeId':'p1'},'target':{'nodeId':'e1'}}
    ]
}, 'v_python')

print()
print('=== 全部测试完成 ===')
