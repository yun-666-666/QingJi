package me.minis.ledger;
import java.util.*;
/** Fixed-size memory-only trace; no raw labels, amount values, names or transaction IDs. */
public final class ReceiptStageTrace {
 private static final int LIMIT=16;
 private static final class Item {long time;String metadata;Item(long t,String m){time=t;metadata=m;}}
 private final Deque<Item> items=new ArrayDeque<>();
 public synchronized void add(MoneyFlow.Frame f,long now,String decision){
  if(!MoneyFlow.moneyActivity(f.activity)&&OwnReceiptEvidence.detailType(f).isEmpty()&&!OwnReceiptEvidence.transferCue(f.text)&&!OwnReceiptEvidence.storedCue(f.text)&&!MoneyFlow.receiveClick(f.click))return;
  String page=f.activity;if(page.length()>180)page=page.substring(0,180);
  String metadata=page+" 标签="+f.text.size()+" "+OwnReceiptEvidence.flags(f)+" 领取点击="+MoneyFlow.receiveClick(f.click)+" 判定="+decision;
  if(!items.isEmpty()&&items.peekLast().metadata.equals(metadata)){items.peekLast().time=now;return;}
  if(items.size()==LIMIT)items.removeFirst();items.addLast(new Item(now,metadata));
 }
 private String body="";public synchronized void body(String safeCounts){body=safeCounts;}
 public synchronized void addService(MoneyFlow.Frame f,long now,int matched){String metadata="微信支付服务页 标签="+f.text.size()+" 自动扣款标记="+f.text.stream().anyMatch(WechatAutoDebit::completed)+" 匹配凭证="+matched+" "+body;if(!items.isEmpty()&&items.peekLast().metadata.equals(metadata)){items.peekLast().time=now;return;}if(items.size()==LIMIT)items.removeFirst();items.addLast(new Item(now,metadata));}
 private long gateScans=0,gatePass=0;private String lastGate="尚无服务门禁扫描";
 public synchronized void addGate(MoneyFlow.Frame f,long now,List<String> nav,boolean pass){gateScans++;if(pass)gatePass++;lastGate="服务门禁 扫描="+gateScans+" 通过="+gatePass+" 最近 "+WechatServiceGate.flags(nav)+" 内容扣款标记="+f.text.stream().anyMatch(WechatAutoDebit::completed)+" 截断="+f.truncated;}
 public synchronized String report(long now){StringBuilder s=new StringBuilder();for(Item i:items)s.append(Math.max(0,now-i.time)/1000).append("秒前 ").append(i.metadata).append('\n');return (s.length()==0?"尚无交易页阶段":s.toString().trim())+(gateScans>0?"\n"+lastGate:"");}
 public synchronized void clear(){items.clear();gateScans=gatePass=0;lastGate="尚无服务门禁扫描";}
}
