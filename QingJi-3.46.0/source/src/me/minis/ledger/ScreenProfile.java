package me.minis.ledger;

import java.util.*;

/** A version-bounded profile binds labels to values without depending on traversal order. */
public final class ScreenProfile {
 public final String id,pkg;public final long minVersion,maxVersion;public final String[] paidIds;
 public ScreenProfile(String id,String pkg,long min,long max,String... paidIds){this.id=id;this.pkg=pkg;minVersion=min;maxVersion=max;this.paidIds=paidIds.clone();}
 public boolean matches(PaymentSnapshot s){return (pkg.isEmpty()||pkg.equals(s.pkg))&&s.version>=minVersion&&(maxVersion==0||s.version<=maxVersion);}
 // No guessed third-party resource IDs: semantic fallback also supports Compose/WebView.
 public static final ScreenProfile SEMANTIC=new ScreenProfile("semantic-payment-v1","",0,0);
 private boolean paidId(PaymentSnapshot.Node n){for(String suffix:paidIds)if(n.resource.endsWith(":id/"+suffix))return true;return false;}
 public List<String> bind(PaymentSnapshot s){
  List<String> out=new ArrayList<>();Set<Integer> consumed=new HashSet<>();Map<Integer,String> bound=new HashMap<>();
  for(PaymentSnapshot.Node label:s.nodes){List<String> lines=label.lines();if(lines.size()!=1)continue;String name=lines.get(0);
   boolean amount=false,merchant=PaymentPageRules.has(PaymentPageRules.MERCHANT,name);
   for(int type=0;type<4;type++)amount|=PaymentPageRules.has(PaymentPageFields.moneyLabels(type),name);
   if(!amount&&!merchant)continue;
   PaymentSnapshot.Node best=null;int score=0;boolean tie=false;
   for(PaymentSnapshot.Node value:s.nodes){if(value.id==label.id||value.window!=label.window||value.editable||value.lines().size()!=1)continue;
    String text=value.lines().get(0);if(amount?PaymentPageFields.cash(text)<0:!PaymentPageFields.name(text))continue;
    int height=Math.max(1,label.bottom-label.top),distance=value.top-label.bottom;
    boolean row=Math.min(label.bottom,value.bottom)>Math.max(label.top,value.top)&&value.left>=label.right;
    boolean below=distance>=0&&distance<=height*3&&Math.min(label.right,value.right)>Math.max(label.left,value.left);
    boolean siblings=label.parent>=0&&label.parent==value.parent;
    if(!row&&!below)continue;
    int candidate=(siblings?4:0)+(row?3:1)+(amount&&paidId(value)?8:0);
    if(candidate>score){score=candidate;best=value;tie=false;}else if(candidate==score)tie=true;
   }
   if(best!=null&&!tie&&!consumed.contains(best.id)){bound.put(label.id,name+"："+best.lines().get(0));consumed.add(best.id);}
  }
  for(PaymentSnapshot.Node n:s.nodes){if(consumed.contains(n.id))continue;String value=bound.get(n.id);if(value!=null)out.add(value);else out.addAll(n.lines());}
  return out;
 }
 public PaymentParser.Result parse(PaymentSnapshot s){
  if(!matches(s)||s.truncated||s.editable)return null;
  List<String> bound=bind(s);PaymentParser.Result r=GenericPaymentRules.parse(s.pkg,bound,false);
  if(r!=null){r.reason="结构化规则 "+id+"：成功状态、离开按钮和金额关系已校验";r.transactionId=TransactionIdentity.providerId(String.join("\n",bound));}
  return r;
 }
}
