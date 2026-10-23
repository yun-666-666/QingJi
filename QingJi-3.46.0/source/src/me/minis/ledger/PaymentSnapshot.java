package me.minis.ledger;

import java.util.*;

/** Immutable values outlive the framework nodes; snapshots are never persisted. */
public final class PaymentSnapshot {
 public static final class Node {
  public final int id,parent,window,left,top,right,bottom;
  public final String resource,role,text,description;
  public final boolean editable;
  public Node(int id,int parent,int window,int left,int top,int right,int bottom,String resource,String role,String text,String description,boolean editable){
   this.id=id;this.parent=parent;this.window=window;this.left=left;this.top=top;this.right=right;this.bottom=bottom;
   this.resource=resource;this.role=role;this.text=text;this.description=description;this.editable=editable;
  }
  public List<String> lines(){List<String> lines=PaymentNodeText.lines(text,description);return lines==null?Collections.emptyList():lines;}
 }
 public final String pkg;public final long version;public final int window,width,height,left,top;
 public final List<Node> nodes;public final boolean truncated,editable;
 public PaymentSnapshot(String pkg,long version,int window,int left,int top,int width,int height,List<Node> nodes,boolean truncated,boolean editable){
  this.pkg=pkg;this.version=version;this.window=window;this.left=left;this.top=top;this.width=width;this.height=height;
  this.nodes=Collections.unmodifiableList(new ArrayList<>(nodes));this.truncated=truncated;this.editable=editable;
 }
 public List<String> lines(){List<String> out=new ArrayList<>();for(Node n:nodes)out.addAll(n.lines());return out;}
 public Node success(){for(Node n:nodes)for(String line:n.lines())if(PaymentPageRules.success(line)&&n.lines().size()==1)return n;return null;}
}
