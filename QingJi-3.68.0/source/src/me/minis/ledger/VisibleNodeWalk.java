package me.minis.ledger;
/** Bounded, visible-only traversal. No raw node text is kept by this policy. */
public final class VisibleNodeWalk {
 public static final int MAX_NODES=1600, MAX_DEPTH=64;
 public interface Node {boolean visible();boolean password();int children();Node child(int index);void close();}
 public interface Visitor {void visit(Node node);}
 public static final class Stats {public int visited,hidden,deepest,examined;public boolean truncated;}
 public static Stats walk(Node root,Visitor visitor){Stats s=new Stats();walk(root,visitor,s,0);return s;}
 private static void walk(Node n,Visitor v,Stats s,int depth){
  if(n==null||s.truncated)return;
  if(depth>MAX_DEPTH||s.examined>=MAX_NODES){s.truncated=true;return;}s.examined++;
  if(!n.visible()&&depth>0){s.hidden++;return;}
  if(n.password())return;
  s.visited++;s.deepest=Math.max(s.deepest,depth);if(n.visible())v.visit(n);
  for(int i=0;i<n.children()&&!s.truncated;i++){Node c=n.child(i);if(c!=null)try{walk(c,v,s,depth+1);}finally{c.close();}}
 }
}
