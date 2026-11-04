import {createRequire} from 'node:module';
import {resolve, dirname, join} from 'node:path';
import {readFileSync, existsSync} from 'node:fs';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';

export function command(kind, project, runtimes) {
  const requireProject = createRequire(join(resolve(project), 'package.json'));
  const core = JSON.parse(readFileSync(requireProject.resolve('@angular/core/package.json'), 'utf8'));
  const major = core.version.split('.')[0];
  const exactRuntime = join(resolve(runtimes), core.version);
  const runtime = existsSync(join(exactRuntime, 'node_modules/@angular/language-server/index.js')) ? exactRuntime : join(resolve(runtimes), major);
  const requireRuntime = createRequire(join(runtime, 'package.json'));
  if (kind === 'angular') {
    const server = requireRuntime.resolve('@angular/language-server');
    const tsDir = dirname(requireProject.resolve('typescript/package.json'));
    // Basic template checking omits embedded views entirely, so definition,
    // hover and resolved colors disappear inside @if/@for/*ngIf. Enable full
    // analysis for this editor process without changing the project's build.
    return [server, '--stdio', '--forceStrictTemplates', '--tsProbeLocations', dirname(tsDir),
      '--ngProbeLocations', join(runtime, 'node_modules')];
  }
  if (kind !== 'typescript') throw new Error(`Unknown server: ${kind}`);
  const pkg = requireRuntime.resolve('typescript-language-server/package.json');
  const bin = JSON.parse(readFileSync(pkg, 'utf8')).bin['typescript-language-server'];
  // typescript-language-server resolves workspace TypeScript from rootUri itself.
  return [resolve(dirname(pkg), bin), '--stdio'];
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [kind, project, runtimes] = process.argv.slice(2);
    const child = spawn(process.execPath, command(kind, project, runtimes), {cwd: project, stdio: 'inherit', windowsHide: true});
    child.on('error', error => { console.error(error.message); process.exitCode = 1; });
    child.on('exit', code => { process.exitCode = code ?? 1; });
    for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => child.kill(signal));
  } catch (error) {
    console.error('Angular Lite: language-server startup failed. Check the Angular Lite notification and the local installation.log. Project dependencies must already be installed.\n' + error.message);
    process.exitCode = 1;
  }
}
