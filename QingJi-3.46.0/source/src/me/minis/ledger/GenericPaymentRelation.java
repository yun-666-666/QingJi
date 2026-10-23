package me.minis.ledger;
import android.content.ContentValues;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import java.util.*;
/** A cross-app payment match is only a relation, never merge by same price. */
final class GenericPaymentRelation {
 static boolean generic(String source){return source.endsWith(":screen")&&!ScreenParser.supported(CaptureDedup.base(source));}
 static List<Long> candidates(SQLiteDatabase db,long exclude,String source,String kind,long cents,long time){List<Long>ids=new ArrayList<>();if(!kind.equals("expense")||source.equals("manual"))return ids;try(Cursor c=db.rawQuery("SELECT _id,source FROM entries WHERE _id<>? AND source<>? AND source<>'manual' AND kind=? AND cents=? AND status IN ('confirmed','review') AND time BETWEEN ? AND ? ORDER BY _id DESC LIMIT 40",new String[]{""+exclude,source,kind,""+cents,""+(time-180000),""+(time+180000)})){while(c.moveToNext())if(generic(source)||generic(c.getString(1)))ids.add(c.getLong(0));}return ids;}
 static List<Long> relate(SQLiteDatabase db,String source,long time,PaymentParser.Result r){List<Long>ids=candidates(db,0,source,r.kind,r.cents,time);if(!ids.isEmpty()){r.confirmed=false;r.reason="疑似重复：应用付款成功页与其他来源有近时同金额记录 #"+ids.get(0)+"，可能是同次付款的不同凭证；未按金额合并，请逐笔核对。";}return ids;}
 static void finish(SQLiteDatabase db,long id,List<Long>others){if(id<0)return;for(long other:others){ContentValues v=new ContentValues();v.put("reason","疑似重复：与应用付款成功页或其他来源记录 #"+id+" 金额及时间接近，请逐笔核对，不能一起批量确认。");db.update("entries",v,"_id=? AND status='review' AND semantic<>'user-edited'",new String[]{""+other});}}
}
