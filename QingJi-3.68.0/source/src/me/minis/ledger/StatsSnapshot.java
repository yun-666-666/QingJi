package me.minis.ledger;
import android.database.sqlite.SQLiteDatabase;import java.util.*;
/** Exact-range reuse inside one short read transaction; never retained across refreshes. */
final class StatsSnapshot implements AutoCloseable {
 final SQLiteDatabase db;private final Map<String,long[]> totals=new HashMap<>();private boolean closed;
 StatsSnapshot(SQLiteDatabase db){this.db=db;db.beginTransactionNonExclusive();}
 long[] totals(long start,long end){if(closed)throw new IllegalStateException("Statistics snapshot closed");String key=start+":"+end;long[] value=totals.get(key);if(value==null){value=LedgerStats.totals(db,start,end);totals.put(key,value);}return value;}
 public void close(){if(closed)return;closed=true;totals.clear();try{db.setTransactionSuccessful();}finally{db.endTransaction();}}
}
