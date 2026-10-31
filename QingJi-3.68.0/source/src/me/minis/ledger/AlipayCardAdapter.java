package me.minis.ledger;
import android.view.accessibility.AccessibilityNodeInfo;import android.graphics.Rect;import java.util.*;
/** Text nodes only. Visibility flags never suppress payment card children. */
final class AlipayCardAdapter {
 private static final class State {List<AlipayCardStructure.Node> nodes=new ArrayList<>();boolean cut;}
 static AlipayCardStructure.Result parse(AccessibilityNodeInfo root,long now){State s=new State();walk(root,-1,0,s);String pkg=String.valueOf(root.getPackageName());AlipayCardStructure.Result r=AlipayCardStructure.parseCombined(pkg,s.nodes,s.cut,now);return r.trusted?r:AlipayCardStructure.recent(pkg,s.nodes,s.cut,now);}
 static AlipayCardStructure.Result parse(AccessibilityNodeInfo root,AccessibilityNodeInfo event,long now){AlipayCardStructure.Result r=parse(root,now);if(!r.receipts.isEmpty()||r.truncated||event==null||event.getWindowId()!=root.getWindowId()||!AlipayPaymentCards.PKG.equals(String.valueOf(event.getPackageName())))return r;AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain(event);try{if(!n.refresh())return r;for(int i=0;i<64;i++){AccessibilityNodeInfo p=n.getParent();if(p==null)break;n.recycle();n=p;}if(n.getWindowId()!=root.getWindowId())return r;AlipayCardStructure.Result extra=parse(n,now);return extra.trusted&&!extra.truncated&&!extra.receipts.isEmpty()?extra:r;}finally{n.recycle();}}
 private static void walk(AccessibilityNodeInfo n,int parent,int depth,State s){if(depth>64||s.nodes.size()>=1600){s.cut=true;return;}AlipayCardStructure.Node row=new AlipayCardStructure.Node();row.id=s.nodes.size();row.parent=parent;row.visible=n.isVisibleToUser();row.password=n.isPassword();row.editable=n.isEditable();Rect r=new Rect();n.getBoundsInScreen(r);row.left=r.left;row.top=r.top;row.right=r.right;row.bottom=r.bottom;
  if(!row.password&&!row.editable){CharSequence t=n.getText(),d=n.getContentDescription();if(t!=null){if(t.length()>4096)s.cut=true;else row.text=t.toString().trim();}if(d!=null){if(d.length()>4096)s.cut=true;else row.desc=d.toString().trim();}}
  s.nodes.add(row);if(row.password||row.editable)return;for(int i=0;i<n.getChildCount();i++){if(s.nodes.size()>=1600){s.cut=true;break;}AccessibilityNodeInfo c=n.getChild(i);if(c!=null)try{walk(c,row.id,depth+1,s);}finally{c.recycle();}}
 }
}
