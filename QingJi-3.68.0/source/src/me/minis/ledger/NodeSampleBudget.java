package me.minis.ledger;
import java.nio.charset.StandardCharsets;
/** Independent evidence lanes: root cannot consume event/query allowance. */
public final class NodeSampleBudget {
 public static final int ROOT_BYTES=63488,EVENT_BYTES=12288,QUERY_BYTES=16384,MAX_BYTES=98304;
 public static final int ROOT_NODES=1300,EVENT_NODES=200,QUERY_NODES=100,MAX_NODES=1600;
 private final int byteLimit,nodeLimit;private int bytes=2,nodes=0;private boolean truncated=false;
 public NodeSampleBudget(int b,int n){byteLimit=b;nodeLimit=n;}
 public boolean add(String row){int size=row.getBytes(StandardCharsets.UTF_8).length+(nodes>0?1:0);if(nodes>=nodeLimit||bytes+size>byteLimit){truncated=true;return false;}bytes+=size;nodes++;return true;}
 public boolean available(){return nodes<nodeLimit&&bytes<byteLimit;}
 public void cut(){truncated=true;}public int bytes(){return bytes;}public int nodes(){return nodes;}public boolean truncated(){return truncated;}
}
