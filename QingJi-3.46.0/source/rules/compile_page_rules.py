from pathlib import Path
import json,hashlib,argparse
D=Path(__file__).resolve().parents[1];p=D/'rules/payment-pages-v1.json';j=json.loads(p.read_text());assert j['schema']==1
q=lambda s:json.dumps(s,ensure_ascii=False)
s='package me.minis.ledger;\n/** Generated from rules/payment-pages-v1.json; package-independent. */\npublic final class PaymentPageRules {\n'
s+=' public static final String VERSION='+q(j['version'])+';\n public static final String SHA256='+q(hashlib.sha256(p.read_bytes()).hexdigest())+';\n'
for key in ['success','exit','paid','order','discount','balance','merchant','method','history','blocked','non_payment_titles']:
 a=j[key];assert a and len(a)==len(set(a));s+=' public static final String[] '+key.upper()+'={'+','.join(q(x) for x in a)+'};\n'
s+=' public static boolean has(String[] a,String s){for(String x:a)if(x.equals(s))return true;return false;}\n public static boolean success(String s){return has(SUCCESS,s.replaceFirst("[！!]$",""));}\n}\n'
a=argparse.ArgumentParser();a.add_argument('--check',action='store_true');args=a.parse_args();out=D/'src/me/minis/ledger/PaymentPageRules.java'
if args.check:
 assert out.exists() and out.read_text()==s,'Run rules/compile_page_rules.py to update generated source'
else:out.write_text(s)
print('Checked' if args.check else 'Generated','page rules',j['version'])
