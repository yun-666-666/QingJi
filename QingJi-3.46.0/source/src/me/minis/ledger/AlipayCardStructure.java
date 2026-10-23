package me.minis.ledger;
import java.util.*;
/** Bounded tree parser; fields from neighbouring cards are never joined. */
public final class AlipayCardStructure {
 public static final class Node {public int id,parent,left,top,right,bottom;public boolean visible,password,editable;public String text="",desc="";boolean positive(){return right>left&&bottom>top;}}
 public static final class Result {public boolean trusted,truncated;public int candidates;public List<WechatReceiptParser.Receipt> receipts=new ArrayList<>();}
 private static boolean under(Node n,Node ancestor,Map<Integer,Node> map){for(int i=0;n!=null&&i<65;i++){if(n.id==ancestor.id)return true;n=map.get(n.parent);}return false;}
 private static boolean within(Node n,Node box){return n.positive()&&box.positive()&&n.left>=box.left&&n.top>=box.top&&n.right<=box.right&&n.bottom<=box.bottom;}
 private static List<String> values(Node n){List<String> t=new ArrayList<>();if(!n.text.isEmpty())t.add(n.text);if(!n.desc.isEmpty()&&!n.desc.equals(n.text))t.add(n.desc);return AlipayPaymentCards.clean(t);}
 private static boolean hasSuccess(Node n){for(String s:values(n))if(AlipayPaymentCards.success(s))return true;return false;}
 public static Result parse(String pkg,List<Node> nodes,boolean cut,long now){
  Result out=new Result();out.truncated=cut;if(!AlipayPaymentCards.PKG.equals(pkg)||nodes==null||nodes.isEmpty()||nodes.size()>1600)return out;
  Map<Integer,Node> map=new HashMap<>();Node root=nodes.get(0);List<String> nav=new ArrayList<>();
  for(Node n:nodes){if(map.put(n.id,n)!=null){out.truncated=true;return out;}if(n.editable&&n.visible)return out;if(!n.password&&!n.editable&&n.visible&&n.positive()&&n.top>=root.top&&n.top<root.top+(root.bottom-root.top)/4)nav.addAll(values(n));}
  out.trusted=AlipayPaymentCards.trusted(pkg,nav);if(!out.trusted||cut)return out;
  Set<String> keys=new HashSet<>();Set<Integer> hasTextChildren=new HashSet<>();Map<Integer,Integer> titleCounts=new HashMap<>();
  for(Node n:nodes)if(!n.password&&!n.editable&&!values(n).isEmpty()){Node p=map.get(n.parent);for(int d=0;p!=null&&d<65;d++,p=map.get(p.parent))hasTextChildren.add(p.id);}
  for(Node n:nodes)if(!hasTextChildren.contains(n.id)&&hasSuccess(n)){Node p=n;for(int d=0;p!=null&&d<65;d++,p=map.get(p.parent))titleCounts.put(p.id,titleCounts.getOrDefault(p.id,0)+1);}
  for(Node title:nodes){if(title.password||title.editable||!hasSuccess(title)||!within(title,root))continue;
   // A parent accessibility description may repeat the entire card: prefer its actual title child.
   if(hasTextChildren.contains(title.id))continue;out.candidates++;if(out.candidates>32){out.truncated=true;out.receipts.clear();return out;}
   Node box=title;
   for(int depth=0;box!=null&&depth<65;depth++,box=map.get(box.parent)){
    if(box.id==root.id||titleCounts.getOrDefault(box.id,0)>1)break;
    if(!within(box,root)||box.password||box.editable)continue;
    List<String> text=new ArrayList<>();boolean bad=false;int titles=0;
    for(Node n:nodes){if(!under(n,box,map))continue;if(n.password||n.editable){bad=true;break;}
     List<String> own=values(n);if(own.isEmpty())continue;
     if(hasTextChildren.contains(n.id))continue;
     if(!within(n,root)||!within(n,box)){bad=true;break;}
     for(String s:own)if(AlipayPaymentCards.success(s))titles++;
     text.addAll(own);
    }
    if(titles>1)break;if(bad)continue;
    WechatReceiptParser.Receipt r=AlipayPaymentCards.parseCard(text,now);
    if(r!=null){if(keys.add(r.key))out.receipts.add(r);break;}
   }
  }
  return out;
 }
 public static Result parseCombined(String pkg,List<Node> nodes,boolean cut,long now){
  Result out=parse(pkg,nodes,cut,now);if(!out.trusted||out.truncated)return out;
  Set<String> keys=new HashSet<>();for(WechatReceiptParser.Receipt r:out.receipts)keys.add(r.key);
  for(WechatReceiptParser.Receipt r:PaymentCardSpatial.parse(nodes,now))if(keys.add(r.key))out.receipts.add(r);
  return out;
 }
 public static Result recent(String pkg,List<Node> nodes,boolean cut,long now){
  Result out=new Result();out.truncated=cut;if(!AlipayPaymentCards.PKG.equals(pkg)||nodes==null||nodes.isEmpty()||cut)return out;
  Map<Integer,Node> map=new HashMap<>();Node root=nodes.get(0),heading=null;Set<Integer> parents=new HashSet<>();boolean scan=false,pay=false;
  for(Node n:nodes){map.put(n.id,n);if(n.password||n.editable)continue;List<String> v=values(n);if(n.visible&&within(n,root)){scan|=v.contains("扫一扫");pay|=v.contains("收付款");if(v.contains("最近消息")){if(heading!=null)return out;heading=n;}}}
  if(heading==null||!scan||!pay)return out;out.trusted=true;
  for(Node n:nodes)if(!n.password&&!n.editable&&!values(n).isEmpty()){Node p=map.get(n.parent);for(int d=0;p!=null&&d<65;d++,p=map.get(p.parent))parents.add(p.id);}
  Node section=map.get(heading.parent);for(int d=0;section!=null&&d<6;d++,section=map.get(section.parent)){
   if(section.id==root.id||!within(section,root)||section.bottom-section.top> (root.bottom-root.top)/2)break;
   Set<String> keys=new HashSet<>();
   for(Node title:nodes){if(title.password||title.editable||parents.contains(title.id)||!under(title,section,map))continue;boolean marker=false;for(String s:values(title))marker|=AlipayRecentMessage.marker(s);if(!marker)continue;out.candidates++;
    Node box=title;for(int depth=0;box!=null&&box.id!=section.id&&depth<12;depth++,box=map.get(box.parent)){
     if(!within(box,section))continue;List<String> text=new ArrayList<>();boolean bad=false;for(Node n:nodes)if(under(n,box,map)){if(n.password||n.editable){bad=true;break;}if(parents.contains(n.id)||values(n).isEmpty())continue;if(!within(n,box)||!within(n,root)){bad=true;break;}text.addAll(values(n));}
     if(bad)continue;WechatReceiptParser.Receipt r=AlipayRecentMessage.parse(text,now);if(r!=null){if(keys.add(r.key))out.receipts.add(r);break;}
    }
   }
   if(!out.receipts.isEmpty())break;
  }
  return out;
 }
}
