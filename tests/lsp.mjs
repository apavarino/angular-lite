// Integration test against real, local Angular and TypeScript language servers.
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {resolve, join} from 'node:path';
import {pathToFileURL, fileURLToPath} from 'node:url';
import {homedir} from 'node:os';
const root = fileURLToPath(new URL('../', import.meta.url));
// Keep the test fixture separate from the demo the user can edit in the IDE.
const demo = process.env.ANGULAR_LITE_TEST_FIXTURE ?? join(root, 'demo/fixture');
const workspace = process.env.ANGULAR_LITE_TEST_WORKSPACE ?? demo;
const legacy = process.env.ANGULAR_LITE_LEGACY_TEST === '1';
const uri = file => pathToFileURL(join(demo, file)).href;
const point = (text, needle, delta=0) => {
  const start=text.indexOf(needle);
  assert(start>=0, `Missing test marker: ${needle}`);
  const offset=start+delta;
  const lines=text.slice(0,offset).split('\n');
  return {line:lines.length-1,character:lines.at(-1).length};
};

async function run(kind, cases) {
  const runtimes=process.env.ANGULAR_LITE_TEST_RUNTIMES ?? join(homedir(),'.angular-lite-idea/runtimes');
  const args=process.env.ANGULAR_LITE_TEST_ROOTS
    ? [join(root,'server/router.mjs'),kind,runtimes,...JSON.parse(process.env.ANGULAR_LITE_TEST_ROOTS)]
    : [join(root,'server/launch.mjs'),kind,demo,runtimes];
  const child = spawn(process.env.ANGULAR_LITE_TEST_NODE ?? process.execPath,args,{windowsHide:true});
  let buffer=Buffer.alloc(0), next=1, stderr=''; const pending=new Map(), diagnostics=new Map();
  child.stderr.on('data',chunk=>stderr+=chunk);
  function send(message) {
    const body=JSON.stringify({jsonrpc:'2.0',...message});
    child.stdin.write(`Content-Length: ${Buffer.byteLength(body)}\r\n\r\n${body}`);
  }
  child.stdout.on('data', chunk=>{
    buffer=Buffer.concat([buffer,chunk]);
    while(true) {
      const end=buffer.indexOf('\r\n\r\n'); if(end<0) return;
      const length=Number(/Content-Length: (\d+)/i.exec(buffer.subarray(0,end).toString())[1]);
      if(buffer.length<end+4+length) return;
      const message=JSON.parse(buffer.subarray(end+4,end+4+length)); buffer=buffer.subarray(end+4+length);
      if(message.method==='textDocument/publishDiagnostics') diagnostics.set(fileURLToPath(message.params.uri).toLowerCase(),message.params.diagnostics);
      if(message.method && message.id!==undefined) send({id:message.id,result:message.method==='workspace/configuration'?[{}]:null});
      else if(pending.has(message.id)) { pending.get(message.id)(message); pending.delete(message.id); }
    }
  });
  const request=(method,params)=>new Promise((resolve,reject)=>{
    const id=next++; const timer=setTimeout(()=>{pending.delete(id);reject(new Error(`${kind}: timeout ${method}\n${stderr}`));},30000);
    pending.set(id,message=>{clearTimeout(timer);message.error?reject(new Error(JSON.stringify(message.error))):resolve(message.result);});
    send({id,method,params});
  });
  try {
    const initialized=await request('initialize',{processId:process.pid,rootUri:pathToFileURL(workspace).href,capabilities:{textDocument:{definition:{linkSupport:true}},workspace:{configuration:true}},workspaceFolders:[{uri:pathToFileURL(workspace).href,name:'demo'}]});
    send({method:'initialized',params:{}});
    for(const file of ['app.ts','child.ts','app.html']) send({method:'textDocument/didOpen',params:{textDocument:{uri:uri(file),languageId:file.endsWith('.html')?'html':'typescript',version:1,text:readFileSync(join(demo,file),'utf8')}}});
    for(const [file,needle,delta,target,targetNeedle] of cases) {
      let result;
      // Angular creates the configured project asynchronously after didOpen.
      for(let retry=0;retry<20;retry++) {
        result=await request('textDocument/definition',{textDocument:{uri:uri(file)},position:point(readFileSync(join(demo,file),'utf8'),needle,delta)});
        if(result && (!Array.isArray(result)||result.length)) break;
        await new Promise(r=>setTimeout(r,500));
      }
      if (!result || !result.length) console.log('Unresolved definition', needle,
        await request('textDocument/hover',{textDocument:{uri:uri(file)},position:point(readFileSync(join(demo,file),'utf8'),needle,delta)}),
        diagnostics.get(fileURLToPath(uri(file)).toLowerCase()));
      const locations=Array.isArray(result)?result:[result];
      const wanted=point(readFileSync(join(demo,target),'utf8'),targetNeedle);
      assert(locations.some(loc=>loc && fileURLToPath(loc.uri??loc.targetUri).toLowerCase()===fileURLToPath(uri(target)).toLowerCase() && (loc.range??loc.targetSelectionRange).start.line===wanted.line),`${kind}: ${file} ${needle} -> ${JSON.stringify(result)} expected ${target}:${wanted.line+1}\n${stderr}`);
      console.log(`PASS ${kind}: ${file} ${needle} -> ${target}:${wanted.line+1}`);
    }
    const completionCases=kind==='angular'?[
      ['app.html','title',3,'title'],
      ['app.html','greet',3,'greet'],
      ['app.html','[label]',3,'label'],
      ['app.ts','{{ message',6,'message'],
      ['app.ts','(click)="toggle',12,'toggle']
    ]:[['app.ts','this.title',8,'title']];
    for(const [file,needle,delta,label] of completionCases.filter(c=>!legacy || kind==='typescript' || c[0]==='app.html')) {
      const result=await request('textDocument/completion',{textDocument:{uri:uri(file)},position:point(readFileSync(join(demo,file),'utf8'),needle,delta),context:{triggerKind:1}});
      const items=Array.isArray(result)?result:result?.items??[];
      assert(items.some(item=>item.label===label),`${kind}: missing completion ${label} in ${file}: ${items.map(x=>x.label).slice(0,30)}`);
      if(initialized.capabilities.completionProvider?.resolveProvider) {
        const item=items.find(item=>item.label===label);
        const resolved=await request('completionItem/resolve',item);
        assert.equal(resolved.label,label,'Completion resolve must return to the owning frontend');
      }
      console.log(`PASS ${kind}: completion ${file} -> ${label}`);
    }
    if(kind==='typescript') {
      const semantic=await request('textDocument/semanticTokens/full',{textDocument:{uri:uri('app.ts')}});
      const text=readFileSync(join(demo,'app.ts'),'utf8');
      const decorator=point(text,'@Component',1);
      let line=0,character=0,found=false;
      for(let i=0;i<semantic.data.length;i+=5) {
        const [dl,dc,length,type]=semantic.data.slice(i,i+5);
        line+=dl; character=dl?dc:character+dc;
        if(line===decorator.line && character===decorator.character) {
          found=true;
          console.log(`PASS reproduced color conflict: TypeScript classifies @Component name as ${initialized.capabilities.semanticTokensProvider.legend.tokenTypes[type]}`);
        }
      }
      assert(found,'TypeScript semantic tokens should reproduce the decorator color override');
    }
    if(kind==='angular') {
      const component=readFileSync(join(demo,'app.ts'),'utf8');
      const changedComponent=component.replace('readonly title', 'readonly country = { eu: true };\n  readonly title');
      send({method:'textDocument/didChange',params:{textDocument:{uri:uri('app.ts'),version:2},contentChanges:[{text:changedComponent}]}});
      const mappingTemplate='{{ country.eu }} {{ country.missing }} {{ missingCountry.eu }}';
      send({method:'textDocument/didChange',params:{textDocument:{uri:uri('app.html'),version:2},contentChanges:[{text:mappingTemplate}]}});
      const mapped=async (needle,delta)=>{
        const result=await request('textDocument/definition',{textDocument:{uri:uri('app.html')},position:point(mappingTemplate,needle,delta)});
        return Array.isArray(result)?result.length>0:!!result;
      };
      assert(await mapped('country.eu',1),'country must resolve');
      assert(await mapped('country.eu',9),'eu must resolve');
      assert(!(await mapped('country.missing',10)),'unknown property must not resolve');
      assert(!(await mapped('missingCountry',2)),'unknown receiver must not resolve');
      send({method:'textDocument/didChange',params:{textDocument:{uri:uri('app.ts'),version:3},contentChanges:[{text:changedComponent.replace('eu: true','europe: true')}]}});
      assert(!(await mapped('country.eu',9)),'renamed property must lose its mapping');
      console.log('PASS semantic colors: known receiver/property resolve, unknown names do not; source rename invalidates mapping');
      send({method:'textDocument/didChange',params:{textDocument:{uri:uri('app.ts'),version:4},contentChanges:[{text:component}]}});
      const file='app.html';
      const original=readFileSync(join(demo,file),'utf8');
      send({method:'textDocument/didChange',params:{textDocument:{uri:uri(file),version:3},contentChanges:[{text:original.replace('title()', 'missingForTest()')}]}});
      let found=false;
      for(let retry=0;retry<40;retry++) {
        found=(diagnostics.get(fileURLToPath(uri(file)).toLowerCase())??[]).some(d=>d.message.includes('missingForTest') && d.severity===1);
        if(found) break;
        await new Promise(r=>setTimeout(r,250));
      }
      assert(found,'Angular must flag an unknown property in the unsaved HTML template');
      console.log('PASS angular: unknown HTML template property produces an error');
      diagnostics.delete(fileURLToPath(uri(file)).toLowerCase());
      send({method:'textDocument/didChange',params:{textDocument:{uri:uri(file),version:4},contentChanges:[{text:original+'\n<unknown-component-for-test></unknown-component-for-test>'}]}});
      found=false;
      for(let retry=0;retry<40;retry++) {
        found=(diagnostics.get(fileURLToPath(uri(file)).toLowerCase())??[]).some(d=>d.message.includes('unknown-component-for-test') && d.severity===1);
        if(found) break;
        await new Promise(r=>setTimeout(r,250));
      }
      assert(found,'Angular must still flag a truly unknown component');
      console.log('PASS angular: truly unknown component produces an error');
      send({method:'textDocument/didChange',params:{textDocument:{uri:uri(file),version:5},contentChanges:[{text:original}]}});
    }
    await request('shutdown',null); send({method:'exit'});
  } finally { child.stdin.end(); child.kill(); }
}

await run('angular',[
  ['app.html','[label] = "row"',12,'app.html','@for (row'],
  ['app.html','[from] = "criteria.from"',12,'app.ts','  criteria ='],
  ['app.html','{{ row }}',4,'app.html','@for (row'],
  ['app.html','*ngIf="items',8,'app.ts','readonly items'],
  ['app.html','of legacyItems',5,'app.html','<ng-container *ngIf'],
  ['app.html','[label]="legacyItem"',11,'app.html','*ngFor="let legacyItem'],
  ['app.html','{{ legacyIndex }}',5,'app.html','*ngFor="let legacyItem'],
  ['app.html','<demo-child [from]="criteria.from"',21,'app.ts','  criteria ='],
  ['app.html','<demo-child [from]="criteria.from"',29,'app.ts',"    from: '2026-01-01'"],
  ['app.html','      [from]="criteria.from"',16,'app.ts','  criteria ='],
  ['app.html','      [from]="criteria.from"',24,'app.ts',"    from: '2026-01-01'"],
  ['app.html','title',1,'app.ts','readonly title'],
  ['app.html','greet',1,'app.ts','greet() {'],
  ['app.html','demo-child',2,'child.ts','export class ChildComponent'],
  ['app.html',"[label]='plainLabel'",11,'app.ts','readonly plainLabel'],
  ['app.html',"[label]='countries[0]'",11,'app.html','@if (items; as countries)'],
  ['app.html','  <demo-child [label]="title()',5,'child.ts','export class ChildComponent'],
  ['app.html','    <demo-child [label]="entry',7,'child.ts','export class ChildComponent'],
  ['app.html','@if (items',7,'app.ts','readonly items'],
  ['app.html','{{ items.length }}',4,'app.ts','readonly items'],
  ['app.html','<section>{{ title()',14,'app.ts','readonly title'],
  ['app.html','entry of items',10,'app.ts','readonly items'],
  ['app.html','[label]="entry',10,'app.html','@for (entry'],
  ['app.html','{{ entry }}',4,'app.html','@for (entry'],
  ['app.html','{{ idx }}',4,'app.html','@for (entry'],
  ['app.html','@case (0) { <p>{{ title()',20,'app.ts','readonly title'],
  ['app.html','@default { <p>{{ greet()',19,'app.ts','greet() {'],
  ['app.html','{{ countries.length }}',5,'app.html','@if (items; as countries)'],
  ['app.html','{{ count }}',4,'app.html','@if (countries.length; as count)'],
  ['app.ts','{{ message',4,'app.ts',"message ="],
  ['app.ts','(click)="toggle',10,'app.ts','toggle() {']
].filter(c=>!legacy || (c[0]==='app.html' && new Set(['*ngIf="items', 'of legacyItems', '[label]="legacyItem"', '{{ legacyIndex }}', 'title', 'greet', 'demo-child']).has(c[1]))));
await run('typescript',[
  ['app.ts','return this.title',14,'app.ts','readonly title'],
  ['app.ts','imports: [ChildComponent',12,'child.ts','export class ChildComponent']
]);
