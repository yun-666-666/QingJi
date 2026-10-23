package me.minis.ledger;
import android.database.*;import android.database.sqlite.*;import android.content.*;
/** Persistent observation aliases. Exact receipts outrank checkout actions; uncertain revisits stay one candidate. */
final class PaymentPageDb {
 static void install(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS payment_pages(source TEXT NOT NULL,signature TEXT NOT NULL,frame TEXT NOT NULL,action TEXT NOT NULL,entry INTEGER NOT NULL,seen INTEGER NOT NULL,PRIMARY KEY(source,signature,frame,action))");db.execSQL("CREATE INDEX IF NOT EXISTS page_entry ON payment_pages(entry)");}
 static boolean apply(SQLiteDatabase db,String source,long now,PaymentParser.Result r){
  if(r.pageSignature.isEmpty())return false;boolean fresh=!r.checkout.isEmpty();
  // Real order identity is not invalidated by a later click. Same checkout remains the same payment across node changes.
  try(Cursor c=db.rawQuery("SELECT p.entry,p.frame,p.action,p.seen,e.status FROM payment_pages p LEFT JOIN entries e ON e._id=p.entry WHERE p.source=? AND p.signature=? ORDER BY p.seen DESC LIMIT 32",new String[]{source,r.pageSignature})){
   while(c.moveToNext()){boolean sameAction=fresh&&r.checkout.equals(c.getString(2));boolean samePage=r.pageFrame.equals(c.getString(1));boolean burst=now>=c.getLong(3)&&now-c.getLong(3)<=2000;
    if(r.pageStrong||sameAction||!fresh&&burst||!fresh&&samePage&&(c.isNull(4)||"review".equals(c.getString(4))||"ignored".equals(c.getString(4)))){remember(db,source,now,r,c.getLong(0));return true;}
   }
  }
  if(fresh||r.pageStrong)return false;
  // Without a transaction number/new checkout, similarity cannot prove another debit. Never auto-count it twice.
  try(Cursor c=db.rawQuery("SELECT _id,status FROM entries WHERE source=? AND kind=? AND cents=? AND merchant=? AND (nkey LIKE 'screen:%') AND time>=? AND time<=? ORDER BY CASE status WHEN 'confirmed' THEN 0 WHEN 'ignored' THEN 1 ELSE 2 END,_id LIMIT 1",new String[]{source,r.kind,""+r.cents,r.merchant,""+(now-86400000L),""+now})){
   if(c.moveToFirst()){long prior=c.getLong(0);try(Cursor pending=db.rawQuery("SELECT _id FROM entries WHERE source=? AND status='review' AND reason LIKE ? ORDER BY _id LIMIT 1",new String[]{source,"%记录 #"+prior+"，%"})){if(pending.moveToFirst()){remember(db,source,now,r,pending.getLong(0));return true;}}
    r.confirmed=false;r.reason="疑似重复：已有付款页面记录 #"+prior+"，本次没有新付款动作或独立交易号，不能证明又扣了一笔；暂不重复计入支出。不同交易请核对确认。";
   }
  }
  return false;
 }
 static void remember(SQLiteDatabase db,String source,long now,PaymentParser.Result r,long entry){if(r.pageSignature.isEmpty()||entry<0)return;ContentValues v=new ContentValues();v.put("source",source);v.put("signature",r.pageSignature);v.put("frame",r.pageFrame);v.put("action",r.checkout);v.put("entry",entry);v.put("seen",now);db.insertWithOnConflict("payment_pages",null,v,SQLiteDatabase.CONFLICT_REPLACE);db.execSQL("DELETE FROM payment_pages WHERE rowid NOT IN (SELECT rowid FROM payment_pages ORDER BY seen DESC LIMIT 512)");}
}
