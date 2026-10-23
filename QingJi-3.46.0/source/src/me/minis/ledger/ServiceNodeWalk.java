package me.minis.ledger;
/** Trusted service only: an invisible container must not hide visible descendants.
 * Hidden node text is NEVER visited; password subtrees remain pruned. */
public final class ServiceNodeWalk {
 public static final class Stats {public int examined,visited,hidden,visibleUnderHidden,deepest;public boolean truncated;}
 public static Stats walk(VisibleNodeWalk.Node n,VisibleNodeWalk.Visitor v){Stats s=new Stats();walk(n,v,s,0,false);return s;}
 private static void walk(VisibleNodeWalk.Node n,VisibleNodeWalk.Visitor v,Stats s,int depth,boolean hiddenParent){if(n==null||s.truncated)return;if(depth>VisibleNodeWalk.MAX_DEPTH||s.examined>=VisibleNodeWalk.MAX_NODES){s.truncated=true;return;}s.examined++;s.deepest=Math.max(depth,s.deepest);if(n.password())return;boolean visible=n.visible();if(visible){s.visited++;if(hiddenParent)s.visibleUnderHidden++;v.visit(n);}else s.hidden++;for(int i=0;i<n.children()&&!s.truncated;i++){VisibleNodeWalk.Node child=n.child(i);if(child!=null)try{walk(child,v,s,depth+1,hiddenParent||!visible);}finally{child.close();}}}
}
