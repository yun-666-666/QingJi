package me.minis.ledger;
/** Payment-only walk: hidden ancestors are traversed, hidden text is accepted only inside the active viewport. */
public final class PaymentNodeWalk {
 public interface Node extends VisibleNodeWalk.Node {boolean editable();boolean inside();}
 public static final class Stats {public int examined,visited,hidden,deepest;public boolean truncated;}
 public static Stats walk(Node root,VisibleNodeWalk.Visitor visitor){Stats s=new Stats();walk(root,visitor,s,0);return s;}
 private static void walk(Node n,VisibleNodeWalk.Visitor v,Stats s,int depth){if(n==null||s.truncated)return;if(depth>VisibleNodeWalk.MAX_DEPTH||s.examined>=VisibleNodeWalk.MAX_NODES){s.truncated=true;return;}s.examined++;s.deepest=Math.max(depth,s.deepest);if(n.password())return;if(!n.visible())s.hidden++;if(n.visible()||n.inside()){s.visited++;v.visit(n);}if(n.editable())return;for(int i=0;i<n.children()&&!s.truncated;i++){Node c=(Node)n.child(i);if(c!=null)try{walk(c,v,s,depth+1);}finally{c.close();}}}
}
