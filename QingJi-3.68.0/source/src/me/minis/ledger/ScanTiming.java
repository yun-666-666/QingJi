package me.minis.ledger;
/** Monotonic deadlines: new events may advance a retry, never postpone one. */
public final class ScanTiming {
 private long due=Long.MAX_VALUE,last=-1;
 public long eventDelay(long now,boolean boundary){return boundary||last<0||now<last?0:Math.max(0,32-(now-last));}
 public boolean request(long now,long delay){long next=now+Math.max(0,delay);if(next>=due)return false;due=next;return true;}
 public void started(long now){due=Long.MAX_VALUE;last=now;}
 public void reset(){due=Long.MAX_VALUE;last=-1;}
}
