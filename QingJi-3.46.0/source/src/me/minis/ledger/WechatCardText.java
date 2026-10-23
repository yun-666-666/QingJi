package me.minis.ledger;
import java.util.*;
/** Trusted service only: collect text AND descriptions per on-screen card, independent of leaf visibility. */
public final class WechatCardText {
 private static boolean within(WechatCardStructure.Node n,WechatCardStructure.Node box){return n!=null&&box!=null&&n.right>n.left&&n.bottom>n.top&&box.right>box.left&&box.bottom>box.top&&n.left>=box.left&&n.top>=box.top&&n.right<=box.right&&n.bottom<=box.bottom;}
 private static boolean under(WechatCardStructure.Node n,WechatCardStructure.Node box,Map<Integer,WechatCardStructure.Node> map){for(int i=0;n!=null&&i<65;i++){if(n.id==box.id)return true;n=map.get(n.parent);}return false;}
 public static List<WechatReceiptParser.Receipt> parse(String pkg,List<String> nav,List<WechatCardStructure.Node> nodes,boolean cut,long now){List<WechatReceiptParser.Receipt> out=new ArrayList<>();if(cut||nodes==null||nodes.size()>1600||!WechatReceiptParser.trustedService(pkg,nav))return out;Map<Integer,WechatCardStructure.Node> map=new HashMap<>();for(WechatCardStructure.Node n:nodes)if(map.put(n.id,n)!=null)return out;Set<String> keys=new HashSet<>();
  for(WechatCardStructure.Node viewport:nodes){if(!viewport.viewId.equals("com.tencent.mm:id/c6b")||!viewport.visible||!viewport.clazz.endsWith("RecyclerView"))continue;
   for(WechatCardStructure.Node item:nodes){if(item.parent!=viewport.id||!item.viewId.equals("com.tencent.mm:id/c3t")||!item.visible)continue;
    List<WechatCardStructure.Node> cards=new ArrayList<>();for(WechatCardStructure.Node n:nodes)if(under(n,item,map)&&n.viewId.matches("com\\.tencent\\.mm:id/c4[14]"))cards.add(n);if(cards.size()!=1)continue;WechatCardStructure.Node card=cards.get(0);if(!card.visible||!within(card,item)||!within(card,viewport))continue;
    List<String> evidence=new ArrayList<>();for(WechatCardStructure.Node n:nodes)if(under(n,item,map)&&n.viewId.equals("com.tencent.mm:id/c8t")&&n.visible&&within(n,item)&&within(n,viewport)&&WechatReceiptParser.clock(n.text,now)>0)evidence.add(n.text);
    boolean invalid=false,hiddenText=false,clippedText=false;int fields=0;
    for(WechatCardStructure.Node n:nodes){if(!under(n,card,map))continue;if(n.password||n.editable){invalid=true;break;}if(!within(n,card)||!within(n,item)||!within(n,viewport)){clippedText|=!n.text.isEmpty()||!n.desc.isEmpty();continue;}
     String[] values=n.text.equals(n.desc)?new String[]{n.text}:new String[]{n.text,n.desc};for(String field:values){if(field==null||field.trim().isEmpty())continue;if(field.length()>2048||++fields>220){invalid=true;break;}hiddenText|=!n.visible;evidence.add(field.trim());}if(invalid)break;
    }
    if(invalid||WechatServiceGate.blocked(evidence))continue;List<String> input=new ArrayList<>(nav);input.addAll(evidence);if(input.size()>240)continue;
    List<String> clean=WechatServiceGate.clean(input);boolean auto=clean.stream().anyMatch(WechatAutoDebit::completed),incoming=clean.contains("你有一笔收款入账");
    // A missing/clipped merchant must not be replaced with the last navigation label.
    if(auto||incoming){List<WechatCardStructure.Node> merchants=new ArrayList<>();for(WechatCardStructure.Node n:nodes)if(under(n,card,map)&&n.viewId.equals("com.tencent.mm:id/mnx"))merchants.add(n);if(merchants.size()!=1)continue;WechatCardStructure.Node m=merchants.get(0);if(m.password||m.editable||m.text.isEmpty()||!WechatServiceGate.label(m.text).isEmpty()||m.text.equals("微信支付")||MoneyFlow.amount(m.text)>0||!within(m,card)||!within(m,item)||!within(m,viewport)||!clean.contains(m.text))continue;}
    // Never alternate between order-based and message-time-based identities as rows get clipped.
    if(auto){int order=clean.indexOf("下单时间");if(order<0||order+1>=clean.size()||!WechatAutoDebit.orderTime(clean.get(order+1)))continue;}
    List<WechatReceiptParser.Receipt> parsed=new ArrayList<>();
    if(clean.stream().anyMatch(WechatPaymentCard::usage)){if(clippedText)continue;WechatReceiptParser.Receipt payment=WechatPaymentCard.parse(WechatServiceGate.clean(evidence),now);if(payment!=null)parsed.add(payment);}
    else {parsed.addAll(WechatIncomingCard.parse(clean,now));parsed.addAll(WechatReceiptParser.parseAll(pkg,input,now));}
    for(WechatReceiptParser.Receipt r:parsed){if(hiddenText&&!clean.stream().anyMatch(WechatPaymentCard::usage)){r.result.confirmed=false;if(!r.result.reason.contains("没有独立收款时间"))r.result.reason="微信支付逐卡读取：包含微信标为不可见的正文，已隔离屏幕内同卡文字及描述；请核对交易时间和是否已记账";}if(keys.add(r.key))out.add(r);}
   }
  }for(WechatReceiptParser.Receipt r:WechatPaymentNodes.parse(nodes,map,now))if(keys.add(r.key))out.add(r);return out;
 }
}
