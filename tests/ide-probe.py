import pathlib, subprocess, os, shutil, zipfile, sys
r=pathlib.Path(__file__).resolve().parents[1]
idea=pathlib.Path(os.environ.get('ANGULAR_LITE_IDEA', str(r/'deps/idea-2026.2.4'))).resolve()
base=r/'build'/('ide-probe'+('-'+sys.argv[1] if len(sys.argv)>1 else ''))
project=pathlib.Path(sys.argv[2]).resolve() if len(sys.argv)>2 else r/'demo/fixture'
classes=base/'classes'
classes.mkdir(parents=True,exist_ok=True)
if (base/'result.txt').exists(): (base/'result.txt').unlink()
cp=os.pathsep.join(os.path.relpath(p,r) for p in [*list((idea/'lib').glob('*.jar')),*list((r/'deps/lsp4ij/lib').glob('*.jar')),r/'build/angular-lite/lib/angular-lite.jar'])
subprocess.run(['javac','--release','21','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(r/'tests/ide/IntegrationProbe.java')],check=True)
for d in ['plugins','config','system','log']: (base/d).mkdir(exist_ok=True)
# The fixture is authored by this repository, and contains no build scripts.
# Trust only this test directory; do not inherit the user's IDE configuration.
options=base/'config/options'; options.mkdir(exist_ok=True)
(options/'trusted-paths.xml').write_text('<application><component name="Trusted.Paths"><option name="TRUSTED_PROJECT_PATHS"><map><entry key="'+str(project)+'" value="true" /></map></option></component></application>')
shutil.copytree(r/'build/angular-lite',base/'plugins/angular-lite',dirs_exist_ok=True)
shutil.copytree(r/'deps/lsp4ij',base/'plugins/lsp4ij',dirs_exist_ok=True)
lib=base/'plugins/probe/lib';lib.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(lib/'probe.jar','w') as z:
    for p in classes.rglob('*.class'):z.write(p,p.relative_to(classes).as_posix())
    z.writestr('META-INF/plugin.xml','<idea-plugin><id>dev.angularlite.qa</id><name>Angular Lite QA</name><version>1</version><vendor>Local tests</vendor><depends>com.intellij.modules.platform</depends><depends>com.redhat.devtools.lsp4ij</depends><extensions defaultExtensionNs="com.intellij"><postStartupActivity implementation="dev.angularlite.qa.IntegrationProbe"/></extensions></idea-plugin>')
(base/'config/disabled_plugins.txt').write_text('JavaScript\nAngularJS\ncom.intellij.modules.ultimate\n')
properties='\n'.join(f'idea.{k}.path={(base/k).as_posix()}' for k in ['config','system','log','plugins'])
(base/'idea.properties').write_text(properties+'\nangularlite.qa.output='+(base/'result.txt').as_posix()+'\n')
print(base/'idea.properties')
