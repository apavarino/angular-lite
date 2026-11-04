"""Integration against Angular 21 + existing Angular 22, in the same LSP session."""
import pathlib, subprocess, os, json, shutil
r = pathlib.Path(__file__).resolve().parents[1]
node = pathlib.Path((r/'build/automatic-test-node.txt').read_text())
runtimes = (r/'build/automatic-test-runtime.txt').read_text()
front = r/'build/multi-root frontend 21'
front.mkdir(exist_ok=True)
for name in ['app.ts','app.html','child.ts','tsconfig.json']:
    shutil.copy2(r/'demo/fixture'/name, front/name)
(front/'package.json').write_text(json.dumps({'private':True,'dependencies':{'@angular/core':'21.0.0','@angular/common':'21.0.0','@angular/compiler':'21.0.0','typescript':'5.9.3','rxjs':'7.8.2'}}))
npm = node.parent/'node_modules/npm/bin/npm-cli.js'
subprocess.run([str(node),str(npm),'install','--ignore-scripts','--no-audit','--no-fund'],cwd=front,check=True)
subprocess.run([str(node),str(r/'setup.mjs'),'--angular','21.0.0','--runtimes',runtimes],cwd=r,check=True)
env=os.environ.copy()
env.update(ANGULAR_LITE_TEST_NODE=str(node),ANGULAR_LITE_TEST_RUNTIMES=runtimes,ANGULAR_LITE_TEST_WORKSPACE=str(r),ANGULAR_LITE_TEST_ROOTS=json.dumps([str(r/'demo'),str(front)]))
for fixture in [r/'demo/fixture',front]:
    env['ANGULAR_LITE_TEST_FIXTURE']=str(fixture)
    subprocess.run([str(node),str(r/'tests/lsp.mjs')],cwd=r,env=env,check=True)
print('PASS two active Angular versions: 22 and 21, separate roots with spaces')
