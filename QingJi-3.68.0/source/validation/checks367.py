from pathlib import Path
import subprocess,json
root=Path('/var/minis/shared/qingji-3.50'); work=root/'work'; out=root/'validation'; old=Path('/var/minis/shared/notification-ledger/tests')
logs=[]
def run(args):
 p=subprocess.run([str(a) for a in args],capture_output=True,text=True);logs.append({'command':' '.join(map(str,args)),'exit':p.returncode,'stdout':p.stdout,'stderr':p.stderr});print(p.stdout,end='');print(p.stderr,end='');
 if p.returncode: raise RuntimeError('Failed '+str(args))
try:
 run(['javac','-g:none','-source','8','-target','8','-encoding','UTF-8','-cp','/var/minis/shared/adblock-module/tools/android34.jar','-d',out/'classes',*sorted((work/'src/me/minis/ledger').glob('*.java'))])
 names=['Generic335Test','Recognition344Test','Universal344Test','Category344Test','Audit345Test','Cards346Test','ParserV2Test','Replay335Test','MoneyFlowTest']
 run(['javac','-encoding','UTF-8','-cp',str(out/'classes')+':'+str(out/'junit.jar'),'-d',out/'classes',work/'tests/unit/Evidence351Test.java',work/'tests/unit/RecognitionTest.java',*[old/(n+'.java') for n in names]])
 for n in ['me.minis.ledger.Evidence351Test','me.minis.ledger.RecognitionTest']+names:run(['java','-cp',out/'classes',n])
 for f in sorted((work/'assets-v2').glob('*.js')):run(['node','--check',f])
 run(['node',out/'ui367.js'])
 run(['node',out/'native367.js'])
 run(['node',out/'native355.js'])
 run(['node',out/'calendar367.js'])
finally:
 (out/'results367.json').write_text(json.dumps(logs,ensure_ascii=False,indent=2))
