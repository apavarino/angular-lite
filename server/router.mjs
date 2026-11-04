// One independent language server per dependency root. No project files are modified.
import {spawn} from 'node:child_process';
import {resolve, relative, isAbsolute, sep} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {command} from './launch.mjs';
import {ComponentIndex} from './component-index.mjs';

export function owner(uri, roots) {
  if (!uri?.startsWith('file:')) return -1;
  let file; try { file=fileURLToPath(uri); } catch { return -1; }
  return roots.map((root,index)=>({root,index})).sort((a,b)=>b.root.length-a.root.length)
    .find(({root})=>{const path=relative(root,file); return path==='' || (path!=='..' && !path.startsWith('..'+sep) && !isAbsolute(path));})?.index ?? -1;
}
function reader(stream, consume) {
  let buffer=Buffer.alloc(0);
  stream.on('data',chunk=>{
    buffer=Buffer.concat([buffer,chunk]);
    while(true) {
      const end=buffer.indexOf('\r\n\r\n'); if(end<0) return;
      const match=/Content-Length:\s*(\d+)/i.exec(buffer.subarray(0,end).toString());
      if(!match) throw new Error('Invalid LSP frame');
      const length=Number(match[1]); if(buffer.length<end+4+length) return;
      const message=JSON.parse(buffer.subarray(end+4,end+4+length).toString());
      buffer=buffer.subarray(end+4+length); consume(message);
    }
  });
}
function write(stream,message) {
  const data=Buffer.from(JSON.stringify({jsonrpc:'2.0',...message}));
  if(!stream.destroyed) stream.write(Buffer.concat([Buffer.from(`Content-Length: ${data.length}\r\n\r\n`),data]));
}
export function run(kind,runtimes,roots) {
  roots=roots.map(resolvePath=>resolve(resolvePath));
  let next=0, ready, stopping=false, documentGeneration=0;
  const clientRequests=new Map(), active=new Map();
  const documents=new Map();
  const indexes=kind==='angular'?roots.map(root=>new ComponentIndex(root,roots,documents)):[];
  const send=message=>write(process.stdout,message);
  const servers=roots.map((root,index)=>{
    const child=spawn(process.execPath,command(kind,root,runtimes),{cwd:root,stdio:['pipe','pipe','inherit'],windowsHide:true});
    const pending=new Map();
    const server={child,pending,index,root};
    reader(child.stdout,message=>{
      if(message.method) {
        if(message.method==='workspace/workspaceFolders' && message.id!==undefined) {
          write(child.stdin,{id:message.id,result:[{uri:pathToFileURL(root).href,name:root}]}); return;
        }
        if(message.id!==undefined) {
          const id=`angular-lite-child-${index}-${++next}`;
          clientRequests.set(id,{server,id:message.id}); send({...message,id});
        } else if(message.method==='textDocument/publishDiagnostics') {
          if(owner(message.params?.uri,roots)===index) send(message);
        } else send(message);
      } else {
        const task=pending.get(message.id);
        if(task) {pending.delete(message.id); message.error?task.reject(message.error):task.resolve(message.result);}
      }
    });
    const failed=error=>{for(const task of pending.values()) task.reject({code:-32603,message:String(error)}); pending.clear();};
    child.on('error',failed);
    child.on('exit',code=>{failed(`Language server ${root} exited (${code})`); if(!stopping) send({method:'window/showMessage',params:{type:1,message:`Angular Lite : moteur arrêté pour ${root}. Rouvrez le projet pour le relancer.`}});});
    return server;
  });
  function request(server,method,params,clientId) {
    return new Promise((resolve,reject)=>{
      if(server.child.exitCode!==null || server.child.stdin.destroyed) return reject({code:-32603,message:'Language server stopped'});
      const id=++next; server.pending.set(id,{resolve,reject});
      if(clientId!==undefined) {const list=active.get(clientId)??[]; list.push({server,id}); active.set(clientId,list);}
      write(server.child.stdin,{id,method,params});
    });
  }
  function tag(value,index) {
    if(Array.isArray(value)) return value.map(v=>tag(v,index));
    if(!value || typeof value!=='object') return value;
    const output={...value};
    if('data' in output) output.data={angularLiteRoot:index,original:output.data};
    if(output.items) output.items=output.items.map(v=>tag(v,index));
    if(typeof output.command==='string' && !output.command.startsWith('angular-lite/')) output.command=`angular-lite/${index}/${output.command}`;
    else if(output.command && typeof output.command==='object') output.command=tag(output.command,index);
    return output;
  }
  function stop() {stopping=true; for(const server of servers) server.child.kill(); process.stdin.destroy();}
  process.on('SIGTERM',stop); process.on('SIGINT',stop);
  process.stdin.on('end',stop);
  reader(process.stdin,message=>{handle(message).catch(error=>{
    if(message.id!==undefined) send({id:message.id,error:{code:error.code??-32603,message:error.message??String(error)}});
  });});
  async function handle(message) {
    const {id,method}=message;
    if(!method) {
      const target=clientRequests.get(id);
      if(target) {clientRequests.delete(id); write(target.server.child.stdin,{...message,id:target.id});}
      return;
    }
    if(method==='initialize') {
      ready=Promise.all(servers.map(server=>request(server,method,{...message.params,
        rootUri:pathToFileURL(server.root).href,rootPath:server.root,
        workspaceFolders:[{uri:pathToFileURL(server.root).href,name:server.root}]})));
      const results=await ready;
      // The same server kind is used for every root; expose only shared capabilities.
      const capabilities={...results[0].capabilities};
      for(const key of Object.keys(capabilities)) if(results.some(r=>!r.capabilities[key])) delete capabilities[key];
      delete capabilities.workspace; // Do not let repository-level folders replace child roots.
      if(capabilities.executeCommandProvider) capabilities.executeCommandProvider={commands:results.flatMap((r,index)=>(r.capabilities.executeCommandProvider?.commands??[]).map(command=>`angular-lite/${index}/${command}`))};
      send({id,result:{...results[0],capabilities}}); return;
    }
    if(method==='$/cancelRequest') {
      for(const target of active.get(message.params.id)??[]) write(target.server.child.stdin,{method,params:{id:target.id}});
      return;
    }
    if(method==='exit') {stopping=true; for(const s of servers) write(s.child.stdin,message); setTimeout(stop,1000).unref(); return;}
    await ready;
    let params=message.params;
    if(method==='textDocument/didOpen') documents.set(params.textDocument.uri,{text:params.textDocument.text,version:++documentGeneration});
    if(method==='textDocument/didClose') documents.delete(params.textDocument.uri);
    if(method==='textDocument/didChange') {
      const old=documents.get(params.textDocument.uri);
      if(old) {
        let text=old.text;
        const offset=p=>{const lines=text.split('\n'); return lines.slice(0,p.line).reduce((n,s)=>n+s.length+1,0)+p.character;};
        for(const change of params.contentChanges) text=change.range?text.slice(0,offset(change.range.start))+change.text+text.slice(offset(change.range.end)):change.text;
        documents.set(params.textDocument.uri,{text,version:++documentGeneration});
      }
    }
    const data=params?.data;
    let index=Number.isInteger(data?.angularLiteRoot)?data.angularLiteRoot:owner(params?.textDocument?.uri??params?.uri,roots);
    if(Number.isInteger(data?.angularLiteRoot)) params={...params,data:data.original};
    if(method==='workspace/executeCommand') {
      const routed=/^angular-lite\/(\d+)\/(.*)$/.exec(params.command);
      if(routed) { index=Number(routed[1]); params={...params,command:routed[2]}; }
    }
    if(index>=servers.length) throw {code:-32602,message:'Unknown frontend'};
    if(method==='workspace/didChangeWorkspaceFolders') return;
    if(method==='workspace/didChangeWatchedFiles') {
      for(const s of servers) {
        const changes=params.changes.filter(change=>owner(change.uri,roots)===s.index);
        if(changes.length) write(s.child.stdin,{method,params:{changes}});
      }
      return;
    }
    if(id===undefined) {
      for(const s of index>=0?[servers[index]]:servers) write(s.child.stdin,{...message,params});
      return;
    }
    try {
      if(method==='shutdown') stopping=true;
      if(index>=0) {
        let result=await request(servers[index],method,params,id);
        if(kind==='angular' && method==='textDocument/definition' && (!result || (Array.isArray(result)&&!result.length)))
          result=await indexes[index].definitions(params);
        const needsRoute=['textDocument/completion','completionItem/resolve','textDocument/codeAction','codeAction/resolve','textDocument/documentLink','textDocument/inlayHint'].includes(method);
        send({id,result:needsRoute?tag(result,index):result});
      }
      else if(method==='workspace/symbol' || method==='shutdown') {
        const results=await Promise.all(servers.map(async s=>tag(await request(s,method,params,id),s.index)));
        send({id,result:method==='shutdown'?null:results.flat().filter(Boolean)});
      } else if(params?.textDocument) send({id,result:null});
      else if(servers.length===1) send({id,result:await request(servers[0],method,params,id)});
      else send({id,error:{code:-32602,message:'Angular Lite: impossible de choisir un frontend pour cette requête.'}});
    } finally {active.delete(id);}
  }
}
if(process.argv[1] && resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  try {const [kind,runtimes,...roots]=process.argv.slice(2); if(!roots.length) throw new Error('No Angular roots'); run(kind,runtimes,roots);}
  catch(error) {console.error(error); process.exitCode=1;}
}
