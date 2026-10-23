package me.minis.ledger;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** One transactional ingestion gate. Never match money alone or fuzzy-match independent payments. */
final class CaptureDedup {
 static String base(String s){return s.endsWith(":screen")?s.substring(0,s.length()-7):s;}
 static String channel(String s,String k){if(k.startsWith("alipay-preview:"))return "preview";if(k.startsWith("wechat-receipt:")||k.startsWith("alipay-receipt:"))return "receipt";return s.endsWith(":screen")?"screen":"notification";}
 static String token(String fp,String channel){return LedgerDb.hash("capture-channel|"+fp+"|"+channel);}
 static boolean marked(SQLiteDatabase db,String token){try(Cursor c=db.rawQuery("SELECT 1 FROM ignored_keys WHERE token=?",new String[]{token})){return c.moveToFirst();}}
 static void mark(SQLiteDatabase db,String token){if(marked(db,token))return;ContentValues v=new ContentValues();v.put("token",token);db.insertOrThrow("ignored_keys",null,v);}
 static void remember(SQLiteDatabase db,String source,String key,String fp){mark(db,LedgerDb.hash("fp|"+fp));if(key.startsWith("wechat-receipt:")||key.startsWith("alipay-receipt:")||key.startsWith("alipay-preview:"))mark(db,LedgerDb.hash("receipt|"+source+"|"+key));}
 static boolean trustworthy(PaymentParser.Result r,String key){return r.confirmed||key.contains("-receipt:")&&r.reason.contains("历史")&&!r.reason.contains("无独立")&&!r.reason.contains("无时间");}
 // 0 = continue; 1 = suppressed unchanged; 2 = supplemented canonical row.
 static int apply(SQLiteDatabase db,String source,String key,long time,String raw,PaymentParser.Result r,String txkey,String fp,boolean keepRaw){
  if(source.equals("manual")||key.startsWith("flow:")||r.cents<=0)return 0;
  String ch=channel(source,key),base=base(source);boolean preview=ch.equals("preview");
  if(!base.equals(AlipayPaymentCards.PKG)&&!base.equals("com.tencent.mm"))return 0;
  if(!preview&&!trustworthy(r,key))return 0;
  long before=preview&&key.startsWith("alipay-preview:h:")?3720000:preview?120000:60000;
  ArrayList<Long> ids=new ArrayList<>();ArrayList<String[]> rows=new ArrayList<>();
  try(Cursor c=db.rawQuery("SELECT _id,source,nkey,merchant,status,semantic,fingerprint,txkey,reason FROM entries WHERE source IN (?,?) AND kind=? AND cents=? AND time BETWEEN ? AND ? ORDER BY _id",new String[]{base,base+":screen",r.kind,""+r.cents,""+(time-before),""+(time+(preview?120000:60000))})){
   while(c.moveToNext()){
    String other=channel(c.getString(1),c.getString(2));if(other.equals("preview"))continue;
    if(!txkey.isEmpty()&&!c.getString(7).isEmpty()&&!txkey.equals(c.getString(7)))continue;
    if(!(preview?MerchantIdentity.previewCompatible(r.merchant,c.getString(3)):MerchantIdentity.same(r.merchant,c.getString(3))))continue;
    if(preview){remember(db,source,key,fp);return 1;}
    // A channel can contribute once only. Two notifications/two receipts are two payments.
    if(ch.equals(other)||!ch.equals("notification")&&!other.equals("notification"))continue;
    // Dynamic success pages may still change from order total to paid total; session updater owns them.
    if(ch.equals("screen")||other.equals("screen"))continue;
    if(c.getString(5).equals("user-edited")||c.getString(4).equals("ignored"))continue;
    if(!c.getString(4).equals("confirmed")&&!(other.equals("receipt")&&c.getString(8).contains("历史")&&!c.getString(8).contains("无独立")&&!c.getString(8).contains("无时间")))continue;
    if(marked(db,token(c.getString(6),ch)))continue;
    ids.add(c.getLong(0));rows.add(new String[]{c.getString(3),c.getString(6)});
   }
  }
  if(ids.size()!=1){if(ids.size()>1){r.confirmed=false;r.reason="疑似重复：存在多笔同商户同金额近时记录，无法唯一关联；请逐笔核对，未自动合并。";}return 0;}
  long id=ids.get(0);String[] row=rows.get(0);mark(db,token(row[1],ch));remember(db,source,key,fp);if(!txkey.isEmpty())mark(db,LedgerDb.hash("tx|"+txkey));
  if(!r.funding.isEmpty()){ContentValues f=new ContentValues();f.put("funding",r.funding);db.update("entries",f,"_id=? AND funding=''",new String[]{""+id});}
  ContentValues v=new ContentValues();if(r.confirmed)v.put("status","confirmed");v.put("reason","已去重：同平台通知与页面、同商户同金额及1分钟内时间唯一匹配；保留一笔。原渠道各最多匹配一次，无单号匹配仍可能存在歧义。");
  db.update("entries",v,"_id=?",new String[]{""+id});return 2;
 }
 /** Retire weak unedited previews when stronger evidence arrives, regardless of arrival order. */
 static void retirePreviews(SQLiteDatabase db,long id,String source,String key,long time,PaymentParser.Result r){
  if(id<0||channel(source,key).equals("preview")||!base(source).equals(AlipayPaymentCards.PKG))return;
  try(Cursor c=db.rawQuery("SELECT _id,merchant,nkey,time,semantic,status FROM entries WHERE source=? AND nkey LIKE 'alipay-preview:%' AND kind=? AND cents=? AND time BETWEEN ? AND ?",new String[]{AlipayPaymentCards.PKG+":screen",r.kind,""+r.cents,""+(time-120000),""+(time+3720000)})){
   while(c.moveToNext()){
    if(!MerchantIdentity.previewCompatible(r.merchant,c.getString(1)))continue;
    if(!c.getString(2).startsWith("alipay-preview:h:")&&Math.abs(time-c.getLong(3))>120000)continue;
    if(!c.getString(5).equals("review")||c.getString(4).equals("user-edited"))continue;
    ContentValues v=new ContentValues();v.put("status","ignored");v.put("ignored_at",System.currentTimeMillis());v.put("reason","弱摘要已由完整记录 #"+id+" 替代，不重复待确认；商户采用完整凭证名称");db.update("entries",v,"_id=?",new String[]{""+c.getLong(0)});
   }
  }
 }
}
