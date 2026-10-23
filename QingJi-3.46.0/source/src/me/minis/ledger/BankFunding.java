package me.minis.ledger;
import android.content.ContentValues;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import java.util.*;
/** Keep both evidence records, but count only the wallet expense when funding is unique. */
final class BankFunding {
 static final String REASON="疑似重复：微信付款与银行卡扣款可能是同一笔，关联记录 #";
 static class Match {long id;boolean strong,bankIncoming;String fingerprint="";}
 static Match match(SQLiteDatabase db,String source,long time,PaymentParser.Result r,boolean originallyConfirmed){
  Match m=new Match();if(!r.kind.equals("expense")||!FundingEvidence.wallet(source)&&!FundingEvidence.bankSource(source))return m;
  int count=0;String oldFunding="",status="",semantic="";long delta=0;
  try(Cursor c=db.rawQuery("SELECT _id,source,funding,status,semantic,fingerprint,time FROM entries WHERE kind='expense' AND cents=? AND status IN ('confirmed','review') AND time BETWEEN ? AND ? ORDER BY _id DESC",new String[]{""+r.cents,""+(time-180000),""+(time+180000)})){
   while(c.moveToNext()){if(!FundingEvidence.opposite(source,c.getString(1))||FundingEvidence.conflict(r.funding,c.getString(2)))continue;count++;m.id=c.getLong(0);oldFunding=c.getString(2);status=c.getString(3);semantic=c.getString(4);m.fingerprint=c.getString(5);delta=Math.abs(time-c.getLong(6));}
  }
  if(count==0)return m;m.bankIncoming=FundingEvidence.bankSource(source);
  // A wallet expense can consume exactly one bank charge. Never pair a second notification.
  m.strong=count==1&&delta<=60000&&originallyConfirmed&&(r.confirmed||r.reason.startsWith("疑似重复：3分钟内已有来自")&&r.reason.contains("记录 #"+m.id+"，"))&&status.equals("confirmed")&&!semantic.equals("user-edited")&&FundingEvidence.strong(r.funding,oldFunding)&&(!m.bankIncoming||!CaptureDedup.marked(db,LedgerDb.hash("funding|"+m.fingerprint)));
  if(m.strong){r.confirmed=true;r.reason="已关联银行卡扣款：同银行卡尾号、金额及1分钟内时间唯一匹配，只计微信付款；未按金额单独合并。";}
  else{r.confirmed=false;r.reason=count==1?REASON+m.id+"；缺少唯一银行卡付款证据或记录已手改，请逐笔核对，未自动合并。":"疑似重复：附近有多笔微信或银行卡同金额记录，无法唯一关联，请逐笔核对，未自动合并。";}
  return m;
 }
 static void finish(SQLiteDatabase db,long id,String source,String fp,Match m){if(id<0||m.id<=0)return;
  if(m.strong){long bankId=m.bankIncoming?id:m.id,walletId=m.bankIncoming?m.id:id;ContentValues v=new ContentValues();v.put("status","ignored");v.put("ignored_at",System.currentTimeMillis());v.put("reason","已去重：此银行卡扣款是微信付款的资金来源，计入微信记录 #"+walletId+"；银行卡记录不再重复计入，可在已忽略记录查看。");db.update("entries",v,"_id=?",new String[]{""+bankId});CaptureDedup.mark(db,LedgerDb.hash("funding|"+(m.bankIncoming?m.fingerprint:fp)));}
  else{ContentValues v=new ContentValues();v.put("reason",REASON+id+"；两条记录请逐笔核对，不可一起批量确认。");db.update("entries",v,"_id=? AND status='review' AND semantic<>'user-edited'",new String[]{""+m.id});}
 }
 static boolean suspect(SQLiteDatabase db,long id,String source,String kind,long cents,long time,String funding){if(!kind.equals("expense")||!FundingEvidence.wallet(source)&&!FundingEvidence.bankSource(source))return false;try(Cursor c=db.rawQuery("SELECT source,funding FROM entries WHERE _id<>? AND kind=? AND cents=? AND status IN ('confirmed','review') AND time BETWEEN ? AND ?",new String[]{""+id,kind,""+cents,""+(time-180000),""+(time+180000)})){while(c.moveToNext())if(FundingEvidence.opposite(source,c.getString(0))&&!FundingEvidence.conflict(funding,c.getString(1)))return true;}return false;}
}
