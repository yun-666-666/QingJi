package me.minis.ledger;
import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import org.json.*;
/** One local-calendar boundary and integer aggregation contract for all surfaces. */
public final class LedgerStats {
 public static int mode(String mode){return "month".equals(mode)?2:"week".equals(mode)?1:0;}
 public static long[] range(long anchor,int mode,boolean throughToday,long now){long[] b=WidgetPeriods.bounds(anchor,mode);long end=b[3];if(throughToday&&b[2]==WidgetPeriods.bounds(now,mode)[2])end=Math.min(end,WidgetPeriods.throughToday(now));return new long[]{b[2],end};}
 public static long[] totals(SQLiteDatabase db,long start,long end){try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN kind='expense' THEN cents ELSE 0 END),0),COALESCE(SUM(CASE WHEN kind='income' THEN cents ELSE 0 END),0),COALESCE(SUM(CASE WHEN kind='refund' THEN cents ELSE 0 END),0),count(*) FROM entries WHERE status='confirmed' AND excluded=0 AND time>=? AND time<?",new String[]{""+start,""+end})){c.moveToFirst();return new long[]{c.getLong(0),c.getLong(1),c.getLong(2),c.getLong(3)};}}
 public static JSONObject json(SQLiteDatabase db,long start,long end)throws JSONException{long[] t=totals(db,start,end);return new JSONObject().put("start",start).put("end",end).put("expense",t[0]).put("income",t[1]).put("refund",t[2]).put("net",t[0]-t[2]).put("count",t[3]);}
 public static long amount(long[] t,boolean income,boolean refund){return t[0]-(income?t[1]:refund?t[2]:0);}
}
