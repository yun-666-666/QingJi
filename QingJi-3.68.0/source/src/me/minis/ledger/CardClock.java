package me.minis.ledger;
import java.util.*;
/** Bind an isolated list item to its own clock or an immediately preceding clock-only sibling. */
final class CardClock {
 private static boolean under(WechatCardStructure.Node n,WechatCardStructure.Node parent,Map<Integer,WechatCardStructure.Node> map){for(int d=0;n!=null&&d<65;d++,n=map.get(n.parent))if(n.id==parent.id)return true;return false;}
 private static String text(WechatCardStructure.Node n,long now){String a=TransactionTime.clean(n.text),b=TransactionTime.clean(n.desc);long x=TransactionTime.clock(a,now),y=TransactionTime.clock(b,now);if(x>0&&y>0&&x!=y)return "";return x>0?a:y>0?b:"";}
 static String read(List<WechatCardStructure.Node> nodes,Map<Integer,WechatCardStructure.Node> map,WechatCardStructure.Node item,WechatCardStructure.Node viewport,long now){
  String found="";long time=0;for(WechatCardStructure.Node n:nodes)if(under(n,item,map)&&n.visible&&n.top>=viewport.top&&n.bottom<=viewport.bottom&&!n.password&&!n.editable){String value=text(n,now);if(value.isEmpty())continue;long t=TransactionTime.clock(value,now);if(time>0&&t!=time)return "";time=t;found=value;}if(time>0)return found;
  WechatCardStructure.Node previous=null;for(WechatCardStructure.Node n:nodes)if(n.parent==viewport.id&&n.id!=item.id&&n.visible&&n.bottom<=item.top&&n.top>=viewport.top&&(previous==null||n.bottom>previous.bottom))previous=n;
  if(previous==null||item.top-previous.bottom>120)return "";
  for(WechatCardStructure.Node n:nodes)if(under(n,previous,map)){if(n.password||n.editable)return "";for(String raw:new String[]{n.text,n.desc}){String value=TransactionTime.clean(raw);if(value.isEmpty())continue;long t=TransactionTime.clock(value,now);if(t==0||time>0&&time!=t)return "";time=t;found=value;}}
  return found;
 }
}
