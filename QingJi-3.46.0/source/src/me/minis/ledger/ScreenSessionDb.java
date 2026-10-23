package me.minis.ledger;
import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import android.content.ContentValues;
/** Only updates the exact live screen session, never nearby independent transactions. */
final class ScreenSessionDb {
 static String group(String key){if(key==null||!key.matches("screen:[0-9a-fA-F-]{36}:[0-9a-f]{64}"))return "";return key.substring(0,key.indexOf(':',7));}
 // 0 no matching session; 1 consumed unchanged; 2 updated.
 static int update(SQLiteDatabase db,String source,String key,long time,String raw,PaymentParser.Result r,boolean keepRaw){
  if(!source.endsWith(":screen"))return 0;String group=group(key);if(group.isEmpty())return 0;
  try(Cursor c=db.rawQuery("SELECT _id,cents,merchant,kind,status,semantic,time,raw,funding,reason FROM entries WHERE source=? AND nkey LIKE ? ORDER BY _id DESC LIMIT 1",new String[]{source,group+":%"})){
   if(!c.moveToFirst())return 0;String status=c.getString(4),merchant=c.getString(2);long old=c.getLong(1);
   if(status.equals("ignored")||c.getString(5).equals("user-edited"))return 1;
   if(!r.kind.equals(c.getString(3))||!merchant.isEmpty()&&!r.merchant.isEmpty()&&!merchant.equals(r.merchant)||time<c.getLong(6)||time-c.getLong(6)>180000)return 0;
   if((c.getString(5).startsWith("paid:")||c.getString(7).contains("实付依据：明确实付或订单减优惠"))&&!r.paidEvidence)return 1;
   if(status.equals("review")&&c.getString(9).contains("疑似重复")){r.confirmed=false;r.reason=c.getString(9);}
   if(status.equals("review")&&r.confirmed&&!r.paidEvidence)return 1;
   if(old==r.cents&&merchant.equals(r.merchant)&&status.equals(r.confirmed?"confirmed":"review")&&(!r.paidEvidence||c.getString(5).startsWith("paid:"))){if(c.getString(8).isEmpty()&&!r.funding.isEmpty()){ContentValues f=new ContentValues();f.put("funding",r.funding);db.update("entries",f,"_id=?",new String[]{""+c.getLong(0)});return 2;}return 1;}
   if(old!=r.cents&&!r.paidEvidence){r.confirmed=false;r.reason="同一支付页面金额发生变化，已替换旧金额候选，不重复记账；实付证据尚不充分，请核对。"+r.reason;}
   ContentValues v=new ContentValues();if(!r.funding.isEmpty()&&c.getString(8).isEmpty())v.put("funding",r.funding);v.put("cents",r.cents);v.put("merchant",r.merchant.isEmpty()?merchant:r.merchant);v.put("category",r.category);v.put("status",r.confirmed?"confirmed":"review");v.put("reason",r.reason);v.put("nkey",key);v.put("semantic",(r.paidEvidence?"paid:":"")+LedgerDb.hash(r.kind+"|"+r.cents+"|"+r.merchant+"|"+r.confirmed));v.put("raw",keepRaw?raw:"");
   db.update("entries",v,"_id=?",new String[]{""+c.getLong(0)});return 2;
  }
 }
}
