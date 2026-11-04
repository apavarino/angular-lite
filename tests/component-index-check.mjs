import assert from 'node:assert/strict';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {readFileSync} from 'node:fs';
import {ComponentIndex,tagAt} from '../server/component-index.mjs';

const root=resolve('demo'),uri=pathToFileURL(resolve(root,'fixture/app.html')).href;
const html='@if (condition) {\n <demo-child [label]="value"></demo-child>\n}';
const docs=new Map([[uri,{text:html,version:1}]]);
const index=new ComponentIndex(root,[root],docs);
assert.equal(tagAt(html,{line:1,character:5}),'demo-child');
assert.equal(tagAt(html,{line:1,character:html.split('\n')[1].indexOf('</demo-child')+4}),'demo-child');
assert.equal(tagAt(html,{line:1,character:17}),null);
const targets=await index.definitions({textDocument:{uri},position:{line:1,character:5}});
assert(targets.some(t=>t.uri.endsWith('/fixture/child.ts')));
const source=`import {Component as C} from '@angular/core';
// @C({selector: 'fake-comment'}) class Fake {}
const ignored="@C({selector: 'fake-string'})";
@C({selector: 'm-exportation, m-export'}) export class ExportComponent {}`;
assert.deepEqual(index.parse(source,resolve(root,'example.ts')).map(e=>e.selector),['m-exportation','m-export']);
assert.equal(index.parse("function Component() {}\n@Component({selector:'fake-component'}) class Nope {}",'fake.ts').length,0);
const childUri=pathToFileURL(resolve(root,'fixture/child.ts')).href;
docs.set(childUri,{text:readFileSync(resolve(root,'fixture/child.ts'),'utf8').replace('demo-child','renamed-child'),version:2});
assert(!(await index.definitions({textDocument:{uri},position:{line:1,character:5}})).some(t=>t.uri===childUri));
const nested=resolve(root,'fixture');
assert.equal(new ComponentIndex(root,[root,nested],docs).owns(resolve(nested,'child.ts')),false);
console.log('PASS fallback: tag in @if resolves to TS; aliases; comments ignored; unsaved rename; frontend isolation');
