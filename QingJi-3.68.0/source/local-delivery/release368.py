from pathlib import Path
import subprocess,zipfile
r=Path('/var/minis/shared/qingji-3.50');w=r/'work';v=r/'validation'
def run(args,log):
 p=subprocess.run(list(map(str,args)),capture_output=True,text=True);(v/log).write_text(p.stdout+p.stderr);assert p.returncode==0,p.stdout+p.stderr;print(p.stdout.strip(),flush=True)
run(['python3',v/'checks367.py'],'checks368.log')
run(['node',v/'motion367.js'],'motion368.log')
run(['sh',r/'build368.sh'],'build368.log')
assets=v/'apk368-assets';assets.mkdir(exist_ok=True)
with zipfile.ZipFile(r/'dist/QingJi-3.68.0.apk') as z:
 assert z.testzip() is None
 for n in z.namelist():
  if n.startswith('assets/'):(assets/Path(n).name).write_bytes(z.read(n))
for name in ['native367.js','native355.js','calendar367.js','budget367.js','native359.js','native360.js','native362.js','scroll362.js','motion367.js']:run(['node',v/name,assets],'apk368-'+name+'.log')
for scenario in ['reminder','backup','plans','review','draft','backupDraft','history','group','cycle']:run(['node',v/'native364.js',assets,scenario],'apk368-'+scenario+'.log')
run(['java','-cp',str(v/'classes')+':/var/minis/shared/notification-ledger/tools/apksig.jar','VerifyApk',r/'dist/QingJi-3.67.0.apk',r/'dist/QingJi-3.68.0.apk'],'signature368.log')
print('PASS release368 build, extracted APK regressions and signature',flush=True)
