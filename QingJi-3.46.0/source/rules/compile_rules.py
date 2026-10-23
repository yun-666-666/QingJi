from pathlib import Path
import json,hashlib
D=Path(__file__).resolve().parents[1]; p=D/'rules/common-notifications-v1.json'; j=json.loads(p.read_text()); rows=j['rules']
assert j['schema']==1 and len(set(r['id'] for r in rows))==len(rows)
q=lambda s:json.dumps(s,ensure_ascii=False)
out='''package me.minis.ledger;
import java.util.*;import java.util.regex.*;
/** Generated from rules/common-notifications-v1.json. Independent implementation. */
public final class CommonNotificationRules {
 public static final String VERSION=%s;
 public static final String SHA256=%s;
 private static final class Rule {final String id,source,kind;final Pattern pattern;final boolean confirm;Rule(String i,String s,String k,String re,boolean c){id=i;source=s;kind=k;pattern=Pattern.compile(re);confirm=c;}}
 private static final Rule[] RULES={
'''%(q(j['version']),q(hashlib.sha256(p.read_bytes()).hexdigest()))
for r in rows:
 assert r['source'] in ['alipay','bank','nfc'] and r['kind'] in ['income','expense','refund'] and r['policy'] in ['review','confirmed']
 out+='  new Rule(%s,%s,%s,%s,%s),\n'%(q(r['id']),q(r['source']),q(r['kind']),q(r['regex']),'true' if r['policy']=='confirmed' else 'false')
out+=''' };
 private static boolean accepts(String source,String pkg,String raw){if(source.equals("alipay"))return "com.eg.android.AlipayGphone".equals(pkg);if(source.equals("nfc"))return "com.miui.tsmclient".equals(pkg);return raw.matches("(?s).*(?:您|你的|您的|你尾号).*(?:账户|尾号|银行卡|借记卡|信用卡).*|")&&!"com.tencent.mm".equals(pkg);}
 public static PaymentParser.Result parse(String pkg,String raw){
  if(raw==null||PaymentParser.rejected(raw)||PaymentParser.has(raw,"信用卡还款","还款到账","花呗还款","借款已发放","贷款发放","提现到账","转入余额宝","余额宝转出"))return null;
  Set<Long> amounts=new HashSet<>();Set<String> kinds=new HashSet<>();boolean confirmed=true;String id="";
  for(Rule rule:RULES)if(accepts(rule.source,pkg,raw)){Matcher m=rule.pattern.matcher(raw);while(m.find()){long n=MoneyFlow.amount(m.group(1));if(n>0){amounts.add(n);kinds.add(rule.kind);confirmed&=rule.confirm;id=rule.id;}}}
  if(amounts.isEmpty())return null;if(kinds.contains("refund")){kinds.remove("income");}
  if(amounts.size()!=1||kinds.size()!=1){PaymentParser.Result r=PaymentParser.parse(raw);if(r!=null){r.confirmed=false;r.reason="规则包匹配多个金额或不同方向，请核对";}return r;}
  long cents=amounts.iterator().next();String kind=kinds.iterator().next();String marker=kind.equals("refund")?"退款成功":kind.equals("income")?"收入人民币":"支出人民币";
  PaymentParser.Result r=PaymentParser.parse(raw+"\\n"+marker+String.format(Locale.US,"%.2f",cents/100.0)+"元");if(r==null)return null;r.cents=cents;r.kind=kind;
  if(!confirmed){r.confirmed=false;r.reason="通知有明确支出候选，但无唯一交易身份；请核对是否与支付页面为同一笔";}
  ExpenseCategory.enrich(r,raw);return r;
 }
}
'''
out=out.replace('.*|"','.*"')
import argparse
parser=argparse.ArgumentParser(description='Generate notification rules or check without modifying source')
parser.add_argument('--check',action='store_true')
args=parser.parse_args()
target=D/'src/me/minis/ledger/CommonNotificationRules.java'
if args.check:
 if not target.exists() or target.read_text()!=out:
  raise SystemExit('Generated rules differ; run python3 rules/compile_rules.py intentionally')
else:
 target.write_text(out)
print('Checked' if args.check else 'Compiled',len(rows),'independent templates',j['version'])
