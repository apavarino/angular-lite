"""Launch only the isolated QA installation; leave the user's IDE untouched."""
import os, pathlib, subprocess, sys
r=pathlib.Path(__file__).resolve().parents[1]
base=r/'build'/('ide-probe-'+(sys.argv[1] if len(sys.argv)>1 else '262'))
env=os.environ.copy()
env['IDEA_PROPERTIES']=str(base/'idea.properties')
idea=pathlib.Path(os.environ.get('ANGULAR_LITE_IDEA',str(r/'deps/idea-2026.2.4')))
project=pathlib.Path(sys.argv[2]).resolve() if len(sys.argv)>2 else r/'demo/fixture'
process=subprocess.Popen([str(idea/'bin/idea64.exe'),'nosplash',str(project)],env=env)
(base/'pid.txt').write_text(str(process.pid))
print('QA IDEA PID',process.pid)
