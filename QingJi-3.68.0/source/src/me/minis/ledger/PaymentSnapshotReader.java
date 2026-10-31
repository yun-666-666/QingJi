package me.minis.ledger;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

final class PaymentSnapshotReader {
 private final List<PaymentSnapshot.Node> nodes=new ArrayList<>();private final Rect viewport=new Rect();private boolean cut,editable;private int visits;
 static PaymentSnapshot read(AccessibilityNodeInfo root,long version){
  PaymentSnapshotReader r=new PaymentSnapshotReader();root.getBoundsInScreen(r.viewport);r.walk(root,-1,0,root.getWindowId(),String.valueOf(root.getPackageName()));
  return new PaymentSnapshot(String.valueOf(root.getPackageName()),version,root.getWindowId(),r.viewport.left,r.viewport.top,r.viewport.width(),r.viewport.height(),r.nodes,r.cut,r.editable);
 }
 private static String text(CharSequence s){return s==null?"":s.toString();}
 private void walk(AccessibilityNodeInfo n,int parent,int depth,int window,String pkg){
  if(depth>64||visits++>=1600){cut=true;return;}
  if(n.getWindowId()!=window||!pkg.equals(String.valueOf(n.getPackageName())))return;
  if(n.isPassword()||n.isEditable()){editable=true;return;}
  Rect b=new Rect();n.getBoundsInScreen(b);int id=visits;
  String t=text(n.getText()),d=text(n.getContentDescription());
  List<String> lines=PaymentNodeText.lines(t,d);if(lines==null){cut=true;return;}
  if(n.isVisibleToUser()||!b.isEmpty()&&viewport.contains(b))nodes.add(new PaymentSnapshot.Node(id,parent,window,b.left,b.top,b.right,b.bottom,text(n.getViewIdResourceName()),text(n.getClassName()),t,d,false,lines));
  for(int i=0;i<n.getChildCount()&&!cut;i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)try{walk(c,id,depth+1,window,pkg);}finally{c.recycle();}}
 }
}
