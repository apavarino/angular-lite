"""Real pre-control-flow Angular 16 engine, not just legacy syntax on Angular 22."""
import pathlib, subprocess, os, json, shutil
r=pathlib.Path(__file__).resolve().parents[1]
front=r/'build/legacy frontend 16'; front.mkdir(exist_ok=True)
for name in ['child.ts','tsconfig.json']: shutil.copy2(r/'demo/fixture'/name,front/name)
(front/'child.ts').write_text("import {Component, Input} from '@angular/core';\n@Component({selector: 'demo-child', standalone: true, template: '<strong>{{ label }}</strong>'})\nexport class ChildComponent {\n @Input() from = '';\n @Input() label = 'Bonjour';\n}\n",encoding='utf-8')
component=(r/'demo/fixture/app.ts').read_text(encoding='utf-8').split("@Component({selector: 'demo-inline'")[0]
(front/'app.ts').write_text(component,encoding='utf-8')
template=(r/'demo/fixture/app.html').read_text(encoding='utf-8')
(front/'app.html').write_text('<h1>{{ title() }}</h1>\n<demo-child [label]="greet()" />\n'+template[template.index('<ng-container *ngIf'):],encoding='utf-8')
(front/'package.json').write_text(json.dumps({'private':True,'dependencies':{'@angular/core':'16.2.12','@angular/common':'16.2.12','@angular/compiler':'16.2.12','typescript':'5.1.6','rxjs':'7.8.2','zone.js':'0.13.3'}}))
node=pathlib.Path((r/'build/automatic-test-node.txt').read_text())
runtimes=(r/'build/automatic-test-runtime.txt').read_text()
npm=node.parent/'node_modules/npm/bin/npm-cli.js'
subprocess.run([str(node),str(npm),'install','--ignore-scripts','--no-audit','--no-fund'],cwd=front,check=True)
subprocess.run([str(node),str(r/'setup.mjs'),'--angular','16.2.12','--runtimes',runtimes],cwd=r,check=True)
env=os.environ.copy()
env.update(ANGULAR_LITE_LEGACY_TEST='1',ANGULAR_LITE_TEST_NODE=str(node),ANGULAR_LITE_TEST_RUNTIMES=runtimes,ANGULAR_LITE_TEST_FIXTURE=str(front),ANGULAR_LITE_TEST_ROOTS=json.dumps([str(front)]))
subprocess.run([str(node),str(r/'tests/lsp.mjs')],cwd=r,env=env,check=True)
