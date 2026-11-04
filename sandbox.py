"""Prepare an isolated IDEA profile; never modify the real user's IDE configuration."""
import pathlib, shutil
root=pathlib.Path(__file__).resolve().parent
sandbox=root/'sandbox'
for d in ['config','system','log','plugins']:
    (sandbox/d).mkdir(parents=True,exist_ok=True)
shutil.copytree(root/'build/angular-lite',sandbox/'plugins/angular-lite',dirs_exist_ok=True)
shutil.copytree(root/'deps/lsp4ij',sandbox/'plugins/lsp4ij',dirs_exist_ok=True)
(sandbox/'config/disabled_plugins.txt').write_text('JavaScript\nAngularJS\ncom.intellij.modules.ultimate\n')
props='\n'.join(f'idea.{k}.path={(sandbox/v).as_posix()}' for k,v in [('config','config'),('system','system'),('log','log'),('plugins','plugins')])
(sandbox/'idea.properties').write_text(props+'\nidea.is.internal=true\n')
print(sandbox/'idea.properties')
