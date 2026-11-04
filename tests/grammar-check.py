import pathlib,subprocess,os
r=pathlib.Path(__file__).resolve().parents[1]
idea=pathlib.Path(os.environ['LOCALAPPDATA'])/'Programs/IntelliJ IDEA Ultimate'
cp=os.pathsep.join(map(str,[*list((idea/'lib').glob('*.jar')),*list((idea/'plugins/textmate-plugin/lib').rglob('*.jar')),r/'build/test-classes']))
(r/'build/test-classes').mkdir(exist_ok=True)
subprocess.run(['javac','-cp',cp,'-d',str(r/'build/test-classes'),str(r/'tests/GrammarCheck.java')],check=True)
subprocess.run(['java','-cp',cp,'GrammarCheck',str(r/'textmate')],check=True)
