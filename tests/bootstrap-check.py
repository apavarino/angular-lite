import pathlib,subprocess,os
root=pathlib.Path(__file__).resolve().parents[1]
jar=root/'build/angular-lite/lib/angular-lite.jar'
classes=root/'build/bootstrap-test-classes'
classes.mkdir(exist_ok=True)
subprocess.run(['javac','--release','21','-cp',str(jar),'-d',str(classes),str(root/'tests/BootstrapCheck.java')],check=True)
subprocess.run(['java','-cp',os.pathsep.join([str(jar),str(classes)]),'BootstrapCheck',str(root)],check=True)
