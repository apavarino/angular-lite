"""Translate VS Code injectTo into TextMate's native per-grammar injections.

IDEA 2025.3's VS Code bundle reader ignores injectTo. Keep the original rules,
but attach their injection selectors directly to the host grammars.
"""
import json,pathlib
root=pathlib.Path(__file__).resolve().parent/'textmate'
package=json.loads((root/'package.json').read_text())
grammars=package['contributes']['grammars']
# HTML allows whitespace around '=', including line breaks. Preserve capture
# numbers: the closing binding quote is a backreference to capture 3.
tag_path=root/'syntaxes/template-tag.json'
tag=json.loads(tag_path.read_text())
for rule in tag['repository'].values():
    if 'begin' in rule and r'\s*(=)\s*' not in rule['begin']:
        rule['begin']=rule['begin'].replace('(=)', r'\s*(=)\s*')
tag_path.write_text(json.dumps(tag,indent=2)+'\n',encoding='utf-8')
injections={}
for g in grammars:
    data=json.loads((root/g['path']).read_text())
    if g.get('injectTo') and 'injectionSelector' in data:
        selector=data['injectionSelector'].replace('#', ' ')
        injections.setdefault(selector,{'patterns':[]})['patterns'].append({'include':data['scopeName']})
    # IntelliJ handles scopes directly; VS Code's language-id mapping is not needed.
    g.pop('embeddedLanguages',None)
for name in ['typescript','html']:
    path=root/'syntaxes'/f'{name}.json'
    data=json.loads(path.read_text())
    data['injections']=injections
    path.write_text(json.dumps(data,indent=2),encoding='utf-8')
alias={'scopeName':'text.html.derivative','patterns':[{'include':'text.html.basic'}]}
(root/'syntaxes/html-derivative.json').write_text(json.dumps(alias),encoding='utf-8')
if not any(g['scopeName']=='text.html.derivative' for g in grammars):
    grammars.append({'scopeName':'text.html.derivative','path':'syntaxes/html-derivative.json'})
(root/'package.json').write_text(json.dumps(package,indent=2),encoding='utf-8')
