package me.minis.ledger;
import java.util.*;
/** Position-based fallback for a trusted receipt feed. Never joins frames or reads pixels. */
public final class PaymentCardSpatial {
 private static boolean inside(AlipayCardStructure.Node n,AlipayCardStructure.Node root){return n.right>n.left&&n.bottom>n.top&&n.left>=root.left&&n.right<=root.right&&n.top>=root.top&&n.bottom<=root.bottom;}
 private static final class Line {String text;AlipayCardStructure.Node node;Line(String s,AlipayCardStructure.Node n){text=s;node=n;}}
 public static List<WechatReceiptParser.Receipt> parse(List<AlipayCardStructure.Node> nodes,long now){
  List<WechatReceiptParser.Receipt> out=new ArrayList<>();if(nodes==null||nodes.isEmpty()||nodes.size()>1600)return out;
  AlipayCardStructure.Node root=nodes.get(0);Map<Integer,AlipayCardStructure.Node> map=new HashMap<>();Set<Integer> parents=new HashSet<>();
  for(AlipayCardStructure.Node n:nodes){map.put(n.id,n);if(n.password||n.editable)return out;}
  for(AlipayCardStructure.Node n:nodes)if(!n.text.isEmpty()||!n.desc.isEmpty()){AlipayCardStructure.Node p=map.get(n.parent);for(int d=0;p!=null&&d<65;d++,p=map.get(p.parent))parents.add(p.id);}
  List<AlipayCardStructure.Node> leaves=new ArrayList<>();for(AlipayCardStructure.Node n:nodes)if(!parents.contains(n.id)&&n.right>n.left&&n.bottom>n.top)leaves.add(n);
  leaves.sort(Comparator.comparingInt((AlipayCardStructure.Node n)->n.top).thenComparingInt(n->n.left).thenComparingInt(n->n.id));
  List<Line> lines=new ArrayList<>();Set<String> seen=new HashSet<>();for(AlipayCardStructure.Node n:leaves){List<String> own=AlipayPaymentCards.clean(Collections.singletonList(n.text)),desc=AlipayPaymentCards.clean(Collections.singletonList(n.desc));if(own.isEmpty()||Collections.indexOfSubList(desc,own)>=0)own=desc;else if(Collections.indexOfSubList(own,desc)<0)own.addAll(desc);if(own.size()>512||lines.size()+own.size()>1600)return out;String signature=n.left+"|"+n.top+"|"+n.right+"|"+n.bottom+"|"+String.join("\n",own);if(!seen.add(signature))continue;for(String s:own)lines.add(new Line(s,n));}
  List<Integer> titles=new ArrayList<>();for(int i=0;i<lines.size();i++)if(AlipayPaymentCards.success(lines.get(i).text))titles.add(i);if(titles.size()>32)return out;
  Set<String> keys=new HashSet<>();for(int k=0;k<titles.size();k++){
   int title=titles.get(k),prev=k==0?-1:titles.get(k-1),next=k+1==titles.size()?lines.size():titles.get(k+1);Line mark=lines.get(title);
   // Require the independent header clock and merchant, not an inferred observation time.
   int clock=-1;for(int i=title-1;i>prev&&i>=title-4;i--)if(AlipayPaymentCards.timestamp(lines.get(i).text)){clock=i;break;}
   if(clock<=prev+1)continue;int start=clock-1;if(!AlipayPaymentCards.merchant(lines.get(start).text))continue;
   int end=-1;for(int i=title+1;i<next;i++){String s=lines.get(i).text;if(s.equals("付款方式")){if(i+1<next)end=i+2;break;}if(s.startsWith("付款方式：")||s.startsWith("付款方式:")||s.startsWith("付款方式 ")){end=i+1;break;}}
   if(end<0)continue;List<String> text=new ArrayList<>();boolean bad=false;
   for(int i=start;i<end;i++){Line line=lines.get(i);AlipayCardStructure.Node n=line.node;
    // Core fields must be fully in the viewport and share the title's column.
    if(!inside(n,root)){bad=true;break;}
    if(i>title&&AlipayPaymentCards.timestamp(line.text)){bad=true;break;}
    text.add(line.text);
   }
   if(bad)continue;int tail=next;if(k+1<titles.size()){for(int i=next-1;i>=Math.max(end,next-4);i--)if(AlipayPaymentCards.timestamp(lines.get(i).text)){tail=Math.max(end,i-1);break;}}for(int i=end;i<tail;i++){String s=lines.get(i).text;if(s.matches("管理当前自动扣款服务|支付奖励|.*积分.*|为你推荐.*|广告.*"))break;if(inside(lines.get(i).node,root))text.add(s);}WechatReceiptParser.Receipt r=AlipayPaymentCards.parseCard(text,now);if(r!=null&&r.time>0&&keys.add(r.key))out.add(r);
  }return out;
 }
}
