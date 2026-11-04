"""Exercise template navigation with the project's strict checking disabled."""
import json, os, pathlib, shutil, subprocess, sys
r = pathlib.Path(__file__).resolve().parents[1]
version = sys.argv[2] if len(sys.argv)>2 else None
fixture = r/'build'/('template-mode-'+version) if version else r/'demo/template-mode-fixture'
fixture.mkdir(exist_ok=True)
for name in ['app.ts', 'app.html', 'child.ts', 'tsconfig.json']:
    shutil.copy2(r/'demo/fixture'/name, fixture/name)
# Match the reported case: a top-level @if encloses the entire form, including
# bindings on multiline custom elements and a nested switch.
template = (fixture/'app.html').read_text(encoding='utf-8')
(fixture/'app.html').write_text('@if (hasMachin) {\n'+template+'\n}\n',encoding='utf-8')
config = json.loads((fixture/'tsconfig.json').read_text())
mode = sys.argv[1] if len(sys.argv)>1 else 'basic'
config['angularCompilerOptions'] = {} if mode=='absent' else {'strictTemplates':False}
if mode in ['full','explicit-basic']: config['angularCompilerOptions']['fullTemplateTypeCheck'] = mode=='full'
config_text = json.dumps(config,indent=2)
(fixture/'tsconfig.json').write_text(config_text)
env = os.environ.copy()
node = 'node'
if version:
    node = pathlib.Path((r/'build/automatic-test-node.txt').read_text())
    runtimes = (r/'build/automatic-test-runtime.txt').read_text()
    (fixture/'package.json').write_text(json.dumps({'private':True,'dependencies':{
        '@angular/core':version, '@angular/common':version, '@angular/compiler':version,
        'typescript':'6.0.3', 'rxjs':'7.8.2'}}))
    subprocess.run([str(node), str(node.parent/'node_modules/npm/bin/npm-cli.js'), 'install', '--ignore-scripts', '--no-audit', '--no-fund'],cwd=fixture,check=True)
    subprocess.run([str(node),str(r/'setup.mjs'),'--angular',version,'--runtimes',runtimes],cwd=r,check=True)
    env.update(ANGULAR_LITE_TEST_NODE=str(node),ANGULAR_LITE_TEST_RUNTIMES=runtimes)
env.update(ANGULAR_LITE_TEST_FIXTURE=str(fixture), ANGULAR_LITE_TEST_ROOTS=json.dumps([str(fixture if version else r/'demo')]))
result = subprocess.run([str(node),str(r/'tests/lsp.mjs')],cwd=r,env=env)
assert (fixture/'tsconfig.json').read_text() == config_text, 'Language tooling must not modify build options'
sys.exit(result.returncode)
