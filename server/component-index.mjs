import {createRequire} from 'node:module';
import {readFile, readdir, stat} from 'node:fs/promises';
import {join, relative, isAbsolute, sep} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';

const skipped=new Set(['node_modules','.git','.idea','.angular','dist','build','coverage','target']);
export function tagAt(text,position) {
  const lines=text.split('\n');
  if(position.line>=lines.length) return null;
  const offset=lines.slice(0,position.line).reduce((n,s)=>n+s.length+1,0)+position.character;
  const char=c=>c!==undefined && /[\w:-]/u.test(c);
  let start=offset,end=offset;
  if(!char(text[start]) && start>0 && char(text[start-1])) start--;
  while(start>0 && char(text[start-1])) start--;
  while(end<text.length && char(text[end])) end++;
  const prefix=text.slice(Math.max(0,start-2),start);
  if(!prefix.endsWith('<') && prefix!=='</') return null;
  const name=text.slice(start,end);
  // Keep native HTML navigation alone; fallback is only for custom element names.
  return /^[a-z][\w]*-[\w-]+$/i.test(name)?name:null;
}
export class ComponentIndex {
  constructor(root,roots,documents) {
    this.root=root; this.roots=roots; this.documents=documents;
    this.ts=createRequire(join(root,'package.json'))('typescript');
    this.cache=new Map(); this.files=[]; this.scanned=0;
  }
  owns(file) {
    const candidates=this.roots.filter(root=>{const r=relative(root,file); return r==='' || (r!=='..'&&!r.startsWith('..'+sep)&&!isAbsolute(r));});
    return candidates.sort((a,b)=>b.length-a.length)[0]===this.root;
  }
  async list() {
    if(Date.now()-this.scanned<5000) return this.files;
    const files=[],queue=[{path:this.root,depth:0}]; let visited=0;
    while(queue.length && visited++<10000) {
      const {path,depth}=queue.shift();
      let entries; try {entries=await readdir(path,{withFileTypes:true});} catch {continue;}
      for(const entry of entries) {
        const file=join(path,entry.name);
        if(!this.owns(file)) continue;
        if(entry.isDirectory() && !skipped.has(entry.name) && depth<12) queue.push({path:file,depth:depth+1});
        else if(entry.isFile() && entry.name.endsWith('.ts') && !entry.name.endsWith('.d.ts')) files.push(file);
      }
    }
    this.files=files; this.scanned=Date.now(); return files;
  }
  parse(text,file) {
    const ts=this.ts,source=ts.createSourceFile(file,text,ts.ScriptTarget.Latest,true);
    const names=new Set(),namespaces=new Set();
    for(const node of source.statements) if(ts.isImportDeclaration(node) && node.moduleSpecifier.text==='@angular/core') {
      const bindings=node.importClause?.namedBindings;
      if(bindings && ts.isNamedImports(bindings)) for(const item of bindings.elements)
        if((item.propertyName?.text??item.name.text)==='Component') names.add(item.name.text);
      if(bindings && ts.isNamespaceImport(bindings)) namespaces.add(bindings.name.text);
    }
    const result=[];
    for(const node of source.statements) {
      if(!ts.isClassDeclaration(node)) continue;
      for(const decorator of ts.getDecorators(node)??[]) {
        const call=decorator.expression;
        if(!ts.isCallExpression(call)) continue;
        const fn=call.expression;
        if(!(ts.isIdentifier(fn)&&names.has(fn.text)) && !(ts.isPropertyAccessExpression(fn)&&namespaces.has(fn.expression.getText(source))&&fn.name.text==='Component')) continue;
        const object=call.arguments[0]; if(!object || !ts.isObjectLiteralExpression(object)) continue;
        for(const prop of object.properties) {
          if(!ts.isPropertyAssignment(prop) || (prop.name.text??prop.name.getText(source))!=='selector') continue;
          const value=prop.initializer;
          if(!ts.isStringLiteral(value)&&!ts.isNoSubstitutionTemplateLiteral(value)) continue;
          const target=node.name??value;
          const start=source.getLineAndCharacterOfPosition(target.getStart(source));
          const end=source.getLineAndCharacterOfPosition(target.getEnd());
          for(const selector of value.text.split(',').map(s=>s.trim()))
            if(/^[a-z][\w]*-[\w-]+$/i.test(selector)) result.push({selector,location:{uri:pathToFileURL(file).href,range:{start,end}}});
        }
      }
    }
    return result;
  }
  async definitions(params) {
    const uri=params?.textDocument?.uri;
    if(!uri?.endsWith('.html')) return [];
    let text=this.documents.get(uri)?.text;
    if(text===undefined) {try {text=await readFile(fileURLToPath(uri),'utf8');} catch {return [];}}
    const name=tagAt(text,params.position); if(!name) return [];
    const results=[];
    for(const file of await this.list()) {
      try {
        const open=this.documents.get(pathToFileURL(file).href);
        const signature=open?`open:${open.version}`:`disk:${(await stat(file)).mtimeMs}`;
        let cached=this.cache.get(file);
        if(!cached || cached.signature!==signature) {
          cached={signature,entries:this.parse(open?.text??await readFile(file,'utf8'),file)};
          this.cache.set(file,cached);
        }
        results.push(...cached.entries.filter(e=>e.selector===name).map(e=>e.location));
      } catch { /* Unreadable or deleted source; do not break normal LSP navigation. */ }
    }
    return results;
  }
}
