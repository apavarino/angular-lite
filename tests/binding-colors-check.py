import pathlib, subprocess, os
r = pathlib.Path(__file__).resolve().parents[1]
idea = pathlib.Path(os.environ.get('ANGULAR_LITE_IDEA') or str(pathlib.Path(os.environ['LOCALAPPDATA']) / 'Programs/IntelliJ IDEA Ultimate'))
out = r/'build/color-tests'
out.mkdir(exist_ok=True)
cp = os.pathsep.join(map(str, [*list((idea/'lib').glob('*.jar')), *list((idea/'plugins/textmate-plugin/lib').rglob('*.jar')), out]))
subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', cp, '-d', str(out), str(r/'src/main/java/dev/angularlite/AngularSyntaxHighlighter.java'), str(r/'tests/BindingColorsCheck.java')], check=True)
subprocess.run(['java', '-Djava.awt.headless=true', '-cp', cp, 'BindingColorsCheck', str(r/'textmate')], check=True)
