"""Build an installable plugin against a locally installed IntelliJ (JDK 21+)."""
import argparse, pathlib, subprocess, zipfile, shutil, os, tempfile, xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parent
version = ET.parse(root/'src/main/resources/META-INF/plugin.xml').getroot().findtext('version')
p = argparse.ArgumentParser()
default_idea = os.environ.get('ANGULAR_LITE_IDEA')
if not default_idea and os.environ.get('LOCALAPPDATA'):
    default_idea = str(pathlib.Path(os.environ['LOCALAPPDATA']) / 'Programs/IntelliJ IDEA Ultimate')
p.add_argument('--idea', default=default_idea, required=not default_idea)
a = p.parse_args()
idea = pathlib.Path(a.idea)
(root/'build').mkdir(exist_ok=True)
classes = pathlib.Path(tempfile.mkdtemp(prefix='classes-',dir=root/'build'))
jars = list((root/'deps/lsp4ij/lib').glob('*.jar')) + list((idea/'lib').glob('*.jar')) + list((idea/'plugins/textmate-plugin/lib').rglob('*.jar'))
sources = list((root/'src/main/java').rglob('*.java'))
subprocess.run(['javac', '--release', '21', '-implicit:none', '-sourcepath', str(root/'src/main/java'), '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str,jars)), '-d', str(classes), *map(str,sources)],check=True)
shutil.copytree(root/'src/main/resources', classes, dirs_exist_ok=True)
output = root/'build/angular-lite'
(output/'lib').mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output/'lib/angular-lite.jar','w',zipfile.ZIP_DEFLATED) as z:
    for f in classes.rglob('*'):
        if f.is_file(): z.write(f, f.relative_to(classes).as_posix())
for directory in ['server','textmate','licenses']:
    shutil.copytree(root/directory, output/directory, dirs_exist_ok=True)
for file in ['setup.mjs','README.md']:
    shutil.copy2(root/file,output/file)
(root/'dist').mkdir(exist_ok=True)
with zipfile.ZipFile(root/f'dist/angular-lite-{version}.zip','w',zipfile.ZIP_DEFLATED) as z:
    for f in output.rglob('*'):
        if f.is_file(): z.write(f, f.relative_to(output.parent).as_posix())
print(root/f'dist/angular-lite-{version}.zip')
