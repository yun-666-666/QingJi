package me.minis.ledger;
import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import android.content.ContentValues;
/** Preview is uncertain evidence, never a second automatic expense or authority to merge payments. */
final class AlipayPreviewDb {
 static boolean preview(String source,String key){return source.equals(AlipayPaymentCards.PKG+":screen")&&key.startsWith("alipay-preview:");}
 static boolean suppress(SQLiteDatabase db,String source,String key,long time,PaymentParser.Result r){
  if(!preview(source,key))return false;r.confirmed=false;
  // Relative time rounds; suppress repeated summary candidates, never full receipts.
  long before=key.startsWith("alipay-preview:h:")?3720000:120000;
  try(Cursor c=db.rawQuery("SELECT _id FROM entries WHERE source IN (?,?) AND kind=? AND cents=? AND merchant=? AND (time BETWEEN ? AND ? OR nkey LIKE 'alipay-preview:h:%' AND time BETWEEN ? AND ?) LIMIT 1",new String[]{AlipayPaymentCards.PKG,AlipayPaymentCards.PKG+":screen",r.kind,""+r.cents,r.merchant,""+(time-before),""+(time+120000),""+(time-120000),""+(time+3720000)})){return c.moveToFirst();}
 }
 static void relate(SQLiteDatabase db,String source,String key,long time,PaymentParser.Result r){
  if(!(source.equals(AlipayPaymentCards.PKG)||source.equals(AlipayPaymentCards.PKG+":screen")&&key.startsWith("alipay-receipt:")))return;
  try(Cursor c=db.rawQuery("SELECT _id,status,merchant FROM entries WHERE source=? AND nkey LIKE 'alipay-preview:%' AND cents=? AND kind=? AND (time BETWEEN ? AND ? OR nkey LIKE 'alipay-preview:h:%' AND time BETWEEN ? AND ?)",new String[]{AlipayPaymentCards.PKG+":screen",""+r.cents,r.kind,""+(time-120000),""+(time+120000),""+(time-120000),""+(time+3720000)})){while(c.moveToNext())if(c.getString(1).equals("confirmed")&&MerchantIdentity.previewCompatible(r.merchant,c.getString(2))){r.confirmed=false;r.reason="疑似重复：最近消息摘要已被确认为记录 #"+c.getLong(0)+"；完整凭证保留待核对，未自动合并。"+r.reason;}}
 }
 static void retire(SQLiteDatabase db,long id,String source,String key,long time,PaymentParser.Result r){
  if(id<0||!(source.equals(AlipayPaymentCards.PKG)||source.equals(AlipayPaymentCards.PKG+":screen")&&key.startsWith("alipay-receipt:")))return;
  // Only unconfirmed previews are superseded; full payments, user-confirmed or ignored rows are untouched.
  ContentValues v=new ContentValues();v.put("status","ignored");v.put("ignored_at",System.currentTimeMillis());v.put("reason","摘要已有更完整的同商户同金额近时凭证 #"+id+"，摘要不再重复待确认；这不是交易唯一身份认定，请核对完整凭证");
  db.update("entries",v,"source=? AND nkey LIKE 'alipay-preview:%' AND status='review' AND merchant=? AND cents=? AND kind=? AND (time BETWEEN ? AND ? OR nkey LIKE 'alipay-preview:h:%' AND time BETWEEN ? AND ?)",new String[]{AlipayPaymentCards.PKG+":screen",r.merchant,""+r.cents,r.kind,""+(time-120000),""+(time+120000),""+(time-120000),""+(time+3720000)});
 }
}
