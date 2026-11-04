import {createRequire} from 'node:module';
import {readFileSync, mkdirSync, writeFileSync} from 'node:fs';
import {join, resolve} from 'node:path';
import {homedir} from 'node:os';
import {spawnSync} from 'node:child_process';

const args = process.argv.slice(2);
const option = name => { const i = args.indexOf(name); return i < 0 ? undefined : args[i + 1]; };
if (!option('--project') && !option('--angular')) {
  console.log('Usage: node setup.mjs --project "C:/path/to/angular-project"\nOr: node setup.mjs --angular latest\nOptional: --runtimes "directory"');
  process.exit(0);
}
try {
  let version = option('--angular');
  if (option('--project')) {
    const req = createRequire(join(resolve(option('--project')), 'package.json'));
    version = JSON.parse(readFileSync(req.resolve('@angular/core/package.json'), 'utf8')).version;
  }
  if (!/^(latest|\d+\.\d+\.\d+)$/.test(version)) throw new Error('Use latest or an exact stable Angular version, e.g. 22.2.4.');
  const response = await fetch(`https://registry.npmjs.org/@angular/language-service/${version}`);
  if (!response.ok) throw new Error(`Angular language service ${version}: HTTP ${response.status}`);
  const metadata = await response.json();
  const exact = metadata.version;
  let serverResponse = await fetch(`https://registry.npmjs.org/@angular/language-server/${exact}`);
  let serverMetadata;
  if (serverResponse.ok) serverMetadata = await serverResponse.json();
  else if (serverResponse.status === 404) {
    // Older Angular VS Code servers were published independently of framework
    // patches (e.g. server 16.2.0, framework 16.2.12). Keep the language service
    // exact and choose a stable server from the same major/minor release only.
    const catalogResponse = await fetch('https://registry.npmjs.org/@angular/language-server');
    if (!catalogResponse.ok) throw new Error(`Angular server catalog: HTTP ${catalogResponse.status}`);
    const catalog = await catalogResponse.json();
    const prefix = exact.split('.').slice(0,2).join('.') + '.';
    const versions = Object.keys(catalog.versions).filter(v => /^\d+\.\d+\.\d+$/.test(v) && v.startsWith(prefix))
      .sort((a,b) => Number(b.split('.')[2]) - Number(a.split('.')[2]));
    if (!versions.length) throw new Error(`No compatible Angular language server published for ${exact}`);
    serverMetadata = catalog.versions[versions[0]];
    console.log(`Angular ${exact}: using server ${serverMetadata.version} with language service ${exact}`);
  } else throw new Error(`Angular language server ${exact}: HTTP ${serverResponse.status}`);
  const folder = join(resolve(option('--runtimes') ?? join(homedir(), '.angular-lite-idea/runtimes')), exact);
  mkdirSync(folder, {recursive: true});
  const pkg = {name: 'angular-lite-local-runtime', private: true, version: '1.0.0', dependencies: {
    '@angular/language-server': serverMetadata.version, '@angular/language-service': exact,
    'typescript-language-server': '6.0.3', typescript: serverMetadata.dependencies.typescript
  }};
  writeFileSync(join(folder, 'package.json'), JSON.stringify(pkg, null, 2));
  const installArgs = ['install', '--ignore-scripts', '--no-audit', '--no-fund'];
  // macOS/Linux: let PATH resolve npm, including Homebrew/nvm symlinks and shims.
  // Windows: execute the JS entrypoint because npm.cmd needs a command shell.
  // Neither branch interpolates project paths into a shell command.
  let executable = 'npm';
  let commandArgs = installArgs;
  if (process.platform === 'win32' || process.env.ANGULAR_LITE_NPM_CLI) {
    const candidates = [process.env.ANGULAR_LITE_NPM_CLI, join(process.execPath, '../node_modules/npm/bin/npm-cli.js'), process.env.npm_execpath].filter(Boolean);
    let npm;
    for (const p of candidates) { try { readFileSync(p); npm = p; break; } catch {} }
    if (!npm) throw new Error('npm-cli.js not found. Check your Node.js/npm installation or run this setup through npm.');
    executable = process.execPath;
    commandArgs = [npm, ...installArgs];
  }
  const result = spawnSync(executable, commandArgs, {cwd: folder, stdio: 'inherit', windowsHide: true});
  if (result.error) throw new Error(`Cannot start npm: ${result.error.message}. Check that npm --version works in this terminal.`);
  if (result.status !== 0) throw new Error('npm installation failed. Check Node.js requirements in the output above.');
  console.log(`Installed Angular ${exact} tools in ${folder}. Restart Angular Lite servers in IntelliJ.`);
} catch (error) { console.error(error.message); process.exitCode = 1; }
