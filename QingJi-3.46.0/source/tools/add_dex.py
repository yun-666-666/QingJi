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
with zipfile.ZipFile(sys.argv[1],'a',compression=zipfile.ZIP_DEFLATED) as z:
 if 'classes.dex' in z.namelist(): raise ValueError('refuse duplicate DEX')
 info=zipfile.ZipInfo('classes.dex',(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
 z.writestr(info,payload)
with zipfile.ZipFile(sys.argv[1]) as z:
 assert z.read('classes.dex')==payload
 print('Lossless DEX compression:',len(payload),'->',z.getinfo('classes.dex').compress_size)
