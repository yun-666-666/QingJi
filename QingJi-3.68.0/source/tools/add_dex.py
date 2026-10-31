#!/usr/bin/env python3
"""Improve ZIP Deflate only. The DEX bytes are never rewritten; dependency is build-only."""
import sys,zipfile,zopfli.zlib
from pathlib import Path
class Deflate:
 def __init__(self): self.data=bytearray()
 def compress(self,data): self.data.extend(data);return b''
 def flush(self): return zopfli.zlib.compress(bytes(self.data),numiterations=15)[2:-4]
original=zipfile._get_compressor
zipfile._get_compressor=lambda kind,level=None: Deflate() if kind==zipfile.ZIP_DEFLATED else original(kind,level)
payload=Path(sys.argv[2]).read_bytes()
apk=Path(sys.argv[1]);temporary=apk.with_suffix('.repacked.apk')
with zipfile.ZipFile(apk) as old:
 if 'classes.dex' in old.namelist(): raise ValueError('refuse duplicate DEX')
 expected={i.filename:old.read(i.filename) for i in old.infolist()}
 with zipfile.ZipFile(temporary,'w') as z:
  for i in old.infolist():z.writestr(i,expected[i.filename])
  info=zipfile.ZipInfo('classes.dex',(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
  z.writestr(info,payload)
expected['classes.dex']=payload
with zipfile.ZipFile(temporary) as z:
 assert z.testzip() is None
 assert set(z.namelist())==set(expected)
 for name,data in expected.items():assert z.read(name)==data,name
 print('Lossless APK compression verified:',len(expected),'entries; DEX',len(payload),'->',z.getinfo('classes.dex').compress_size)
temporary.replace(apk)
