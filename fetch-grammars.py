"""Vendor MIT-licensed upstream TextMate grammars, pinning commits in provenance.json."""
import urllib.request, json, pathlib
root = pathlib.Path(__file__).resolve().parent
bundle = root/'textmate'
(bundle/'syntaxes').mkdir(parents=True,exist_ok=True)
(root/'licenses').mkdir(exist_ok=True)
def get(url):
    return urllib.request.urlopen(url).read()
def sha(repo):
    return json.loads(get('https://api.github.com/repos/'+repo+'/commits/HEAD'))['sha']
angular = sha('angular/vscode-ng-language-service')
vscode = sha('microsoft/vscode')
base = 'https://raw.githubusercontent.com/angular/vscode-ng-language-service/'+angular+'/'
meta = json.loads(get(base+'package.json'))
grammars = meta['contributes']['grammars']
for grammar in grammars:
    path = grammar['path'].removeprefix('./')
    (bundle/path).write_bytes(get(base+path))
(root/'licenses/Angular-MIT.txt').write_bytes(get(base+'LICENSE'))
base = 'https://raw.githubusercontent.com/microsoft/vscode/'+vscode+'/'
for name, upstream, scope, language in [
    ('typescript','typescript-basics/syntaxes/TypeScript.tmLanguage.json','source.ts','typescript'),
    ('html','html/syntaxes/html.tmLanguage.json','text.html.basic','html')]:
    path = 'syntaxes/'+name+'.json'
    (bundle/path).write_bytes(get(base+'extensions/'+upstream))
    grammars.append({'path':path,'scopeName':scope,'language':language})
(root/'licenses/VSCode-MIT.txt').write_bytes(get(base+'LICENSE.txt'))
package = {'name':'angular-lite-grammars','version':'0.1.0','contributes':{
    'languages':[{'id':'typescript','extensions':['.ts']},{'id':'html','extensions':['.html']}],
    'grammars':grammars}}
(bundle/'package.json').write_text(json.dumps(package,indent=2),encoding='utf-8')
(bundle/'provenance.json').write_text(json.dumps({'angular/vscode-ng-language-service':angular,'microsoft/vscode':vscode},indent=2))
print('Vendored upstream grammars:', angular, vscode)
