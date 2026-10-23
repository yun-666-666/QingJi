package me.minis.ledger;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Fuse only a provider/bank identity already resolved by TransactionIdentity. */
final class IdentityEvidence {
 // 0 absent, 1 unchanged, 2 existing candidate supplemented or conflict marked for review.
 static int merge(SQLiteDatabase db,String source,String identity,PaymentParser.Result r){
  if(identity.isEmpty())return 0;
  try(Cursor c=db.rawQuery("SELECT _id,source,cents,kind,merchant,status,semantic,reason FROM entries WHERE txkey=? LIMIT 1",new String[]{identity})){
   if(!c.moveToFirst())return 0;
   if(c.getString(5).equals("ignored")||c.getString(6).equals("user-edited"))return 1;
   boolean conflict=c.getLong(2)!=r.cents||!c.getString(3).equals(r.kind)||!c.getString(4).isEmpty()&&!r.merchant.isEmpty()&&!MerchantIdentity.same(c.getString(4),r.merchant);
   ContentValues values=new ContentValues();
   if(conflict){values.put("status","review");values.put("reason","同一交易标识的证据存在金额、方向或商户冲突，请核对；未重复入账");}
   else if(!c.getString(1).equals(source)){
    if(c.getString(7).contains("冲突")||c.getString(7).contains("疑似重复"))return 1;
    if(r.confirmed)values.put("status","confirmed");
    if(c.getString(4).isEmpty()&&!r.merchant.isEmpty()){values.put("merchant",r.merchant);values.put("category",r.category);}
    values.put("reason","同一交易标识的多来源证据一致，已合并为一笔；"+(r.confirmed?"交易完成证据已校验":"仍需核对原始交易证据"));
   }else return 1;
   db.update("entries",values,"_id=?",new String[]{""+c.getLong(0)});return 2;
  }
 }
}
