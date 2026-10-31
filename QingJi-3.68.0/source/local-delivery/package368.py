from pathlib import Path
import subprocess,zipfile,hashlib,json,shutil,re,tempfile,os,xml.etree.ElementTree as ET
r=Path('/var/minis/shared/qingji-3.50');w=r/'work';v=r/'validation';old=r/'archive/v3.67-before-dedup368';apk=r/'dist/QingJi-3.68.0.apk'
for name,text in [('release368.log','PASS release368'),('dedup368.log','PASS 148'),('card368.log','PASS 132'),('apk-ui368.log','PASS 24')]:assert text in (v/name).read_text(),name
with tempfile.TemporaryDirectory() as t:
 subprocess.run(['node',str(w/'tools/minify_assets.cjs'),str(w/'assets-v2'),t],env={**os.environ,'NODE_PATH':str(v/'node_modules')},check=True)
 with zipfile.ZipFile(apk) as z:
  assert z.testzip() is None
  for p in Path(t).iterdir():
   if p.is_file():assert z.read('assets/'+p.name)==p.read_bytes()
ns='{http://schemas.android.com/apk/res/android}';a=ET.parse(w/'AndroidManifest.xml').getroot();b=ET.parse(old/'AndroidManifest.xml').getroot();assert a.attrib[ns+'versionCode']=='85'
for e in [a,b]:
 for k in ['versionCode','versionName']:e.attrib.pop(ns+k)
assert ET.tostring(a)==ET.tostring(b)
for p in (w/'res').rglob('*'):
 if p.is_file():assert p.read_bytes()==(old/p.relative_to(w)).read_bytes()
changed=[str(p.relative_to(w)) for p in w.rglob('*') if p.is_file() and (not (old/p.relative_to(w)).exists() or p.read_bytes()!=(old/p.relative_to(w)).read_bytes())];(v/'scope368.json').write_text(json.dumps(changed,ensure_ascii=False,indent=2))
meta=json.loads((w/'docs/current.json').read_text());assert meta['apk_sha256']==hashlib.sha256(apk.read_bytes()).hexdigest()
links=0
for f in [r/'README.md',w/'README.md',*list((w/'docs').glob('*.md'))]:
 for target in re.findall(r'\]\(([^)]+)\)',f.read_text()):
  if '://' not in target and not target.startswith('#'):assert (f.parent/target.split('#')[0]).exists(),(f,target);links+=1
extras=['checks367.py','ui367.js','native367.js','native355.js','calendar367.js','budget367.js','native359.js','native360.js','native362.js','scroll362.js','motion367.js','native364.js','VerifyApk.java','package.json','package-lock.json']
extras+=sorted(p.name for p in v.iterdir() if p.is_file() and '368' in p.name)
source=r/'dist/QingJi-3.68.0-source.zip'
with zipfile.ZipFile(source,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for f in sorted(w.rglob('*')):
  if f.is_file():z.write(f,'QingJi-3.68.0/'+str(f.relative_to(w)))
 for name in sorted(set(extras)):z.write(v/name,'QingJi-3.68.0/validation/'+name)
 for name in ['build368.sh','release368.py','package368.py','update_docs368.py']:z.write(r/name,'QingJi-3.68.0/local-delivery/'+name)
with zipfile.ZipFile(source) as z:
 assert z.testzip() is None
 for f in w.rglob('*'):
  if f.is_file():assert z.read('QingJi-3.68.0/'+str(f.relative_to(w)))==f.read_bytes()
 assert not any(Path(n).suffix in ['.jks','.keystore','.db'] for n in z.namelist())
out={}
for f in [apk,source]:
 shutil.copy2(f,Path('/var/minis/attachments')/f.name);out[f.name]={'bytes':f.stat().st_size,'sha256':hashlib.sha256(f.read_bytes()).hexdigest()}
(r/'dist/DELIVERY-3.68.0.json').write_text(json.dumps(out,indent=2));(r/'dist/SHA256SUMS-3.68.0.txt').write_text(''.join(x['sha256']+'  '+name+'\n' for name,x in out.items()))
shutil.copy2(w/'RELEASE-3.68.0.md',Path('/var/minis/attachments/RELEASE-3.68.0.md'))
print(json.dumps(out,indent=2));print('PASS exact APK assets, unchanged native resources/permissions/schema, CRC, exact sources, '+str(links)+' documentation links')
