package me.minis.ledger;
import android.content.ContentValues;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import java.util.*;
/** Shared ingestion gate for every provider. Amount alone is never an identity. */
final class ReceiptDedup {
 static boolean receipt(String key){return key!=null&&key.contains("-receipt:");}
 static boolean unknown(String key,String reason){return key.startsWith("wechat-receipt:0|")||reason.contains("没有独立")||reason.contains("无独立")||reason.contains("无时间")||reason.contains("时间仅为识别");}
 static boolean compatible(String a,String b){return a.isEmpty()||b.isEmpty()||a.equals(b);}
 static long match(SQLiteDatabase db,String source,String key,long time,PaymentParser.Result r,String txkey,long self){
  if(source.equals("manual")||key.startsWith("flow:")||key.startsWith("alipay-preview:")||!r.checkout.isEmpty()||r.cents<=0||MerchantIdentity.exact(r.merchant).isEmpty())return 0;
  if(r.pageStrong&&txkey.isEmpty())return 0;
  boolean card=r.receiptEvidence||receipt(key),known=r.transactionTime>0||card&&!unknown(key,r.reason);
  if(!card&&!known)return 0;
  // A clipped historical card is weak evidence, not a new debit at the time it was opened.
  long start=known?time-time%60000:TransactionTime.day(time),end=known?start+60000:TransactionTime.nextDay(time);
  if(!known&&!txkey.isEmpty())return 0;
  String base=CaptureDedup.base(source);long found=0,confirmed=0;int count=0,confirmedCount=0;
  try(Cursor c=db.rawQuery("SELECT _id,merchant,txkey,status,nkey,time FROM entries WHERE source IN (?,?) AND kind=? AND cents=? AND time>=? AND time<? AND _id<>? ORDER BY _id",new String[]{base,base+":screen",r.kind,""+r.cents,""+start,""+end,""+self})){
   while(c.moveToNext()){
    if(!MerchantIdentity.same(r.merchant,c.getString(1))||!compatible(txkey,c.getString(2))||!txkey.isEmpty()&&c.getString(2).isEmpty())continue;
    // Two independent notifications or two un-timed messages cannot prove equality.
    if(!card&&!source.endsWith(":screen")&&!receipt(c.getString(4)))continue;
    if(!known&&!c.getString(3).equals("confirmed")&&!receipt(c.getString(4)))continue;
    if(self>0&&!c.getString(3).equals("confirmed"))continue;
    if(self>0&&protectedRow(db,self,c.getLong(0)))continue;
    found=c.getLong(0);count++;if(c.getString(3).equals("confirmed")){confirmed=found;confirmedCount++;}
   }
  }
  return confirmedCount==1?confirmed:confirmedCount>1?0:count==1?found:0;
 }
 static boolean apply(SQLiteDatabase db,String source,String key,long time,PaymentParser.Result r,String txkey,String fp){
  long id=match(db,source,key,time,r,txkey,0);if(id<=0)return false;
  // Timed identities may be remembered. An undated card remains a same-day weak observation,
  // not a permanent merchant/amount ban that would swallow tomorrow's purchase.
  if(r.transactionTime>0||receipt(key)&&!unknown(key,r.reason))CaptureDedup.remember(db,source,key,fp);
  return true;
 }
 private static long pagePeer(SQLiteDatabase db,long id,String source,String kind,long cents,String merchant,String tx){long found=0;int count=0;try(Cursor c=db.rawQuery("SELECT DISTINCT e._id,e.merchant,e.txkey FROM payment_pages a JOIN payment_pages b ON a.source=b.source AND a.signature=b.signature AND a.frame=b.frame JOIN entries e ON e._id=b.entry WHERE a.entry=? AND a.action='' AND b.entry<>? AND e.source=? AND e.status='confirmed' AND e.kind=? AND e.cents=?",new String[]{""+id,""+id,source,kind,""+cents})){while(c.moveToNext())if(MerchantIdentity.same(merchant,c.getString(1))&&compatible(tx,c.getString(2))&&!protectedRow(db,id,c.getLong(0))){found=c.getLong(0);count++;}}return count==1?found:0;}
 private static boolean protectedRow(SQLiteDatabase db,long id,long peer){try(Cursor c=db.rawQuery("SELECT 1 FROM finance WHERE (entry=? OR peer=?) AND type NOT IN ('evidence','source') LIMIT 1",new String[]{""+id,""+id})){return c.moveToFirst();}}
 /** Retire only unedited pending observations with a unique confirmed counterpart; never delete confirmed rows. */
 static int reconcile(SQLiteDatabase db){int changed=0;List<Long> retire=new ArrayList<>(),peers=new ArrayList<>();
  try(Cursor c=db.rawQuery("SELECT _id,source,nkey,time,kind,cents,merchant,reason,txkey FROM entries WHERE status='review' AND semantic<>'user-edited' AND source<>'manual' ORDER BY _id",null)){
   while(c.moveToNext()){
    String key=c.getString(2);if(!receipt(key)){if(key.startsWith("screen:")){long peer=pagePeer(db,c.getLong(0),c.getString(1),c.getString(4),c.getLong(5),c.getString(6),c.getString(8));if(peer>0){retire.add(c.getLong(0));peers.add(peer);}}continue;}
    PaymentParser.Result r=new PaymentParser.Result();r.receiptEvidence=true;r.kind=c.getString(4);r.cents=c.getLong(5);r.merchant=c.getString(6);r.reason=c.getString(7);if(!unknown(key,r.reason))r.transactionTime=c.getLong(3);
    long id=match(db,c.getString(1),key,c.getLong(3),r,c.getString(8),c.getLong(0));if(id>0){retire.add(c.getLong(0));peers.add(id);}
   }
  }
  for(int i=0;i<retire.size();i++){ContentValues v=new ContentValues();v.put("status","ignored");v.put("ignored_at",System.currentTimeMillis());v.put("reason","自动去重：已有记录 #"+peers.get(i)+"，同来源、商户、金额及方向，交易时间、完整页面或同日无时间凭证唯一匹配；已移出待确认，可从已忽略记录恢复");changed+=db.update("entries",v,"_id=? AND status='review' AND semantic<>'user-edited'",new String[]{""+retire.get(i)});}
  return changed;
 }
}
