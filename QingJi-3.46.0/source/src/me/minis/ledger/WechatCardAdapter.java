package me.minis.ledger;
import android.view.accessibility.AccessibilityNodeInfo;import android.graphics.Rect;import java.util.*;
/** Only called after trusted service navigation; nodes never enter general text/flow input. */
final class WechatCardAdapter {
 static final class State {int count=0;boolean cut=false;List<WechatCardStructure.Node> nodes=new ArrayList<>();}
 static WechatCardStructure.Result parse(AccessibilityNodeInfo root,List<String> nav,long now){State s=new State();walk(root,-1,0,s,false);String pkg=String.valueOf(root.getPackageName());WechatCardStructure.Result result=WechatCardStructure.parse(pkg,nav,s.nodes,s.cut,now);if(result.items==0)for(WechatCardStructure.Node viewport:s.nodes)if(WechatPaymentNodes.list(viewport)&&viewport.visible)for(WechatCardStructure.Node item:s.nodes)if(item.parent==viewport.id&&item.visible)result.items++;Set<String> keys=new HashSet<>();for(WechatReceiptParser.Receipt r:result.receipts)keys.add(r.key);for(WechatReceiptParser.Receipt r:WechatCardText.parse(pkg,nav,s.nodes,s.cut,now))if(keys.add(r.key)){result.receipts.add(r);result.accepted++;}return result;}
 private static void walk(AccessibilityNodeInfo n,int parent,int depth,State s,boolean inCard){if(depth>64||s.count>=1600){s.cut=true;return;}WechatCardStructure.Node row=new WechatCardStructure.Node();row.id=s.count++;row.parent=parent;row.visible=n.isVisibleToUser();row.password=n.isPassword();row.editable=n.isEditable();row.viewId=n.getViewIdResourceName()==null?"":n.getViewIdResourceName();row.clazz=String.valueOf(n.getClassName());Rect r=new Rect();n.getBoundsInScreen(r);row.left=r.left;row.top=r.top;row.right=r.right;row.bottom=r.bottom;
  // Every card field is collected regardless of isVisibleToUser; no chat-wide hidden-text read.
  String id=row.viewId;inCard|=id.matches("com\\.tencent\\.mm:id/c4[14]")||WechatPaymentNodes.list(row);boolean field=inCard||id.equals("com.tencent.mm:id/c8t");
  if(field&&!row.password&&!row.editable){CharSequence t=n.getText(),d=n.getContentDescription();if(t!=null){if(t.length()>2048)s.cut=true;else row.text=t.toString().trim();}if(d!=null){if(d.length()>2048)s.cut=true;else row.desc=d.toString().trim();}}
  s.nodes.add(row);if(row.password||row.editable)return;
  for(int i=0;i<n.getChildCount();i++){if(s.count>=1600){s.cut=true;break;}AccessibilityNodeInfo c=n.getChild(i);if(c!=null)try{walk(c,row.id,depth+1,s,inCard);}finally{c.recycle();}}
 }
}
