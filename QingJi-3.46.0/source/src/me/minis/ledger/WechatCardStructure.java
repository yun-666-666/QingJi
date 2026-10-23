package me.minis.ledger;
import java.util.*;
/** Narrow WeChat service-card profile. False visibility is never globally overridden. */
public final class WechatCardStructure {
 public static final class Node {
  public int id,parent,left,top,right,bottom;public boolean visible,password,editable;public String viewId="",text="",desc="",clazz="";
  String role(){return viewId.startsWith("com.tencent.mm:id/")?viewId.substring(18):"";}
  boolean positive(){return right>left&&bottom>top;}
 }
 public static final class Result {public List<WechatReceiptParser.Receipt> receipts=new ArrayList<>();public int items,candidates,accepted;public boolean truncated;}
 private static boolean within(Node n,Node box){return n!=null&&box!=null&&n.positive()&&box.positive()&&n.left>=box.left&&n.top>=box.top&&n.right<=box.right&&n.bottom<=box.bottom;}
 private static boolean under(Node n,Node ancestor,Map<Integer,Node> map){for(int i=0;n!=null&&i<65;i++){if(n.id==ancestor.id)return true;n=map.get(n.parent);}return false;}
 private static List<Node> role(List<Node> nodes,String role,Node ancestor,Map<Integer,Node> map){List<Node> out=new ArrayList<>();for(Node n:nodes)if(n.role().equals(role)&&under(n,ancestor,map))out.add(n);return out;}
 private static Node one(List<Node> ns){return ns.size()==1?ns.get(0):null;}
 private static boolean readable(Node n,Node item,Node viewport){return n!=null&&!n.password&&!n.editable&&within(n,item)&&within(n,viewport);}
 public static Result parse(String pkg,List<String> navigation,List<Node> nodes,boolean truncated,long now){
  Result out=new Result();out.truncated=truncated;if(truncated||nodes==null||nodes.size()>1600||!WechatReceiptParser.trustedService(pkg,navigation))return out;
  Map<Integer,Node> map=new HashMap<>();for(Node n:nodes){if(map.put(n.id,n)!=null){out.truncated=true;return out;}}
  Set<String> seen=new HashSet<>();
  for(Node viewport:nodes){if(!viewport.role().equals("c6b")||!viewport.visible||!viewport.positive()||!viewport.clazz.endsWith("RecyclerView"))continue;
   for(Node item:nodes){if(item.parent!=viewport.id||!item.role().equals("c3t")||!item.visible||!item.positive())continue;out.items++;
    List<Node> cards=new ArrayList<>();cards.addAll(role(nodes,"c44",item,map));cards.addAll(role(nodes,"c41",item,map));Node card=one(cards);if(card==null||!card.visible||!within(card,item))continue;
    Node body=one(role(nodes,"mm3",card,map)),header=one(role(nodes,"mn2",card,map)),merchant=one(role(nodes,"mnx",header==null?card:header,map));
    if(body==null||header==null||merchant==null||!body.visible||!header.visible||!merchant.visible||!readable(body,item,viewport)||!readable(header,item,viewport)||!readable(merchant,item,viewport))continue;
    Node heading=one(role(nodes,"mno",body,map)),money=one(role(nodes,"mnp",body,map)),label=one(role(nodes,"mnr",body,map)),currency=one(role(nodes,"mnt",body,map)),amount=one(role(nodes,"mns",body,map));
    if(heading==null||!WechatAutoDebit.completed(heading.text))continue;out.candidates++;
    boolean safe=true;for(Node n:nodes)if(under(n,body,map)&&(n.password||n.editable||PaymentParser.has(n.text+"\n"+n.desc,"验证码","支付密码","支付失败","未支付","待支付","已退款","申请售后","取消订单")))safe=false;
    for(Node n:new Node[]{heading,money,label,currency,amount})if(!readable(n,item,viewport)||!within(n,body))safe=false;
    if(!safe||!under(label,money,map)||!under(currency,money,map)||!under(amount,money,map)||PaymentParser.has(merchant.text,"验证码","支付密码")||!label.text.matches("通过.{1,40}扣款")||!currency.text.matches("[¥￥]")||!amount.text.matches("[0-9]{1,9}\\.[0-9]{2}")||merchant.text.isEmpty()||merchant.text.length()>80)continue;
    long cents=MoneyFlow.amount(amount.text);if(cents<=0)continue;
    String expected=label.text+currency.text+amount.text+"交易详情";if(!money.desc.replaceAll("\\s","").equals(expected.replaceAll("\\s","")))continue;
    List<String> evidence=new ArrayList<>();Node clock=one(role(nodes,"c8t",item,map));if(clock!=null&&clock.visible&&readable(clock,item,viewport)&&WechatReceiptParser.clock(clock.text,now)>0)evidence.add(clock.text);
    evidence.add(merchant.text);evidence.add(heading.text);evidence.add(label.text);evidence.add(currency.text+amount.text);
    List<Node> orders=role(nodes,"k6x",body,map);for(Node order:orders){if(!order.text.equals("下单时间")||!readable(order,item,viewport)||!within(order,body))continue;Node sibling=null;for(Node n:role(nodes,"k7a",body,map))if(n.parent==order.parent){if(sibling!=null){sibling=null;break;}sibling=n;}if(sibling!=null&&readable(sibling,item,viewport)&&within(sibling,body)&&sibling.text.matches("[0-9]{1,2}月[0-9]{1,2}日\\s+[0-9]{1,2}:[0-9]{2}")){evidence.add("下单时间");evidence.add(sibling.text);}}
    if(!evidence.contains("下单时间"))continue;
    List<WechatReceiptParser.Receipt> parsed=WechatAutoDebit.cards(evidence,now);if(parsed.size()!=1)continue;WechatReceiptParser.Receipt r=parsed.get(0);if(r.result.cents!=cents||!r.result.kind.equals("expense"))continue;
    r.result.confirmed=false;r.result.reason="微信支付卡结构补充：正文被微信标为不可见，已核对同卡位置、完成状态和金额；未据此自动确认，请核对支付日期与是否已记账";
    if(seen.add(r.key)){out.receipts.add(r);out.accepted++;}
   }
  }return out;
 }
}
