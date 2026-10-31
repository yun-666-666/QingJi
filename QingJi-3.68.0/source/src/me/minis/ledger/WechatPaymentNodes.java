package me.minis.ledger;
import java.util.*;
/** Unknown layouts: only complete direct list items, never the page or adjacent messages. */
final class WechatPaymentNodes {
 private static boolean within(WechatCardStructure.Node n,WechatCardStructure.Node box){return n.right>n.left&&n.bottom>n.top&&box.right>box.left&&box.bottom>box.top&&n.left>=box.left&&n.top>=box.top&&n.right<=box.right&&n.bottom<=box.bottom;}
 private static boolean under(WechatCardStructure.Node n,WechatCardStructure.Node box,Map<Integer,WechatCardStructure.Node> map){for(int i=0;n!=null&&i<65;i++){if(n.id==box.id)return true;n=map.get(n.parent);}return false;}
 static boolean list(WechatCardStructure.Node n){return n.clazz.endsWith("RecyclerView")||n.clazz.endsWith("ListView");}
 static List<WechatReceiptParser.Receipt> parse(List<WechatCardStructure.Node> nodes,Map<Integer,WechatCardStructure.Node> map,long now){
  List<WechatReceiptParser.Receipt> out=new ArrayList<>();Set<String> keys=new HashSet<>();
  for(WechatCardStructure.Node viewport:nodes){if(!list(viewport)||!viewport.visible)continue;
   for(WechatCardStructure.Node item:nodes){if(item.parent!=viewport.id||!item.visible||!within(item,viewport))continue;
    // The existing profile remains authoritative, including its rejection of clipped required fields.
    boolean known=false,invalid=false;List<String> fields=new ArrayList<>();
    for(WechatCardStructure.Node n:nodes){if(!under(n,item,map))continue;if(viewport.viewId.equals("com.tencent.mm:id/c6b")&&item.viewId.equals("com.tencent.mm:id/c3t")&&n.viewId.matches("com\\.tencent\\.mm:id/c4[14]")){known=true;break;}
     if(n.password||n.editable){invalid=true;break;}
     if(n.text.isEmpty()&&n.desc.isEmpty())continue;
     if(!within(n,item)||!within(n,viewport)||n.text.length()>2048||n.desc.length()>2048){invalid=true;break;}
     if(!n.text.isEmpty())fields.add(n.text);if(!n.desc.isEmpty()&&!n.desc.equals(n.text))fields.add(n.desc);if(fields.size()>220){invalid=true;break;}
    }
    if(known||invalid||WechatServiceGate.blocked(fields))continue;
    List<String> clean=WechatServiceGate.clean(fields);String ownClock=CardClock.read(nodes,map,item,viewport,now);if(!ownClock.isEmpty()&&!clean.contains(ownClock))clean.add(0,ownClock);WechatReceiptParser.Receipt r=WechatPaymentCard.parse(clean,now);if(r!=null&&keys.add(r.key))out.add(r);
   }
  }return out;
 }
}
